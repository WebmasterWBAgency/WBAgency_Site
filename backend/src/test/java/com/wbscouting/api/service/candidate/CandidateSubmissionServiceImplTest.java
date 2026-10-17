package com.wbscouting.api.service.candidate;

import com.wbscouting.api.dto.candidate.CandidateApplyRequestDto;
import com.wbscouting.api.dto.candidate.CandidateApplyResponseDto;
import com.wbscouting.api.repository.CandidateSubmissionRepository;
import com.wbscouting.api.service.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateSubmissionServiceImplTest {

    @Mock
    private CandidateApplicationService candidateApplicationService;

    @Mock
    private CandidateSubmissionRepository candidateSubmissionRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private CandidateSubmissionServiceImpl candidateSubmissionService;

    @Test
    @DisplayName("Deve delegar a submissão de formulário de candidatura para o serviço transacional")
    void shouldDelegateSubmissionToCandidateApplicationService() {
        CandidateApplyRequestDto requestDto = CandidateApplyRequestDto.builder()
                .fullName("Helena Ramos")
                .email("helena@ramos.com")
                .build();

        List<MultipartFile> photos = List.of(
                new MockMultipartFile("photos", "photo1.jpg", "image/jpeg", "bytes1".getBytes())
        );

        UUID candidateId = UUID.randomUUID();
        CandidateApplyResponseDto expectedResponse = CandidateApplyResponseDto.builder()
                .protocol("WB-20261012-ABC123")
                .status("PENDING")
                .build();

        when(candidateApplicationService.apply(any(), any())).thenReturn(expectedResponse);

        CandidateApplyResponseDto response = candidateSubmissionService.submit(requestDto, photos);

        assertThat(response).isNotNull();
        assertThat(response.getProtocol()).isEqualTo("WB-20261012-ABC123");
        assertThat(response.getStatus()).isEqualTo("PENDING");

        verify(candidateApplicationService, times(1)).apply(eq(requestDto), eq(photos));
    }

    @Test
    @DisplayName("Deve disparar rollback compensatório no storage quando ocorrer falha relacional no banco")
    void shouldCompensateStorageWhenDatabaseFails() {
        CandidateApplyRequestDto requestDto = CandidateApplyRequestDto.builder()
                .fullName("Helena Ramos")
                .email("helena@ramos.com")
                .build();

        List<MultipartFile> photos = List.of(
                new MockMultipartFile("photos", "photo1.jpg", "image/jpeg", "bytes1".getBytes())
        );

        doThrow(new DataIntegrityViolationException("Erro de chave estrangeira simulado"))
                .when(candidateApplicationService).apply(any(), any());

        assertThatThrownBy(() -> candidateSubmissionService.submitCandidate(requestDto, photos))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Erro de chave estrangeira");

        verify(candidateApplicationService, times(1)).apply(eq(requestDto), eq(photos));
    }
}
