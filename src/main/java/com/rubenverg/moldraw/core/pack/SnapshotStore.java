package com.rubenverg.moldraw.core.pack;

/** Only the owning client thread publishes; failed or stale candidates never replace the live value. */
public final class SnapshotStore<T> {

    private final Thread owner = Thread.currentThread();
    private volatile T value;
    private volatile long version;
    private long request;
    private String error = "";

    public SnapshotStore(T initial) {
        value = java.util.Objects.requireNonNull(initial);
    }

    private void owner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Snapshot publication requires owner thread");
    }

    public long request() {
        owner();
        return ++request;
    }

    public boolean publish(long ticket, T candidate) {
        owner();
        if (ticket != request) return false;
        value = java.util.Objects.requireNonNull(candidate);
        version++;
        error = "";
        return true;
    }

    public boolean fail(long ticket, String message) {
        owner();
        if (ticket != request) return false;
        error = message;
        return true;
    }

    public T value() {
        return value;
    }

    public long version() {
        return version;
    }

    public long requested() {
        owner();
        return request;
    }

    public String error() {
        owner();
        return error;
    }
}
