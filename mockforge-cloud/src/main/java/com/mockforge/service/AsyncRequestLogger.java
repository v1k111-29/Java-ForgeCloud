package com.mockforge.service;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mockforge.model.RequestLog;
import com.mockforge.repository.RequestLogRepository;

@Service
public class AsyncRequestLogger {

    private final RequestLogRepository requestLogRepository;
    private final BlockingQueue<RequestLog> queue;
    private final ExecutorService workers;
    private final Logger logger = LoggerFactory.getLogger(AsyncRequestLogger.class);

    // These counters can be updated safely by multiple threads.
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong saved = new AtomicLong();

    // volatile makes changes to this flag visible to the worker threads.
    private volatile boolean accepting = true;

    public AsyncRequestLogger(RequestLogRepository requestLogRepository,
    @Value("${mockforge.logging.capacity:500}") int capacity,
    @Value("${mockforge.logging.workers:2}") int workerCount) {
        if (capacity < 1 || workerCount < 1) {
            throw new IllegalArgumentException("Queue and workers must be positive");
        }
        this.requestLogRepository = requestLogRepository;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.workers = Executors.newFixedThreadPool(workerCount);
        // Start a fixed number of workers instead of a new thread for each log.
        for (int i = 0; i < workerCount; i++) {
            workers.submit(this::saveQueuedLogs);
        }
    }

    // Take logs from the queue and save them in the background.
    private void saveQueuedLogs() {
        while (accepting || !queue.isEmpty()) {
            try {
                RequestLog log = queue.poll(200, TimeUnit.MILLISECONDS);
                if (log != null) {
                    try {
                        requestLogRepository.save(log);
                        saved.incrementAndGet();
                        logger.info("LOG SAVED: endpoint={}, source={}, status={} (MySQL insert completed)",
                                log.getEndpointId(), log.getResponseSource(), log.getResponseStatus());
                    } catch (Exception exception) {
                        failed.incrementAndGet();
                        logger.warn("Request log could not be saved");
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // Add a log without making the mock request wait for a database insert.
    public synchronized void submit(RequestLog log) {
        if (!accepting || !queue.offer(log)) {
            rejected.incrementAndGet();
            logger.warn("LOG REJECTED: queue full or application stopping");
        }
    }

    // Return the queue size and log counters for the dashboard.
    public Map<String, Long> stats() {
        return Map.of(
        "queued", (long) queue.size(),
        "saved", saved.get(),
        "rejected", rejected.get(),
        "failed", failed.get());
    }

    // Allow workers up to five seconds to finish when the application stops.

    @PreDestroy

    public void stop() {
        synchronized (this) {
            accepting = false;
        }
        workers.shutdown();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException exception) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
