package com.wbscouting.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wbscouting.api.dto.model.ModelAdminResponseDto;
import com.wbscouting.api.dto.talent.TalentStatusPatchDto;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.security.JwtAuthenticationEntryPoint;
import com.wbscouting.api.security.JwtAuthenticationFilter;
import com.wbscouting.api.security.JwtService;
import com.wbscouting.api.service.model.ModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminTalentController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminTalentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ModelService modelService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UUID modelId;
    private ModelAdminResponseDto mockResponse;

    @BeforeEach
    void setUp() {
        modelId = UUID.randomUUID();
        mockResponse = ModelAdminResponseDto.builder()
                .id(modelId)
                .stageName("Isabella Viana")
                .status(TalentLifecycleStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/talents/{id}/status - Deve atualizar status do talento com sucesso")
    void shouldUpdateTalentStatusSuccessfully() throws Exception {
        TalentStatusPatchDto request = TalentStatusPatchDto.builder()
                .status(TalentLifecycleStatus.ACTIVE)
                .internalNotes("Aprovada em reunião de casting")
                .build();

        when(modelService.updateTalentStatus(eq(modelId), any(TalentStatusPatchDto.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(patch("/api/v1/admin/talents/{id}/status", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(modelId.toString()))
                .andExpect(jsonPath("$.stageName").value("Isabella Viana"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/talents/{id}/status - Deve rejeitar request com status nulo")
    void shouldRejectWhenStatusIsNull() throws Exception {
        String invalidJson = "{\"internalNotes\": \"sem status informado\"}";

        mockMvc.perform(patch("/api/v1/admin/talents/{id}/status", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
