package com.ori_leibo.nbafantasyscout.client;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GET with a timeout, a rate limit and retries, shared by the ESPN and Sleeper clients.
 * Every attempt takes a rate limiter permit, so retries count against the limit too.
 */
public class HttpFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpFetcher.class);

    private final HttpClient httpClient;
    private final String source;
    private final Duration timeout;
    private final Retry retry;
    private final RateLimiter rateLimiter;

    public HttpFetcher(HttpClient httpClient, String source, Duration timeout, Retry retry, RateLimiter rateLimiter) {
        this.httpClient = httpClient;
        this.source = source;
        this.timeout = timeout;
        this.retry = retry;
        this.rateLimiter = rateLimiter;
        retry.getEventPublisher().onRetry(event -> log.warn("{} call failed (attempt {}), retrying in {} ms: {}",
                source, event.getNumberOfRetryAttempts(), event.getWaitInterval().toMillis(),
                event.getLastThrowable().getMessage()));
    }

    /** The body of a 200 response. */
    public String get(String url) {
        URI uri = URI.create(url);
        return Retry.decorateSupplier(retry, RateLimiter.decorateSupplier(rateLimiter, () -> fetch(uri))).get();
    }

    /** For the retry config: network errors and 429/5xx can pass, other 4xx (and interrupts) will not. */
    public static boolean isRetryable(Throwable e) {
        return e instanceof UncheckedIOException
                || e instanceof HttpStatusException status && (status.status() == 429 || status.status() >= 500);
    }

    private String fetch(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(source + " request failed: " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + uri, e);
        }
        if (response.statusCode() != 200) {
            throw new HttpStatusException(source, response.statusCode(), uri);
        }
        return response.body();
    }

    public static class HttpStatusException extends IllegalStateException {

        private final int status;

        HttpStatusException(String source, int status, URI uri) {
            super(source + " returned HTTP " + status + " for " + uri);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
