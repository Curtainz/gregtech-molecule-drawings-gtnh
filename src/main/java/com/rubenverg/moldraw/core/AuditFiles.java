package com.rubenverg.moldraw.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import com.google.gson.*;

/** Shared, escaped export format; no game or GL dependencies. */
public final class AuditFiles {

    private AuditFiles() {}

    public static String html(String s) {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    public static String csv(String s) {
        String trimmed = s.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public static void write(Path directory, JsonObject snapshot) throws Exception {
        Files.createDirectories(directory);
        for (Path p = directory.toAbsolutePath(); p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IllegalArgumentException("Symlink audit output");
        JsonArray rows = snapshot.getAsJsonArray("rows");
        List<String> columns = rows.size() == 0 ? List.of("id")
            : new ArrayList<>(
                rows.get(0)
                    .getAsJsonObject()
                    .entrySet()
                    .stream()
                    .map(Map.Entry::getKey)
                    .toList());
        StringBuilder materials = new StringBuilder(String.join(",", columns) + "\r\n"),
            fluids = new StringBuilder(materials);
        StringBuilder table = new StringBuilder(
            "<!doctype html><meta charset=\"utf-8\"><title>GTMolDraw 材料与流体对照</title><style>body{font:15px system-ui}td,th{padding:6px;border:1px solid #ccc}table{border-collapse:collapse}input{width:70%;padding:10px}</style><h1>材料与流体对照</h1><p>数据状态不等于 GUI 实测通过。中文名称来自游戏中文环境导出。</p><input placeholder=\"搜索中文、英文、ID、公式或诊断\" oninput=\"for(const r of document.querySelectorAll('tbody tr'))r.hidden=!r.textContent.toLowerCase().includes(this.value.toLowerCase())\"><table><thead><tr>");
        for (String c : columns) table.append("<th>")
            .append(html(c))
            .append("</th>");
        table.append("</tr></thead><tbody>");
        for (var value : rows) {
            JsonObject row = value.getAsJsonObject();
            List<String> cells = new ArrayList<>();
            table.append("<tr>");
            for (String c : columns) {
                String s = row.get(c)
                    .getAsString();
                cells.add(csv(s));
                table.append("<td>")
                    .append(html(s))
                    .append("</td>");
            }
            table.append("</tr>");
            (row.get("id")
                .getAsString()
                .startsWith("fluid:") ? fluids : materials).append(String.join(",", cells))
                    .append("\r\n");
        }
        atomic(directory.resolve("materials.csv"), "\uFEFF" + materials);
        atomic(directory.resolve("fluids.csv"), "\uFEFF" + fluids);
        atomic(
            directory.resolve("snapshot.json"),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(snapshot));
        atomic(directory.resolve("index.html"), table + "</tbody></table>");
    }

    private static void atomic(Path target, String text) throws Exception {
        if (Files.isSymbolicLink(target)) throw new IllegalArgumentException("Symlink output");
        Path temp = Files.createTempFile(target.getParent(), ".audit-", ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
