package dev.pvpclient.event;

import dev.pvpclient.util.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal synchronous event bus. Events are dispatched by exact class (no inheritance lookup),
 * which keeps dispatch O(1) and avoids reflection. A listener that throws never affects other
 * listeners or the poster.
 */
public final class EventBus {
    public static final EventBus GLOBAL = new EventBus();

    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    /** Subscribes a listener. Returns a Runnable that removes it again. */
    public <T> Runnable subscribe(Class<T> type, Consumer<? super T> listener) {
        Consumer<Object> wrapper = event -> listener.accept(type.cast(event));
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(wrapper);
        return () -> {
            List<Consumer<Object>> list = listeners.get(type);
            if (list != null) list.remove(wrapper);
        };
    }

    public void post(Object event) {
        List<Consumer<Object>> list = listeners.get(event.getClass());
        if (list == null) return;
        for (Consumer<Object> listener : list) {
            try {
                listener.accept(event);
            } catch (Exception | LinkageError e) {
                Log.LOGGER.error("Event listener failed for {}", event.getClass().getSimpleName(), e);
            }
        }
    }
}
