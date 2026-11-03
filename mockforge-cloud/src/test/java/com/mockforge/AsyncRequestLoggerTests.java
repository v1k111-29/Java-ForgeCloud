package com.mockforge;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.mockforge.model.RequestLog;
import com.mockforge.repository.RequestLogRepository;
import com.mockforge.service.AsyncRequestLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncRequestLoggerTests {

    @Test
    void queuedLogIsNotChangedWhenCallerChangesItsOriginalObject() throws Exception {

        CountDownLatch firstWriteStarted = new CountDownLatch(1);

        CountDownLatch releaseFirstWrite = new CountDownLatch(1);

        List<RequestLog> savedLogs = new CopyOnWriteArrayList<>();

        RequestLogRepository requestLogRepository = new RequestLogRepository(null) {

            @Override
            public int saveRequestLog(RequestLog requestLog) {

                if (savedLogs.isEmpty()) {
                    firstWriteStarted.countDown();

                    try {
                        releaseFirstWrite.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }

                savedLogs.add(requestLog);

                return 1;
            }
        };

        AsyncRequestLogger asyncRequestLogger = new AsyncRequestLogger(requestLogRepository, 2, 1);

        try {
            asyncRequestLogger.submitRequestLog(new RequestLog());

            assertTrue(firstWriteStarted.await(2, TimeUnit.SECONDS));

            RequestLog originalRequestLog = new RequestLog();

            originalRequestLog.setEndpointId(42L);

            originalRequestLog.setRequestBodySample("original sample");

            asyncRequestLogger.submitRequestLog(originalRequestLog);

            originalRequestLog.setEndpointId(99L);

            originalRequestLog.setRequestBodySample("changed after submission");
        } finally {
            releaseFirstWrite.countDown();

            asyncRequestLogger.stopLoggingWorkers();
        }

        assertEquals(2, savedLogs.size());

        assertEquals(42L, savedLogs.get(1).getEndpointId());

        assertEquals("original sample", savedLogs.get(1).getRequestBodySample());
    }


    @Test
    void fullQueueRejectsWithoutBlockingAndShutdownDrains() throws Exception {

        CountDownLatch writeStarted = new CountDownLatch(1);

        CountDownLatch releaseWrite = new CountDownLatch(1);

        RequestLogRepository slowRequestLogRepository = new RequestLogRepository(null) {

            @Override
            public int saveRequestLog(RequestLog requestLog) {

                writeStarted.countDown();

                try {
                    releaseWrite.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }

                return 1;
            }
        };

        AsyncRequestLogger asyncRequestLogger = new AsyncRequestLogger(slowRequestLogRepository, 1, 1);

        try {
            asyncRequestLogger.submitRequestLog(new RequestLog());

            assertTrue(writeStarted.await(2, TimeUnit.SECONDS));

            asyncRequestLogger.submitRequestLog(new RequestLog());

            assertTimeoutPreemptively(Duration.ofMillis(500), () -> asyncRequestLogger.submitRequestLog(new RequestLog()));

            assertEquals(1L, asyncRequestLogger.getLoggingStatistics().get("rejected"));
        } finally {
            releaseWrite.countDown();
            asyncRequestLogger.stopLoggingWorkers();
        }

        assertEquals(2L, asyncRequestLogger.getLoggingStatistics().get("saved"));

        assertEquals(0L, asyncRequestLogger.getLoggingStatistics().get("queued"));
    }


    @Test
    void databaseFailureIsCounted() throws Exception {

        AsyncRequestLogger asyncRequestLogger = new AsyncRequestLogger(new RequestLogRepository(null) {

            @Override
            public int saveRequestLog(RequestLog requestLog) {
                throw new IllegalStateException("test failure");
            }
        }, 1, 1);

        asyncRequestLogger.submitRequestLog(new RequestLog());

        asyncRequestLogger.stopLoggingWorkers();

        assertEquals(1L, asyncRequestLogger.getLoggingStatistics().get("failed"));
    }
}
