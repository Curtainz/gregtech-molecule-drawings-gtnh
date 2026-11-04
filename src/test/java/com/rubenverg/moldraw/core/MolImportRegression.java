package com.rubenverg.moldraw.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import com.google.gson.*;
import com.rubenverg.moldraw.core.pack.*;
import com.rubenverg.moldraw.tool.PackTool;

public final class MolImportRegression {

    private static int assertions;

    private static void check(boolean b, String message) {
        assertions++;
        if (!b) throw new AssertionError(message);
    }

    private static String atom(double x, double y, double z, String element, int charge, int hydrogen) {
        return String.format(
            Locale.ROOT,
            "%10.4f%10.4f%10.4f %-3s%2d%3d%3d%3d%3d%3d",
            x,
            y,
            z,
            element,
            0,
            charge,
            0,
            hydrogen,
            0,
            0);
    }

    private static String mol(String[] atoms, String[] bonds, String props) {
        return "regression\nGTMolDraw\n\n"
            + String.format(Locale.ROOT, "%3d%3d  0  0  0  0            999 V2000\n", atoms.length, bonds.length)
            + String.join("\n", atoms)
            + "\n"
            + (bonds.length == 0 ? "" : String.join("\n", bonds) + "\n")
            + props
            + "M  END\n";
    }

    private static JsonObject parse(String s) {
        return MolV2000.parse("fixture.mol", s, "custom:imported");
    }

    private static void reject(String s) {
        try {
            parse(s);
            throw new AssertionError("Unsupported chemistry accepted");
        } catch (IllegalArgumentException expected) {
            check(
                expected.getMessage()
                    .contains(":"),
                "Missing source diagnostic");
        }
    }

    public static void main(String[] args) throws Exception {
        String water = mol(
            new String[] { atom(0, 0, 0, "O", 0, 0), atom(1, 0, 0, "H", 0, 0), atom(-1, 0, 0, "H", 0, 0) },
            new String[] { "  1  2  1  0", "  1  3  1  0" },
            "");
        var result = parse(water);
        check(
            result.getAsJsonArray("atoms")
                .size() == 3,
            "Water atoms");
        check(
            result.getAsJsonArray("bonds")
                .size() == 2,
            "Water bonds");
        String isotope = mol(new String[] { atom(0, 0, 0, "C", 3, 2) }, new String[] {}, "M  ISO  1   1  13\n");
        var j = parse(isotope);
        var a = j.getAsJsonArray("atoms")
            .get(0)
            .getAsJsonObject();
        check(
            a.get("isotope")
                .getAsInt() == 13
                && a.get("charge")
                    .getAsInt() == 1
                && a.get("hydrogens")
                    .getAsInt() == 1,
            "Isotope/charge/explicit H lost");
        var d = new PackCatalog.Definition(
            "custom",
            "custom:imported",
            "molecule",
            Jsonc.parse("import", new Gson().toJson(j)));
        var graph = new ModuleAssembler(Map.of(d.id(), d)).compile(d.id());
        var scene = graph.scene(symbol -> -1);
        check(
            !scene.atoms()
                .get(0)
                .isInvisible(),
            "Charged isotope carbon hidden");
        check(
            scene.atoms()
                .get(0)
                .element()
                .toString()
                .contains("H"),
            "Explicit H label lost");
        var chg = parse(mol(new String[] { atom(0, 0, 0, "N", 3, 0) }, new String[] {}, "M  CHG  1   1  -1\n"));
        check(
            chg.getAsJsonArray("atoms")
                .get(0)
                .getAsJsonObject()
                .get("charge")
                .getAsInt() == -1,
            "M CHG override");
        check(
            parse(mol(new String[] { atom(0, 0, 0, "D", 0, 0) }, new String[] {}, "")).getAsJsonArray("atoms")
                .get(0)
                .getAsJsonObject()
                .get("isotope")
                .getAsInt() == 2,
            "Deuterium normalization");
        for (int stereo : new int[] { 1, 6 }) {
            var wedge = parse(
                mol(
                    new String[] { atom(0, 0, 0, "C", 0, 0), atom(1, 0, 0, "O", 0, 0) },
                    new String[] { "  1  2  1  " + stereo },
                    ""));
            check(
                wedge.getAsJsonArray("bonds")
                    .get(0)
                    .getAsJsonObject()
                    .get("style")
                    .getAsString()
                    .equals(stereo == 1 ? "outward" : "inward"),
                "Wedge direction");
        }
        reject(water.replace("V2000", "V3000"));
        reject(water + "$$$$\n");
        reject(water.replace("  1  2  1  0", "  1  2  4  0"));
        reject(water.replace("  1  2  1  0", "  1  2  1  4"));
        reject(mol(new String[] { atom(0, 0, 1, "C", 0, 0) }, new String[] {}, ""));
        reject(mol(new String[] { atom(0, 0, 0, "C", 4, 0) }, new String[] {}, ""));
        reject(mol(new String[] { atom(0, 0, 0, "R#", 0, 0) }, new String[] {}, ""));
        reject(mol(new String[] { atom(0, 0, 0, "C", 0, 0) }, new String[] {}, "M  RAD  1   1   2\n"));
        reject(isotope.replace("M  ISO  1   1  13", "M  ISO  1   2  13"));
        reject(water.replace("M  END", ""));
        reject(
            mol(
                new String[] { atom(0, 0, 0, "C", 0, 0), atom(0, 0, 0, "C", 0, 0) },
                new String[] { "  1  2  1  0" },
                ""));
        Path output = Files.createTempDirectory(
            Path.of(args[0])
                .resolve("build"),
            "mol-regression-");
        Path source = output.resolve("water.mol"), json = output.resolve("water.jsonc");
        Files.writeString(source, water);
        var sink = new PrintStream(new ByteArrayOutputStream());
        String[] command = { "import-mol", source.toString(), json.toString(), "--id", "custom:water" };
        check(PackTool.run(command, sink, sink) == 0, "CLI import");
        byte[] before = Files.readAllBytes(json);
        check(
            PackTool.run(command, sink, sink) == 2 && Arrays.equals(before, Files.readAllBytes(json)),
            "CLI overwrote output");
        check(
            !Files.readString(json)
                .contains("bindings"),
            "Importer bound material");
        System.out.println(
            "MOL V2000 regression: " + assertions
                + " checks passed; chemical metadata, stereo, rejection, layout and CLI no-overwrite");
    }
}
