package com.wbscouting.api.service.candidate;

import com.wbscouting.api.entity.CandidatePhoto;
import com.wbscouting.api.entity.CandidateSubmission;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.enums.CandidateStatus;
import com.wbscouting.api.repository.CandidatePhotoRepository;
import com.wbscouting.api.repository.CandidateSubmissionRepository;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.audit.AuditLogService;
import com.wbscouting.api.service.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateSubmissionAdminServiceImplTest {

    @Mock
    private CandidateSubmissionRepository candidateSubmissionRepository;

    @Mock
    private CandidatePhotoRepository candidatePhotoRepository;

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private ModelMediaRepository modelMediaRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CandidateSubmissionAdminServiceImpl adminService;

    @Test
    @DisplayName("MEDIO-004: Tentativa de exclusão física de candidatura PENDING deve lançar IllegalStateException")
    void shouldBlockDeletionWhenStatusIsPending() {
        UUID id = UUID.randomUUID();
        CandidateSubmission submission = CandidateSubmission.builder()
                .id(id)
                .status("PENDING")
                .fullName("Ana Clara")
                .email("ana@example.com")
                .protocol("WB-20261017-PEND")
                .build();

        when(candidateSubmissionRepository.findById(id)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> adminService.deleteApplication(id, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Candidaturas com status PENDENTE ou APROVADO não podem ser excluídas fisicamente");

        verify(candidateSubmissionRepository, never()).delete(any(CandidateSubmission.class));
        verify(candidatePhotoRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("MEDIO-004: Tentativa de exclusão física de candidatura APPROVED deve lançar IllegalStateException")
    void shouldBlockDeletionWhenStatusIsApproved() {
        UUID id = UUID.randomUUID();
        CandidateSubmission submission = CandidateSubmission.builder()
                .id(id)
                .status("APPROVED")
                .fullName("Bruno Costa")
                .email("bruno@example.com")
                .protocol("WB-20261017-APPR")
                .build();

        when(candidateSubmissionRepository.findById(id)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> adminService.deleteApplication(id, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Candidaturas com status PENDENTE ou APROVADO não podem ser excluídas fisicamente");

        verify(candidateSubmissionRepository, never()).delete(any(CandidateSubmission.class));
    }

    @Test
    @DisplayName("MEDIO-004: Exclusão física definitiva de candidatura REJECTED deve remover fotos, registro e registrar audit log")
    void shouldAllowDeletionWhenStatusIsRejectedAndRecordAuditLog() {
        UUID id = UUID.randomUUID();
        CandidateSubmission submission = CandidateSubmission.builder()
                .id(id)
                .status("REJECTED")
                .fullName("Carlos Eduardo")
                .email("carlos@example.com")
                .protocol("WB-20261017-REJ1")
                .build();

        CandidatePhoto photo = CandidatePhoto.builder()
                .id(UUID.randomUUID())
                .storagePath("candidates-uploads/submissions/" + id + "/photo1.jpg")
                .submission(submission)
                .build();

        when(candidateSubmissionRepository.findById(id)).thenReturn(Optional.of(submission));
        when(candidatePhotoRepository.findBySubmissionIdOrderByDisplayOrderAsc(id)).thenReturn(List.of(photo));

        adminService.deleteApplication(id, true);

        verify(storageService, times(1)).deleteFile("candidates-uploads", "candidates-uploads/submissions/" + id + "/photo1.jpg");
        verify(candidatePhotoRepository, times(1)).deleteAll(List.of(photo));
        verify(candidateSubmissionRepository, times(1)).delete(submission);

        verify(auditLogService, times(1)).recordLogAsync(
                isNull(),
                isNull(),
                eq("PURGE_CANDIDATE_DATA"),
                eq("CANDIDATE_SUBMISSION"),
                eq(id.toString()),
                contains("Remoção definitiva autorizada"),
                anyMap(),
                isNull(),
                isNull()
        );
    }

    @Test
    @DisplayName("MEDIO-001 & Biometria: promoteToModel deve mapear altura e medidas diretamente como inteiros em cm")
    void shouldPromoteApprovedCandidateWithIntegerBiometrics() {
        UUID id = UUID.randomUUID();
        CandidateSubmission submission = CandidateSubmission.builder()
                .id(id)
                .status("APPROVED")
                .fullName("Juliana Paes")
                .email("juliana@example.com")
                .phone("+5511988887777")
                .gender("FEMALE")
                .birthDate(LocalDate.of(2000, 1, 15))
                .age(26)
                .heightCm(178)
                .bustCm(86)
                .waistCm(61)
                .hipsCm(90)
                .shoeSize(38)
                .eyeColor("Castanhos")
                .hairColor("Castanho")
                .city("São Paulo")
                .state("SP")
                .protocol("WB-20261017-PROM")
                .facePhotoUrl("https://example.com/face.jpg")
                .build();

        when(candidateSubmissionRepository.findById(id)).thenReturn(Optional.of(submission));
        when(modelRepository.save(any(Model.class))).thenAnswer(invocation -> {
            Model m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        UUID modelId = adminService.promoteToModel(id, true);

        assertThat(modelId).isNotNull();

        ArgumentCaptor<Model> modelCaptor = ArgumentCaptor.forClass(Model.class);
        verify(modelRepository, atLeastOnce()).save(modelCaptor.capture());
        Model savedModel = modelCaptor.getAllValues().get(0);

        assertThat(savedModel.getHeightCm()).isEqualTo(178);
        assertThat(savedModel.getBustCm()).isEqualTo(86);
        assertThat(savedModel.getWaistCmInt()).isEqualTo(61);
        assertThat(savedModel.getHipsCmInt()).isEqualTo(90);
    }
}
