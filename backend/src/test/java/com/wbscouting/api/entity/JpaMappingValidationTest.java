package com.wbscouting.api.entity;

import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.MediaType;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("local")
class JpaMappingValidationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("DoD: EntityManagerFactory inicializa com sucesso e mapeamento bidirecional funciona sem divergência de mappedBy na entidade canônica Model e ModelMedia")
    void shouldInitializeEntityManagerFactoryAndPersistModelWithMedia() {
        assertThat(entityManagerFactory).isNotNull();
        assertThat(entityManagerFactory.isOpen()).isTrue();

        // Cria a entidade canônica Model
        Model model = Model.builder()
                .protocol("WB-20261010-TEST")
                .stageName("Isabeli Fontana")
                .fullName("Isabeli Fontana")
                .email("isabeli@agency.com")
                .phone("+55 41 99999-8888")
                .birthDate(LocalDate.of(1983, 7, 4))
                .age(23)
                .gender(GenderType.FEMALE)
                .city("Curitiba")
                .state("PR")
                .heightCm(177)
                .status(TalentLifecycleStatus.APPLICANT_PENDING)
                .lgpdAccepted(true)
                .isActive(true)
                .build();

        entityManager.persist(model);

        // Media 1
        ModelMedia media1 = ModelMedia.builder()
                .model(model)
                .mediaType(MediaType.POLAROID)
                .filePath("models/test/face.jpg")
                .fileUrl("https://example.com/face.jpg")
                .displayOrder(1)
                .isCover(true)
                .isActive(true)
                .build();

        entityManager.persist(media1);
        entityManager.flush();
        entityManager.clear();

        // Carrega do banco e valida persistência e mapeamento
        Model reloaded = entityManager.find(Model.class, model.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getFullName()).isEqualTo("Isabeli Fontana");
        assertThat(reloaded.getStatus()).isEqualTo(TalentLifecycleStatus.APPLICANT_PENDING);

        ModelMedia reloadedMedia = entityManager.find(ModelMedia.class, media1.getId());
        assertThat(reloadedMedia).isNotNull();
        assertThat(reloadedMedia.getModel().getId()).isEqualTo(model.getId());
        assertThat(reloadedMedia.getFilePath()).isEqualTo("models/test/face.jpg");
    }
}
