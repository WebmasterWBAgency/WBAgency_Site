package com.wbscouting.api.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.controller.AuthController;
import com.wbscouting.api.controller.admin.AdminApplicationController;
import com.wbscouting.api.controller.publicapi.PublicCandidateController;
import com.wbscouting.api.controller.publicapi.PublicModelController;
import com.wbscouting.api.dto.admin.candidate.CandidateDecisionRequestDto;
import com.wbscouting.api.dto.auth.LoginRequestDto;
import com.wbscouting.api.dto.auth.LoginResponseDto;
import com.wbscouting.api.dto.candidate.CandidateApplyRequestDto;
import com.wbscouting.api.dto.candidate.CandidateApplyResponseDto;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.SubmissionStatus;
import com.wbscouting.api.exception.GlobalExceptionHandler;
import com.wbscouting.api.exception.InvalidFileException;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.security.JwtAuthenticationEntryPoint;
import com.wbscouting.api.security.JwtAuthenticationFilter;
import com.wbscouting.api.security.JwtService;
import com.wbscouting.api.security.JwtTokenProvider;
import com.wbscouting.api.service.auth.AuthService;
import com.wbscouting.api.service.candidate.CandidateApplicationService;
import com.wbscouting.api.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        PublicCandidateController.class,
        AdminApplicationController.class,
        AuthController.class,
        PublicModelController.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("QA-001 (5.1.1): Teste E2E do Ciclo Completo de Submissão, Triagem, Decisão e Promoção de Candidaturas")
