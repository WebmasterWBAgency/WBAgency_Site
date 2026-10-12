package com.wbscouting.api.service.candidate;

import com.wbscouting.api.config.MailProperties;
import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.dto.CandidateApplicationDto;
import com.wbscouting.api.dto.candidate.CandidateApplyRequestDto;
import com.wbscouting.api.dto.candidate.CandidateApplyResponseDto;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.exception.InvalidApplicationException;
import com.wbscouting.api.exception.StorageException;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.email.EmailService;
import com.wbscouting.api.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateApplicationServiceImplTest {

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private ModelMediaRepository modelMediaRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private EmailService emailService;

    @Mock
    private MailProperties mailProperties;

    @Mock
    private SupabaseProperties supabaseProperties;

    @Mock
    private com.wbscouting.api.repository.CandidateSubmissionRepository candidateSubmissionRepository;

    @Mock
    private com.wbscouting.api.repository.CandidatePhotoRepository candidatePhotoRepository;

    @InjectMocks
    private CandidateApplicationServiceImpl candidateApplicationService;

    private CandidateApplyRequestDto validAdultDto;
    private List<MultipartFile> validPhotos;

    @BeforeEach
    void setUp() {
        validAdultDto = CandidateApplyRequestDto.builder()
                .fullName("Isabella Swan")
                .email("isabella@swan.com")
                .phone("(11) 98765-4321")
                .birthDate(LocalDate.now().minusYears(20))
                .gender(GenderType.FEMALE)
                .heightCm(178)
                .city("São Paulo")
                .state("SP")
                .bustChestCm(new BigDecimal("86.00"))
                .waistCm(new BigDecimal("60.00"))
                .hipsCm(new BigDecimal("89.00"))
                .shoeSize("38")
                .dressSize("36")
                .instagramHandle("@isabella_swan")
                .build();

        validPhotos = List.of(
                new MockMultipartFile("photos", "photo1.jpg", "image/jpeg", "fake-image-1".getBytes()),
                new MockMultipartFile("photos", "photo2.png", "image/png", "fake-image-2".getBytes()),
                new MockMultipartFile("photos", "photo3.webp", "image/webp", "fake-image-3".getBytes())
        );

        lenient().when(supabaseProperties.resolveBucketCandidates()).thenReturn("candidates-uploads");
        lenient().when(modelRepository.existsByProtocol(anyString())).thenReturn(false);
        lenient().when(modelRepository.save(any(Model.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(candidateSubmissionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Deve submeter candidatura de adulto com sucesso, efetuar uploads e despachar email")
    void shouldApplySuccessfullyForAdultCandidate() {
        when(mailProperties.getAgencyNotificationEmail()).thenReturn("agency@wbscouting.com");

        UUID generatedId = UUID.randomUUID();
        when(modelRepository.saveAndFlush(any(Model.class))).thenAnswer(invocation -> {
            Model model = invocation.getArgument(0);
            model.setId(generatedId);
            return model;
        });

        when(storageService.getPublicUrl(anyString(), anyString())).thenReturn("https://supabase.co/storage/photo.jpg");

        CandidateApplyResponseDto response = candidateApplicationService.apply(validAdultDto, validPhotos);

        assertThat(response).isNotNull();
        assertThat(response.getCandidateId()).isEqualTo(generatedId);
        assertThat(response.getMessage()).isEqualTo("Candidatura recebida com sucesso.");
        assertThat(response.getSubmittedAt()).isNotNull();

        verify(modelRepository, times(1)).saveAndFlush(any(Model.class));
        verify(modelMediaRepository, times(1)).saveAll(anyList());
        verify(storageService, times(3)).uploadFile(eq("candidates-uploads"), anyString(), any(MultipartFile.class));
        verify(emailService, times(1)).sendCandidateApplicationNotification(eq("agency@wbscouting.com"), any(CandidateApplicationDto.class));
    }

    @Test
    @DisplayName("Deve submeter candidatura de menor com responsável legal informado")
    void shouldApplySuccessfullyForMinorWithGuardian() {
        when(mailProperties.getAgencyNotificationEmail()).thenReturn("agency@wbscouting.com");

        CandidateApplyRequestDto minorDto = CandidateApplyRequestDto.builder()
                .fullName("Lucas Silva")
                .email("lucas@silva.com")
                .phone("(11) 91111-2222")
                .birthDate(LocalDate.now().minusYears(16))
                .gender(GenderType.MALE)
                .heightCm(185)
                .city("Campinas")
                .state("SP")
                .legalGuardianName("Maria Silva")
                .legalGuardianContact("(11) 99999-8888")
                .build();

        UUID generatedId = UUID.randomUUID();
        when(modelRepository.saveAndFlush(any(Model.class))).thenAnswer(invocation -> {
            Model model = invocation.getArgument(0);
            model.setId(generatedId);
            return model;
        });

        CandidateApplyResponseDto response = candidateApplicationService.apply(minorDto, validPhotos);

        assertThat(response).isNotNull();
        assertThat(response.getCandidateId()).isEqualTo(generatedId);
        verify(modelRepository, times(1)).saveAndFlush(any(Model.class));
    }

    @Test
    @DisplayName("Deve lançar InvalidApplicationException quando menor de 18 anos não informar responsável")
    void shouldFailWhenMinorWithoutGuardian() {
        CandidateApplyRequestDto minorWithoutGuardian = CandidateApplyRequestDto.builder()
                .fullName("Lucas Silva")
                .email("lucas@silva.com")
                .phone("(11) 91111-2222")
                .birthDate(LocalDate.now().minusYears(16))
                .gender(GenderType.MALE)
                .heightCm(185)
                .city("Campinas")
                .state("SP")
                .legalGuardianName("")
                .legalGuardianContact("")
                .build();

        assertThatThrownBy(() -> candidateApplicationService.apply(minorWithoutGuardian, validPhotos))
                .isInstanceOf(InvalidApplicationException.class)
                .hasMessageContaining("Candidatos menores de 18 anos devem informar obrigatoriamente o nome e contato do responsável legal.");

        verify(modelRepository, never()).save(any());
        verify(storageService, never()).uploadFile(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Deve falhar se data de nascimento for no futuro")
    void shouldFailWhenBirthDateInFuture() {
        CandidateApplyRequestDto futureDto = CandidateApplyRequestDto.builder()
                .fullName("Jane Doe")
                .email("jane@doe.com")
                .phone("(11) 91111-2222")
                .birthDate(LocalDate.now().plusDays(2))
                .gender(GenderType.FEMALE)
                .heightCm(175)
                .city("São Paulo")
                .state("SP")
                .build();

        assertThatThrownBy(() -> candidateApplicationService.apply(futureDto, validPhotos))
                .isInstanceOf(InvalidApplicationException.class)
                .hasMessageContaining("A data de nascimento deve ser uma data no passado.");
    }

    @Test
    @DisplayName("Deve falhar se quantidade de fotos for menor que 3 ou maior que 8")
    void shouldFailWhenInvalidPhotoCount() {
        List<MultipartFile> twoPhotos = List.of(
                new MockMultipartFile("photos", "1.jpg", "image/jpeg", "1".getBytes()),
                new MockMultipartFile("photos", "2.jpg", "image/jpeg", "2".getBytes())
        );

        assertThatThrownBy(() -> candidateApplicationService.apply(validAdultDto, twoPhotos))
                .isInstanceOf(InvalidApplicationException.class)
                .hasMessageContaining("no mínimo 3 e no máximo 8 fotos");

        List<MultipartFile> ninePhotos = List.of(
                new MockMultipartFile("photos", "1.jpg", "image/jpeg", "1".getBytes()),
                new MockMultipartFile("photos", "2.jpg", "image/jpeg", "2".getBytes()),
                new MockMultipartFile("photos", "3.jpg", "image/jpeg", "3".getBytes()),
                new MockMultipartFile("photos", "4.jpg", "image/jpeg", "4".getBytes()),
                new MockMultipartFile("photos", "5.jpg", "image/jpeg", "5".getBytes()),
                new MockMultipartFile("photos", "6.jpg", "image/jpeg", "6".getBytes()),
                new MockMultipartFile("photos", "7.jpg", "image/jpeg", "7".getBytes()),
                new MockMultipartFile("photos", "8.jpg", "image/jpeg", "8".getBytes()),
                new MockMultipartFile("photos", "9.jpg", "image/jpeg", "9".getBytes())
        );

        assertThatThrownBy(() -> candidateApplicationService.apply(validAdultDto, ninePhotos))
                .isInstanceOf(InvalidApplicationException.class)
                .hasMessageContaining("no mínimo 3 e no máximo 8 fotos");
    }

    @Test
    @DisplayName("Deve falhar se qualquer foto estiver vazia")
    void shouldFailWhenPhotoIsEmpty() {
        List<MultipartFile> photosWithEmpty = List.of(
                new MockMultipartFile("photos", "1.jpg", "image/jpeg", "1".getBytes()),
                new MockMultipartFile("photos", "2.jpg", "image/jpeg", new byte[0]),
                new MockMultipartFile("photos", "3.jpg", "image/jpeg", "3".getBytes())
        );

        assertThatThrownBy(() -> candidateApplicationService.apply(validAdultDto, photosWithEmpty))
                .isInstanceOf(InvalidApplicationException.class)
                .hasMessageContaining("está vazia");
    }

    @Test
    @DisplayName("Deve falhar se foto possuir formato não permitido")
    void shouldFailWhenPhotoHasInvalidContentType() {
        List<MultipartFile> photosWithGif = List.of(
                new MockMultipartFile("photos", "1.jpg", "image/jpeg", "1".getBytes()),
                new MockMultipartFile("photos", "2.gif", "image/gif", "2".getBytes()),
                new MockMultipartFile("photos", "3.jpg", "image/jpeg", "3".getBytes())
        );

        assertThatThrownBy(() -> candidateApplicationService.apply(validAdultDto, photosWithGif))
                .isInstanceOf(InvalidApplicationException.class)
                .hasMessageContaining("Permitidos apenas JPEG, PNG e WEBP");
    }

    @Test
    @DisplayName("Deve falhar e efetuar compensação (deleção no bucket) caso o upload falhe")
    void shouldCompensateStorageFilesWhenUploadFails() {
        UUID generatedId = UUID.randomUUID();
        when(modelRepository.saveAndFlush(any(Model.class))).thenAnswer(invocation -> {
            Model model = invocation.getArgument(0);
            model.setId(generatedId);
            return model;
        });

        // Configura para que upload da primeira foto funcione e da segunda lance erro
        doAnswer(inv -> "uploaded-path-1")
                .doThrow(new StorageException("Falha de conexão com Supabase Storage"))
                .when(storageService).uploadFile(eq("candidates-uploads"), anyString(), any(MultipartFile.class));

        assertThatThrownBy(() -> candidateApplicationService.apply(validAdultDto, validPhotos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Falha de conexão com Supabase Storage");

        // Verifica que se algum arquivo foi enviado antes do erro, a compensação chamou deleteFile
        verify(modelMediaRepository, never()).saveAll(anyList());
        verify(emailService, never()).sendCandidateApplicationNotification(anyString(), any());
    }
}
