package org.unibl.etf.shelterapi.service;

import org.springframework.stereotype.Component;
import org.unibl.etf.shelterapi.config.SupabaseStorageProperties;
import org.unibl.etf.shelterapi.exception.ImageStorageException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Stores photos in a Supabase Storage bucket through its HTTPS API. */
@Component
public class SupabaseImageStorage implements ImageStorage {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String NEW_STYLE_KEY_PREFIX = "sb_";
    private static final int STATUS_NOT_FOUND = 404;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final String objectBase;
    private final String publicBase;
    private final String apiKey;

    public SupabaseImageStorage(SupabaseStorageProperties properties) {
        String storage = properties.url() + "/storage/v1/object/";
        this.objectBase = storage + properties.bucket() + "/";
        this.publicBase = storage + "public/" + properties.bucket() + "/";
        this.apiKey = properties.serviceKey();
    }

    @Override
    public void store(String key, byte[] content, String contentType) {
        HttpRequest request = authorized(HttpRequest.newBuilder(URI.create(objectBase + key)))
                .header("Content-Type", contentType)
                // The key is unique and never rewritten, so browsers may keep the photo for a year.
                .header("Cache-Control", "max-age=31536000")
                .header("x-upsert", "false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        int status = send(request, "store");
        if (status < 200 || status >= 300) {
            throw new ImageStorageException("The image storage rejected the file (status " + status + ")");
        }
    }

    @Override
    public void delete(String key) {
        HttpRequest request = authorized(HttpRequest.newBuilder(URI.create(objectBase + key)))
                .DELETE()
                .build();
        int status = send(request, "delete");
        if ((status < 200 || status >= 300) && status != STATUS_NOT_FOUND) {
            throw new ImageStorageException("The image storage could not delete the file (status " + status + ")");
        }
    }

    @Override
    public String publicUrl(String key) {
        return publicBase + key;
    }

    /**
     * New style keys (sb_secret_...) are not JWTs and go in the apikey header only. The older service_role key
     * is a JWT and is also sent as a bearer token.
     */
    private HttpRequest.Builder authorized(HttpRequest.Builder builder) {
        builder.timeout(REQUEST_TIMEOUT).header("apikey", apiKey);
        if (!apiKey.startsWith(NEW_STYLE_KEY_PREFIX)) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    private int send(HttpRequest request, String action) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            throw new ImageStorageException("The image storage is not reachable (" + action + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageStorageException("The image storage call was interrupted (" + action + ")", e);
        }
    }
}
