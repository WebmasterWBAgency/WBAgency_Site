package com.wbscouting.api.service.publicapi;

import com.wbscouting.api.dto.publicapi.ModelDetailPublicDto;
import com.wbscouting.api.dto.publicapi.ModelMediaPublicItemDto;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.MediaType;
import com.wbscouting.api.exception.ResourceNotFoundException;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.storage.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicModelDetailServiceImpl implements PublicModelDetailService {

    private final ModelRepository modelRepository;
    private final ModelMediaRepository modelMediaRepository;
    private final SupabaseStorageService storageService;

    // Helper: constroi ModelMediaPublicItemDto com URL validada e completa quando
    // fileUrl estiver vazio / incompleto (bug JPA storagePath antigo).
    private ModelMediaPublicItemDto buildMediaItem(ModelMedia m) {
        if (m == null) return null;
        String url;
        if (storageService == null || storageService.getProperties() == null) {
            url = m.getFileUrl();
        } else {
            String bucket = MediaType.COMPOSITE == m.getMediaType()
                    ? storageService.getProperties().resolveBucketSiteAssets()
                    : storageService.getProperties().resolveBucketModelsMedia();
            url = storageService.resolvePublicUrlFromFields(bucket, m.getFilePath(), m.getFileUrl());
        }
        return ModelMediaPublicItemDto.builder()
                .id(m.getId())
                .fileUrl(url)
                .displayOrder(m.getDisplayOrder())
                .isCover(m.getIsCover())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ModelDetailPublicDto getModelDetail(UUID id) {
        log.info("Buscando detalhes públicos do perfil do modelo ID: {}", id);

        Model model = modelRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo não encontrado ou inativo"));

        List<ModelMedia> mediaList = modelMediaRepository.findByModelIdAndIsActiveTrueOrderByDisplayOrderAsc(id);
        if (mediaList == null || mediaList.isEmpty()) {
            try {
                if (model.getMedia() != null && !model.getMedia().isEmpty()) {
                    mediaList = model.getMedia();
                }
            } catch (Exception ignored) {}
        }
        if (mediaList == null) {
            mediaList = new ArrayList<>();
        }

        List<ModelMediaPublicItemDto> bookPhotos = new ArrayList<>();
        List<ModelMediaPublicItemDto> polaroids = new ArrayList<>();
        ModelMediaPublicItemDto composite = null;
        String coverUrlFromMedia = null;

        for (ModelMedia m : mediaList) {
            if (m != null && Boolean.TRUE.equals(m.getIsActive())) {
                ModelMediaPublicItemDto item = buildMediaItem(m);

                if (Boolean.TRUE.equals(m.getIsCover()) && coverUrlFromMedia == null && item != null) {
                    coverUrlFromMedia = item.getFileUrl();
                }

                if (m.getMediaType() == MediaType.BOOK) {
                    bookPhotos.add(item);
                } else if (m.getMediaType() == MediaType.POLAROID) {
                    polaroids.add(item);
                } else if (m.getMediaType() == MediaType.COMPOSITE) {
                    if (composite == null) {
                        composite = item;
                    }
                }
            }
        }

        String compositeUrl = composite != null ? composite.getFileUrl() : null;

        // Resolução resiliente da foto de capa
        String bucketModels = (storageService != null && storageService.getProperties() != null)
                ? storageService.getProperties().resolveBucketModelsMedia()
                : "models-media";

        String primaryPhotoUrl = model.getPrimaryPhotoUrl();
        if (primaryPhotoUrl != null && !primaryPhotoUrl.isBlank()) {
            if (storageService != null) {
                primaryPhotoUrl = storageService.resolvePublicUrlFromFields(bucketModels, null, primaryPhotoUrl);
            }
        } else {
            // Fallbacks: 1. isCover=true em media, 2. primeira foto do book, 3. primeira polaroid
            if (coverUrlFromMedia != null && !coverUrlFromMedia.isBlank()) {
                primaryPhotoUrl = coverUrlFromMedia;
            } else if (!bookPhotos.isEmpty() && bookPhotos.get(0).getFileUrl() != null) {
                primaryPhotoUrl = bookPhotos.get(0).getFileUrl();
            } else if (!polaroids.isEmpty() && polaroids.get(0).getFileUrl() != null) {
                primaryPhotoUrl = polaroids.get(0).getFileUrl();
            }
        }

        // Idade demográfica com cálculo automático caso birthday esteja presente
        Integer age = model.getAge();
        if (age == null && model.getBirthDate() != null) {
            try {
                age = Period.between(model.getBirthDate(), LocalDate.now()).getYears();
            } catch (Exception ignored) {}
        }

        String rawInstagram = model.getInstagramUrl();
        String instagramHandle = null;
        if (rawInstagram != null && !rawInstagram.isBlank()) {
            String clean = rawInstagram.trim();
            if (clean.startsWith("http://") || clean.startsWith("https://")) {
                clean = clean.replaceAll("/+$", "");
                int slash = clean.lastIndexOf('/');
                if (slash >= 0 && slash < clean.length() - 1) {
                    instagramHandle = "@" + clean.substring(slash + 1).replace("@", "");
                } else {
                    instagramHandle = clean;
                }
            } else {
                instagramHandle = clean.startsWith("@") ? clean : "@" + clean;
            }
        }

        return ModelDetailPublicDto.builder()
                .id(model.getId())
                .stageName(model.getStageName())
                .gender(model.getGender())
                .isStar(model.getIsStar())
                .city(model.getCity())
                .nationality(model.getNationality())
                .age(age)
                .primaryPhotoUrl(primaryPhotoUrl)
                .instagramUrl(model.getInstagramUrl())
                .instagramHandle(instagramHandle)
                .heightCm(model.getHeightCm())
                .bustChestCm(model.getBustChestCm())
                .waistCm(model.getWaistCm())
                .hipsCm(model.getHipsCm())
                .dressSize(model.getDressSize())
                .shoeSize(model.getShoeSize())
                .eyeColor(model.getEyesColor())
                .hairColor(model.getHairColor())
                .bookPhotos(bookPhotos)
                .polaroids(polaroids)
                .composite(composite)
                .compositeUrl(compositeUrl)
                .build();
    }
}
