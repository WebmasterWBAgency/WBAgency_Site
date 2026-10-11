package com.wbscouting.api.integration;

import com.wbscouting.api.entity.Candidate;
import com.wbscouting.api.entity.CandidatePhoto;
import com.wbscouting.api.enums.CandidateStatus;
import com.wbscouting.api.repository.CandidatePhotoRepository;
import com.wbscouting.api.repository.CandidateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class CandidateSubmissionLifecycleIntegrationTest {

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private CandidatePhotoRepository candidatePhotoRepository;

    @Test
    @Transactional
    @DisplayName("Garante persistência atômica da candidatura canônica e fotos filhas sem violação de FK")
    void shouldPersistSubmissionAndPhotosWithoutFkViolation() {
        // 1. Cria e persiste entidade pai canônica Candidate
        Candidate candidate = new Candidate();
        candidate.setFullName("Marina Silva");
        candidate.setEmail("marina.test@example.com");
        candidate.setPhone("+5511999998888");
        candidate.setBirthDate(LocalDate.of(2000, 5, 20));
        candidate.setAge(26);
        candidate.setGender("FEMALE");
        candidate.setCity("São Paulo");
        candidate.setState("SP");
        candidate.setHeightCm(BigDecimal.valueOf(178));
        candidate.setStatus(CandidateStatus.PENDING);
        candidate.setLgpdAccepted(true);

        Candidate savedCandidate = candidateRepository.saveAndFlush(candidate);
        assertNotNull(savedCandidate.getId(), "ID do candidato persistido não pode ser nulo");
        assertTrue(candidateRepository.existsById(savedCandidate.getId()), "Candidato pai deve existir no banco de dados");

        // 2. Itera sobre uploads simulados e associa ao candidato persistido
        List<CandidatePhoto> photoEntities = new ArrayList<>();
        short position = 1;
        for (int i = 0; i < 3; i++) {
            String storagePath = "candidates/" + savedCandidate.getId() + "/photo_" + position + ".jpg";
            CandidatePhoto photo = new CandidatePhoto();
            photo.setCandidate(savedCandidate);
            photo.setPhotoPosition(position++);
            photo.setFileUrl("https://example.com/" + storagePath);
            photo.setFilePath(storagePath);
            photo.setStoragePath(storagePath);
            photo.setDisplayOrder((int) position);
            photo.setUploadedAt(OffsetDateTime.now());
            photoEntities.add(photo);
        }

        // 3. Salva fotos filhas vinculadas ao candidato
        candidatePhotoRepository.saveAll(photoEntities);

        // 4. Valida integridade referencial e contagem
        assertTrue(candidateRepository.existsById(savedCandidate.getId()), "Candidato pai deve continuar existindo");
        assertEquals(3L, candidatePhotoRepository.countByCandidateId(savedCandidate.getId()), "Devem existir 3 fotos filhas associadas via FK ao candidato");
    }
}
