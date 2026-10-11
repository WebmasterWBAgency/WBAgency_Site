package com.wbscouting.api.entity;

import com.wbscouting.api.enums.CandidateStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("local")
class JpaMappingValidationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("DoD: EntityManagerFactory inicializa com sucesso e mapeamento bidirecional funciona sem divergência de mappedBy na entidade canônica Candidate")
    void shouldInitializeEntityManagerFactoryAndPersistCandidateSubmissionWithPhotos() {
        assertThat(entityManagerFactory).isNotNull();
        assertThat(entityManagerFactory.isOpen()).isTrue();

        // Cria a entidade canônica Candidate
        Candidate candidate = Candidate.builder()
                .protocol("WB-20261010-TEST")
                .fullName("Isabeli Fontana")
                .email("isabeli@agency.com")
                .phone("+55 41 99999-8888")
                .birthDate(LocalDate.of(1983, 7, 4))
                .age(23)
                .gender("FEMALE")
                .city("Curitiba")
                .state("PR")
                .heightCm(BigDecimal.valueOf(177))
                .status(CandidateStatus.PENDING)
                .lgpdAccepted(true)
                .build();

        // Foto 1
        CandidatePhoto photo1 = new CandidatePhoto();
        photo1.setCandidate(candidate);
        photo1.setStoragePath("submissions/test/face.jpg");
        photo1.setFilePath("submissions/test/face.jpg");
        photo1.setFileUrl("https://example.com/face.jpg");
        photo1.setDisplayOrder(1);
        photo1.setPhotoPosition((short) 1);

        candidate.addPhoto(photo1);

        entityManager.persist(candidate);
        entityManager.flush();
        entityManager.clear();

        // Carrega do banco e valida persistência e mapeamento
        Candidate reloaded = entityManager.find(Candidate.class, candidate.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getFullName()).isEqualTo("Isabeli Fontana");
        assertThat(reloaded.getStatus()).isEqualTo(CandidateStatus.PENDING);
        assertThat(reloaded.getPhotos()).hasSize(1);
        assertThat(reloaded.getPhotos().get(0).getCandidate().getId()).isEqualTo(candidate.getId());
        assertThat(reloaded.getPhotos().get(0).getStoragePath()).isEqualTo("submissions/test/face.jpg");
    }
}
