package org.unibl.etf.shelterapi.service;

/**
 * Object storage for photos. The rest of the application only knows this interface, so the provider
 * can be replaced without touching the services.
 */
public interface ImageStorage {

    /** Saves a new file under a key that does not exist yet. */
    void store(String key, byte[] content, String contentType);

    /** Removes a file. A file that does not exist is not an error. */
    void delete(String key);

    /** The address that browsers use to load the file. Photos are public. */
    String publicUrl(String key);
}
