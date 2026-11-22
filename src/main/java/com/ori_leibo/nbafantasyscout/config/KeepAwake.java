package com.ori_leibo.nbafantasyscout.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Keeps a free host awake: it puts an idle server to sleep after 15 minutes without a visit, and the next visitor
 * then waits about a minute. Opening the site's own public address every 10 minutes counts as a visit, because the
 * request comes back in through the host's front door. It opens the static page only, so the database still sleeps.
 * No address configured (a local run, the tests) = off.
 */
public class KeepAwake {

    private static final Logger log = LoggerFactory.getLogger(KeepAwake.class);

    private final HttpClient http;
    private final URI url;

    public KeepAwake(HttpClient http, String url) {
        this.http = http;
        this.url = url == null || url.isBlank() ? null : URI.create(url.endsWith("/") ? url : url + "/");
        if (this.url != null) {
            log.info("Keep-awake on: opening {} every 10 minutes", this.url);
        }
    }

    @Scheduled(initialDelay = 1, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void ping() {
        if (url == null) {
            return;
        }
        try {
            HttpResponse<Void> response = http.send(HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            log.debug("Keep-awake ping: HTTP {}", response.statusCode());
        } catch (IOException e) {
            log.warn("Keep-awake ping to {} failed: {}", url, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
