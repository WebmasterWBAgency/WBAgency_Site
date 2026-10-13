package com.wbscouting.api.service.candidate;

import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.dto.admin.candidate.CandidateDetailAdminDto;
import com.wbscouting.api.dto.admin.candidate.CandidateListItemAdminDto;
import com.wbscouting.api.dto.common.PageResponseDto;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.CandidateStatus;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.MediaType;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.exception.ResourceNotFoundException;
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
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation")
class AdminCandidateQueryServiceImplTest {

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private ModelMediaRepository modelMediaRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private SupabaseProperties supabaseProperties;

    @InjectMocks
    private AdminCandidateQueryServiceImpl adminCandidateQueryService;

    private UUID candidateId;
    private Model candidate;
    private ModelMedia photo1;
    private ModelMedia photo2;

    @BeforeEach
    void setUp() {
        candidateId = UUID.randomUUID();

        photo1 = ModelMedia.builder()
                .id(UUID.randomUUID())
                .filePath("cand-test/photo1.jpg")
                .fileUrl("https://supabase.co/signed/cand-test/photo1.jpg")
                .mediaType(MediaType.POLAROID)
                .displayOrder(1)
                .build();

        photo2 = ModelMedia.builder()
                .id(UUID.randomUUID())
                .filePath("cand-test/photo2.jpg")
                .fileUrl("https://supabase.co/signed/cand-test/photo2.jpg")
                .mediaType(MediaType.POLAROID)
                .displayOrder(2)
                .build();

        candidate = Model.builder()
                .id(candidateId)
                .stageName("Gisele Bündchen")
                .fullName("Gisele Bündchen")
                .email("gisele@exemplo.com")
                .phone("51999998888")
                .birthDate(LocalDate.now().minusYears(17))
                .age(17)
                .gender(GenderType.FEMALE)
                .heightCm(180)
                .city("Horizontina")
                .state("RS")
                .status(TalentLifecycleStatus.APPLICANT_PENDING)
                .internalNotes("Grande potencial editorial")
                .media(new ArrayList<>(List.of(photo1, photo2)))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        photo1.setModel(candidate);
        photo2.setModel(candidate);

        lenient().when(supabaseProperties.resolveBucketCandidates()).thenReturn("candidates-uploads");
        lenient().when(storageService.createSignedUrl(eq("candidates-uploads"), anyString(), eq(900)))
                .thenAnswer(inv -> "https://testref.supabase.co/storage/v1/object/sign/candidates-uploads/" + inv.getArgument(1) + "?token=mock-signed-token");
    }

    @Test
    @DisplayName("Deve listar candidaturas com PageResponseDto")
    void listCandidates_ReturnsPageResponseDto() {
        Page<Model> modelPage = new PageImpl<>(List.of(candidate));
        when(modelRepository.findAdminWithFiltersAndStatuses(any(), any(), any(), anyList(), any(), any(Pageable.class)))
                .thenReturn(modelPage);
        when(modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(candidateId))
                .thenReturn(List.of(photo1, photo2));

        PageResponseDto<CandidateListItemAdminDto> result = adminCandidateQueryService.listCandidates(
                CandidateStatus.PENDING, "FEMALE", "Gisele", true, 0, 20
        );

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);

        CandidateListItemAdminDto dto = result.getContent().get(0);
        assertThat(dto.getId()).isEqualTo(candidateId);
        assertThat(dto.getFullName()).isEqualTo("Gisele Bündchen");
        assertThat(dto.getAge()).isEqualTo(17);
        assertThat(dto.getIsMinor()).isTrue();
        assertThat(dto.getPhotoCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Deve retornar detalhes completos de candidatura canônica")
    void getCandidateDetail_Success() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(candidateId))
                .thenReturn(List.of(photo1, photo2));

        CandidateDetailAdminDto detail = adminCandidateQueryService.getCandidateDetail(candidateId);

        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo(candidateId);
        assertThat(detail.getFullName()).isEqualTo("Gisele Bündchen");
        assertThat(detail.getPhotos()).hasSize(2);
        assertThat(detail.getPhotos().get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(detail.getPhotos().get(1).getDisplayOrder()).isEqualTo(2);
        assertThat(detail.getPhotos().get(0).getSignedUrl()).contains("photo1.jpg?token=mock-signed-token");
        assertThat(detail.getPhotos().get(1).getSignedUrl()).contains("photo2.jpg?token=mock-signed-token");
    }

    @Test
    @DisplayName("Deve gerar Signed URLs com TTL de 900 segundos para fotos de candidatos")
    void getCandidateDetail_GeneratesSignedUrlsWith900sTtl() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(candidateId))
                .thenReturn(List.of(photo1, photo2));

        CandidateDetailAdminDto detail = adminCandidateQueryService.getCandidateDetail(candidateId);

        assertThat(detail.getPhotos()).hasSize(2);
        verify(storageService).createSignedUrl("candidates-uploads", "cand-test/photo1.jpg", 900);
        verify(storageService).createSignedUrl("candidates-uploads", "cand-test/photo2.jpg", 900);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao consultar detalhes de ID inexistente")
    void getCandidateDetail_NotFound_ThrowsException() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminCandidateQueryService.getCandidateDetail(candidateId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Candidatura não encontrada com ID: " + candidateId);
    }

    @Test
    @DisplayName("Deve expurgar candidatura (hard delete) excluindo mídias e registro de model")
    void deleteCandidate_Success() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(candidateId))
                .thenReturn(List.of(photo1, photo2));

        adminCandidateQueryService.deleteCandidate(candidateId);

        verify(modelMediaRepository).deleteAll(anyList());
        verify(modelRepository).delete(candidate);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao tentar excluir ID inexistente")
    void deleteCandidate_NotFound_ThrowsException() {
        when(modelRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminCandidateQueryService.deleteCandidate(candidateId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Candidato não encontrado: " + candidateId);

        verify(modelRepository, never()).delete(any(Model.class));
    }
}
