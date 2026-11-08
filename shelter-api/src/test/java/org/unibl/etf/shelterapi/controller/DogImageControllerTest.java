package org.unibl.etf.shelterapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.unibl.etf.shelterapi.dto.DogImageResponse;
import org.unibl.etf.shelterapi.exception.ApiExceptionHandler;
import org.unibl.etf.shelterapi.exception.ImageStorageException;
import org.unibl.etf.shelterapi.exception.InvalidRequestException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.exception.UploadTooLargeException;
import org.unibl.etf.shelterapi.service.DogImageService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DogImageControllerTest {

    private static final String IMAGES = "/api/dogs/1/images";

    @Mock
    private DogImageService imageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DogImageController(imageService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "dog.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void uploadReturnsTheCreatedImage() throws Exception {
        when(imageService.upload(1L, new byte[]{1, 2, 3})).thenReturn(new DogImageResponse(5L, "https://files/a.jpg"));

        mockMvc.perform(multipart(IMAGES).file(file()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "https://files/a.jpg"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.url").value("https://files/a.jpg"));
    }

    @Test
    void uploadWithoutAFileIsABadRequest() throws Exception {
        mockMvc.perform(multipart(IMAGES))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidImageIsABadRequest() throws Exception {
        when(imageService.upload(any(), any())).thenThrow(new InvalidRequestException("The file is not a valid JPEG or PNG image"));

        mockMvc.perform(multipart(IMAGES).file(file()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is not a valid JPEG or PNG image"));
    }

    @Test
    void unknownDogIsNotFound() throws Exception {
        when(imageService.upload(any(), any())).thenThrow(new ResourceNotFoundException("Dog with id 1 not found"));

        mockMvc.perform(multipart(IMAGES).file(file()))
                .andExpect(status().isNotFound());
    }

    @Test
    void tooBigImageIsRejectedWith413() throws Exception {
        when(imageService.upload(any(), any())).thenThrow(new UploadTooLargeException("big"));

        mockMvc.perform(multipart(IMAGES).file(file()))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.detail").value("The image is too large (at most 5 MB)"));
    }

    @Test
    void storageFailureIsABadGateway() throws Exception {
        when(imageService.upload(any(), any())).thenThrow(new ImageStorageException("The image storage is not reachable (store)"));

        mockMvc.perform(multipart(IMAGES).file(file()))
                .andExpect(status().isBadGateway());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete(IMAGES + "/5"))
                .andExpect(status().isNoContent());

        verify(imageService).delete(1L, 5L);
    }

    @Test
    void deleteOfAnUnknownImageIsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Image with id 5 not found for dog 1"))
                .when(imageService).delete(1L, 5L);

        mockMvc.perform(delete(IMAGES + "/5"))
                .andExpect(status().isNotFound());
    }
}
