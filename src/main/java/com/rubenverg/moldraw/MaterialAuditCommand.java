package com.rubenverg.moldraw;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

/** Client-only export of the actual material registry, not a filename coverage estimate. */
public final class MaterialAuditCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtmoldraw";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtmoldraw reload | status | audit | format-info";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 1 && "reload".equals(args[0])) {
            com.rubenverg.moldraw.client.ExternalPackService.request(true, true);
            sender.addChatMessage(
                new ChatComponentText("GTMolDraw: reload queued; previous valid snapshot remains active."));
            return;
        }
        if (args.length == 1 && "status".equals(args[0])) {
            sender.addChatMessage(
                new ChatComponentText("GTMolDraw: " + com.rubenverg.moldraw.client.ExternalPackService.status()));
            sender.addChatMessage(new ChatComponentText(com.rubenverg.moldraw.client.ChromaticIntegration.status()));
            sender.addChatMessage(
                new ChatComponentText("Directory: " + com.rubenverg.moldraw.client.ExternalPackService.directory()));
            return;
        }
        if (args.length == 1 && "format-info".equals(args[0])) {
            sender.addChatMessage(
                new ChatComponentText(
                    "GTMolDraw " + MolDraw.VERSION
                        + "; JSONC schema "
                        + com.rubenverg.moldraw.core.DataPackContract.SCHEMA_VERSION
                        + "; stage "
                        + com.rubenverg.moldraw.core.DataPackContract.IMPLEMENTATION_STAGE));
            sender.addChatMessage(
                new ChatComponentText(
                    "Pack directory: " + com.rubenverg.moldraw.client.ExternalPackService.directory()));
            sender.addChatMessage(
                new ChatComponentText(
                    "Stage 4: external loading enabled. /gtmoldraw reload validates a candidate before atomic publication; no bundled fallback."));
            sender.addChatMessage(new ChatComponentText(com.rubenverg.moldraw.client.ExternalPackService.status()));
            return;
        }
        if (args.length != 1 || !"audit".equals(args[0])) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        com.rubenverg.moldraw.client.AuditExport.start(sender);
    }

}
