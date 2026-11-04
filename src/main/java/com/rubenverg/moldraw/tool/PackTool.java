package com.rubenverg.moldraw.tool;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import com.rubenverg.moldraw.core.pack.*;

/** Standalone Java25 authoring CLI. Does not start or require Minecraft. */
public final class PackTool {

    private PackTool() {}

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    public static int run(String[] args, PrintStream output, PrintStream error) {
        try {
            List<String> arguments = new ArrayList<>(List.of(args));
            String version = "0.7.0", only = null, audit = null;
            for (String flag : List.of("--mod-version", "--id", "--audit")) {
                int index = arguments.indexOf(flag);
                if (index >= 0) {
                    if (index + 1 >= arguments.size()) throw new PackException("Missing value for " + flag);
                    String value = arguments.remove(index + 1);
                    arguments.remove(index);
                    if (arguments.contains(flag)) throw new PackException("Duplicate " + flag);
                    if (flag.equals("--mod-version")) version = value;
                    else if (flag.equals("--id")) only = value;
                    else audit = value;
                }
            }
            if (arguments.isEmpty() || arguments.equals(List.of("--help"))) {
                usage(output);
                return 0;
            }
            String command = arguments.getFirst();
            if (command.equals("install-check") && arguments.size() == 2 && only == null && audit == null) {
                var mods = Path.of(arguments.get(1));
                com.rubenverg.moldraw.core.InstallationScanner.requireSingle(mods);
                output.println(
                    "GTMolDraw installation candidates: " + com.rubenverg.moldraw.core.InstallationScanner.find(mods));
                return 0;
            }
            if (command.equals("import-mol") && arguments.size() == 3 && only != null && audit == null) {
                Path input = Path.of(arguments.get(1)), target = Path.of(arguments.get(2))
                    .toAbsolutePath()
                    .normalize();
                if (!target.getFileName()
                    .toString()
                    .endsWith(".jsonc")) throw new PackException("Import output must end in .jsonc");
                if (Files.exists(target)) throw new PackException("Import output already exists: " + target);
                byte[] bytes;
                try (var stream = Files.newInputStream(input)) {
                    bytes = stream.readNBytes(2_097_153);
                }
                if (bytes.length > 2_097_152) throw new PackException("MOL exceeds 2 MiB");
                String source = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
                var molecule = com.rubenverg.moldraw.core.MolV2000.parse(input.toString(), source, only);
                String json = new com.google.gson.GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(molecule);
                String formatted = JsoncFormatter
                    .format(Jsonc.parse(target.toString(), json), ContractSchema.bundled("definition"));
                Files.createDirectories(target.getParent());
                String hash = sourceHash(bytes);
                Files.writeString(
                    target,
                    "// MOL V2000 import; no material binding.\n// source SHA-256: " + hash + "\n" + formatted,
                    java.nio.charset.StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
                output.println("Imported unbound molecule: " + target);
                return 0;
            }
            if (command.equals("review-audit") && arguments.size() == 3 && only == null && audit == null) {
                AuditReview.write(Path.of(arguments.get(1)), Path.of(arguments.get(2)));
                output.println("Portable audit review: " + arguments.get(2));
                return 0;
            }
            if (command.equals("migrate") && arguments.size() == 4 && only == null) {
                LegacyMigrator
                    .migrate(Path.of(arguments.get(1)), Path.of(arguments.get(2)), Path.of(arguments.get(3)), version);
                output.println("Migrated and validated new pack directory: " + arguments.get(3));
                return 0;
            }
            boolean write = command.equals("format") && arguments.size() == 3
                && arguments.get(1)
                    .equals("--write");
            boolean check = command.equals("format") && arguments.size() == 3
                && arguments.get(1)
                    .equals("--check");
            if (!(command.equals("validate") && arguments.size() == 2
                || command.equals("preview") && arguments.size() == 3
                || check
                || write)) throw new PackException("Invalid arguments; use --help");
            if ((only != null || audit != null) && !command.equals("preview"))
                throw new PackException("--id is only available for preview");
            Path root = Path.of(arguments.get(check || write ? 2 : 1))
                .toAbsolutePath()
                .normalize();
            PackCatalog catalog = PackCompiler.compile(root, version);
            output.println(
                "Validated " + catalog.packCount()
                    + " packs; "
                    + catalog.definitions()
                        .size()
                    + " effective definitions; "
                    + catalog.molecules()
                        .size()
                    + " expanded geometries.");
            for (String warning : catalog.warnings()) output.println("WARNING " + warning);
            if (command.equals("format")) {
                Map<Path, String> changes = new LinkedHashMap<>();
                for (var document : catalog.documents()) {
                    ContractSchema schema = ContractSchema.bundled(
                        Path.of(document.source())
                            .getFileName()
                            .toString()
                            .equals("pack.jsonc") ? "pack" : "definition");
                    String formatted = JsoncFormatter.format(document, schema);
                    if (!Jsonc.sameValue(
                        document.value(),
                        Jsonc.parse(document.source(), formatted)
                            .value()))
                        throw new PackException("Formatter changed values: " + document.source());
                    if (!JsoncFormatter.format(Jsonc.parse(document.source(), formatted), schema)
                        .equals(formatted))
                        throw new PackException("Formatter idempotence failure: " + document.source());
                    if (!document.text()
                        .equals(formatted)) changes.put(Path.of(document.source()), formatted);
                }
                for (var change : changes.entrySet())
                    if (write) PreviewWriter.atomic(change.getKey(), change.getValue());
                    else output.println("NEEDS_FORMAT " + change.getKey());
                output.println((write ? "Formatted " : "Formatting differences: ") + changes.size() + " files.");
                return !write && !changes.isEmpty() ? 1 : 0;
            }
            if (command.equals("preview")) {
                Path destination = Path.of(arguments.get(2))
                    .toAbsolutePath()
                    .normalize();
                if (destination.startsWith(root) || root.startsWith(destination))
                    throw new PackException("Preview output and source packs must not overlap");
                Path existing = destination;
                while (!Files.exists(existing)) existing = existing.getParent();
                Path resolved = existing.toRealPath()
                    .resolve(existing.relativize(destination))
                    .normalize();
                Path actualRoot = root.toRealPath();
                if (resolved.startsWith(actualRoot) || actualRoot.startsWith(resolved))
                    throw new PackException("Preview output resolves into source packs");
                for (Path p = destination; p != null; p = p.getParent())
                    if (Files.isSymbolicLink(p)) throw new PackException("Symlink in preview output path " + p);
                PreviewWriter.write(catalog, destination, only);
                if (audit != null) PreviewWriter.localize(catalog, destination, Path.of(audit));
                output.println("Preview: " + destination.resolve("index.html"));
            }
            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            error.println("ERROR " + exception.getMessage());
            return 2;
        }
    }

    private static String sourceHash(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                .formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(bytes));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static void usage(PrintStream out) {
        out.println(
            "GTMolDraw pack tool (Java25, offline)\ninstall-check <mods>\nimport-mol <source.mol> <new-output.jsonc> --id PACK:MOLECULE\nvalidate <packs> [--mod-version VERSION]\nformat --check|--write <packs> [--mod-version VERSION]\nreview-audit <snapshot.json> <new-output>\npreview <packs> <output> [--id DEFINITION] [--audit snapshot.json] [--mod-version VERSION]\nmigrate <legacy-assets> <legacy-bindings.json> <new-output> [--mod-version VERSION]\nExit: 0 success, 1 formatting differs, 2 invalid input/IO. This offline tool does not publish game state. In-game loading/reload is available in stage 4; MOL V2000 2D import is available; unsupported chemistry is rejected.");
    }
}
