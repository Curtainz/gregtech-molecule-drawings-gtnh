package com.rubenverg.moldraw.core;

/** Stage-1 format contract only. This class does not read or mount data packs. */
public final class DataPackContract {

    public static final int SCHEMA_VERSION = 1;
    public static final int IMPLEMENTATION_STAGE = 1;
    public static final String PACK_DIRECTORY = "config/gtmoldraw/packs";
    public static final boolean EXTERNAL_LOADING_ENABLED = false;

    private DataPackContract() {}
}
