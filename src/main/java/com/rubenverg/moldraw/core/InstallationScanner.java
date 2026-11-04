package com.rubenverg.moldraw.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Detect duplicate payloads even when the loader deduplicates their class names. */
public final class InstallationScanner {

    private InstallationScanner() {}

    public static List<Path> find(Path mods) throws IOException {
        if (!Files.isDirectory(mods)) return List.of();
        Path root = mods.toAbsolutePath()
            .normalize();
        TreeSet<Path> found = new TreeSet<>();
        try (var paths = Files.walk(root, 2)) {
            for (Path path : paths.filter(
                p -> Files.isRegularFile(p) && (p.getParent()
                    .equals(root)
                    || p.getParent()
                        .equals(root.resolve("1.7.10"))))
                .toList()) {
                String name = path.getFileName()
                    .toString()
                    .toLowerCase(Locale.ROOT);
                if (!name.endsWith(".jar") && !name.endsWith(".zip")) continue;
                try (var jar = new ZipFile(path.toFile())) {
                    boolean own = jar.getEntry("com/rubenverg/moldraw/MolDraw.class") != null;
                    var metadata = jar.getEntry("mcmod.info");
                    if (!own && metadata != null) {
                        try (var stream = jar.getInputStream(metadata)) {
                            var data = ResourceJson.read(stream);
                            var list = data.isJsonArray() ? data.getAsJsonArray()
                                : data.getAsJsonObject()
                                    .getAsJsonArray("modList");
                            if (list != null) for (var item : list) {
                                var id = item.getAsJsonObject()
                                    .get("modid");
                                if (id != null && Set.of("gtmoldraw", "moldraw")
                                    .contains(id.getAsString())) own = true;
                            }
                        } catch (IllegalArgumentException | IllegalStateException
                            | com.google.gson.JsonParseException malformedMetadata) {
                            // FML handles unrelated malformed metadata.
                        }
                    }
                    if (own) found.add(path.toRealPath());
                } catch (java.util.zip.ZipException invalidArchive) {
                    // FML handles unrelated invalid archives.
                }
            }
        }
        return List.copyOf(found);
    }

    public static void requireSingle(Path mods) throws IOException {
        var found = find(mods);
        if (found.size() > 1) throw new IllegalStateException(
            "Duplicate GTMolDraw installations. Keep exactly one game JAR; move older versions out of mods (including version subdirectories): "
                + found);
    }
}
