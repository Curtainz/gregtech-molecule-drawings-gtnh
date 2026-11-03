package com.rubenverg.moldraw.core;

/** Material presentation intent, independent of client drawables. */
public record PresentationData(Kind kind, MaterialDefinition material) {

    public enum Kind {
        NONE,
        MOLECULE,
        ALLOY
    }
}
