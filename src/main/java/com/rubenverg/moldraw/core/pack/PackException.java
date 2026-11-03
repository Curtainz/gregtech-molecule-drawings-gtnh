package com.rubenverg.moldraw.core.pack;

/** A bounded input/compilation error, never a rendering or VM exception. */
public final class PackException extends IllegalArgumentException {

    public PackException(String message) {
        super(message);
    }
}
