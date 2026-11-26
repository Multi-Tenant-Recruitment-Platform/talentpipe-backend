package com.talentpipe.candidate.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class CvExtractionConfigTest {

    /** The caller is an upload still holding its database connection: it must never do the extraction itself. */
    @Test
    void whenThePoolAndTheQueueAreFull_theTaskIsDropped_notRunByTheCaller() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new CvExtractionConfig().cvExtractionExecutor();
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean ranAnyway = new AtomicBoolean();
        try {
            int capacity = executor.getMaxPoolSize() + executor.getQueueCapacity();
            for (int i = 0; i < capacity; i++) {
                executor.execute(() -> await(release));
            }

            executor.execute(() -> ranAnyway.set(true));

            assertThat(ranAnyway).isFalse();
            assertThat(executor.getActiveCount()).isEqualTo(executor.getMaxPoolSize());
        } finally {
            release.countDown();
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(10);
            executor.shutdown();
        }
        assertThat(ranAnyway).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
