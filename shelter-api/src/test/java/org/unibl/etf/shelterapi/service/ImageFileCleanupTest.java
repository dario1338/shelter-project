package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unibl.etf.shelterapi.exception.ImageStorageException;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImageFileCleanupTest {

    @Mock
    private ImageStorage storage;

    @InjectMocks
    private ImageFileCleanup cleanup;

    @Test
    void deletesEveryFile() {
        cleanup.onImagesRemoved(new ImageFilesToRemove(List.of("dogs/1/a.jpg", "dogs/1/b.png")));

        verify(storage).delete("dogs/1/a.jpg");
        verify(storage).delete("dogs/1/b.png");
    }

    @Test
    void failureOnOneFileDoesNotStopTheOthers() {
        doThrow(new ImageStorageException("down")).when(storage).delete("dogs/1/a.jpg");

        cleanup.onImagesRemoved(new ImageFilesToRemove(List.of("dogs/1/a.jpg", "dogs/1/b.png")));

        verify(storage).delete("dogs/1/b.png");
    }
}
