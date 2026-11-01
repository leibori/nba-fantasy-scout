package com.ori_leibo.nbafantasyscout.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpFetcherTest {

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private HttpFetcher fetcher;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        Retry retry = Retry.of("test", RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(1), 2))
                .retryOnException(HttpFetcher::isRetryable)
                .build());
        RateLimiter rateLimiter = RateLimiter.of("test", RateLimiterConfig.custom().limitForPeriod(100).build());
        fetcher = new HttpFetcher(HttpClient.newHttpClient(), "Test", Duration.ofSeconds(5), retry, rateLimiter);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void retriesAServerErrorAndReturnsTheBody() {
        respond(503, 200);

        assertEquals("ok", fetcher.get(url()));
        assertEquals(2, calls.get());
    }

    @Test
    void doesNotRetryANotFound() {
        respond(404);

        HttpFetcher.HttpStatusException e = assertThrows(HttpFetcher.HttpStatusException.class, () -> fetcher.get(url()));
        assertEquals(404, e.status());
        assertEquals(1, calls.get());
    }

    @Test
    void givesUpAfterTheLastAttempt() {
        respond(500, 500, 500, 200);

        assertThrows(HttpFetcher.HttpStatusException.class, () -> fetcher.get(url()));
        assertEquals(3, calls.get());
    }

    // the n-th call gets the n-th status (the last one repeats), with body "ok"
    private void respond(int... statuses) {
        server.createContext("/", exchange -> {
            int status = statuses[Math.min(calls.getAndIncrement(), statuses.length - 1)];
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    private String url() {
        return "http://localhost:" + server.getAddress().getPort() + "/";
    }
}
