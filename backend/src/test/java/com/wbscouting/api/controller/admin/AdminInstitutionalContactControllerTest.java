package com.wbscouting.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wbscouting.api.dto.content.ContactSettingsDto;
import com.wbscouting.api.entity.SiteContent;
import com.wbscouting.api.exception.GlobalExceptionHandler;
import com.wbscouting.api.repository.ContactChannelRepository;
import com.wbscouting.api.repository.SiteContentRepository;
import com.wbscouting.api.security.JwtAuthenticationEntryPoint;
import com.wbscouting.api.security.JwtAuthenticationFilter;
import com.wbscouting.api.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminInstitutionalContactController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminInstitutionalContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SiteContentRepository siteContentRepository;

    @MockitoBean
    private ContactChannelRepository contactChannelRepository;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Test
    @DisplayName("GET /api/v1/admin/institutional/contact - Retorna 200 OK com dados padrão quando banco está vazio")
    void getContactSettings_emptyDatabase_returnsDefaultWith200() throws Exception {
        when(siteContentRepository.findBySectionKey("CONTACT_SETTINGS")).thenReturn(Optional.empty());
        when(siteContentRepository.findBySectionKey("contact")).thenReturn(Optional.empty());
        when(contactChannelRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(Collections.emptyList());
        when(siteContentRepository.saveAndFlush(any(SiteContent.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(get("/api/v1/admin/institutional/contact")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryEmail").value("info@wbagency.com.br"))
                .andExpect(jsonPath("$.whatsapp").value("+55 11 97065-6003"))
                .andExpect(jsonPath("$.phone").value("+55 11 97065-6003"))
                .andExpect(jsonPath("$.socialMediaList[0].name").value("Instagram"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/institutional/contact - Atualiza e persiste com sucesso")
    void updateContactSettings_validPayload_returns200() throws Exception {
        when(siteContentRepository.findBySectionKey("CONTACT_SETTINGS")).thenReturn(Optional.empty());
        when(siteContentRepository.findBySectionKey("contact")).thenReturn(Optional.empty());
        when(siteContentRepository.saveAndFlush(any(SiteContent.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("primaryEmail", "novo@wbagency.com.br");
        payload.put("whatsapp", "+55 11 99999-8888");
        payload.put("phone", "+55 11 3333-4444");

        mockMvc.perform(put("/api/v1/admin/institutional/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryEmail").value("novo@wbagency.com.br"))
                .andExpect(jsonPath("$.whatsapp").value("+55 11 99999-8888"))
                .andExpect(jsonPath("$.phone").value("+55 11 3333-4444"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/institutional/contact - Atualização parcial com sucesso")
    void patchContactSettings_partialPayload_returns200() throws Exception {
        SiteContent existing = SiteContent.builder()
                .sectionKey("CONTACT_SETTINGS")
                .payloadPt(new LinkedHashMap<>(Map.of(
                        "primaryEmail", "antigo@wbagency.com.br",
                        "whatsapp", "+55 11 97065-6003"
                )))
                .build();

        when(siteContentRepository.findBySectionKey("CONTACT_SETTINGS")).thenReturn(Optional.of(existing));
        when(siteContentRepository.saveAndFlush(any(SiteContent.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> patch = Map.of("primaryEmail", "atualizado@wbagency.com.br");

        mockMvc.perform(patch("/api/v1/admin/institutional/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryEmail").value("atualizado@wbagency.com.br"))
                .andExpect(jsonPath("$.whatsapp").value("+55 11 97065-6003"));
    }
}
