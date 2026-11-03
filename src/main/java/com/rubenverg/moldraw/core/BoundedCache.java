package com.rubenverg.moldraw.core;

import java.util.*;
import java.util.function.Function;

/** Access-order, bounded cache. Owned exclusively by the client adapter thread. */
public final class BoundedCache<K, V> {

    private final int capacity;
    private final LinkedHashMap<K, V> values = new LinkedHashMap<>(16, 0.75f, true);

    public BoundedCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException();
        this.capacity = capacity;
    }

    public V get(K key, Function<K, V> loader) {
        V value = values.get(key);
        if (value != null) return value;
        value = Objects.requireNonNull(loader.apply(key));
        values.put(key, value);
        while (values.size() > capacity) values.remove(
            values.keySet()
                .iterator()
                .next());
        return value;
    }

    public void clear() {
        values.clear();
    }

    public int size() {
        return values.size();
    }
}
