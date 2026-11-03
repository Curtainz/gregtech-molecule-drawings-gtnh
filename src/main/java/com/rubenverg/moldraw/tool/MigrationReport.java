package com.rubenverg.moldraw.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

import com.google.gson.JsonObject;

/** Human-readable provenance alongside the machine-readable migration report. */
final class MigrationReport {

    private MigrationReport() {}

    static void write(Path directory, JsonObject summary) throws IOException {
        StringBuilder rows = new StringBuilder(), notes = new StringBuilder();
        StringBuilder csv = new StringBuilder("source,definition,status,sha256\n");
        for (var item : summary.getAsJsonArray("sources")) {
            var row = item.getAsJsonObject();
            rows.append("<tr>");
            int column = 0;
            for (String key : List.of("source", "definition", "status", "sha256")) {
                String value = row.get(key)
                    .getAsString();
                rows.append("<td>")
                    .append(html(value))
                    .append("</td>");
                if (column++ > 0) csv.append(',');
                csv.append('"')
                    .append(value.replace("\"", "\"\""))
                    .append('"');
            }
            rows.append("</tr>");
            csv.append('\n');
        }
        for (var note : summary.getAsJsonArray("reviewNotes")) notes.append("<li>")
            .append(html(note.getAsString()))
            .append("</li>");
        Files.writeString(
            directory.resolve("migration-report.csv"),
            "\uFEFF" + csv.toString()
                .replace("\n", "\r\n"),
            StandardCharsets.UTF_8);
        Files.writeString(
            directory.resolve("migration-report.html"),
            "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>GTMolDraw 迁移核对</title><style>body{background:#171b25;color:#e5e8ef;font:15px system-ui;margin:28px}table{border-collapse:collapse;width:100%;font-size:12px}td,th{border:1px solid #4b5567;padding:8px;text-align:left;overflow-wrap:anywhere;max-width:350px}th{background:#303a4b}li{margin:8px 0}a{color:#8ed5ff}</style><h1>第③阶段迁移核对</h1><p>"
                + summary.get("structureSources")
                + " 份结构、"
                + summary.get("compositionSources")
                + " 份组成，生成 "
                + summary.get("definitions")
                + " 条定义。名称绑定结构 "
                + summary.get("effectiveStructureNames")
                + " 份，其余同名结构保留；文件数量不代表GTNH覆盖率。</p><p>bound_by_name：按旧优先级绑定；shadowed_structure_preserved：保留但未绑定；dynamic_gt_candidate：动态GT候选；explicit_mixture_parts：显式材料份数；shadowed_composition：旧命名空间中被遮蔽的组成来源。动态候选不包含GT组成或质量快照。</p><h2>需注意的兼容记录</h2><ul>"
                + notes
                + "</ul><h2>逐资源来源</h2><p><a href=\"migration-report.csv\">下载CSV核对表</a></p><table><thead><tr><th>源文件</th><th>目标定义</th><th>迁移状态</th><th>源SHA-256</th></tr></thead><tbody>"
                + rows
                + "</tbody></table></html>\n",
            StandardCharsets.UTF_8);
    }

    private static String html(String value) {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
