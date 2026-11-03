package com.rubenverg.moldraw.tool;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

import com.google.gson.*;
import com.rubenverg.moldraw.core.*;
import com.rubenverg.moldraw.core.pack.*;

/** Static, network-free preview; uses the same pure layout builders as the game. */
public final class PreviewWriter {

    private static final TextMeasure FONT = new TextMeasure() {

        public int width(String text) {
            return text.codePointCount(0, text.length()) * 6;
        }

        public int lineHeight() {
            return 9;
        }
    };
    private static final Map<String, Integer> CPK = Map.of(
        "H",
        0xFFFFFFFF,
        "C",
        0xFF777777,
        "N",
        0xFF3050F8,
        "O",
        0xFFFF0D0D,
        "F",
        0xFF90E050,
        "Cl",
        0xFF1FF01F,
        "Br",
        0xFFA62929,
        "S",
        0xFFFFFF30,
        "P",
        0xFFFF8000,
        "I",
        0xFF940094);

    private PreviewWriter() {}

    public static void write(PackCatalog catalog, Path output, String only) throws IOException {
        if (only != null && !catalog.definitions()
            .containsKey(only)) throw new PackException("Unknown preview definition " + only);
        List<String> geometry = catalog.molecules()
            .keySet()
            .stream()
            .filter(id -> only == null || id.equals(only))
            .toList();
        List<PackCatalog.Definition> materials = catalog.definitions()
            .values()
            .stream()
            .filter(
                d -> d.type()
                    .equals("material")
                    && (only == null || d.id()
                        .equals(only)))
            .toList();
        if (geometry.size() + materials.size() > 512)
            throw new PackException("Preview exceeds 512 cards; use --id to select a definition");
        if (Files.isSymbolicLink(output)) throw new PackException("Preview output cannot be a symlink");
        if (Files.exists(output)) {
            try (var files = Files.list(output)) {
                if (files.findAny()
                    .isPresent() && !Files.isRegularFile(output.resolve(".gtmoldraw-preview")))
                    throw new PackException("Output is not an owned/empty preview directory");
            }
        }
        Files.createDirectories(output);
        Map<String, String> files = new LinkedHashMap<>();
        StringBuilder cards = new StringBuilder();
        JsonObject report = new JsonObject();
        report.addProperty("kind", "offline-stage-2");
        JsonArray graphs = new JsonArray(), pies = new JsonArray();
        for (String id : geometry) {
            var graph = catalog.molecules()
                .get(id);
            var scene = graph.scene(element -> CPK.getOrDefault(element, 0xFFFFFFFF));
            TooltipLayout layout = new MoleculeLayoutBuilder(scene, 24, 0xFFFFFFFF).build(FONT);
            String file = filename(id) + ".svg";
            files.put(file, svg(layout, id));
            JsonObject item = new JsonObject();
            item.addProperty("id", id);
            item.addProperty(
                "atoms",
                graph.atoms()
                    .size());
            item.addProperty(
                "bonds",
                graph.bonds()
                    .size());
            item.addProperty(
                "doubleBonds",
                graph.bonds()
                    .stream()
                    .filter(
                        b -> b.lines()
                            .equals(List.of(MoleculeScene.Line.SOLID, MoleculeScene.Line.SOLID)))
                    .count());
            item.addProperty(
                "ports",
                graph.ports()
                    .size());
            graphs.add(item);
            cards.append("<article><h3>")
                .append(escape(id))
                .append("</h3><img alt=\"")
                .append(escape(id))
                .append("\" src=\"")
                .append(file)
                .append("\"><p>")
                .append(
                    graph.atoms()
                        .size())
                .append(" atoms · ")
                .append(
                    graph.bonds()
                        .size())
                .append(" bonds · ")
                .append(
                    graph.ports()
                        .size())
                .append(" ports</p></article>");
        }
        StringBuilder materialCards = new StringBuilder();
        Map<String, MaterialDefinition> offline = offline(catalog);
        for (var definition : materials) {
            JsonObject json = definition.json();
            String mode = json.has("display") ? json.getAsJsonObject("display")
                .get("mode")
                .getAsString() : "auto";
            boolean disabled = json.has("disabled") && json.get("disabled")
                .getAsBoolean();
            String message;
            materialCards.append("<article><h3>")
                .append(escape(definition.id()))
                .append("</h3><p>provider: ")
                .append(escape(definition.owner()))
                .append(" · mode: ")
                .append(escape(mode))
                .append("</p>");
            if (disabled || mode.equals("text")) message = "文本/禁用：不生成该材料的图形。";
            else if (json.has("structure") && !mode.equals("composition")) {
                String structure = json.get("structure")
                    .getAsString();
                var graph = catalog.molecules()
                    .get(structure);
                TooltipLayout layout = new MoleculeLayoutBuilder(
                    graph.scene(e -> CPK.getOrDefault(e, 0xFFFFFFFF)),
                    24,
                    0xFFFFFFFF).build(FONT);
                String file = filename(definition.id() + "/material") + ".svg";
                files.put(file, svg(layout, definition.id()));
                materialCards.append("<img alt=\"material diagram\" src=\"")
                    .append(file)
                    .append("\">");
                message = "绑定结构：" + structure;
            } else {
                var result = new CompositionResolver(offline::get).resolve(definition.id(), false, true);
                if (result.valid() && result.shares()
                    .size() > 1) {
                    List<PieLayout.Slice> slices = new ArrayList<>();
                    JsonObject fractions = new JsonObject();
                    for (var entry : CompositionResolver.fold(result.shares(), 8, 0)
                        .entrySet()) {
                        String leaf = entry.getKey();
                        slices.add(
                            new PieLayout.Slice(
                                leaf,
                                entry.getValue(),
                                leaf.substring(leaf.indexOf(':') + 1),
                                palette(leaf)));
                        fractions.addProperty(
                            leaf,
                            entry.getValue()
                                .numerator() + "/"
                                + entry.getValue()
                                    .denominator());
                    }
                    var layout = PieLayout.build(slices, 36, FONT);
                    String file = filename(definition.id() + "/quantity") + ".svg";
                    files.put(file, svg(layout, definition.id()));
                    materialCards.append("<img alt=\"quantity pie\" src=\"")
                        .append(file)
                        .append("\">");
                    JsonObject item = new JsonObject();
                    item.addProperty("id", definition.id());
                    item.add("shares", fractions);
                    pies.add(item);
                    message = "数量份额（非质量）；离线GT叶节点仅保留材料身份。";
                } else message = "没有可离线展开的组成；GT元数据需后续游戏加载阶段提供。";
            }
            materialCards.append("<p>")
                .append(escape(message))
                .append("</p><p>bindings: ")
                .append(
                    escape(
                        json.get("bindings")
                            .toString()))
                .append("</p></article>");
        }
        report.add("graphs", graphs);
        report.add("quantityPies", pies);
        JsonArray warnings = new JsonArray();
        for (String warning : catalog.warnings()) warnings.add(new JsonPrimitive(warning));
        report.add("warnings", warnings);
        files.put(
            "report.json",
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report) + "\n");
        String html = "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>GTMolDraw stage 2 preview</title><style>body{background:#171b25;color:#e5e8ef;font:16px system-ui,sans-serif;margin:32px}h1{font-size:26px}section{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:16px}article{background:#202634;border:1px solid #3c4961;border-radius:8px;padding:18px;overflow:auto}h3{font-size:15px;overflow-wrap:anywhere}img{max-width:100%;min-height:100px;image-rendering:pixelated}p{color:#c2ccdc;font-size:13px;overflow-wrap:anywhere}.notice{max-width:1000px;line-height:1.6}a{color:#8ed5ff}</style><h1>GTMolDraw 第②阶段离线预览</h1><p class=\"notice\">直接查看组装结果，无需阅读JSON。使用与游戏相同的纯布局代码及近似字体，游戏字体可能不同。没有自动补氢、结构推测或联网查询；饼图仅展示可确定的数量份额，未捏造GT质量。此工具不向游戏发布数据。</p><h2>结构定义（不等于材料最终显示选择）</h2><section>"
            + cards
            + "</section><h2>覆盖后的材料显示意图</h2><section>"
            + materialCards
            + "</section><p><a href=\"report.json\">原子/键数量与精确份额报告</a></p></html>\n";
        files.put("index.html", html);
        long bytes = files.values()
            .stream()
            .mapToLong(value -> value.getBytes(StandardCharsets.UTF_8).length)
            .sum();
        if (bytes > 33_554_432) throw new PackException("Preview output exceeds 32 MiB; use --id");
        // All compilation/layout succeeds before output files are changed.
        atomic(output.resolve(".gtmoldraw-preview"), "stage-2\n");
        for (var e : files.entrySet()) atomic(output.resolve(e.getKey()), e.getValue());
    }

    private static Map<String, MaterialDefinition> offline(PackCatalog catalog) {
        Map<String, MaterialDefinition> result = new LinkedHashMap<>();
        for (var definition : catalog.definitions()
            .values())
            if (definition.type()
                .equals("material")) {
                    JsonObject json = definition.json();
                    List<MaterialDefinition.Component> parts = new ArrayList<>();
                    List<String> diagnostics = new ArrayList<>();
                    MaterialDefinition.Basis basis = MaterialDefinition.Basis.MIXTURE_PARTS;
                    if (json.has("composition")) {
                        var c = json.getAsJsonObject("composition");
                        String source = c.get("source")
                            .getAsString();
                        if (source.equals("explicit")) {
                            List<Fraction> amounts = new ArrayList<>();
                            BigInteger denominator = BigInteger.ONE;
                            for (var component : c.getAsJsonArray("components")) {
                                Fraction amount = PackCompiler.amount(
                                    component.getAsJsonObject()
                                        .get("amount"));
                                amounts.add(amount);
                                denominator = denominator.divide(denominator.gcd(amount.denominator()))
                                    .multiply(amount.denominator());
                                PackCompiler.numericBudget(denominator);
                            }
                            int i = 0;
                            for (var component : c.getAsJsonArray("components")) {
                                var amount = amounts.get(i++);
                                BigInteger count = amount.numerator()
                                    .multiply(denominator.divide(amount.denominator()));
                                PackCompiler.numericBudget(count);
                                parts.add(
                                    new MaterialDefinition.Component(
                                        component.getAsJsonObject()
                                            .get("material")
                                            .getAsString(),
                                        count));
                            }
                            basis = MaterialDefinition.Basis.valueOf(
                                c.get("basis")
                                    .getAsString()
                                    .toUpperCase(Locale.ROOT));
                        } else if (source.equals("formula")) {
                            basis = MaterialDefinition.Basis.STOICHIOMETRIC;
                            for (var e : ChemicalFormula.parseExact(
                                json.get("formula")
                                    .getAsString())
                                .entrySet())
                                parts.add(new MaterialDefinition.Component("symbol:" + e.getKey(), e.getValue()));
                        } else diagnostics.add("OFFLINE_GT_DATA:" + definition.id());
                    } else diagnostics.add("OFFLINE_GT_DATA:" + definition.id());
                    boolean disabled = json.has("disabled") && json.get("disabled")
                        .getAsBoolean();
                    result.put(
                        definition.id(),
                        new MaterialDefinition(
                            definition.id(),
                            definition.id(),
                            json.has("formula") ? json.get("formula")
                                .getAsString() : "",
                            "",
                            null,
                            0xFFFFFFFF,
                            disabled ? MaterialDefinition.State.DISABLED : MaterialDefinition.State.PRESENT,
                            basis,
                            parts,
                            "offline",
                            diagnostics));
                }
        Set<String> external = new HashSet<>();
        for (var material : result.values())
            for (var c : material.components()) if (!result.containsKey(c.id())) external.add(c.id());
        for (String id : external) result.put(
            id,
            new MaterialDefinition(
                id,
                id,
                "",
                "",
                null,
                palette(id),
                MaterialDefinition.State.ABSENT,
                MaterialDefinition.Basis.MIXTURE_PARTS,
                List.of(),
                "offline",
                List.of("OFFLINE_IDENTITY:" + id)));
        return result;
    }

    private static int palette(String id) {
        int[] colors = { 0xFF58BFEF, 0xFFEDB953, 0xFFC79CEC, 0xFF72CE9F, 0xFFEB8C97, 0xFFB3C4D8 };
        return colors[Math.floorMod(id.hashCode(), colors.length)];
    }

    private static String filename(String id) {
        try {
            return id.replaceAll("[^a-zA-Z0-9_]", "_") + "-"
                + HexFormat.of()
                    .formatHex(
                        MessageDigest.getInstance("SHA-256")
                            .digest(id.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 12);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private static String paint(int color, boolean opaqueZero) {
        int alpha = color >>> 24;
        if (alpha == 0 && opaqueZero) alpha = 255;
        return "fill=\"" + String.format(Locale.ROOT, "#%06x", color & 0xFFFFFF)
            + "\" fill-opacity=\""
            + (alpha / 255.0)
            + "\"";
    }

    private static String svg(TooltipLayout layout, String id) {
        StringBuilder out = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
            .append((layout.width() + 8) * 3)
            .append("\" height=\"")
            .append((layout.height() + 8) * 3)
            .append("\" viewBox=\"-4 -4 ")
            .append(layout.width() + 8)
            .append(' ')
            .append(layout.height() + 8)
            .append("\"><title>")
            .append(escape(id))
            .append("</title><rect x=\"-4\" y=\"-4\" width=\"100%\" height=\"100%\" fill=\"#202634\"/>");
        for (var command : layout.commands()) {
            if (command instanceof TooltipLayout.Pixels pixels) {
                Map<Integer, StringBuilder> paths = new LinkedHashMap<>();
                for (var p : pixels.pixels()) paths.computeIfAbsent(p.color(), k -> new StringBuilder())
                    .append('M')
                    .append(p.x())
                    .append(' ')
                    .append(p.y())
                    .append("h1v1h-1z");
                for (var e : paths.entrySet()) out.append("<path ")
                    .append(paint(e.getKey(), true))
                    .append(" d=\"")
                    .append(e.getValue())
                    .append("\"/>");
            } else if (command instanceof TooltipLayout.Fill f) out.append("<rect x=\"")
                .append(Math.min(f.x0(), f.x1()))
                .append("\" y=\"")
                .append(Math.min(f.y0(), f.y1()))
                .append("\" width=\"")
                .append(Math.abs(f.x1() - f.x0()))
                .append("\" height=\"")
                .append(Math.abs(f.y1() - f.y0()))
                .append("\" ")
                .append(paint(f.color(), false))
                .append("/>");
            else if (command instanceof TooltipLayout.Text t) out.append("<text x=\"")
                .append(t.x())
                .append("\" y=\"")
                .append(t.y())
                .append("\" font-family=\"monospace\" font-size=\"9\" dominant-baseline=\"hanging\" ")
                .append(paint(t.color(), true))
                .append('>')
                .append(escape(t.text()))
                .append("</text>");
        }
        return out.append("</svg>\n")
            .toString();
    }

    static void atomic(Path target, String text) throws IOException {
        if (Files.isSymbolicLink(target)) throw new PackException("Refusing to replace symlink " + target);
        Path temporary = Files.createTempFile(target.getParent(), ".gtmoldraw-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("Atomic replacement not supported: " + target, unsupported);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
