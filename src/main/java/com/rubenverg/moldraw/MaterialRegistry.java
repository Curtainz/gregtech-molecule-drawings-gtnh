package com.rubenverg.moldraw;

import com.rubenverg.moldraw.client.ExternalPackService;
import com.rubenverg.moldraw.core.*;
import com.rubenverg.moldraw.core.pack.RuntimeSnapshot;

import gregtech.api.enums.Materials;

/** All tooltip and audit consumers read the same atomically published external snapshot. */
public final class MaterialRegistry {

    public record Entry(MaterialDefinition definition, DefinitionResult<MoleculeScene> molecule,
        boolean chartEligible) {}

    private static final BoundedCache<String, CompositionResolver.Result> COMPOSITIONS = new BoundedCache<>(1024);
    private static long version;

    private MaterialRegistry() {}

    public static boolean enabled() {
        return ExternalPackService.snapshot()
            .enabled();
    }

    public static long version() {
        return version;
    }

    public static void clear() {
        COMPOSITIONS.clear();
        version++;
    }

    public static String id(Materials material) {
        return "gt:" + material.mName;
    }

    private static void thread() {
        if (!MolDraw.isClientThread()) throw new IllegalStateException("Snapshot access requires client thread");
    }

    private static Entry adapt(RuntimeSnapshot.Entry e) {
        return new Entry(e.definition(), e.molecule(), e.chartEligible());
    }

    public static Entry entry(Materials material) {
        thread();
        return adapt(
            ExternalPackService.snapshot()
                .provider(id(material)));
    }

    public static Entry named(String id, String name) {
        return bound(id, name, null);
    }

    public static Entry bound(String id, String name, Materials provider) {
        thread();
        return adapt(
            ExternalPackService.snapshot()
                .lookup(id, name, GT5MaterialLookup.isMaterialValid(provider) ? MaterialRegistry.id(provider) : null));
    }

    public static MaterialDefinition definition(String id) {
        thread();
        var entry = ExternalPackService.snapshot()
            .entry(id);
        return entry == null ? null : entry.definition();
    }

    public static CompositionResolver.Result composition(String id, boolean mass, boolean recursive) {
        thread();
        return COMPOSITIONS.get(
            id + ":" + mass + ":" + recursive,
            ignored -> new CompositionResolver(MaterialRegistry::definition).resolve(id, mass, recursive));
    }
}
