package com.wbscouting.api.integration;

import com.wbscouting.api.entity.CandidatePhoto;
import com.wbscouting.api.entity.CandidateSubmission;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.MediaType;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.repository.CandidatePhotoRepository;
import com.wbscouting.api.repository.CandidateSubmissionRepository;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class CandidateSubmissionLifecycleIntegrationTest {

    @Autowired
    private ModelRepository modelRepository;

    @Autowired
    private ModelMediaRepository modelMediaRepository;

    @Autowired
    private CandidateSubmissionRepository candidateSubmissionRepository;

    @Autowired
    private CandidatePhotoRepository candidatePhotoRepository;

    @Test
    @Transactional
    @DisplayName("Garante persistência atômica da candidatura canônica em Model e fotos filhas em ModelMedia sem violação de FK")
    void shouldPersistSubmissionAndPhotosWithoutFkViolation() {
        // 1. Cria e persiste entidade canônica Model como candidata
        Model model = Model.builder()
                .stageName("Marina Silva")
                .fullName("Marina Silva")
                .email("marina.test@example.com")
                .phone("+5511999998888")
                .birthDate(LocalDate.of(2000, 5, 20))
                .age(26)
                .gender(GenderType.FEMALE)
                .city("São Paulo")
                .state("SP")
                .heightCm(178)
                .status(TalentLifecycleStatus.APPLICANT_PENDING)
                .lgpdAccepted(true)
                .isActive(true)
                .build();

        Model savedModel = modelRepository.saveAndFlush(model);
        assertNotNull(savedModel.getId(), "ID do modelo persistido não pode ser nulo");
        assertTrue(modelRepository.existsById(savedModel.getId()), "Modelo pai deve existir no banco de dados");

        // 2. Itera sobre uploads simulados e associa ao modelo persistido
        List<ModelMedia> mediaEntities = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String storagePath = "models/" + savedModel.getId() + "/photo_" + (i + 1) + ".jpg";
            ModelMedia media = ModelMedia.builder()
                    .model(savedModel)
                    .mediaType(MediaType.BOOK)
                    .fileUrl("https://example.com/" + storagePath)
                    .filePath(storagePath)
                    .displayOrder(i + 1)
                    .isCover(i == 0)
                    .isActive(true)
                    .build();
            mediaEntities.add(media);
        }

        // 3. Salva fotos filhas vinculadas ao modelo
        modelMediaRepository.saveAll(mediaEntities);

        // 4. Valida integridade referencial e contagem
        assertTrue(modelRepository.existsById(savedModel.getId()), "Modelo pai deve continuar existindo");
        List<ModelMedia> loadedMedia = modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(savedModel.getId());
        assertEquals(3, loadedMedia.size(), "Devem existir 3 fotos filhas associadas via FK ao modelo");
    }

    @Test
    @Transactional
    @DisplayName("Garante persistência atômica da entidade canônica CandidateSubmission e fotos filhas CandidatePhoto sem violação de FK")
    void shouldPersistCandidateSubmissionAndPhotosAtomicallyWithoutFkViolation() {
        UUID submissionId = UUID.randomUUID();
        CandidateSubmission submission = CandidateSubmission.builder()
                .id(submissionId)
                .protocol("WB-20261011-TEST1")
                .fullName("Marina Silva")
                .email("marina.silva@example.com")
                .phone("+5511999998888")
                .birthDate(LocalDate.of(2000, 5, 20))
                .age(26)
                .gender("FEMALE")
                .city("São Paulo")
                .state("SP")
                .height(new BigDecimal("1.78"))
                .facePhotoUrl("https://example.com/face.jpg")
                .profilePhotoUrl("https://example.com/profile.jpg")
                .fullBodyPhotoUrl("https://example.com/body.jpg")
                .status("PENDING")
                .lgpdConsent(true)
                .build();

        for (int i = 0; i < 3; i++) {
            CandidatePhoto photo = CandidatePhoto.builder()
                    .storagePath("candidates/" + submissionId + "/photo_" + (i + 1) + ".jpg")
                    .filePath("candidates/" + submissionId + "/photo_" + (i + 1) + ".jpg")
                    .fileUrl("https://example.com/photo_" + (i + 1) + ".jpg")
                    .displayOrder(i + 1)
                    .photoPosition(i + 1)
                    .build();
            submission.addPhoto(photo);
        }

        CandidateSubmission savedSubmission = candidateSubmissionRepository.saveAndFlush(submission);
        assertNotNull(savedSubmission.getId(), "ID da submissão não pode ser nulo");
        assertEquals(submissionId, savedSubmission.getId(), "ID pré-definido deve ser preservado");
        assertEquals(3, savedSubmission.getPhotos().size(), "Devem existir 3 fotos filhas associadas na submissão");

        List<CandidatePhoto> photosFromDb = candidatePhotoRepository.findByCandidateId(submissionId);
        assertEquals(3, photosFromDb.size(), "Devem ser encontradas 3 fotos via repository de CandidatePhoto");
        for (CandidatePhoto p : photosFromDb) {
            assertEquals(submissionId, p.getCandidateId(), "A FK candidate_id deve apontar perfeitamente para a submissão");
            assertNotNull(p.getSubmission(), "O objeto submission vinculado deve estar presente");
        }
    }
}
