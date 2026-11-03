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

@Service
public class AsyncRequestLogger {

    private final RequestLogRepository requestLogRepository;

    private final BlockingQueue<RequestLogEvent> requestLogQueue;

    private final ExecutorService loggingWorkers;

    private final Logger logger = LoggerFactory.getLogger(AsyncRequestLogger.class);

    // These counters can be updated safely by multiple threads.
    private final AtomicLong rejectedLogCount = new AtomicLong();

    private final AtomicLong failedLogCount = new AtomicLong();

    private final AtomicLong savedLogCount = new AtomicLong();

    // volatile makes changes to this flag visible to the worker threads.
    private volatile boolean acceptingNewLogs = true;

    public AsyncRequestLogger(RequestLogRepository requestLogRepository,
        @Value("${mockforge.logging.capacity:500}") int queueCapacity,
        @Value("${mockforge.logging.workers:2}") int loggingWorkerCount) {

        if (queueCapacity < 1 || loggingWorkerCount < 1) {
            throw new IllegalArgumentException("Queue and workers must be positive");
        }

        this.requestLogRepository = requestLogRepository;

        this.requestLogQueue = new ArrayBlockingQueue<>(queueCapacity);

        this.loggingWorkers = Executors.newFixedThreadPool(loggingWorkerCount);

        // Start a fixed number of workers instead of a new thread for each log.
        for (int workerNumber = 0; workerNumber < loggingWorkerCount; workerNumber++) {
            loggingWorkers.submit(this::saveQueuedLogs);
        }
    }


    // Take logs from the queue and save them in the background.
    private void saveQueuedLogs() {

        while (acceptingNewLogs || !requestLogQueue.isEmpty()) {
            try {
                RequestLogEvent requestLogEvent = requestLogQueue.poll(200, TimeUnit.MILLISECONDS);

                if (requestLogEvent != null) {
                    try {
                        RequestLog requestLog = requestLogEvent.toRequestLog();

                        requestLogRepository.saveRequestLog(requestLog);

                        savedLogCount.incrementAndGet();

                        logger.info("LOG SAVED: endpoint={}, source={}, status={} (MySQL insert completed)",
                            requestLog.getEndpointId(), requestLog.getResponseSource(), requestLog.getResponseStatus());
                    } catch (Exception exception) {
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
