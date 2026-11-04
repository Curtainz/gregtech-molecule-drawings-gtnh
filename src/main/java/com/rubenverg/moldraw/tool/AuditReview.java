package com.rubenverg.moldraw.tool;

import java.nio.file.*;

import com.google.gson.*;
import com.rubenverg.moldraw.core.*;

/** Derive a portable review without altering the original live audit. */
public final class AuditReview {

    private AuditReview() {}

    public static void write(Path input, Path output) throws java.io.IOException {
        if (input.toAbsolutePath()
            .normalize()
            .startsWith(
                output.toAbsolutePath()
                    .normalize()))
            throw new IllegalArgumentException("Review output must not contain source audit");
        JsonObject snapshot;
        try (var stream = Files.newInputStream(input)) {
            snapshot = ResourceJson.read(stream, 33_554_432)
                .getAsJsonObject();
        }
        if (!snapshot.has("formatVersion") || snapshot.get("formatVersion")
            .getAsInt() != 1 || !snapshot.has("rows")) throw new IllegalArgumentException("Unsupported audit snapshot");
        snapshot.addProperty("reviewNote", "派生审核：原游戏名称与公式保留，参考译名不代表原版本游戏已翻译；不是新版本实测覆盖率");
        for (var value : snapshot.getAsJsonArray("rows")) {
            var row = value.getAsJsonObject();
            String localized = row.get("localizedName")
                .getAsString();
            row.addProperty("gameLocalizedNameRaw", localized);
            String zh = row.get("nameZhCN")
                .getAsString();
            if (!DisplayNames.chinese(zh)) {
                zh = DisplayNames.reference(
                    row.get("id")
                        .getAsString()
                        .replaceFirst("^[^:]+:", ""));
                row.addProperty(
                    "translationState",
                    zh.isEmpty() ? "missing_translation" : "reference_zh_CN_not_game_translation");
            }
            row.addProperty("nameZhCN", zh);
            if (!DisplayNames.chinese(localized) && !zh.isEmpty()) row.addProperty("localizedName", zh + " [参考译名]");
            if (row.has("formula")) {
                String formula = row.get("formula")
                    .getAsString();
                if (!row.has("formulaRawEscaped"))
                    row.addProperty("formulaRawEscaped", FormulaGlyphs.escapedRaw(formula));
                row.addProperty("formula", FormulaGlyphs.display(formula));
            }
        }
        try {
            AuditFiles.write(output, snapshot);
        } catch (java.io.IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new java.io.IOException("Cannot write audit review", ex);
        }
    }
}
