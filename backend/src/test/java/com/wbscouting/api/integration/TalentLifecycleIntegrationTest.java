package com.wbscouting.api.integration;

import com.wbscouting.api.dto.talent.TalentStatusPatchDto;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.MediaType;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.media.ModelMediaService;
import com.wbscouting.api.service.model.ModelService;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TalentLifecycleIntegrationTest {

    @Autowired
    private ModelRepository modelRepository;

    @Autowired
    private ModelMediaRepository modelMediaRepository;

    @Autowired
    private ModelService modelService;

    @Autowired
    private ModelMediaService modelMediaService;

    @Test
    @Transactional
    @DisplayName("DoD: Criar aplicante com fotos -> validar presença em models com APPLICANT_PENDING e model_media")
    void shouldCreateApplicantWithPhotosAndCorrectStatus() {
        // 1. Cria talento com status APPLICANT_PENDING
        Model applicant = Model.builder()
                .protocol("WB-20261011-0001")
                .stageName("Beatriz Lima")
                .fullName("Beatriz Lima da Silva")
                .email("beatriz.lima@example.com")
                .phone("+5511988887777")
                .gender(GenderType.FEMALE)
                .birthDate(LocalDate.of(2002, 3, 15))
                .age(24)
                .city("São Paulo")
                .state("SP")
                .heightCm(177)
                .bustChestCm(new BigDecimal("86.00"))
                .waistCm(new BigDecimal("61.00"))
                .hipsCm(new BigDecimal("90.00"))
                .status(TalentLifecycleStatus.APPLICANT_PENDING)
                .isActive(true)
                .isStar(false)
                .primaryPhotoUrl("https://storage.example.com/models-media/beatriz/photo1.jpg")
                .build();

        Model savedApplicant = modelRepository.saveAndFlush(applicant);
        assertNotNull(savedApplicant.getId());
        assertEquals(TalentLifecycleStatus.APPLICANT_PENDING, savedApplicant.getStatus());

        // 2. Associa 3 fotos em model_media, primeira como is_cover=true
        List<ModelMedia> mediaList = new ArrayList<>();
        mediaList.add(ModelMedia.builder()
                .model(savedApplicant)
                .mediaType(MediaType.BOOK)
                .fileUrl("https://storage.example.com/models-media/beatriz/photo1.jpg")
                .filePath("models-media/beatriz/photo1.jpg")
                .displayOrder(1)
                .isCover(true)
                .isActive(true)
                .build());

        mediaList.add(ModelMedia.builder()
                .model(savedApplicant)
                .mediaType(MediaType.BOOK)
                .fileUrl("https://storage.example.com/models-media/beatriz/photo2.jpg")
                .filePath("models-media/beatriz/photo2.jpg")
                .displayOrder(2)
                .isCover(false)
                .isActive(true)
                .build());

        mediaList.add(ModelMedia.builder()
                .model(savedApplicant)
                .mediaType(MediaType.BOOK)
                .fileUrl("https://storage.example.com/models-media/beatriz/photo3.jpg")
                .filePath("models-media/beatriz/photo3.jpg")
                .displayOrder(3)
                .isCover(false)
                .isActive(true)
                .build());

        modelMediaRepository.saveAll(mediaList);

        // 3. Valida no banco de dados
        Model found = modelRepository.findById(savedApplicant.getId()).orElse(null);
        assertNotNull(found);
        assertEquals(TalentLifecycleStatus.APPLICANT_PENDING, found.getStatus());
        assertEquals("https://storage.example.com/models-media/beatriz/photo1.jpg", found.getPrimaryPhotoUrl());

        List<ModelMedia> mediasInDb = modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(found.getId());
        assertEquals(3, mediasInDb.size());
        assertTrue(mediasInDb.get(0).getIsCover());
        assertFalse(mediasInDb.get(1).getIsCover());
    }

    @Test
    @Transactional
    @DisplayName("DoD: Transicionar aplicante para CAST_STAR -> validar fotos originais intactas no mesmo UUID")
    void shouldTransitionApplicantToCastStarPreservingMedia() {
        // 1. Cria aplicante com 3 fotos
        Model applicant = Model.builder()
                .protocol("WB-20261011-0002")
                .stageName("Lucas Martins")
                .fullName("Lucas Martins de Souza")
                .email("lucas.martins@example.com")
                .gender(GenderType.MALE)
                .status(TalentLifecycleStatus.APPLICANT_PENDING)
                .isActive(true)
                .primaryPhotoUrl("https://storage.example.com/models-media/lucas/cover.jpg")
                .build();
        applicant = modelRepository.saveAndFlush(applicant);
        UUID talentId = applicant.getId();

        ModelMedia m1 = modelMediaRepository.saveAndFlush(ModelMedia.builder()
                .model(applicant)
                .mediaType(MediaType.BOOK)
                .fileUrl("https://storage.example.com/models-media/lucas/cover.jpg")
                .filePath("models-media/lucas/cover.jpg")
                .displayOrder(1)
                .isCover(true)
                .isActive(true)
                .build());

        ModelMedia m2 = modelMediaRepository.saveAndFlush(ModelMedia.builder()
                .model(applicant)
                .mediaType(MediaType.BOOK)
                .fileUrl("https://storage.example.com/models-media/lucas/photo2.jpg")
                .filePath("models-media/lucas/photo2.jpg")
                .displayOrder(2)
                .isCover(false)
                .isActive(true)
                .build());

        // 2. Transiciona status via updateTalentStatus para CAST_STAR (sem mover tabelas)
        TalentStatusPatchDto patchDto = TalentStatusPatchDto.builder()
                .status(TalentLifecycleStatus.CAST_STAR)
                .internalNotes("Aprovado com louvor no casting internacional.")
                .build();

        modelService.updateTalentStatus(talentId, patchDto);

        // 3. Valida que o UUID permanece exatamente o mesmo
        Model promoted = modelRepository.findById(talentId).orElse(null);
        assertNotNull(promoted);
        assertEquals(talentId, promoted.getId());
        assertEquals(TalentLifecycleStatus.CAST_STAR, promoted.getStatus());
        assertTrue(promoted.getIsStar());
        assertEquals("Aprovado com louvor no casting internacional.", promoted.getInternalNotes());

        // 4. Valida que as mídias originais permanecem intactas no mesmo modelId
        List<ModelMedia> medias = modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(talentId);
        assertEquals(2, medias.size());
        assertEquals(m1.getId(), medias.get(0).getId());
        assertEquals(m2.getId(), medias.get(1).getId());
        assertEquals("https://storage.example.com/models-media/lucas/cover.jpg", promoted.getPrimaryPhotoUrl());
    }

    @Test
    @Transactional
    @DisplayName("DoD: Definir nova capa -> models.primary_photo_url reflete nova mídia atomicamente")
    void shouldUpdateCoverPhotoAndSyncPrimaryPhotoUrlAtomically() {
        // 1. Cria modelo com duas mídias
        Model model = Model.builder()
                .stageName("Camila Rocha")
                .gender(GenderType.FEMALE)
                .status(TalentLifecycleStatus.CAST_PUBLIC)
                .isActive(true)
                .primaryPhotoUrl("https://storage.example.com/models-media/camila/old_cover.jpg")
                .build();
        model = modelRepository.saveAndFlush(model);
        UUID modelId = model.getId();

        ModelMedia oldCoverMedia = modelMediaRepository.saveAndFlush(ModelMedia.builder()
                .model(model)
                .mediaType(MediaType.BOOK)
                .fileUrl("https://storage.example.com/models-media/camila/old_cover.jpg")
                .filePath("models-media/camila/old_cover.jpg")
                .displayOrder(1)
                .isCover(true)
                .isActive(true)
                .build());

        ModelMedia newCoverMedia = modelMediaRepository.saveAndFlush(ModelMedia.builder()
                .model(model)
                .mediaType(MediaType.BOOK)
                .fileUrl("https://storage.example.com/models-media/camila/new_cover.jpg")
                .filePath("models-media/camila/new_cover.jpg")
                .displayOrder(2)
                .isCover(false)
                .isActive(true)
                .build());

        // 2. Executa setCoverPhoto definindo newCoverMedia como nova capa
        modelMediaService.setCoverPhoto(modelId, newCoverMedia.getId());

        // 3. Valida sincronização transacional atômica
        Model updatedModel = modelRepository.findById(modelId).orElse(null);
        assertNotNull(updatedModel);
        assertEquals("https://storage.example.com/models-media/camila/new_cover.jpg", updatedModel.getPrimaryPhotoUrl());

        ModelMedia reloadedOldCover = modelMediaRepository.findById(oldCoverMedia.getId()).orElse(null);
        assertNotNull(reloadedOldCover);
        assertFalse(reloadedOldCover.getIsCover(), "Mídia antiga não deve mais ser capa");

        ModelMedia reloadedNewCover = modelMediaRepository.findById(newCoverMedia.getId()).orElse(null);
        assertNotNull(reloadedNewCover);
        assertTrue(reloadedNewCover.getIsCover(), "Nova mídia selecionada deve ser capa");
    }
}
