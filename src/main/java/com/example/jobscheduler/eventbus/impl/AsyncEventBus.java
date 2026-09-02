package com.example.jobscheduler.eventbus.impl;

import com.example.jobscheduler.eventbus.Consumer;
import com.example.jobscheduler.eventbus.Event;
import com.example.jobscheduler.eventbus.EventBus;
import com.example.jobscheduler.eventbus.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class AsyncEventBus implements EventBus, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AsyncEventBus.class);

    private final ExecutorService executorService;
    private final Map<Class<?>, List<Consumer<?>>> subscriptions = new ConcurrentHashMap<>();
    private final BlockingQueue<Event> pendingEvents = new LinkedBlockingQueue<>();
    private volatile boolean running = true;

    public AsyncEventBus(ExecutorService executorService, int threadsCount) {
        this.executorService = Objects.requireNonNull(executorService, "executorService cannot be null");
        int workers = Math.max(1, threadsCount);
        for (int i = 0; i < workers; i++) {
            this.executorService.submit(this::consume);
        }
    }

    @Override
    public <T extends Event> Subscription subscribe(Class<T> eventType, Consumer<T> listener) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");

        List<Consumer<?>> listeners = subscriptions.computeIfAbsent(
                eventType, key -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public void publish(Event event) {
        if (event == null) {
            log.warn("Attempted to publish null event");
            return;
        }
        if (!running) {
            log.warn("EventBus is shutting down, ignoring event: {}", event.getClass().getSimpleName());
            return;
        }
        pendingEvents.offer(event);
    }

    @SuppressWarnings("unchecked")
    public void deliver(Event event) {
        if (event == null) {
            return;
        }
        for (Map.Entry<Class<?>, List<Consumer<?>>> entry : subscriptions.entrySet()) {
            Class<?> subscribedType = entry.getKey();
            if (subscribedType.isInstance(event)) {
                List<Consumer<?>> listeners = entry.getValue();
                if (listeners == null || listeners.isEmpty()) {
                    continue;
                }

                for (Consumer<?> listener : listeners) {
                    try {
                        ((Consumer<Event>) listener).accept(event);
                    } catch (Throwable t) {
                        log.error("Error dispatching event {} to listener {}: {}",
                                event.getClass().getSimpleName(), listener.getClass().getName(), t.getMessage(), t);
                    }
                }
            }
        }
    }

    private void consume() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Event event = pendingEvents.poll(500, TimeUnit.MILLISECONDS);
                if (event != null) {
                    deliver(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                log.error("Unexpected error in event bus consume loop", t);
            }
        }

        // Drain any remaining events during graceful shutdown
        Event remainingEvent;
        while ((remainingEvent = pendingEvents.poll()) != null) {
            try {
                deliver(remainingEvent);
            } catch (Throwable t) {
                log.error("Error draining event during shutdown", t);
            }
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        running = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
