package org.unibl.etf.shelterapi.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.unibl.etf.shelterapi.config.SupabaseStorageProperties;
import org.unibl.etf.shelterapi.exception.ImageStorageException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupabaseImageStorageTest {

    private static final String TEST_KEY = "sb_secret_test-value";
    private static final String LEGACY_KEY = "eyJ.legacy.jwt";

    /** What the fake storage received. */
    private static final class Received {
        final AtomicReference<String> method = new AtomicReference<>();
        final AtomicReference<String> path = new AtomicReference<>();
        final AtomicReference<String> apiKey = new AtomicReference<>();
        final AtomicReference<String> authorization = new AtomicReference<>();
        final AtomicReference<String> contentType = new AtomicReference<>();
        final AtomicReference<String> upsert = new AtomicReference<>();
        final AtomicReference<byte[]> body = new AtomicReference<>();
        final AtomicInteger calls = new AtomicInteger();
    }

    private static HttpServer start(int status, Received received) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.calls.incrementAndGet();
            received.method.set(exchange.getRequestMethod());
            received.path.set(exchange.getRequestURI().getPath());
            received.apiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
            received.authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            received.contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            received.upsert.set(exchange.getRequestHeaders().getFirst("x-upsert"));
            received.body.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static SupabaseImageStorage storage(HttpServer server, String key) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        return new SupabaseImageStorage(new SupabaseStorageProperties(url, key, "dog-images"));
    }

    @Test
    void storeSendsTheFileToTheBucket() throws IOException {
        Received received = new Received();
        HttpServer server = start(200, received);
        try {
            storage(server, TEST_KEY).store("dogs/1/a.jpg", new byte[]{1, 2, 3}, "image/jpeg");
        } finally {
            server.stop(0);
        }

        assertEquals("POST", received.method.get());
        assertEquals("/storage/v1/object/dog-images/dogs/1/a.jpg", received.path.get());
        assertEquals("image/jpeg", received.contentType.get());
        assertEquals("false", received.upsert.get());
        assertArrayEquals(new byte[]{1, 2, 3}, received.body.get());
    }

    @Test
    void newStyleKeyIsSentOnlyInTheApiKeyHeader() throws IOException {
        Received received = new Received();
        HttpServer server = start(200, received);
        try {
            storage(server, TEST_KEY).store("dogs/1/a.jpg", new byte[]{1}, "image/jpeg");
        } finally {
            server.stop(0);
        }

        assertEquals(TEST_KEY, received.apiKey.get());
        assertNull(received.authorization.get());
    }

    @Test
    void legacyKeyIsAlsoSentAsBearerToken() throws IOException {
        Received received = new Received();
        HttpServer server = start(200, received);
        try {
            storage(server, LEGACY_KEY).store("dogs/1/a.jpg", new byte[]{1}, "image/jpeg");
        } finally {
            server.stop(0);
        }

        assertEquals(LEGACY_KEY, received.apiKey.get());
        assertEquals("Bearer " + LEGACY_KEY, received.authorization.get());
    }

    @Test
    void storeFailsWhenTheStorageRejectsTheFile() throws IOException {
        HttpServer server = start(400, new Received());
        try {
            SupabaseImageStorage storage = storage(server, TEST_KEY);
            byte[] content = {1};

            ImageStorageException error = assertThrows(ImageStorageException.class,
                    () -> storage.store("dogs/1/a.jpg", content, "image/jpeg"));

            assertTrue(error.getMessage().contains("400"));
            assertFalse(error.getMessage().contains(TEST_KEY));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deleteSendsADeleteRequest() throws IOException {
        Received received = new Received();
        HttpServer server = start(200, received);
        try {
            storage(server, TEST_KEY).delete("dogs/1/a.jpg");
        } finally {
            server.stop(0);
        }

        assertEquals("DELETE", received.method.get());
        assertEquals("/storage/v1/object/dog-images/dogs/1/a.jpg", received.path.get());
    }

    @Test
    void deletingAMissingFileIsNotAnError() throws IOException {
        Received received = new Received();
        HttpServer server = start(404, received);
        try {
            storage(server, TEST_KEY).delete("dogs/1/gone.jpg");
        } finally {
            server.stop(0);
        }

        assertEquals(1, received.calls.get());
    }

    @Test
    void deleteFailsOnServerError() throws IOException {
        HttpServer server = start(500, new Received());
        try {
            SupabaseImageStorage storage = storage(server, TEST_KEY);

            assertThrows(ImageStorageException.class, () -> storage.delete("dogs/1/a.jpg"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unreachableStorageIsReportedAsStorageFailure() throws IOException {
        HttpServer server = start(200, new Received());
        SupabaseImageStorage storage = storage(server, TEST_KEY);
        server.stop(0);
        byte[] content = {1};

        assertThrows(ImageStorageException.class, () -> storage.store("dogs/1/a.jpg", content, "image/jpeg"));
    }

    @Test
    void publicUrlPointsToThePublicBucketPath() {
        SupabaseImageStorage storage = new SupabaseImageStorage(
                new SupabaseStorageProperties("https://abc.supabase.co/", TEST_KEY, "dog-images"));

        assertEquals("https://abc.supabase.co/storage/v1/object/public/dog-images/dogs/1/a.jpg",
                storage.publicUrl("dogs/1/a.jpg"));
    }

    @Test
    void propertiesRequireAllValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new SupabaseStorageProperties("https://abc.supabase.co", " ", "dog-images"));
        assertThrows(IllegalArgumentException.class,
                () -> new SupabaseStorageProperties(null, TEST_KEY, "dog-images"));
        assertThrows(IllegalArgumentException.class,
                () -> new SupabaseStorageProperties("https://abc.supabase.co", TEST_KEY, ""));
    }

    @Test
    void propertiesDoNotPrintTheKey() {
        SupabaseStorageProperties properties =
                new SupabaseStorageProperties("https://abc.supabase.co", TEST_KEY, "dog-images");

        assertFalse(properties.toString().contains(TEST_KEY));
    }
}
