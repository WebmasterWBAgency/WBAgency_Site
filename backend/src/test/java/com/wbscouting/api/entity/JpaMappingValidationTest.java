package com.wbscouting.api.entity;

import com.wbscouting.api.enums.SubmissionGender;
import com.wbscouting.api.enums.SubmissionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("local")
class JpaMappingValidationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("DoD: EntityManagerFactory inicializa com sucesso e mapeamento bidirecional funciona sem divergência de mappedBy")
    void shouldInitializeEntityManagerFactoryAndPersistCandidateSubmissionWithPhotos() {
        assertThat(entityManagerFactory).isNotNull();
        assertThat(entityManagerFactory.isOpen()).isTrue();

        // Cria a submissão canônica
        CandidateSubmission submission = CandidateSubmission.builder()
                .protocol("WB-20261010-TEST")
                .fullName("Isabeli Fontana")
                .email("isabeli@agency.com")
                .phone("+55 41 99999-8888")
                .birthDate(LocalDate.of(1983, 7, 4))
                .age(23)
                .gender(SubmissionGender.FEMALE)
                .city("Curitiba")
                .state("PR")
                .height(new BigDecimal("1.77"))
                .facePhotoUrl("https://example.com/face.jpg")
                .profilePhotoUrl("https://example.com/profile.jpg")
                .fullBodyPhotoUrl("https://example.com/body.jpg")
                .status(SubmissionStatus.PENDING)
                .lgpdConsent(true)
                .lgpdConsentAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        // Foto 1
        CandidatePhoto photo1 = new CandidatePhoto();
        photo1.setSubmission(submission);
        photo1.setStoragePath("submissions/test/face.jpg");
        photo1.setFilePath("submissions/test/face.jpg");
        photo1.setFileUrl("https://example.com/face.jpg");
        photo1.setDisplayOrder(1);
        photo1.setPhotoPosition((short) 1);

        submission.addPhoto(photo1);

        entityManager.persist(submission);
        entityManager.flush();
        entityManager.clear();

        // Carrega do banco e valida persistência e mapeamento
        CandidateSubmission reloaded = entityManager.find(CandidateSubmission.class, submission.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getFullName()).isEqualTo("Isabeli Fontana");
        assertThat(reloaded.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(reloaded.getPhotos()).hasSize(1);
        assertThat(reloaded.getPhotos().get(0).getSubmission().getId()).isEqualTo(submission.getId());
        assertThat(reloaded.getPhotos().get(0).getStoragePath()).isEqualTo("submissions/test/face.jpg");
    }
}
