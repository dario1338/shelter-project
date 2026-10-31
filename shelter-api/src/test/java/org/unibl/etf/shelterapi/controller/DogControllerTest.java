package org.unibl.etf.shelterapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.unibl.etf.shelterapi.dto.DogRequest;
import org.unibl.etf.shelterapi.dto.DogResponse;
import org.unibl.etf.shelterapi.exception.ApiExceptionHandler;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.DogStatus;
import org.unibl.etf.shelterapi.service.DogService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DogControllerTest {

    @Mock
    private DogService dogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DogController(dogService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private static DogResponse rex() {
        return new DogResponse(1L, "Rex", "Mixed", 2020, "Friendly", DogStatus.AVAILABLE);
    }

    @Test
    void getDogsReturnsPagedContent() throws Exception {
        when(dogService.getDogs(eq(DogStatus.AVAILABLE), any()))
                .thenReturn(new PageImpl<>(List.of(rex())));

        mockMvc.perform(get("/api/dogs").param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Rex"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void getDogsRejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/dogs").param("status", "FLYING"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDogReturnsDog() throws Exception {
        when(dogService.getDog(1L)).thenReturn(rex());

        mockMvc.perform(get("/api/dogs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rex"));
    }

    @Test
    void getDogReturnsNotFoundProblemDetail() throws Exception {
        when(dogService.getDog(99L)).thenThrow(new ResourceNotFoundException("Dog with id 99 not found"));

        mockMvc.perform(get("/api/dogs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Dog with id 99 not found"));
    }

    @Test
    void createDogReturnsCreatedWithLocation() throws Exception {
        when(dogService.createDog(any(DogRequest.class))).thenReturn(rex());

        mockMvc.perform(post("/api/dogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rex\",\"breed\":\"Mixed\",\"birthYear\":2020}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/dogs/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createDogRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/dogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDogRejectsInvalidBirthYear() throws Exception {
        mockMvc.perform(post("/api/dogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rex\",\"birthYear\":1800}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDogReturnsUpdatedDog() throws Exception {
        when(dogService.updateDog(eq(1L), any(DogRequest.class))).thenReturn(rex());

        mockMvc.perform(put("/api/dogs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rex\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rex"));
    }

    @Test
    void deleteDogReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/dogs/1"))
                .andExpect(status().isNoContent());

        verify(dogService).deleteDog(1L);
    }

    @Test
    void deleteDogReturnsNotFoundForUnknownId() throws Exception {
        doThrow(new ResourceNotFoundException("Dog with id 99 not found")).when(dogService).deleteDog(99L);

        mockMvc.perform(delete("/api/dogs/99"))
                .andExpect(status().isNotFound());
    }
}
