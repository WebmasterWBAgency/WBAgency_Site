package com.wbscouting.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wbscouting.api.dto.media.MediaOrderItemDto;
import com.wbscouting.api.dto.media.MediaReorderRequestDto;
import com.wbscouting.api.dto.media.MediaUploadResponseDto;
import com.wbscouting.api.enums.MediaType;
import com.wbscouting.api.security.JwtAuthenticationEntryPoint;
import com.wbscouting.api.security.JwtAuthenticationFilter;
import com.wbscouting.api.security.JwtService;
import com.wbscouting.api.security.JwtTokenProvider;
import com.wbscouting.api.service.media.ModelMediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.wbscouting.api.exception.ResourceNotFoundException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminModelMediaController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminModelMediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ModelMediaService modelMediaService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UUID modelId;
    private UUID mediaId;
    private MediaUploadResponseDto responseDto;

    @BeforeEach
    void setUp() {
        modelId = UUID.randomUUID();
        mediaId = UUID.randomUUID();

        responseDto = MediaUploadResponseDto.builder()
                .id(mediaId)
                .modelId(modelId)
                .mediaType(MediaType.BOOK)
                .fileUrl("https://supabase.co/models-media/test.jpg")
                .filePath("models-media/test.jpg")
                .displayOrder(1)
                .isCover(true)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("POST /admin/models/{modelId}/media deve realizar upload e retornar 201 Created")
    void uploadMedia_ReturnsCreated() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "content".getBytes());

        when(modelMediaService.uploadMedia(eq(modelId), eq(MediaType.BOOK), eq(true), any()))
                .thenReturn(responseDto);

        mockMvc.perform(multipart("/admin/models/{modelId}/media", modelId)
                        .file(file)
                        .param("mediaType", "BOOK")
                        .param("isCover", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(mediaId.toString()))
                .andExpect(jsonPath("$.mediaType").value("BOOK"))
                .andExpect(jsonPath("$.isCover").value(true))
                .andExpect(jsonPath("$.fileUrl").value("https://supabase.co/models-media/test.jpg"));
    }

    @Test
    @DisplayName("POST /admin/models/{modelId}/media aceitando type=POLAROID deve retornar 201 Created")
    void uploadMedia_WithTypeParam_ReturnsCreated() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "polaroid.jpg", "image/jpeg", "content".getBytes());

        MediaUploadResponseDto polaroidDto = MediaUploadResponseDto.builder()
                .id(UUID.randomUUID())
                .modelId(modelId)
                .mediaType(MediaType.POLAROID)
                .fileUrl("https://supabase.co/models-media/polaroid.jpg")
                .filePath("models-media/polaroid.jpg")
                .displayOrder(1)
                .isCover(false)
                .createdAt(OffsetDateTime.now())
                .build();

        when(modelMediaService.uploadMedia(eq(modelId), eq(MediaType.POLAROID), eq(false), any()))
                .thenReturn(polaroidDto);

        mockMvc.perform(multipart("/admin/models/{modelId}/media", modelId)
                        .file(file)
                        .param("type", "POLAROID"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("POLAROID"))
                .andExpect(jsonPath("$.isCover").value(false));
    }

    @Test
    @DisplayName("PUT /admin/models/{modelId}/media/reorder deve retornar 204 No Content")
    void reorderMedia_ReturnsNoContent() throws Exception {
        MediaReorderRequestDto request = MediaReorderRequestDto.builder()
                .items(List.of(MediaOrderItemDto.builder().mediaId(mediaId).displayOrder(1).build()))
                .build();

        doNothing().when(modelMediaService).reorderMedia(eq(modelId), any(MediaReorderRequestDto.class));

        mockMvc.perform(put("/admin/models/{modelId}/media/reorder", modelId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /admin/models/{modelId}/media/{mediaId}/cover deve retornar 204 No Content")
    void setCoverMedia_ReturnsNoContent() throws Exception {
        doNothing().when(modelMediaService).setCoverMedia(modelId, mediaId);

        mockMvc.perform(patch("/admin/models/{modelId}/media/{mediaId}/cover", modelId, mediaId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /admin/models/{modelId}/media/{mediaId} deve retornar 204 No Content")
    void deleteMedia_ReturnsNoContent() throws Exception {
        doNothing().when(modelMediaService).deleteMedia(modelId, mediaId);

        mockMvc.perform(delete("/admin/models/{modelId}/media/{mediaId}", modelId, mediaId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /admin/models/{modelId}/media deve listar mídias e retornar 200 OK")
    void listMedia_ReturnsOk() throws Exception {
        when(modelMediaService.listModelMedia(modelId)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/admin/models/{modelId}/media", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(mediaId.toString()))
                .andExpect(jsonPath("$[0].mediaType").value("BOOK"))
                .andExpect(jsonPath("$[0].isCover").value(true));
    }

    @Test
    @DisplayName("DELETE /admin/models/{modelId}/media/{mediaId} deve retornar 404 quando mídia não pertence ao modelo (IDOR)")
    void deleteMedia_WhenMediaNotFound_ReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Mídia não encontrada para o modelo especificado"))
                .when(modelMediaService).deleteMedia(modelId, mediaId);

        mockMvc.perform(delete("/admin/models/{modelId}/media/{mediaId}", modelId, mediaId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /admin/models/{modelId}/media/{mediaId}/cover deve retornar 404 quando mídia não pertence ao modelo (IDOR)")
    void setCoverMedia_WhenMediaNotFound_ReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Mídia não encontrada para o modelo especificado"))
                .when(modelMediaService).setCoverMedia(modelId, mediaId);

        mockMvc.perform(patch("/admin/models/{modelId}/media/{mediaId}/cover", modelId, mediaId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /admin/models/{modelId}/media/reorder deve retornar 404 quando mídia não pertence ao modelo (IDOR)")
    void reorderMedia_WhenMediaNotFound_ReturnsNotFound() throws Exception {
        MediaReorderRequestDto request = MediaReorderRequestDto.builder()
                .items(List.of(MediaOrderItemDto.builder().mediaId(mediaId).displayOrder(1).build()))
                .build();

        doThrow(new ResourceNotFoundException("Mídia não encontrada para o modelo especificado"))
                .when(modelMediaService).reorderMedia(eq(modelId), any(MediaReorderRequestDto.class));

        mockMvc.perform(put("/admin/models/{modelId}/media/reorder", modelId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /admin/models/{modelId}/media/{mediaId}/type deve atualizar o tipo e retornar 200 OK")
    void updateMediaType_ReturnsOk() throws Exception {
        MediaUploadResponseDto updatedDto = MediaUploadResponseDto.builder()
                .id(mediaId)
                .modelId(modelId)
                .mediaType(MediaType.POLAROID)
                .fileUrl("https://supabase.co/models-media/test.jpg")
                .filePath("models-media/test.jpg")
                .displayOrder(5)
                .isCover(false)
                .createdAt(OffsetDateTime.now())
                .build();

        when(modelMediaService.updateMediaType(eq(modelId), eq(mediaId), eq(MediaType.POLAROID)))
                .thenReturn(updatedDto);

        mockMvc.perform(patch("/admin/models/{modelId}/media/{mediaId}/type", modelId, mediaId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"mediaType\":\"POLAROID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mediaId.toString()))
                .andExpect(jsonPath("$.mediaType").value("POLAROID"))
                .andExpect(jsonPath("$.isCover").value(false));
    }
}
