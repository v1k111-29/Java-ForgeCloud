package com.mockforge.service;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.mockforge.model.RequestLog;
import com.mockforge.model.RequestLogEvent;
import com.mockforge.repository.RequestLogRepository;

// Idea of this class (producer / consumer):
// - Mock requests are the "producers": they drop a log into a queue and leave.
// - A few background worker threads are the "consumers": they take logs out of
//   the queue and insert them into the database.
// So a slow database never slows down the mock response. The cost: logs show up
// a little later, and logs still in the queue are lost if the app crashes.
@Service
public class AsyncRequestLogger {

    private final RequestLogRepository requestLogRepository;

    // BlockingQueue = a thread-safe waiting line. Many threads can add/remove safely.
    private final BlockingQueue<RequestLogEvent> requestLogQueue;

    // ExecutorService = a managed group (pool) of threads that runs tasks for us.
    private final ExecutorService loggingWorkers;

    private final Logger logger = LoggerFactory.getLogger(AsyncRequestLogger.class);

    // These counters can be updated safely by multiple threads.
    // AtomicLong: incrementAndGet() is one unbreakable step, so two threads adding
    // at the same moment never lose a count (a plain "count++" could).
    private final AtomicLong rejectedLogCount = new AtomicLong();

    private final AtomicLong failedLogCount = new AtomicLong();

    private final AtomicLong savedLogCount = new AtomicLong();

    // volatile makes changes to this flag visible to the worker threads.
    // Without it, a worker might keep reading an old cached "true" forever.
    private volatile boolean acceptingNewLogs = true;

    // @Value("${name:default}") reads a setting from application.properties.
    // If the setting is missing, the number after ":" is used (500 and 2 here).
    public AsyncRequestLogger(RequestLogRepository requestLogRepository,
        @Value("${mockforge.logging.capacity:500}") int queueCapacity,
        @Value("${mockforge.logging.workers:2}") int loggingWorkerCount) {

        if (queueCapacity < 1 || loggingWorkerCount < 1) {
            throw new IllegalArgumentException("Queue and workers must be positive");
        }

        this.requestLogRepository = requestLogRepository;

        // ArrayBlockingQueue has a fixed size, so memory use can never grow without limit.
        this.requestLogQueue = new ArrayBlockingQueue<>(queueCapacity);

        this.loggingWorkers = Executors.newFixedThreadPool(loggingWorkerCount);

        // Start a fixed number of workers instead of a new thread for each log.
        // "this::saveQueuedLogs" is a method reference: each worker runs that method's loop.
        for (int workerNumber = 0; workerNumber < loggingWorkerCount; workerNumber++) {
            loggingWorkers.submit(this::saveQueuedLogs);
        }
    }


    // Take logs from the queue and save them in the background.
    private void saveQueuedLogs() {

        // Keep looping while the app runs, and after shutdown starts keep going
        // until the queue is empty (so already-queued logs still get saved).
        while (acceptingNewLogs || !requestLogQueue.isEmpty()) {
            try {
                // poll(200 ms): wait up to 200 ms for an item, else return null.
                // The short wait lets the loop re-check the stop flag regularly.
                RequestLogEvent requestLogEvent = requestLogQueue.poll(200, TimeUnit.MILLISECONDS);

                if (requestLogEvent != null) {
                    try {
                        RequestLog requestLog = requestLogEvent.toRequestLog();

                        requestLogRepository.saveRequestLog(requestLog);

                        savedLogCount.incrementAndGet();

                        logger.info("LOG SAVED: endpoint={}, source={}, status={} (MySQL insert completed)",
                            requestLog.getEndpointId(), requestLog.getResponseSource(), requestLog.getResponseStatus());
                    } catch (Exception exception) {
                        // DB error: count it and move on. No retry, the log is dropped.
                        failedLogCount.incrementAndGet();

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
    // offer() vs put(): offer() returns false right away if the queue is full,
    // while put() would block (wait) until there is room. We use offer() so a
    // mock request never gets stuck; a full queue just means the log is rejected.
    // synchronized: only one thread at a time runs this method, so the
    // "still accepting?" check and the add happen together with shutdown.
    public synchronized void submitRequestLog(RequestLog requestLog) {

        if (!acceptingNewLogs || !requestLogQueue.offer(new RequestLogEvent(requestLog))) {
            rejectedLogCount.incrementAndGet();

            logger.warn("LOG REJECTED: queue full or application stopping");
        }
    }


    // Return the queue size and log counters for the dashboard.
    public Map<String, Long> getLoggingStatistics() {

        return Map.of(
            "queued", (long) requestLogQueue.size(),
            "saved", savedLogCount.get(),
            "rejected", rejectedLogCount.get(),
            "failed", failedLogCount.get());
    }


    // Allow workers up to five seconds to finish when the application stops.
    // @PreDestroy: Spring calls this method automatically just before the object
    // is thrown away (when the application shuts down).
    // shutdown() = no new tasks; awaitTermination = wait up to 5 s;
    // shutdownNow() = interrupt the workers if they are still running.

    @PreDestroy
    public void stopLoggingWorkers() {

        synchronized (this) {

            acceptingNewLogs = false;
        }

        loggingWorkers.shutdown();

        try {
            if (!loggingWorkers.awaitTermination(5, TimeUnit.SECONDS)) {
                loggingWorkers.shutdownNow();
            }
        } catch (InterruptedException exception) {
            loggingWorkers.shutdownNow();

            Thread.currentThread().interrupt();
        }
    }
}
