package com.wbscouting.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wbscouting.api.dto.admin.candidate.CandidateDecisionRequestDto;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.SubmissionStatus;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.exception.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminApplicationController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminApplicationControllerTest {

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

    private Model model;
    private UUID submissionId;

    @BeforeEach
    void setUp() {
        submissionId = UUID.randomUUID();
        model = Model.builder()
                .id(submissionId)
                .protocol("WB-2026-TEST")
                .stageName("Mariana Souza Fagundes")
                .fullName("Mariana Souza Fagundes")
                .email("mariana.souza@email.com")
                .phone("+55 11 98888-7777")
                .birthDate(LocalDate.of(2008, 5, 14))
                .age(18)
                .gender(GenderType.FEMALE)
                .city("São Paulo")
                .state("SP")
                .heightCm(178)
                .bustChestCm(new BigDecimal("83.0"))
                .waistCm(new BigDecimal("59.0"))
                .hipsCm(new BigDecimal("88.0"))
                .shoeSize("37")
                .eyesColor("Castanho Claro")
                .hairColor("Castanho Natural")
                .instagramUrl("@marianasouza")
                .lgpdAccepted(true)
                .status(TalentLifecycleStatus.APPLICANT_PENDING)
                .primaryPhotoUrl("https://example.com/face.jpg")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/applications deve retornar 200 OK com página de candidaturas")
    void listApplications_ReturnsOk() throws Exception {
        when(modelRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(model)));
        when(modelMediaRepository.findByModelIdInOrderByDisplayOrderAsc(anyList()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/applications")
                        .param("page", "0")
                        .param("size", "15")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Mariana Souza Fagundes"))
                .andExpect(jsonPath("$.content[0].height").value(178))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/admin/applications/counts deve retornar contadores por status")
    void getCounts_ReturnsOk() throws Exception {
        when(modelRepository.countByStatus(TalentLifecycleStatus.APPLICANT_PENDING)).thenReturn(10L);
        when(modelRepository.countByStatus(TalentLifecycleStatus.APPLICANT_APPROVED)).thenReturn(5L);
        when(modelRepository.countByStatus(TalentLifecycleStatus.APPLICANT_DECLINED)).thenReturn(2L);
        when(modelRepository.countByStatus(TalentLifecycleStatus.ARCHIVED)).thenReturn(1L);

        mockMvc.perform(get("/api/v1/admin/applications/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(10))
                .andExpect(jsonPath("$.approved").value(5))
                .andExpect(jsonPath("$.rejected").value(2))
                .andExpect(jsonPath("$.archived").value(1))
                .andExpect(jsonPath("$.total").value(17));
    }

    @Test
    @DisplayName("GET /api/v1/admin/applications/{id} deve retornar 200 OK com detalhes completos")
    void getApplicationById_ReturnsOk() throws Exception {
        when(modelRepository.findById(submissionId)).thenReturn(Optional.of(model));
        when(modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/applications/{id}", submissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId.toString()))
                .andExpect(jsonPath("$.fullName").value("Mariana Souza Fagundes"))
                .andExpect(jsonPath("$.biometrics.height").value(178))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/applications/{id}/decision deve atualizar status e parecer do scouter")
    void updateDecision_ReturnsOk() throws Exception {
        when(modelRepository.findById(submissionId)).thenReturn(Optional.of(model));
        when(modelRepository.save(any(Model.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        CandidateDecisionRequestDto decisionDto = CandidateDecisionRequestDto.builder()
                .status(SubmissionStatus.APPROVED)
                .internalNotes("Excelente desenvoltura editorial e biometria adequada.")
                .build();

        mockMvc.perform(patch("/api/v1/admin/applications/{id}/decision", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decisionDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.internalNotes").value("Excelente desenvoltura editorial e biometria adequada."));
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/applications/{id} deve excluir candidatura e retornar 204 No Content")
    void deleteApplication_ReturnsNoContent() throws Exception {
        doNothing().when(adminCandidateService).deleteCandidate(eq(submissionId));

        mockMvc.perform(delete("/api/v1/admin/applications/{id}", submissionId))
                .andExpect(status().isNoContent());

        verify(adminCandidateService).deleteCandidate(eq(submissionId));
    }

    @Test
    @DisplayName("POST /api/v1/admin/applications/{id}/promote deve retornar 201 Created com payload do modelo criado")
    void promoteCandidateToModel_ReturnsCreated() throws Exception {
        when(adminCandidateService.promoteToModel(eq(submissionId), any()))
                .thenReturn(submissionId);

        mockMvc.perform(post("/api/v1/admin/applications/{id}/promote", submissionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Candidato promovido a modelo com sucesso."))
                .andExpect(jsonPath("$.data.id").value(submissionId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/admin/applications/{id}/promote com ID inexistente deve retornar 404 Not Found")
    void promoteCandidateToModel_WhenNotFound_ReturnsNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(adminCandidateService.promoteToModel(eq(nonExistentId), any()))
                .thenThrow(new ResourceNotFoundException("Candidatura não encontrada com ID: " + nonExistentId));

        mockMvc.perform(post("/api/v1/admin/applications/{id}/promote", nonExistentId))
                .andExpect(status().isNotFound());
    }
}