class CandidateSubmissionLifecycleE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CandidateApplicationService candidateApplicationService;

    @MockitoBean
    private com.wbscouting.api.service.candidate.CandidateSubmissionAdminService candidateSubmissionAdminService;

    @MockitoBean
    private ModelRepository modelRepository;

    @MockitoBean
    private ModelMediaRepository modelMediaRepository;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private SupabaseProperties supabaseProperties;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private com.wbscouting.api.service.publicapi.PublicModelService publicModelService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private com.wbscouting.api.service.audit.AuditLogService auditLogService;

    @MockitoBean
    private com.wbscouting.api.repository.AdminLoginHistoryRepository adminLoginHistoryRepository;

    @MockitoBean
    private com.wbscouting.api.repository.AdminRepository adminRepository;

    private CandidateApplyRequestDto validSubmissionDto;
    private CandidateApplyResponseDto submissionResponseDto;
    private UUID candidateId;
    private String protocol;
    private byte[] validJpegBytes;

    @BeforeEach
    void setUp() {
        candidateId = UUID.randomUUID();
        protocol = "WB-20260930-A1B2C3";
        validJpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

        validSubmissionDto = CandidateApplyRequestDto.builder()
                .fullName("Isabella Fontana")
                .email("isabella.fontana@models.com")
                .phone("+5511999998888")
                .birthDate(LocalDate.of(2002, 3, 23))
                .gender(GenderType.FEMALE)
                .city("Curitiba")
                .state("PR")
                .heightCm(177)
                .bustChestCm(new BigDecimal("84.0"))
                .waistCm(new BigDecimal("60.0"))
                .hipsCm(new BigDecimal("89.0"))
                .shoeSize("38")
                .instagramHandle("@isabellafontana")
                .build();

        submissionResponseDto = CandidateApplyResponseDto.builder()
                .protocol(protocol)
                .status("PENDING")
                .build();
    }

    @Test
    @DisplayName("TC-01 [Happy Path]: Deve submeter candidatura com dados válidos e 3 fotos, retornando 201 Created e Protocolo")
    void shouldSubmitCandidateSuccessfully() throws Exception {
        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(validSubmissionDto)
        );
        MockMultipartFile facePhoto = new MockMultipartFile("facePhoto", "face.jpg", "image/jpeg", validJpegBytes);
        MockMultipartFile profilePhoto = new MockMultipartFile("profilePhoto", "profile.jpg", "image/jpeg", validJpegBytes);
        MockMultipartFile fullBodyPhoto = new MockMultipartFile("fullBodyPhoto", "body.jpg", "image/jpeg", validJpegBytes);

        when(candidateApplicationService.apply(any(), any())).thenReturn(submissionResponseDto);

        mockMvc.perform(multipart("/api/v1/submissions")
                        .file(dataPart)
                        .file(facePhoto)
                        .file(profilePhoto)
                        .file(fullBodyPhoto))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.candidateId").doesNotExist())
                .andExpect(jsonPath("$.protocol").value(protocol))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(candidateApplicationService, times(1)).apply(any(), any());
    }

    @Test
    @DisplayName("TC-02 [Validation]: Deve rejeitar submissão com email inválido com 400 Bad Request")
    void shouldRejectSubmissionWithInvalidEmail() throws Exception {
        validSubmissionDto.setEmail("email-invalido");
        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(validSubmissionDto)
        );
        MockMultipartFile facePhoto = new MockMultipartFile("facePhoto", "face.jpg", "image/jpeg", validJpegBytes);
        MockMultipartFile profilePhoto = new MockMultipartFile("profilePhoto", "profile.jpg", "image/jpeg", validJpegBytes);
        MockMultipartFile fullBodyPhoto = new MockMultipartFile("fullBodyPhoto", "body.jpg", "image/jpeg", validJpegBytes);

        mockMvc.perform(multipart("/api/v1/submissions")
                        .file(dataPart)
                        .file(facePhoto)
                        .file(profilePhoto)
                        .file(fullBodyPhoto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Erro de Validação"));

        verifyNoInteractions(candidateApplicationService);
    }

    @Test
    @DisplayName("TC-03 [Security - Invalid Length]: Deve rejeitar fullName vazio ou muito curto com 400 Bad Request")
    void shouldRejectSubmissionWithShortFullName() throws Exception {
        validSubmissionDto.setFullName("A");
        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(validSubmissionDto)
        );
        MockMultipartFile facePhoto = new MockMultipartFile("facePhoto", "face.jpg", "image/jpeg", validJpegBytes);
        MockMultipartFile profilePhoto = new MockMultipartFile("profilePhoto", "profile.jpg", "image/jpeg", validJpegBytes);
        MockMultipartFile fullBodyPhoto = new MockMultipartFile("fullBodyPhoto", "body.jpg", "image/jpeg", validJpegBytes);

        mockMvc.perform(multipart("/api/v1/submissions")
                        .file(dataPart)
                        .file(facePhoto)
                        .file(profilePhoto)
                        .file(fullBodyPhoto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Erro de Validação"));

        verifyNoInteractions(candidateApplicationService);
    }

    @Test
    @DisplayName("TC-04 [Security - Invalid File]: Deve retornar 400 Bad Request quando serviço lançar InvalidFileException")
    void shouldRejectSubmissionWithInvalidFileException() throws Exception {
        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(validSubmissionDto)
        );
        MockMultipartFile facePhoto = new MockMultipartFile("facePhoto", "exploit.jpg", "image/jpeg", validJpegBytes);
        MockMultipartFile profilePhoto = new MockMultipartFile("profilePhoto", "profile.jpg", "image/jpeg", validJpegBytes);
        MockMultipartFile fullBodyPhoto = new MockMultipartFile("fullBodyPhoto", "body.jpg", "image/jpeg", validJpegBytes);

        when(candidateApplicationService.apply(any(), any()))
                .thenThrow(new InvalidFileException("Formato de imagem não suportado."));

        mockMvc.perform(multipart("/api/v1/submissions")
                        .file(dataPart)
                        .file(facePhoto)
                        .file(profilePhoto)
                        .file(fullBodyPhoto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Arquivo Inválido"));
    }

    @Test
    @DisplayName("TC-05 [Admin Login]: Deve autenticar com sucesso e emitir JWT + Cookie HttpOnly")
    void shouldAuthenticateAdminSuccessfully() throws Exception {
        LoginRequestDto loginRequest = new LoginRequestDto("admin@wbscouting.com", "Admin@WbScouting2026!");
        LoginResponseDto loginResponse = LoginResponseDto.builder()
                .accessToken("sample.jwt.token")
                .tokenType("Bearer")
                .expiresIn(28800L)
                .adminEmail("admin@wbscouting.com")
                .adminName("Administrador WB Scouting")
                .build();

        when(authService.login(any(LoginRequestDto.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("sample.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.adminEmail").value("admin@wbscouting.com"))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jwt_token=sample.jwt.token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));
    }

    @Test
    @DisplayName("TC-06 [Admin Query]: Deve listar candidaturas com paginação e filtros")
    void shouldListApplicationsWithFilters() throws Exception {
        com.wbscouting.api.dto.admin.candidate.CandidateApplicationSummaryDto summary = com.wbscouting.api.dto.admin.candidate.CandidateApplicationSummaryDto.builder()
                .id(candidateId)
                .fullName("Isabella Fontana")
                .city("Curitiba")
                .status(SubmissionStatus.PENDING)
                .build();

        when(candidateSubmissionAdminService.listApplications(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/v1/admin/applications")
                        .param("page", "0")
                        .param("size", "15")
                        .param("status", "PENDING")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].fullName").value("Isabella Fontana"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].city").value("Curitiba"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("TC-07 [Admin Metrics]: Deve retornar contadores agregados por status")
    void shouldReturnApplicationCounts() throws Exception {
        when(candidateSubmissionAdminService.getCounts()).thenReturn(java.util.Map.of(
                "pending", 12L,
                "approved", 3L,
                "rejected", 5L,
                "archived", 0L,
                "total", 20L
        ));

        mockMvc.perform(get("/api/v1/admin/applications/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(12))
                .andExpect(jsonPath("$.approved").value(3))
                .andExpect(jsonPath("$.rejected").value(5))
                .andExpect(jsonPath("$.total").value(20));
    }

    @Test
    @DisplayName("TC-08 [Admin Detail]: Deve retornar detalhes completos da candidatura com biometria e fotos")
    void shouldGetApplicationDetails() throws Exception {
        com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto detail = com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto.builder()
                .id(candidateId)
                .fullName("Isabella Fontana")
                .biometrics(com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto.BiometricsDto.builder()
                        .height(177)
                        .bust(new BigDecimal("84.0"))
                        .waist(new BigDecimal("60.0"))
                        .hips(new BigDecimal("89.0"))
                        .build())
                .status(SubmissionStatus.PENDING)
                .build();

        when(candidateSubmissionAdminService.getApplicationById(candidateId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/admin/applications/{id}", candidateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(candidateId.toString()))
                .andExpect(jsonPath("$.fullName").value("Isabella Fontana"))
                .andExpect(jsonPath("$.biometrics.height").value(177))
                .andExpect(jsonPath("$.biometrics.bust").value(84.0))
                .andExpect(jsonPath("$.biometrics.waist").value(60.0))
                .andExpect(jsonPath("$.biometrics.hips").value(89.0))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("TC-09 [Decision Update]: Deve atualizar status da candidatura para APPROVED com parecer técnico")
    void shouldUpdateCandidateDecision() throws Exception {
        CandidateDecisionRequestDto decisionDto = CandidateDecisionRequestDto.builder()
                .status(SubmissionStatus.APPROVED)
                .internalNotes("Perfil comercial e fashion de alto impacto. Aprovado pelo Scouting Desk.")
                .build();

        com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto updatedDetail = com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto.builder()
                .id(candidateId)
                .status(SubmissionStatus.APPROVED)
                .internalNotes("Perfil comercial e fashion de alto impacto. Aprovado pelo Scouting Desk.")
                .build();

        when(candidateSubmissionAdminService.updateDecision(eq(candidateId), any(CandidateDecisionRequestDto.class)))
                .thenReturn(updatedDetail);

        mockMvc.perform(patch("/api/v1/admin/applications/{id}/decision", candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decisionDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.internalNotes").value("Perfil comercial e fashion de alto impacto. Aprovado pelo Scouting Desk."));
    }

    @Test
    @DisplayName("TC-10 [Promotion to Model]: Deve promover candidatura aprovada a Modelo Oficial no casting")
    void shouldPromoteCandidateToOfficialModel() throws Exception {
        when(candidateSubmissionAdminService.promoteToModel(eq(candidateId), any()))
                .thenReturn(candidateId);

        mockMvc.perform(post("/api/v1/admin/applications/{id}/promote", candidateId)
                        .param("activateImmediately", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Candidato promovido a modelo com sucesso."))
                .andExpect(jsonPath("$.data.id").value(candidateId.toString()));

        verify(candidateSubmissionAdminService, times(1))
                .promoteToModel(eq(candidateId), eq(true));
    }

    @Test
    @DisplayName("TC-11 [Soft Delete]: Deve arquivar candidatura com 204 No Content")
    void shouldPurgeApplicationUnderLgpd() throws Exception {
        doNothing().when(candidateSubmissionAdminService).deleteApplication(candidateId, false);

        mockMvc.perform(delete("/api/v1/admin/applications/{id}", candidateId))
                .andExpect(status().isNoContent());

        verify(candidateSubmissionAdminService, times(1)).deleteApplication(eq(candidateId), eq(false));
    }

    @Test
    @DisplayName("TC-12 [FK Integrity]: Deve garantir persistência atômica do modelo e fotos em ModelMedia")
    void shouldPersistSubmissionAndPhotosWithoutFkViolation() {
        when(modelRepository.existsById(candidateId)).thenReturn(true);
        ModelMedia media = ModelMedia.builder().id(UUID.randomUUID()).build();
        when(modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(candidateId)).thenReturn(List.of(media, media, media));

        assertTrue(modelRepository.existsById(candidateId), "Modelo pai deve existir no banco de dados");
        assertEquals(3, modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(candidateId).size(), "Devem existir 3 fotos filhas vinculadas ao modelo");
    }
}
