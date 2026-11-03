package com.rubenverg.moldraw;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import gregtech.api.enums.Materials;

/** Client-only export of the actual material registry, not a filename coverage estimate. */
public final class MaterialAuditCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtmoldraw";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtmoldraw audit";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1 || !"audit".equals(args[0])) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        try {
            List<Materials> materials = new ArrayList<>(Materials.getAll());
            materials.sort(Comparator.comparing(material -> material.mName));
            List<String> rows = new ArrayList<>();
            rows.add(
                "id,name,formula,formulaStatus,molecule,composition,display,structureState,compositionState,source,quantityShares,massShares,diagnostics");
            for (Materials material : materials) {
                if (!GT5MaterialLookup.isMaterialValid(material)) continue;
                var entry = MaterialRegistry.entry(material);
                var definition = entry.definition();
                var quantity = MaterialRegistry
                    .composition(definition.id(), false, MolDrawConfig.INSTANCE.alloy.recursive);
                var mass = MaterialRegistry.composition(definition.id(), true, MolDrawConfig.INSTANCE.alloy.recursive);
                String formula = definition.formula();
                String status;
                try {
                    com.rubenverg.moldraw.core.ChemicalFormula.parseExact(formula);
                    status = "parsed";
                } catch (IllegalArgumentException | ArithmeticException invalid) {
                    status = "unsupported_or_unknown";
                }
                var display = MaterialTargetResolver
                    .resolveFromMaterial(material, Minecraft.getMinecraft().fontRenderer);
                boolean molecule = display.isValid() && display.getType() == DisplayPlan.Type.MOLECULE;
                boolean composition = display.isValid() && display.getType() == DisplayPlan.Type.ALLOY;
                rows.add(
                    csv(definition.id()) + ","
                        + csv(definition.name())
                        + ","
                        + csv(formula)
                        + ","
                        + status
                        + ","
                        + molecule
                        + ","
                        + composition
                        + ","
                        + (molecule ? "molecule" : composition ? "composition" : "text")
                        + ","
                        + csv(
                            entry.molecule()
                                .state()
                                .name())
                        + ","
                        + csv(
                            definition.state()
                                .name())
                        + ","
                        + csv(
                            definition.source() + ";"
                                + entry.molecule()
                                    .source())
                        + ","
                        + csv(shares(quantity))
                        + ","
                        + csv(shares(mass))
                        + ","
                        + csv(
                            String.join(
                                ";",
                                entry.molecule()
                                    .diagnostics())
                                + ";"
                                + String.join(";", quantity.diagnostics())
                                + ";"
                                + String.join(";", mass.diagnostics())
                                + ";"
                                + String.join(";", RendererCache.diagnostics(definition.id()))));
            }
            Path directory = Minecraft.getMinecraft().mcDataDir.toPath()
                .resolve("gtmoldraw-audit");
            Files.createDirectories(directory);
            Path output = directory.resolve("materials.csv");
            Files.write(output, ("\uFEFF" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8));
            sender.addChatMessage(new ChatComponentText("MolDraw: " + (rows.size() - 1) + " materials -> " + output));
        } catch (Exception failure) {
            MolDraw.LOGGER.error("Material export failed", failure);
            sender.addChatMessage(new ChatComponentText("MolDraw export failed; see client log."));
        }
    }

    private static String shares(com.rubenverg.moldraw.core.CompositionResolver.Result result) {
        return result.shares()
            .entrySet()
            .stream()
            .map(
                e -> e.getKey() + "="
                    + e.getValue()
                        .numerator()
                    + "/"
                    + e.getValue()
                        .denominator())
            .collect(java.util.stream.Collectors.joining(";"));
    }

    private static String csv(String value) {
        String text = value == null ? "" : value;
        // Prevent spreadsheet formula execution when registry names originate from other mods.
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
