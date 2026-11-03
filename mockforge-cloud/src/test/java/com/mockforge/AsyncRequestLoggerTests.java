package com.mockforge;
import java.util.concurrent.*;
import com.mockforge.model.RequestLog;
import com.mockforge.repository.RequestLogRepository;
import com.mockforge.service.AsyncRequestLogger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsyncRequestLoggerTests {
    @Test void fullQueueRejectsWithoutBlockingAndShutdownDrains() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RequestLogRepository slow = new RequestLogRepository(null) {
            @Override public int save(RequestLog log) {
                started.countDown();
                try { release.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                return 1;
            }
        };
        AsyncRequestLogger logger = new AsyncRequestLogger(slow, 1, 1);
        try {
            logger.submit(new RequestLog());
            assertTrue(started.await(2, TimeUnit.SECONDS));
            logger.submit(new RequestLog());
            assertTimeoutPreemptively(java.time.Duration.ofMillis(500), () -> logger.submit(new RequestLog()));
            assertEquals(1L, logger.stats().get("rejected"));
        } finally { release.countDown(); logger.stop(); }
        assertEquals(2L, logger.stats().get("saved"));
        assertEquals(0L, logger.stats().get("queued"));
    }
    @Test void databaseFailureIsCounted() throws Exception {
        AsyncRequestLogger logger = new AsyncRequestLogger(new RequestLogRepository(null) {
            @Override public int save(RequestLog log) { throw new IllegalStateException("test failure"); }
        }, 1, 1);
        logger.submit(new RequestLog());
        logger.stop();
        assertEquals(1L, logger.stats().get("failed"));
    }
}
