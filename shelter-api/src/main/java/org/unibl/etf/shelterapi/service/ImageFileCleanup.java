package org.unibl.etf.shelterapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.unibl.etf.shelterapi.exception.ImageStorageException;

/**
 * Deletes the files of removed photos, but only after the database change has been committed, so that a failed
 * transaction never loses files. A file that cannot be deleted stays in the storage as an orphan: it is not
 * visible anywhere, so this is logged and not treated as an error.
 */
@Component
public class ImageFileCleanup {

    private static final Logger LOG = LoggerFactory.getLogger(ImageFileCleanup.class);

    private final ImageStorage storage;

    public ImageFileCleanup(ImageStorage storage) {
        this.storage = storage;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onImagesRemoved(ImageFilesToRemove event) {
        for (String key : event.storageKeys()) {
            try {
                storage.delete(key);
            } catch (ImageStorageException e) {
                LOG.warn("Could not delete image file {}: {}", key, e.getMessage());
            }
        }
    }
}
