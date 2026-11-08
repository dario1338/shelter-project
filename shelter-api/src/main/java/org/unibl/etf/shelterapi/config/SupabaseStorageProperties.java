package org.unibl.etf.shelterapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the photos are stored. All three values are required and come from the environment, so that the
 * application refuses to start with a half configured storage instead of failing at the first upload.
 *
 * @param url        base URL of the Supabase project, for example https://abcdefgh.supabase.co
 * @param serviceKey secret key of the project; it can write to every bucket, so it never leaves the server
 * @param bucket     name of a bucket that is public for reading
 */
@ConfigurationProperties(prefix = "app.storage.supabase")
public record SupabaseStorageProperties(String url, String serviceKey, String bucket) {

    public SupabaseStorageProperties {
        if (isBlank(url) || isBlank(serviceKey) || isBlank(bucket)) {
            throw new IllegalArgumentException(
                    "app.storage.supabase.url, app.storage.supabase.service-key and app.storage.supabase.bucket must be set");
        }
        String trimmed = url.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        url = trimmed;
        serviceKey = serviceKey.strip();
        bucket = bucket.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** The key must never end up in a log, so it is left out. */
    @Override
    public String toString() {
        return "SupabaseStorageProperties[url=" + url + ", bucket=" + bucket + "]";
    }
}
