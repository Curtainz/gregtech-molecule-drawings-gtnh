package com.rubenverg.moldraw.core.pack;

import java.util.*;

import com.google.gson.*;

/** Fully validated offline catalog. It does not publish or replace game state. */
public final class PackCatalog {

    public record Definition(String owner, String id, String type, Jsonc.Document document) {

        public JsonObject json() {
            return document.object();
        }

        public PackException error(String pointer, String message) {
            return document.error(pointer, "[" + id + "] " + message);
        }
    }

    private final Map<String, Definition> definitions;
    private final Map<String, CompiledMolecule> molecules;
    private final List<Jsonc.Document> documents;
    private final Set<String> providers;
    private final List<String> warnings;
    private final int packCount;

    PackCatalog(Map<String, Definition> definitions, Map<String, CompiledMolecule> molecules,
        List<Jsonc.Document> documents, Set<String> providers, List<String> warnings, int packCount) {
        this.definitions = Collections.unmodifiableMap(new TreeMap<>(definitions));
        this.molecules = Collections.unmodifiableMap(new TreeMap<>(molecules));
        this.documents = List.copyOf(documents);
        this.providers = Set.copyOf(providers);
        this.warnings = List.copyOf(warnings);
        this.packCount = packCount;
    }

    public Map<String, Definition> definitions() {
        return definitions;
    }

    public Map<String, CompiledMolecule> molecules() {
        return molecules;
    }

    public List<Jsonc.Document> documents() {
        return documents;
    }

    public Set<String> providers() {
        return providers;
    }

    public List<String> warnings() {
        return warnings;
    }

    public int packCount() {
        return packCount;
    }
}
