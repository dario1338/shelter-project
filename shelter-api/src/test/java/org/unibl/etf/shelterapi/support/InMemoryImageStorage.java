package org.unibl.etf.shelterapi.support;

import org.unibl.etf.shelterapi.service.ImageStorage;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the photos in memory, so that tests never call the real storage. */
public class InMemoryImageStorage implements ImageStorage {

    private static final String BASE_URL = "https://images.shelter.test/";

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    @Override
    public void store(String key, byte[] content, String contentType) {
        files.put(key, content);
    }

    @Override
    public void delete(String key) {
        files.remove(key);
    }

    @Override
    public String publicUrl(String key) {
        return BASE_URL + key;
    }

    public Set<String> keys() {
        return files.keySet();
    }

    public boolean contains(String key) {
        return files.containsKey(key);
    }

    /** The storage key behind an address returned by publicUrl. */
    public static String keyOf(String url) {
        return url.substring(BASE_URL.length());
    }
}
