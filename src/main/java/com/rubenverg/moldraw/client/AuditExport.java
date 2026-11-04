package com.rubenverg.moldraw.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fluids.*;

import com.google.gson.*;
import com.rubenverg.moldraw.*;
import com.rubenverg.moldraw.core.*;
import com.rubenverg.moldraw.core.pack.RuntimeSnapshot;

import gregtech.api.enums.Materials;

/** Capture game-owned labels on the client; calculations and export use only immutable snapshots. */
public final class AuditExport {

    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private record Target(String id, String provider, String defaultName, String localized, String language) {}

    private AuditExport() {}

    public static void start(ICommandSender sender) {
        if (!MolDraw.isClientThread()) throw new IllegalStateException("Audit requires client thread");
        if (!RUNNING.compareAndSet(false, true)) {
            sender.addChatMessage(new ChatComponentText("GTMolDraw: audit already running."));
            return;
        }
        try {
            var mc = Minecraft.getMinecraft();
            String language = mc.getLanguageManager()
                .getCurrentLanguage()
                .getLanguageCode();
            var snapshot = ExternalPackService.snapshot();
            if (!snapshot.enabled()) throw new IllegalStateException("Wait for a valid data pack snapshot");
            List<Target> targets = new ArrayList<>();
            for (Materials m : Materials.getAll()) if (GT5MaterialLookup.isMaterialValid(m)) {
                String localized;
                try {
                    localized = m.getDefaultLocalizedNameForItem("");
                } catch (RuntimeException failure) {
                    localized = m.mDefaultLocalName;
                }
                targets.add(
                    new Target(
                        "gt:" + m.mName,
                        "gt:" + m.mName,
                        Objects.requireNonNullElse(m.mDefaultLocalName, m.mName),
                        Objects.requireNonNullElse(localized, m.mName),
                        language));
            }
            for (Fluid f : FluidRegistry.getRegisteredFluids()
                .values()) {
                Materials m = GT5MaterialLookup.getMaterialFromFluid(f);
                String localized;
                try {
                    localized = f.getLocalizedName(new FluidStack(f, 1000));
                } catch (RuntimeException failure) {
                    localized = f.getName();
                }
                targets.add(
                    new Target(
                        "fluid:" + f.getName(),
                        GT5MaterialLookup.isMaterialValid(m) ? "gt:" + m.mName : "",
                        f.getName(),
                        Objects.requireNonNullElse(localized, f.getName()),
                        language));
            }
            JsonObject metadata = new JsonObject();
            metadata.addProperty("formatVersion", 1);
            metadata.addProperty("modVersion", MolDraw.VERSION);
            metadata.addProperty(
                "createdAt",
                Instant.now()
                    .toString());
            metadata.addProperty("language", language);
            metadata.addProperty("snapshotVersion", MaterialRegistry.version());
            metadata.addProperty("status", ExternalPackService.status());
            metadata.addProperty("recursive", MolDrawConfig.INSTANCE.alloy.recursive);
            metadata.addProperty("partsByMass", MolDrawConfig.INSTANCE.alloy.partsByMass);
            metadata.addProperty("showMolecules", MolDrawConfig.INSTANCE.molecule.showMolecules);
            metadata.addProperty("showAlloys", MolDrawConfig.INSTANCE.alloy.showAlloys);
            JsonArray packs = new JsonArray();
            for (var doc : snapshot.catalog()
                .documents())
                if (Path.of(doc.source())
                    .getFileName()
                    .toString()
                    .equals("pack.jsonc")) packs.add(doc.value());
            metadata.add("packs", packs);
            JsonArray hashes = new JsonArray();
            for (var doc : snapshot.catalog()
                .documents()) {
                JsonObject h = new JsonObject();
                h.addProperty("source", doc.source());
                h.addProperty(
                    "sha256",
                    java.util.HexFormat.of()
                        .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                .digest(
                                    doc.text()
                                        .replace("\r\n", "\n")
                                        .getBytes(StandardCharsets.UTF_8))));
                hashes.add(h);
            }
            metadata.add("sourceHashes", hashes);
            Path output = mc.mcDataDir.toPath()
                .resolve("gtmoldraw-audit");
            boolean recursive = MolDrawConfig.INSTANCE.alloy.recursive;
            Thread worker = new Thread(() -> {
                try {
                    write(output, snapshot, List.copyOf(targets), metadata, recursive);
                    MolDraw.LOGGER.info("Audit completed: {}", output);
                } catch (Exception failure) {
                    MolDraw.LOGGER.error("Audit export failed", failure);
                } finally {
                    RUNNING.set(false);
                }
            }, "gtmoldraw-audit");
            worker.setDaemon(true);
            worker.start();
            sender.addChatMessage(
                new ChatComponentText("GTMolDraw: audit queued -> " + output + "; completion/failure in client log."));
        } catch (Exception failure) {
            RUNNING.set(false);
            MolDraw.LOGGER.error("Audit capture failed", failure);
            sender.addChatMessage(new ChatComponentText("GTMolDraw audit failed: " + failure.getMessage()));
        }
    }

    private static void write(Path output, RuntimeSnapshot snapshot, List<Target> targets, JsonObject metadata,
        boolean recursive) throws Exception {
        JsonArray rows = new JsonArray();
        CompositionResolver resolver = new CompositionResolver(id -> {
            var e = snapshot.entry(id);
            return e == null ? null : e.definition();
        });
        for (Target t : targets.stream()
            .sorted(Comparator.comparing(Target::id))
            .toList()) {
            var e = t.id.startsWith("gt:") ? snapshot.provider(t.id)
                : snapshot.lookup(t.id, t.defaultName, t.provider.isEmpty() ? null : t.provider);
            var d = e.definition();
            var q = resolver.resolve(d.id(), false, recursive);
            var m = resolver.resolve(d.id(), true, recursive);
            JsonObject r = new JsonObject();
            r.addProperty("id", t.id);
            r.addProperty("providerId", t.provider);
            r.addProperty("definitionId", d.id());
            r.addProperty("defaultName", t.defaultName);
            r.addProperty("localizedName", t.localized);
            r.addProperty("nameZhCN", t.language.equalsIgnoreCase("zh_CN") ? t.localized : "");
            r.addProperty(
                "translationState",
                t.language.equalsIgnoreCase("zh_CN") ? "captured_zh_CN" : "missing_zh_CN_select_Chinese_and_export");
            r.addProperty("formula", d.formula());
            try {
                ChemicalFormula.parseExact(d.formula());
                r.addProperty("formulaStatus", "parsed");
            } catch (IllegalArgumentException failure) {
                r.addProperty("formulaStatus", "unsupported_or_unknown");
            }
            r.addProperty("structure", d.structure());
            r.addProperty(
                "structureState",
                e.molecule()
                    .state()
                    .name());
            r.addProperty(
                "compositionState",
                d.state()
                    .name());
            r.addProperty("chartEligible", e.chartEligible());
            r.addProperty("quantityValid", q.valid());
            r.addProperty("massValid", m.valid());
            r.addProperty(
                "quantityResolved",
                q.valid() && q.shares()
                    .size() > 1 && complete(q, false));
            r.addProperty(
                "massResolved",
                m.valid() && m.shares()
                    .size() > 1 && d.mass() != null && complete(m, true));
            r.addProperty("knownMass", d.mass() != null);
            r.addProperty("source", d.source());
            r.addProperty("quantityShares", shares(q));
            r.addProperty("massShares", shares(m));
            TreeSet<String> diagnostics = new TreeSet<>(d.diagnostics());
            diagnostics.addAll(q.diagnostics());
            diagnostics.addAll(m.diagnostics());
            diagnostics.addAll(
                e.molecule()
                    .diagnostics());
            r.addProperty("diagnostics", String.join(";", diagnostics));
            r.addProperty("displayAssessment", "data_only_not_GUI_verified");
            rows.add(r);
        }
        metadata.add("rows", rows);
        AuditFiles.write(output, metadata);
    }

    private static boolean complete(CompositionResolver.Result result, boolean mass) {
        return result.diagnostics()
            .stream()
            .noneMatch(
                d -> d.startsWith("UNKNOWN_SYMBOL:") || d.startsWith("UNSUPPORTED_FORMULA:")
                    || d.startsWith("CYCLE:")
                    || d.startsWith("DEPTH_LIMIT:")
                    || d.startsWith("WORK_LIMIT:")
                    || d.startsWith("ABSENT:")
                    || d.startsWith("INVALID_GT_COMPONENT:")
                    || d.startsWith("AMBIGUOUS_")
                    || d.startsWith("NO_GT_CONTEXT:")
                    || mass && (d.startsWith("MISSING_MASS:") || d.startsWith("MASS_CYCLE_OR_LIMIT:")));
    }

    private static String shares(CompositionResolver.Result result) {
        return result.shares()
            .entrySet()
            .stream()
            .map(
                e -> e.getKey() + "="
                    + e.getValue()
                        .numerator()
                    + "/"
                    + e.getValue()
                        .denominator())
            .collect(java.util.stream.Collectors.joining(";"));
    }

}
