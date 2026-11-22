package com.ori_leibo.nbafantasyscout.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClientRequestLimitTest {

    private final MovableClock clock = new MovableClock(Instant.parse("2026-11-22T10:00:00Z"));

    @Test
    void eachClientGetsItsOwnCountAndAFreshOneWhenTheWindowEnds() {
        ClientRequestLimit limit = new ClientRequestLimit(2, Duration.ofMinutes(1), clock);

        assertTrue(limit.tryAcquire("1.1.1.1").allowed());
        assertTrue(limit.tryAcquire("1.1.1.1").allowed());
        clock.advance(Duration.ofSeconds(15));
        ClientRequestLimit.Decision third = limit.tryAcquire("1.1.1.1");
        assertFalse(third.allowed());
        assertEquals(45, third.retryAfterSeconds());

        // another visitor is not slowed down by the first one
        assertTrue(limit.tryAcquire("2.2.2.2").allowed());

        clock.advance(Duration.ofSeconds(45));
        assertTrue(limit.tryAcquire("1.1.1.1").allowed());
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
