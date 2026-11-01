package com.ori_leibo.nbafantasyscout.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void theTaskSeesTheSubmittersContextAndTheWorkerGetsItsOwnBack() throws InterruptedException {
        MDC.put("correlationId", "sync-1");
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable decorated = new MdcTaskDecorator().decorate(() -> seen.set(MDC.get("correlationId")));
        AtomicReference<Map<String, String>> after = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            MDC.put("worker", "own");
            decorated.run();
            after.set(MDC.getCopyOfContextMap());
        });
        worker.start();
        worker.join();

        assertEquals("sync-1", seen.get());
        assertEquals(Map.of("worker", "own"), after.get());
    }
}
