package com.gei.autoant.watch;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Debouncer implements AutoCloseable {
    private final Duration delay;
    private final Consumer<ChangeBatch> consumer;
    private final ScheduledExecutorService executor;
    private final Set<Path> pending = new LinkedHashSet<>();
    private ScheduledFuture<?> scheduledFlush;

    public Debouncer(Duration delay, Consumer<ChangeBatch> consumer) {
        this(delay, consumer, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "auto-ant-watch-debouncer");
            thread.setDaemon(true);
            return thread;
        }));
    }

    Debouncer(Duration delay, Consumer<ChangeBatch> consumer, ScheduledExecutorService executor) {
        this.delay = requireNonNegative(delay);
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public synchronized void submit(Path path) {
        if (path == null) {
            return;
        }
        pending.add(path);
        reschedule();
    }

    public synchronized boolean hasPending() {
        return !pending.isEmpty();
    }

    public void flushNow() {
        ChangeBatch batch;
        synchronized (this) {
            cancelScheduledFlush();
            batch = drainPending();
        }
        consume(batch);
    }

    @Override
    public void close() {
        flushNow();
        executor.shutdownNow();
    }

    private synchronized void reschedule() {
        cancelScheduledFlush();
        scheduledFlush = executor.schedule(this::flushNow, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void cancelScheduledFlush() {
        if (scheduledFlush != null) {
            scheduledFlush.cancel(false);
            scheduledFlush = null;
        }
    }

    private ChangeBatch drainPending() {
        ChangeBatch batch = new ChangeBatch(pending);
        pending.clear();
        return batch;
    }

    private void consume(ChangeBatch batch) {
        if (!batch.isEmpty()) {
            consumer.accept(batch);
        }
    }

    private Duration requireNonNegative(Duration value) {
        Objects.requireNonNull(value, "delay");
        if (value.isNegative()) {
            throw new IllegalArgumentException("Debounce delay must not be negative.");
        }
        return value;
    }
}