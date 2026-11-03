package com.rubenverg.moldraw.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import com.rubenverg.moldraw.*;
import com.rubenverg.moldraw.core.pack.*;

/** One worker prepares candidates; only client ticks publish them. No rendering during reload. */
public final class ExternalPackService {

    private static SnapshotStore<RuntimeSnapshot> store;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gtmoldraw-pack-loader");
        t.setDaemon(true);
        return t;
    });
    private static Future<RuntimeSnapshot> pending;
    private static long ticket;
    private static boolean requested, readDisk, announce, noticeShown, pendingDisk;

    private ExternalPackService() {}

    private static void thread() {
        if (!MolDraw.isClientThread()) throw new IllegalStateException("Pack service requires client thread");
    }

    public static void initialize() {
        thread();
        if (store == null) store = new SnapshotStore<>(RuntimeSnapshot.empty());
        request(true, false);
    }

    public static RuntimeSnapshot snapshot() {
        return store == null ? RuntimeSnapshot.empty() : store.value();
    }

    public static String status() {
        thread();
        return "packs=" + (snapshot().catalog() == null ? 0
            : snapshot().catalog()
                .packCount())
            + ", definitions="
            + (snapshot().catalog() == null ? 0
                : snapshot().catalog()
                    .definitions()
                    .size())
            + ", snapshot="
            + (store == null ? 0 : store.version())
            + ", loading="
            + (requested || pending != null)
            + ", "
            + (store == null ? "not initialized"
                : store.error()
                    .isEmpty() ? "lastError=none" : "lastError=" + store.error());
    }

    public static void request(boolean disk, boolean notify) {
        thread();
        if (store == null) store = new SnapshotStore<>(RuntimeSnapshot.empty());
        store.request();
        requested = true;
        readDisk |= disk || pending != null && pendingDisk;
        announce |= notify;
    }

    public static Path directory() {
        return Minecraft.getMinecraft().mcDataDir.toPath()
            .resolve(com.rubenverg.moldraw.core.DataPackContract.PACK_DIRECTORY);
    }

    public static void tick() {
        thread();
        if (store == null) return;
        if (pending != null && pending.isDone()) {
            try {
                RuntimeSnapshot candidate = pending.get();
                if (store.publish(ticket, candidate)) {
                    ResourceData.invalidate();
                    writeDiagnostics(candidate);
                    noticeShown = false;
                    String message = candidate.enabled() ? "GTMolDraw: loaded " + candidate.catalog()
                        .packCount()
                        + " packs / "
                        + candidate.catalog()
                            .definitions()
                            .size()
                        + " definitions; snapshot "
                        + store.version()
                        : "GTMolDraw: no packs installed; graphics disabled. Install packs then /gtmoldraw reload.";
                    MolDraw.LOGGER.info(message);
                    if (announce) chat(message);
                }
            } catch (Exception failure) {
                Throwable cause = failure instanceof ExecutionException ? failure.getCause() : failure;
                if (store.fail(ticket, String.valueOf(cause.getMessage()))) {
                    MolDraw.LOGGER
                        .error("External pack reload rejected; retaining snapshot {}", store.version(), cause);
                    chat(
                        "GTMolDraw reload failed; previous snapshot " + store.version()
                            + " retained. "
                            + cause.getMessage());
                }
            } finally {
                pending = null;
                if (!requested) {
                    announce = false;
                    readDisk = false;
                }
            }
        }
        if (pending == null && requested) {
            requested = false;
            ticket = store.requested();
            boolean disk = readDisk;
            pendingDisk = disk;
            readDisk = false;
            try {
                var input = GTRegistrySnapshot.capture();
                var previous = store.value()
                    .catalog();
                Path root = directory();
                pending = WORKER.submit(() -> {
                    PackCatalog catalog;
                    if (!disk && previous != null) catalog = previous;
                    else {
                        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return RuntimeSnapshot.empty();
                        catalog = PackCompiler.compile(root, MolDraw.VERSION);
                    }
                    return RuntimeSnapshot.bind(catalog, input);
                });
            } catch (Exception failure) {
                store.fail(ticket, failure.toString());
                MolDraw.LOGGER.error("GT snapshot capture failed", failure);
                chat("GTMolDraw reload rejected; previous snapshot retained: " + failure.getMessage());
            }
        }
        if (!snapshot().enabled() && !noticeShown
            && Minecraft.getMinecraft().thePlayer != null
            && pending == null
            && !requested) {
            noticeShown = true;
            chat(
                "GTMolDraw: graphics inactive. Install the data pack at " + directory()
                    + " and run /gtmoldraw reload. "
                    + store.error());
        }
    }

    private static void chat(String message) {
        if (Minecraft.getMinecraft().thePlayer != null)
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(message));
    }

    private static void writeDiagnostics(RuntimeSnapshot value) {
        try {
            Path root = Minecraft.getMinecraft().mcDataDir.toPath()
                .resolve("gtmoldraw-audit");
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) return;
            Path target = root.resolve("pack-diagnostics.txt");
            if (Files.isSymbolicLink(target)) return;
            Files.writeString(target, String.join("\n", value.diagnostics()) + "\n", StandardCharsets.UTF_8);
        } catch (Exception failure) {
            MolDraw.LOGGER.warn("Cannot write pack diagnostics", failure);
        }
    }
}
