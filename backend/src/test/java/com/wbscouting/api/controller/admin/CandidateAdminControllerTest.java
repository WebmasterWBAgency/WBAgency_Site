package com.wbscouting.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wbscouting.api.dto.UpdateSubmissionStatusDto;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.SubmissionStatus;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.security.JwtAuthenticationEntryPoint;
import com.wbscouting.api.security.JwtAuthenticationFilter;
import com.wbscouting.api.security.JwtService;
import com.wbscouting.api.security.JwtTokenProvider;
import com.wbscouting.api.service.candidate.AdminCandidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandidateAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class CandidateAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ModelRepository modelRepository;

    @MockitoBean
    private ModelMediaRepository modelMediaRepository;

    @MockitoBean
    private AdminCandidateService adminCandidateService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UUID sampleId;
    private Model sampleModel;

    @BeforeEach
    void setUp() {
        sampleId = UUID.randomUUID();
        sampleModel = Model.builder()
                .id(sampleId)
                .protocol("WB-20260920-XYZ999")
                .stageName("Isabeli Fontana")
                .fullName("Isabeli Fontana")
                .email("isabeli@models.com")
                .phone("+5541999998888")
                .birthDate(LocalDate.of(2001, 7, 4))
                .age(25)
                .gender(GenderType.FEMALE)
                .city("Curitiba")
                .state("PR")
                .heightCm(177)
                .bustChestCm(new BigDecimal("86"))
                .waistCm(new BigDecimal("60"))
                .hipsCm(new BigDecimal("90"))
                .status(TalentLifecycleStatus.APPLICANT_PENDING)
                .primaryPhotoUrl("https://storage.supabase.co/face.jpg")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/submissions - Deve retornar página de candidaturas com 200 OK")
    void shouldListSubmissions() throws Exception {
        when(modelRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleModel), PageRequest.of(0, 20), 1));
        when(modelMediaRepository.findByModelIdInOrderByDisplayOrderAsc(anyList()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/submissions")
                        .param("page", "0")
                        .param("size", "20")
                        .param("search", "Isabeli")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(sampleId.toString()))
                .andExpect(jsonPath("$.content[0].fullName").value("Isabeli Fontana"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/admin/submissions/{id} - Deve retornar dossiê da candidatura")
    void shouldGetSubmissionById() throws Exception {
        when(modelRepository.findById(sampleId)).thenReturn(Optional.of(sampleModel));
        when(modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(sampleId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/submissions/{id}", sampleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleId.toString()))
                .andExpect(jsonPath("$.fullName").value("Isabeli Fontana"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/submissions/{id}/status - Deve atualizar status com UpdateSubmissionStatusDto")
    void shouldUpdateSubmissionStatus() throws Exception {
        UpdateSubmissionStatusDto updateDto = UpdateSubmissionStatusDto.builder()
                .status(SubmissionStatus.APPROVED)
                .adminNotes("Perfil sob avaliação detalhada.")
                .build();

        when(modelRepository.findById(sampleId)).thenReturn(Optional.of(sampleModel));
        when(modelRepository.save(any(Model.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(sampleId)).thenReturn(List.of());

        mockMvc.perform(patch("/api/v1/admin/submissions/{id}/status", sampleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.internalNotes").value("Perfil sob avaliação detalhada."));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/submissions/{id}/status - Deve rejeitar payload sem status com 400 Bad Request")
    void shouldRejectEmptyStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/submissions/{id}/status", sampleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminNotes\": \"Sem status\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Erro de Validação"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/submissions/{id}/promote-to-model - Promove com sucesso e retorna 200 OK")
    void shouldPromoteToModel() throws Exception {
        when(adminCandidateService.promoteToModel(eq(sampleId), any()))
                .thenReturn(sampleId);

        mockMvc.perform(post("/api/v1/admin/submissions/{id}/promote-to-model", sampleId)
                        .param("activateImmediately", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROMOTED"))
                .andExpect(jsonPath("$.convertedToModelId").value(sampleId.toString()));
    }
}
