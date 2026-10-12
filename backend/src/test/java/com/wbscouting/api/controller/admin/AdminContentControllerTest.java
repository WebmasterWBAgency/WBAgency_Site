package com.wbscouting.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wbscouting.api.dto.content.AssetUploadResponseDto;
import com.wbscouting.api.dto.content.SiteContentAdminDto;
import com.wbscouting.api.dto.content.SiteContentUpdateRequestDto;
import com.wbscouting.api.entity.Admin;
import com.wbscouting.api.exception.GlobalExceptionHandler;
import com.wbscouting.api.security.JwtAuthenticationEntryPoint;
import com.wbscouting.api.security.JwtAuthenticationFilter;
import com.wbscouting.api.security.JwtService;
import com.wbscouting.api.security.JwtTokenProvider;
import com.wbscouting.api.service.content.SiteContentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminContentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SiteContentService siteContentService;

    @MockitoBean
    private com.wbscouting.api.service.content.ApplyFaqService applyFaqService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Test
    @DisplayName("GET /api/v1/admin/content - Deve retornar 200 OK com lista de conteúdos institucionais")
    void getAllAdminContent_ReturnsOk() throws Exception {
        SiteContentAdminDto dto = SiteContentAdminDto.builder()
                .id(UUID.randomUUID())
                .sectionKey("HOME_HERO")
                .payloadPt(Map.of("title", "Título"))
                .payloadEn(Map.of("title", "Title"))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(siteContentService.getAllAdminContent()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/admin/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sectionKey").value("HOME_HERO"))
                .andExpect(jsonPath("$[0].payloadPt.title").value("Título"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/content/{sectionKey} - Deve atualizar conteúdo e retornar 200 OK")
    void updateContent_ReturnsOk() throws Exception {
        UUID adminId = UUID.randomUUID();
        Admin admin = Admin.builder().id(adminId).email("admin@wbscouting.com").build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(admin, null, List.of());

        SiteContentUpdateRequestDto request = SiteContentUpdateRequestDto.builder()
                .payloadPt(Map.of("title", "Novo Título"))
                .payloadEn(Map.of("title", "New Title"))
                .mediaUrls(Map.of("videoUrl", "https://supabase.co/video.mp4"))
                .build();

        SiteContentAdminDto responseDto = SiteContentAdminDto.builder()
                .id(UUID.randomUUID())
                .sectionKey("HOME_HERO")
                .payloadPt(Map.of("title", "Novo Título"))
                .payloadEn(Map.of("title", "New Title"))
                .mediaUrls(Map.of("videoUrl", "https://supabase.co/video.mp4"))
                .updatedBy(adminId)
                .build();

        when(siteContentService.updateContent(eq("HOME_HERO"), any(SiteContentUpdateRequestDto.class), any()))
                .thenReturn(responseDto);

        mockMvc.perform(put("/api/v1/admin/content/HOME_HERO")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionKey").value("HOME_HERO"))
                .andExpect(jsonPath("$.payloadPt.title").value("Novo Título"))
                .andExpect(jsonPath("$.updatedBy").value(adminId.toString()));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/content/{sectionKey} - Deve retornar 400 Bad Request se payload obrigatório for nulo")
    void updateContent_InvalidPayload_ReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/admin/content/HOME_HERO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payloadPt\": null, \"payloadEn\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/admin/content/assets/upload - Deve retornar 201 Created ao enviar arquivo válido")
    void uploadAsset_ReturnsCreated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "banner.webp", "image/webp", new byte[100]
        );

        AssetUploadResponseDto responseDto = AssetUploadResponseDto.builder()
                .fileUrl("https://supabase.co/site-assets/assets/banner.webp")
                .storagePath("assets/uuid-banner.webp")
                .fileType("image/webp")
                .fileSizeBytes(100L)
                .build();

        when(siteContentService.uploadAsset(any(), eq("home"))).thenReturn(responseDto);

        mockMvc.perform(multipart("/api/v1/admin/content/assets/upload")
                        .file(file)
                        .param("folder", "home"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileUrl").value("https://supabase.co/site-assets/assets/banner.webp"))
                .andExpect(jsonPath("$.storagePath").value("assets/uuid-banner.webp"))
                .andExpect(jsonPath("$.fileType").value("image/webp"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/institutional/translations/{sectionKey} - Deve atualizar/upsert com sucesso")
    void updateTranslations_Put_ReturnsOk() throws Exception {
        SiteContentAdminDto responseDto = SiteContentAdminDto.builder()
                .id(UUID.randomUUID())
                .sectionKey("TERMS")
                .payloadPt(Map.of("content", "Termos em PT"))
                .payloadEn(Map.of("content", "Terms in EN"))
                .build();

        when(siteContentService.updateTranslation(eq("TERMS"), anyMap(), any())).thenReturn(responseDto);

        mockMvc.perform(put("/api/v1/admin/institutional/translations/TERMS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pt\": {\"content\": \"Termos em PT\"}, \"en\": {\"content\": \"Terms in EN\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionKey").value("TERMS"))
                .andExpect(jsonPath("$.payloadPt.content").value("Termos em PT"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/institutional/translations/{sectionKey} - Deve aceitar POST e atualizar/upsert com sucesso")
    void updateTranslations_Post_ReturnsOk() throws Exception {
        SiteContentAdminDto responseDto = SiteContentAdminDto.builder()
                .id(UUID.randomUUID())
                .sectionKey("PRIVACY")
                .payloadPt(Map.of("content", "LGPD em PT"))
                .payloadEn(Map.of("content", "GDPR in EN"))
                .build();

        when(siteContentService.updateTranslation(eq("PRIVACY"), anyMap(), any())).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/admin/institutional/translations/PRIVACY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pt\": {\"content\": \"LGPD em PT\"}, \"en\": {\"content\": \"GDPR in EN\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionKey").value("PRIVACY"))
                .andExpect(jsonPath("$.payloadPt.content").value("LGPD em PT"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/institutional/translations/SCOUTING - Deve persistir FAQ via applyFaqService")
    void updateTranslations_Scouting_PersistsFaq() throws Exception {
        SiteContentAdminDto responseDto = SiteContentAdminDto.builder()
                .id(UUID.randomUUID())
                .sectionKey("SCOUTING_FAQ")
                .build();

        when(siteContentService.updateTranslation(eq("SCOUTING"), anyMap(), any())).thenReturn(responseDto);

        mockMvc.perform(put("/api/v1/admin/institutional/translations/SCOUTING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"question\": \"Q1\", \"answer\": \"A1\"}]}"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(applyFaqService).saveBatch(any());
    }
}
