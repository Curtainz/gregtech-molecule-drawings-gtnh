package com.rubenverg.moldraw.core;

import java.io.*;
import java.nio.file.*;

import com.google.gson.*;
import com.rubenverg.moldraw.core.pack.*;
import com.rubenverg.moldraw.tool.PreviewWriter;

public final class AuditRegression {

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), output = Files.createTempDirectory(root.resolve("build"), "audit-regression-");
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("formatVersion", 1);
        JsonArray rows = new JsonArray();
        for (String id : new String[] { "gt:Water", "fluid:water" }) {
            JsonObject row = new JsonObject();
            row.addProperty("id", id);
            row.addProperty("nameZhCN", "水<script>");
            row.addProperty("localizedName", "水");
            row.addProperty("defaultName", "water");
            row.addProperty("structure", "official:molecules/water");
            row.addProperty("definitionId", "official:materials/water");
            rows.add(row);
        }
        snapshot.add("rows", rows);
        AuditFiles.write(output, snapshot);
        if (!Files.readString(output.resolve("fluids.csv"))
            .contains("fluid:water") || Files.readString(output.resolve("materials.csv"))
                .contains("fluid:water"))
            throw new AssertionError("Fluid partition");
        if (Files.readString(output.resolve("index.html"))
            .contains("水<script>")) throw new AssertionError("HTML injection");
        if (!AuditFiles.csv("  =1+1")
            .startsWith("\"'")) throw new AssertionError("CSV formula injection");
        var catalog = PackCompiler.compile(root.resolve("data-packs/migrated"), "0.7.0-test.A");
        Path preview = output.resolve("preview");
        PreviewWriter.write(catalog, preview, "official:materials/water");
        PreviewWriter.localize(catalog, preview, output.resolve("snapshot.json"));
        if (!Files.readString(preview.resolve("index.html"))
            .contains("水&lt;script&gt;")) throw new AssertionError("Localized preview");
        try {
            ResourceJson.read(new ByteArrayInputStream(new byte[17]), 16);
            throw new AssertionError("Size guard");
        } catch (IOException expected) {}
        System.out.println(
            "Audit regression: fluid partition, Chinese roundtrip, HTML/CSV escaping, localized preview and size limits passed");
    }
}
