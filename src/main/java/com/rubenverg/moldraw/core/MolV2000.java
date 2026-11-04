package com.rubenverg.moldraw.core;

import java.util.*;

import com.google.gson.*;
import com.rubenverg.moldraw.core.pack.*;

/** Conservative single-record 2D MOL importer. Unsupported chemical information is rejected. */
public final class MolV2000 {

    private static final Set<String> ELEMENTS = Set.of(
        ("H He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca Sc Ti V Cr Mn Fe Co Ni Cu Zn Ga Ge As Se Br Kr Rb Sr Y Zr Nb Mo Tc Ru Rh Pd Ag Cd In Sn Sb Te I Xe Cs Ba La Ce Pr Nd Pm Sm Eu Gd Tb Dy Ho Er Tm Yb Lu Hf Ta W Re Os Ir Pt Au Hg Tl Pb Bi Po At Rn Fr Ra Ac Th Pa U Np Pu Am Cm Bk Cf Es Fm Md No Lr Rf Db Sg Bh Hs Mt Ds Rg Cn Nh Fl Mc Lv Ts Og D T")
            .split(" "));

    private MolV2000() {}

    private static IllegalArgumentException error(String source, int line, String message) {
        return new IllegalArgumentException(source + ":" + line + ": " + message);
    }

    private static String field(String line, int start, int end) {
        return start >= line.length() ? ""
            : line.substring(start, Math.min(end, line.length()))
                .trim();
    }

    private static int integer(String source, int n, String line, int start, int end) {
        String f = field(line, start, end);
        try {
            return f.isEmpty() ? 0 : Integer.parseInt(f);
        } catch (NumberFormatException e) {
            throw error(source, n, "Invalid integer at column " + (start + 1));
        }
    }

    private static double coordinate(String source, int n, String line, int start, int end) {
        try {
            double x = Double.parseDouble(field(line, start, end));
            if (!Double.isFinite(x) || Math.abs(x) > 10000) throw new NumberFormatException();
            return x;
        } catch (NumberFormatException e) {
            throw error(source, n, "Invalid or out-of-bounds coordinate");
        }
    }

