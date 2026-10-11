package com.wbscouting.api.service.media;

import com.wbscouting.api.WbScoutingApiApplication;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.MediaType;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = WbScoutingApiApplication.class)
@ActiveProfiles("test")
@Transactional
class ModelMediaSynchronizationTest {

    @Autowired
    private ModelRepository modelRepository;

    @Autowired
    private ModelMediaRepository modelMediaRepository;

    @Autowired
    private ModelMediaService modelMediaService;

    @Test
    void deveAtualizarPrimaryPhotoUrlNoModeloAoDefinirNovaCapa() {
        // 1. Criar Modelo base
        Model model = new Model();
        model.setStageName("Modelo Teste");
        model.setGender(GenderType.FEMALE);
        model = modelRepository.save(model);

        // 2. Adicionar Mídia 1 (Capa inicial)
        ModelMedia media1 = new ModelMedia();
        media1.setModel(model);
        media1.setFileUrl("https://storage.supabase.co/models-media/foto1.jpg");
        media1.setFilePath("foto1.jpg");
        media1.setMediaType(MediaType.BOOK);
        media1.setIsCover(true);
        media1 = modelMediaRepository.save(media1);

        // Disparar sincronização do serviço
        modelMediaService.setCoverPhoto(model.getId(), media1.getId());

        Model modelAtualizado = modelRepository.findById(model.getId()).orElseThrow();
        assertEquals("https://storage.supabase.co/models-media/foto1.jpg", modelAtualizado.getPrimaryPhotoUrl());

        // 3. Adicionar Mídia 2 e definir como nova Capa
        ModelMedia media2 = new ModelMedia();
        media2.setModel(model);
        media2.setFileUrl("https://storage.supabase.co/models-media/foto2.jpg");
        media2.setFilePath("foto2.jpg");
        media2.setMediaType(MediaType.BOOK);
        media2.setIsCover(false);
        media2 = modelMediaRepository.save(media2);

        modelMediaService.setCoverPhoto(model.getId(), media2.getId());

        // 4. Assertivas
        Model modelFinal = modelRepository.findById(model.getId()).orElseThrow();
        assertEquals("https://storage.supabase.co/models-media/foto2.jpg", modelFinal.getPrimaryPhotoUrl());

        ModelMedia media1Recarregada = modelMediaRepository.findById(media1.getId()).orElseThrow();
        assertFalse(media1Recarregada.getIsCover(), "Mídia anterior deve ter isCover resetado para false");
    }
}
