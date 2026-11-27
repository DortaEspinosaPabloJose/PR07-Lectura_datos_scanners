package es.ull.calidadaire.util;

import java.time.Duration;
import java.util.function.Supplier;

/** Caché de un único valor con caducidad; evita repetir llamadas pesadas a la API. */
public final class TtlCache<T> {
    private final Duration ttl;
    private final Supplier<T> loader;
    private T value;
    private long loadedAtNanos;
    private boolean loaded;

    public TtlCache(Duration ttl, Supplier<T> loader) {
        this.ttl = ttl;
        this.loader = loader;
    }

    public synchronized T get() {
        long now = System.nanoTime();
        if (!loaded || now - loadedAtNanos > ttl.toNanos()) {
            value = loader.get();      // si falla, se propaga y no se cachea
            loadedAtNanos = now;
            loaded = true;
        }
        return value;
    }

    public synchronized void invalidate() { loaded = false; }
}
