package com.wbscouting.api.service;

import com.wbscouting.api.config.MailProperties;
import com.wbscouting.api.dto.CandidateApplicationDto;
import com.wbscouting.api.dto.CandidateDTO;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.MediaType;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final ModelRepository modelRepository;
    private final ModelMediaRepository modelMediaRepository;
    private final EmailService emailService;
    private final MailProperties mailProperties;

    @Transactional
    public CandidateDTO.Response submitApplication(CandidateDTO.ApplicationRequest request) {
        GenderType gender;
        try {
            gender = GenderType.valueOf(request.getGender().toUpperCase());
        } catch (Exception e) {
            gender = GenderType.FEMALE;
        }

        Model model = Model.builder()
                .stageName(request.getFullName())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .age(request.getAge())
                .legalGuardianName(request.getGuardianName())
                .gender(gender)
                .heightCm(request.getHeightCm() != null ? request.getHeightCm().intValue() : 170)
                .weightKg(request.getWeightKg())
                .bustChestCm(request.getBustChestCm())
                .waistCm(request.getWaistCm())
                .hipsCm(request.getHipsCm())
                .instagramUrl(request.getInstagramHandle())
                .lgpdAccepted(request.getLgpdAccepted() != null ? request.getLgpdAccepted() : true)
                .status(TalentLifecycleStatus.APPLICANT_PENDING)
                .isActive(true)
                .build();

        Model saved = modelRepository.save(model);

        if (request.getPhotos() != null && !request.getPhotos().isEmpty()) {
            List<ModelMedia> mediaList = new ArrayList<>();
            for (int i = 0; i < request.getPhotos().size(); i++) {
                var p = request.getPhotos().get(i);
                ModelMedia media = ModelMedia.builder()
                        .model(saved)
                        .mediaType(MediaType.POLAROID)
                        .fileUrl(p.getFileUrl() != null ? p.getFileUrl() : p.getFilePath())
                        .filePath(p.getFilePath() != null ? p.getFilePath() : "photo.jpg")
                        .displayOrder(i + 1)
                        .isCover(i == 0)
                        .isActive(true)
                        .build();
                mediaList.add(media);
            }
            modelMediaRepository.saveAll(mediaList);
            saved.setMediaList(mediaList);
            saved.setPrimaryPhotoUrl(mediaList.get(0).getFileUrl());
            modelRepository.save(saved);
        }

        CandidateApplicationDto applicationDto = CandidateApplicationDto.fromModel(saved);
        if (mailProperties.getAgencyNotificationEmail() != null && !mailProperties.getAgencyNotificationEmail().isBlank()) {
            emailService.sendCandidateApplicationNotification(mailProperties.getAgencyNotificationEmail(), applicationDto);
        }
        if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
            emailService.sendCandidateApplicationNotification(saved.getEmail(), applicationDto);
        }

        return CandidateDTO.Response.builder()
                .id(saved.getId())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .age(saved.getAge())
                .gender(saved.getGender().name())
                .heightCm(saved.getHeightCm() != null ? java.math.BigDecimal.valueOf(saved.getHeightCm()) : null)
                .createdAt(saved.getCreatedAt())
                .photoCount(saved.getMediaList() != null ? saved.getMediaList().size() : 0)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<CandidateDTO.Response> listCandidates(Pageable pageable) {
        return modelRepository.findByStatusIn(List.of(TalentLifecycleStatus.APPLICANT_PENDING, TalentLifecycleStatus.APPLICANT_APPROVED), pageable)
                .map(m -> CandidateDTO.Response.builder()
                        .id(m.getId())
                        .fullName(m.getFullName() != null ? m.getFullName() : m.getStageName())
                        .email(m.getEmail())
                        .phone(m.getPhone())
                        .age(m.getAge())
                        .gender(m.getGender() != null ? m.getGender().name() : "FEMALE")
                        .heightCm(m.getHeightCm() != null ? java.math.BigDecimal.valueOf(m.getHeightCm()) : null)
                        .createdAt(m.getCreatedAt())
                        .photoCount(m.getMediaList() != null ? m.getMediaList().size() : 0)
                        .build());
    }
}
