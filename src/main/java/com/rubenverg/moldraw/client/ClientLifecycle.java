package com.rubenverg.moldraw.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

import com.rubenverg.moldraw.CommonLifecycle;
import com.rubenverg.moldraw.MaterialAuditCommand;
import com.rubenverg.moldraw.MolDraw;
import com.rubenverg.moldraw.MolDrawConfig;
import com.rubenverg.moldraw.NEITooltipHelper;
import com.rubenverg.moldraw.ResourceData;
import com.rubenverg.moldraw.TooltipEventHandler;
import com.rubenverg.moldraw.nei.NEIRecipeTooltipHandler;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Install integrations after loading, and confine cache mutations to the client tick. */
public final class ClientLifecycle extends CommonLifecycle {

    private volatile Thread clientThread;
    private boolean installed;
    private final java.util.concurrent.atomic.AtomicBoolean configPending = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean reloadPending = new java.util.concurrent.atomic.AtomicBoolean();

    @Override
    public void postInit() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Override
    public boolean isClientThread() {
        return clientThread != null && Thread.currentThread() == clientThread;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        clientThread = Thread.currentThread();
        if (!installed) {
            installed = true;
            MolDraw.LOGGER.info("[lifecycle] first client tick: installing integrations");
            ClientCommandHandler.instance.registerCommand(new MaterialAuditCommand());
            if (Minecraft.getMinecraft()
                .getResourceManager() instanceof IReloadableResourceManager manager) {
                // Registration invokes the listener immediately. Never initialize caches in that callback.
                manager.registerReloadListener(ignored -> reloadPending.set(true));
            }
            MinecraftForge.EVENT_BUS.register(new TooltipEventHandler());
            if (Loader.isModLoaded("NotEnoughItems")) {
                NEIRecipeTooltipHandler.register();
                NEITooltipHelper.init();
            }
            MolDraw.LOGGER.info("[lifecycle] client integrations installed");
        }
        if (configPending.getAndSet(false)) {
            MolDrawConfig.syncConfig();
            reloadPending.set(true);
        }
        if (reloadPending.getAndSet(false)) {
            ResourceData.invalidate();
            MolDraw.LOGGER.info("[lifecycle] caches invalidated on client thread");
        }
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (MolDraw.MOD_ID.equals(event.modID)) {
            configPending.set(true);
        }
    }
}