    public static JsonObject parse(String source, String text, String id) {
        if (text.length() > 2_097_152) throw error(source, 1, "MOL exceeds 2 MiB");
        String[] lines = text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .split("\n", -1);
        if (lines.length < 5 || !lines[3].stripTrailing()
            .endsWith("V2000")) throw error(source, 4, "Expected MOL V2000; V3000 is not supported");
        int atoms = integer(source, 4, lines[3], 0, 3), bonds = integer(source, 4, lines[3], 3, 6);
        if (atoms < 1 || atoms > 999 || bonds < 0 || bonds > 999 || lines.length < 5 + atoms + bonds)
            throw error(source, 4, "Invalid atom/bond counts or truncated record");
        for (int start : new int[] { 6, 9, 12, 15 }) if (integer(source, 4, lines[3], start, start + 3) != 0)
            throw error(source, 4, "Query, chiral flag or obsolete extension is not representable");
        JsonObject molecule = new JsonObject();
        molecule.addProperty("schemaVersion", 1);
        molecule.addProperty("type", "molecule");
        molecule.addProperty("id", id);
        JsonArray atomList = new JsonArray(), bondList = new JsonArray();
        for (int i = 0; i < atoms; i++) {
            int n = i + 5;
            String line = lines[i + 4];
            if (line.length() < 34) throw error(source, n, "Truncated atom");
            double x = coordinate(source, n, line, 0, 10), y = coordinate(source, n, line, 10, 20),
                z = coordinate(source, n, line, 20, 30);
            if (Math.abs(z) > 0.000001)
                throw error(source, n, "3D coordinates require offline 2D layout before import");
            String element = field(line, 31, 34);
            if (!ELEMENTS.contains(element)) throw error(source, n, "Unsupported element/query atom " + element);
            if (integer(source, n, line, 34, 36) != 0)
                throw error(source, n, "Relative isotope mass is unsupported; export absolute M ISO");
            int code = integer(source, n, line, 36, 39);
            if (code < 0 || code > 7 || code == 4) throw error(source, n, "Radical or invalid charge code");
            int charge = code == 0 ? 0 : 4 - code;
            for (int start : new int[] { 39, 45, 48, 51, 54, 57, 60, 63, 66 })
                if (integer(source, n, line, start, start + 3) != 0) throw error(
                    source,
                    n,
                    "Unsupported atom parity/valence/query/reaction field at column " + (start + 1));
            if (line.length() > 69 && !line.substring(69)
                .isBlank()) throw error(source, n, "Unexpected atom extension");
            int hydrogen = integer(source, n, line, 42, 45);
            if (hydrogen < 0 || hydrogen > 5) throw error(source, n, "Unsupported hydrogen count");
            JsonObject a = new JsonObject();
            a.addProperty("id", "a" + i);
            JsonArray position = new JsonArray();
            position.add(new JsonPrimitive(x));
            position.add(new JsonPrimitive(y));
            a.add("position", position);
            if (element.equals("D") || element.equals("T")) {
                a.addProperty("isotope", element.equals("D") ? 2 : 3);
                element = "H";
            }
            a.addProperty("element", element);
            a.addProperty("labelMode", element.equals("C") ? "skeletal" : "symbol");
            if (charge != 0) a.addProperty("charge", charge);
            if (hydrogen != 0) a.addProperty("hydrogens", hydrogen - 1);
            atomList.add(a);
        }
        Set<String> edges = new HashSet<>();
        for (int i = 0; i < bonds; i++) {
            int n = atoms + i + 5;
            String line = lines[n - 1];
            int from = integer(source, n, line, 0, 3), to = integer(source, n, line, 3, 6),
                order = integer(source, n, line, 6, 9), stereo = integer(source, n, line, 9, 12);
            if (from < 1 || to < 1 || from > atoms || to > atoms || from == to || order < 1 || order > 3)
                throw error(source, n, "Invalid bond or aromatic/query order; export explicit Kekule bonds");
            String edge = Math.min(from, to) + ":" + Math.max(from, to);
            if (!edges.add(edge)) throw error(source, n, "Duplicate bond");
            var fp = atomList.get(from - 1)
                .getAsJsonObject()
                .getAsJsonArray("position");
            var tp = atomList.get(to - 1)
                .getAsJsonObject()
                .getAsJsonArray("position");
            if (Math.hypot(
                fp.get(0)
                    .getAsDouble()
                    - tp.get(0)
                        .getAsDouble(),
                fp.get(1)
                    .getAsDouble()
                    - tp.get(1)
                        .getAsDouble())
                < 0.000001) throw error(source, n, "Zero-length bond");
            if (stereo != 0 && (order != 1 || (stereo != 1 && stereo != 6)))
                throw error(source, n, "Unsupported stereo bond; ambiguous stereo is not discarded");
            for (int start : new int[] { 12, 15, 18 }) if (integer(source, n, line, start, start + 3) != 0)
                throw error(source, n, "Unsupported bond topology/reaction field");
            if (line.length() > 21 && !line.substring(21)
                .isBlank()) throw error(source, n, "Unexpected bond extension");
            JsonObject bond = new JsonObject();
            bond.addProperty("from", "a" + (from - 1));
            bond.addProperty("to", "a" + (to - 1));
            bond.addProperty("order", order);
            if (stereo != 0) bond.addProperty("style", stereo == 1 ? "outward" : "inward");
            bondList.add(bond);
        }
        boolean ended = false, chargeProperties = false;
        Set<Integer> charged = new HashSet<>(), isotopes = new HashSet<>();
        for (int i = 4 + atoms + bonds; i < lines.length; i++) {
            String line = lines[i];
            if (ended) {
                if (!line.isBlank()) throw error(source, i + 1, "Multiple records/SDF data are unsupported");
                continue;
            }
            if (line.stripTrailing()
                .equals("M  END")) {
                ended = true;
                continue;
            }
            String[] values = line.trim()
                .split("\\s+");
            if (values.length < 3 || !values[0].equals("M")
                || !Set.of("CHG", "ISO")
                    .contains(values[1]))
                throw error(source, i + 1, "Unsupported property/alias/group: " + line);
            int count;
            try {
                count = Integer.parseInt(values[2]);
            } catch (NumberFormatException e) {
                throw error(source, i + 1, "Invalid property count");
            }
            if (count < 1 || count > 8 || values.length != 3 + 2 * count)
                throw error(source, i + 1, "Invalid property pairs");
            boolean chg = values[1].equals("CHG");
            if (chg && !chargeProperties) {
                for (var a : atomList) a.getAsJsonObject()
                    .remove("charge");
                chargeProperties = true;
            }
            for (int j = 0; j < count; j++) {
                int atom, value;
                try {
                    atom = Integer.parseInt(values[3 + j * 2]);
                    value = Integer.parseInt(values[4 + j * 2]);
                } catch (NumberFormatException e) {
                    throw error(source, i + 1, "Invalid property value");
                }
                if (atom < 1 || atom > atoms || !(chg ? charged : isotopes).add(atom))
                    throw error(source, i + 1, "Duplicate or out-of-range property atom");
                if (chg ? (value < -16 || value > 16) : (value < 1 || value > 999))
                    throw error(source, i + 1, "Property exceeds supported range");
                atomList.get(atom - 1)
                    .getAsJsonObject()
                    .addProperty(chg ? "charge" : "isotope", value);
            }
        }
        if (!ended) throw error(source, lines.length, "Missing M  END");
        molecule.add("atoms", atomList);
        molecule.add("bonds", bondList);
        ContractSchema.bundled("definition")
            .validate(molecule);
        var document = Jsonc.parse(source, new Gson().toJson(molecule));
        new ModuleAssembler(
            Map.of(id, new PackCatalog.Definition(id.substring(0, id.indexOf(':')), id, "molecule", document)))
                .compile(id);
        return molecule;
    }
}
