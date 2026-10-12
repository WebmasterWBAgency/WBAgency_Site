package com.wbscouting.api.service.candidate;

import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.dto.admin.candidate.CandidateDetailAdminDto;
import com.wbscouting.api.dto.admin.candidate.CandidateListItemAdminDto;
import com.wbscouting.api.dto.admin.candidate.CandidateNotesUpdateDto;
import com.wbscouting.api.dto.admin.candidate.CandidateStatusUpdateDto;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.CandidateStatus;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.exception.ResourceNotFoundException;
import com.wbscouting.api.repository.CandidateSubmissionRepository;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCandidateServiceImplTest {

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private ModelMediaRepository modelMediaRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private SupabaseProperties supabaseProperties;

    @Mock
    private com.wbscouting.api.repository.CandidateSubmissionRepository candidateSubmissionRepository;

    @InjectMocks
    private AdminCandidateServiceImpl adminCandidateService;

    private UUID candidateId;
    private Model model;
    private ModelMedia media1;
    private ModelMedia media2;

    @BeforeEach
    void setUp() {
        candidateId = UUID.randomUUID();

        media1 = ModelMedia.builder()
                .id(UUID.randomUUID())
                .filePath("cand-1/photo1.jpg")
                .displayOrder(1)
                .isCover(true)
                .build();

        media2 = ModelMedia.builder()
                .id(UUID.randomUUID())
                .filePath("cand-1/photo2.jpg")
                .displayOrder(2)
                .isCover(false)
                .build();

        model = Model.builder()
                .id(candidateId)
                .stageName("Mariana Silva")
                .fullName("Mariana Silva")
                .email("mariana@exemplo.com")
                .phone("11988887777")
                .birthDate(LocalDate.now().minusYears(17)) // Menor de idade
                .age(17)
                .gender(GenderType.FEMALE)
                .heightCm(178)
                .city("São Paulo")
                .state("SP")
                .status(TalentLifecycleStatus.APPLICANT_PENDING)
                .internalNotes("Perfil promissor")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        lenient().when(supabaseProperties.resolveBucketCandidates()).thenReturn("candidates-uploads");
    }

    @Test
    @DisplayName("Deve listar candidaturas com projeção compacta e contagem agregada de fotos")
    void listCandidates_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Model> modelPage = new PageImpl<>(List.of(model), pageable, 1);

        when(modelRepository.findAdminWithFiltersAndStatuses(any(), any(), any(), anyList(), any(), eq(pageable)))
                .thenReturn(modelPage);
        when(modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(candidateId))
                .thenReturn(List.of(media1, media2));

        Page<CandidateListItemAdminDto> result = adminCandidateService.listCandidates(
                CandidateStatus.PENDING, "Mariana", "FEMALE", true, pageable
        );

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);

        CandidateListItemAdminDto dto = result.getContent().get(0);
        assertThat(dto.getId()).isEqualTo(candidateId);
        assertThat(dto.getFullName()).isEqualTo("Mariana Silva");
        assertThat(dto.getAge()).isEqualTo(17);
        assertThat(dto.getIsMinor()).isTrue();
        assertThat(dto.getPhotoCount()).isEqualTo(2);
        assertThat(dto.getStatus()).isEqualTo(CandidateStatus.PENDING);
    }

    @Test
    @DisplayName("Deve retornar página vazia quando nenhuma candidatura for encontrada")
    void listCandidates_Empty() {
        Pageable pageable = PageRequest.of(0, 10);
        when(modelRepository.findAdminWithFiltersAndStatuses(any(), any(), any(), anyList(), any(), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        Page<CandidateListItemAdminDto> result = adminCandidateService.listCandidates(
                null, null, null, null, pageable
        );

        assertThat(result).isEmpty();
        verify(modelMediaRepository, never()).findAllByModelIdOrderByDisplayOrderAsc(any());
    }

    @Test
    @DisplayName("Deve retornar detalhes da candidatura com fotos acompanhadas de Signed URLs de 900s e ordenadas")
    void getCandidateDetail_Success() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.of(model));
        when(modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(candidateId))
                .thenReturn(List.of(media1, media2));
        when(storageService.createSignedUrl(eq("candidates-uploads"), eq("cand-1/photo1.jpg"), eq(900)))
                .thenReturn("https://supabase.co/signed/cand-1/photo1.jpg?token=abc");
        when(storageService.createSignedUrl(eq("candidates-uploads"), eq("cand-1/photo2.jpg"), eq(900)))
                .thenReturn("https://supabase.co/signed/cand-1/photo2.jpg?token=def");

        CandidateDetailAdminDto detail = adminCandidateService.getCandidateDetail(candidateId);

        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo(candidateId);
        assertThat(detail.getFullName()).isEqualTo("Mariana Silva");
        assertThat(detail.getIsMinor()).isTrue();
        assertThat(detail.getPhotos()).hasSize(2);

        // Verifica ordenação por displayOrder ASC
        assertThat(detail.getPhotos().get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(detail.getPhotos().get(0).getSignedUrl()).isEqualTo("https://supabase.co/signed/cand-1/photo1.jpg?token=abc");

        assertThat(detail.getPhotos().get(1).getDisplayOrder()).isEqualTo(2);
        assertThat(detail.getPhotos().get(1).getSignedUrl()).isEqualTo("https://supabase.co/signed/cand-1/photo2.jpg?token=def");
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao buscar detalhes de ID inexistente")
    void getCandidateDetail_NotFound_ThrowsException() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminCandidateService.getCandidateDetail(candidateId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Candidatura não encontrada com ID: " + candidateId);
    }

    @Test
    @DisplayName("Deve atualizar status da candidatura com sucesso")
    void updateStatus_Success() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.of(model));
        when(modelRepository.save(any(Model.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(candidateId)).thenReturn(List.of());

        CandidateStatusUpdateDto dto = CandidateStatusUpdateDto.builder()
                .status(CandidateStatus.APPROVED)
                .build();

        CandidateDetailAdminDto result = adminCandidateService.updateStatus(candidateId, dto);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(CandidateStatus.APPROVED);
        assertThat(model.getStatus()).isEqualTo(TalentLifecycleStatus.APPLICANT_APPROVED);
        verify(modelRepository).save(model);
    }

    @Test
    @DisplayName("Deve atualizar anotações internas da candidatura com sucesso")
    void updateNotes_Success() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.of(model));
        when(modelRepository.save(any(Model.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(candidateId)).thenReturn(List.of());

        CandidateNotesUpdateDto dto = CandidateNotesUpdateDto.builder()
                .notes("Aprovada para casting de passarela")
                .build();

        CandidateDetailAdminDto result = adminCandidateService.updateNotes(candidateId, dto);

        assertThat(result).isNotNull();
        assertThat(result.getInternalNotes()).isEqualTo("Aprovada para casting de passarela");
        assertThat(model.getInternalNotes()).isEqualTo("Aprovada para casting de passarela");
        verify(modelRepository).save(model);
    }

    @Test
    @DisplayName("Deve arquivar candidatura (soft delete) definindo status ARCHIVED")
    void deleteCandidate_Success() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.of(model));

        adminCandidateService.deleteCandidate(candidateId);

        assertThat(model.getStatus()).isEqualTo(TalentLifecycleStatus.ARCHIVED);
        verify(modelRepository).save(model);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao tentar arquivar ID inexistente")
    void deleteCandidate_NotFound_ThrowsException() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminCandidateService.deleteCandidate(candidateId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Candidatura não encontrada com ID: " + candidateId);

        verify(modelRepository, never()).delete(any(Model.class));
    }

    @Test
    @DisplayName("Deve promover candidato a modelo com status CAST_PUBLIC")
    void promoteToModel_Success() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.of(model));
        when(modelRepository.save(any(Model.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID resultId = adminCandidateService.promoteToModel(candidateId, true);

        assertThat(resultId).isEqualTo(candidateId);
        verify(modelRepository).save(model);
        assertThat(model.getStatus()).isEqualTo(TalentLifecycleStatus.CAST_PUBLIC);
        assertThat(model.getIsActive()).isTrue();
    }
}
