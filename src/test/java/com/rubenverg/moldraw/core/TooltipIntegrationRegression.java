package com.rubenverg.moldraw.core;

import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

public final class TooltipIntegrationRegression {

    public interface Component {

        int getWidth();

        int getHeight();

        int getSpacing();

        void draw(int x, int y, int z, Object context);

        Component[] paginate(Object context, int width, int height);
    }

    private static void check(boolean b, String message) {
        if (!b) throw new AssertionError(message);
    }

    private static void jar(Path path, boolean payload, String id) throws Exception {
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            if (payload) {
                zip.putNextEntry(new ZipEntry("com/rubenverg/moldraw/MolDraw.class"));
                zip.write(new byte[] { 1 });
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("mcmod.info"));
            zip.write(("[{\"modid\":\"" + id + "\"}]").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static void contract(Class<?> api) throws Exception {
        AtomicInteger draws = new AtomicInteger();
        AtomicBoolean live = new AtomicBoolean(true);
        Object proxy = NativeTooltipComponent
            .create(api, () -> live.get() ? 40 : 0, () -> live.get() ? 60 : 0, (x, y) -> {
                if (live.get()) {
                    check(x == 5 && y == 7, "Draw coordinates");
                    draws.incrementAndGet();
                }
            });
        check(
            api.getMethod("getWidth")
                .invoke(proxy)
                .equals(40),
            "Native width");
        check(
            api.getMethod("getHeight")
                .invoke(proxy)
                .equals(60),
            "Native height");
        var draw = Arrays.stream(api.getMethods())
            .filter(
                m -> m.getName()
                    .equals("draw"))
            .findFirst()
            .orElseThrow();
        draw.invoke(proxy, 5, 7, 0, null);
        check(draws.get() == 1, "Native draw dispatch");
        var paginate = Arrays.stream(api.getMethods())
            .filter(
                m -> m.getName()
                    .equals("paginate"))
            .findFirst()
            .orElseThrow();
        check(java.lang.reflect.Array.getLength(paginate.invoke(proxy, null, 100, 100)) == 1, "Pagination");
        live.set(false);
        check(
            api.getMethod("getWidth")
                .invoke(proxy)
                .equals(0),
            "Stale width");
        draw.invoke(proxy, 5, 7, 0, null);
        check(draws.get() == 1, "Stale draw suppressed");
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 100; i++) {
            check(
                NativeTooltipComponent.state(true, true, true, false) == NativeTooltipComponent.State.HINT,
                "Unshift hint");
            check(
                NativeTooltipComponent.state(true, true, true, true) == NativeTooltipComponent.State.GRAPH,
                "Shift graph");
            check(
                NativeTooltipComponent.state(true, false, true, true) == NativeTooltipComponent.State.NONE,
                "Missing target");
            check(
                NativeTooltipComponent.state(false, true, false, true) == NativeTooltipComponent.State.NONE,
                "Disabled");
        }
        contract(Component.class);
        Path mods = Files.createTempDirectory(
            Path.of(args[0])
                .resolve("build"),
            "installation-regression-");
        jar(mods.resolve("renamed.jar"), true, "gtmoldraw");
        jar(mods.resolve("pack-tool.jar"), false, "other");
        for (String metadata : List.of("{\"modList\":7}", "[".repeat(65) + "]".repeat(65), " ".repeat(2_097_153))) {
            try (var zip = new ZipOutputStream(Files.newOutputStream(mods.resolve("unrelated.jar")))) {
                zip.putNextEntry(new ZipEntry("mcmod.info"));
                zip.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            InstallationScanner.requireSingle(mods);
        }
        InstallationScanner.requireSingle(mods);
        Files.createDirectory(mods.resolve("backup"));
        jar(mods.resolve("backup/archived.jar"), true, "moldraw");
        Files.createDirectory(mods.resolve("1.20.1"));
        jar(mods.resolve("1.20.1/other-version.jar"), true, "moldraw");
        InstallationScanner.requireSingle(mods);
        Files.createDirectory(mods.resolve("1.7.10"));
        jar(mods.resolve("1.7.10/old.jar"), true, "moldraw");
        check(
            InstallationScanner.find(mods)
                .size() == 2,
            "Renamed and old ID duplicates");
        try {
            InstallationScanner.requireSingle(mods);
            throw new AssertionError("Duplicates accepted");
        } catch (IllegalStateException expected) {
            check(
                expected.getMessage()
                    .contains("renamed.jar")
                    && expected.getMessage()
                        .contains("old.jar"),
                "Conflict paths");
        }
        var guard = new com.rubenverg.gtmoldrawbootstrap.DuplicateGuard();
        check(
            guard.getASMTransformerClass().length == 0 && guard.getAccessTransformerClass() == null,
            "Installation guard registered a transformer");
        Path game = Files.createTempDirectory(
            Path.of(args[0])
                .resolve("build"),
            "bootstrap-regression-");
        Files.createDirectory(game.resolve("mods"));
        jar(game.resolve("mods/first.jar"), true, "gtmoldraw");
        jar(game.resolve("mods/second.jar"), true, "moldraw");
        try {
            guard.injectData(Map.of("mcLocation", game.toFile()));
            throw new AssertionError("Early duplicate guard accepted conflict");
        } catch (IllegalStateException expected) {
            check(
                expected.getMessage()
                    .contains("first.jar"),
                "Early duplicate paths missing");
        }
        Files.move(mods.resolve("1.7.10/old.jar"), mods.resolve("1.7.10/old.jar.disabled"));
        InstallationScanner.requireSingle(mods);
        if (args.length > 1) try (var loader = new URLClassLoader(
            new URL[] { Path.of(args[1])
                .toUri()
                .toURL() },
            TooltipIntegrationRegression.class.getClassLoader())) {
                contract(Class.forName("com.slprime.chromatictooltips.api.ITooltipComponent", false, loader));
                System.out.println(
                    "Actual ChromaticTooltips API: measure, paginate, draw callback and stale suppression passed without GL");
            }
        System.out.println(
            "Tooltip integration regression: 400 shift-state transitions, component contract and renamed/nested/disabled JAR detection passed");
    }
}
