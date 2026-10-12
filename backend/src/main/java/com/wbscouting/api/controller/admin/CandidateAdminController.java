package com.wbscouting.api.controller.admin;

import com.wbscouting.api.dto.UpdateSubmissionStatusDto;
import com.wbscouting.api.dto.admin.candidate.CandidateApplicationSummaryDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.SubmissionGender;
import com.wbscouting.api.enums.SubmissionStatus;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.exception.ResourceNotFoundException;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.candidate.AdminCandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping({"/api/v1/admin/submissions", "/admin/submissions"})
@PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'ADMIN')")
@RequiredArgsConstructor
public class CandidateAdminController {

    private final ModelRepository modelRepository;
    private final ModelMediaRepository modelMediaRepository;
    private final AdminCandidateService adminCandidateService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.wbscouting.api.service.storage.StorageService storageService;

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<Page<CandidateApplicationSummaryDto>> listSubmissions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(required = false) SubmissionGender gender,
            @RequestParam(required = false) BigDecimal minHeight,
            @RequestParam(required = false) BigDecimal maxHeight,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.info("Consulta administrativa de candidaturas (models). Status: {}, Search: {}, Pageable: {}", status, search, pageable);

        Specification<Model> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (status != null) {
                TalentLifecycleStatus targetStatus = switch (status) {
                    case APPROVED -> TalentLifecycleStatus.APPLICANT_APPROVED;
                    case REJECTED, DECLINED -> TalentLifecycleStatus.APPLICANT_DECLINED;
                    case ARCHIVED -> TalentLifecycleStatus.ARCHIVED;
                    case PROMOTED -> TalentLifecycleStatus.CAST_PUBLIC;
                    default -> TalentLifecycleStatus.APPLICANT_PENDING;
                };
                predicates.add(cb.equal(root.get("status"), targetStatus));
            } else {
                predicates.add(root.get("status").in(
                        TalentLifecycleStatus.APPLICANT_PENDING,
                        TalentLifecycleStatus.APPLICANT_APPROVED,
                        TalentLifecycleStatus.APPLICANT_DECLINED
                ));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("protocol")), pattern),
                        cb.like(cb.lower(root.get("city")), pattern)
                ));
            }

            if (gender != null) {
                predicates.add(cb.equal(cb.upper(root.get("gender")), gender.name().toUpperCase()));
            }

            if (minHeight != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("height"), minHeight.intValue()));
            }
            if (maxHeight != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("height"), maxHeight.intValue()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Model> pageResult = modelRepository.findAll(spec, pageable);
        List<UUID> modelIds = pageResult.getContent().stream().map(Model::getId).toList();

        Map<UUID, List<ModelMedia>> mediaByModel = Map.of();
        if (!modelIds.isEmpty()) {
            List<ModelMedia> allMedia = modelMediaRepository.findByModelIdInOrderByDisplayOrderAsc(modelIds);
            mediaByModel = allMedia.stream()
                    .filter(m -> m.getModel() != null && m.getModel().getId() != null)
                    .collect(Collectors.groupingBy(m -> m.getModel().getId()));
        }

        final Map<UUID, List<ModelMedia>> finalMediaMap = mediaByModel;
        Page<CandidateApplicationSummaryDto> result = pageResult
                .map(m -> CandidateApplicationSummaryDto.fromModel(m, finalMediaMap.getOrDefault(m.getId(), List.of())));
        return ResponseEntity.ok(result);
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ResponseEntity<CandidateDetailResponseDto> getSubmissionById(@PathVariable UUID id) {
        log.info("Obtenção de dossiê da candidatura ID: {}", id);
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura", "id", id));
        List<ModelMedia> media = modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(id);
        return ResponseEntity.ok(CandidateDetailResponseDto.fromModel(model, media, storageService));
    }

    @Transactional
    @PatchMapping("/{id}/status")
    public ResponseEntity<CandidateDetailResponseDto> updateSubmissionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubmissionStatusDto updateDto
    ) {
        log.info("Atualização de status da candidatura ID: {} para {}", id, updateDto.getStatus());
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura", "id", id));

        if (updateDto.getStatus() != null) {
            TalentLifecycleStatus targetStatus = switch (updateDto.getStatus()) {
                case APPROVED -> TalentLifecycleStatus.APPLICANT_APPROVED;
                case REJECTED, DECLINED -> TalentLifecycleStatus.APPLICANT_DECLINED;
                case ARCHIVED -> TalentLifecycleStatus.ARCHIVED;
                case PROMOTED -> TalentLifecycleStatus.CAST_PUBLIC;
                default -> TalentLifecycleStatus.APPLICANT_PENDING;
            };
            model.setStatus(targetStatus);
        }

        if (updateDto.getFeedbackNotes() != null) {
            model.setInternalNotes(updateDto.getFeedbackNotes());
        }

        Model saved = modelRepository.save(model);
        List<ModelMedia> media = modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(id);
        return ResponseEntity.ok(CandidateDetailResponseDto.fromModel(saved, media, storageService));
    }

    @PostMapping({"/{id}/promote-to-model", "/{id}/convert-to-model", "/{id}/promote", "/{id}/convert"})
    public ResponseEntity<Map<String, Object>> promoteToModel(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "true") Boolean activateImmediately
    ) {
        log.info("Ação operacional de promoção para modelo da candidatura ID: {}", id);
        UUID modelId = adminCandidateService.promoteToModel(id, activateImmediately);
        return ResponseEntity.ok(Map.of(
                "status", "PROMOTED",
                "convertedToModelId", modelId != null ? modelId.toString() : id.toString(),
                "modelId", modelId != null ? modelId.toString() : id.toString(),
                "message", "Candidatura promovida com sucesso para o elenco oficial.",
                "redirectUrl", "/admin/models"
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> deleteSubmission(@PathVariable UUID id) {
        log.warn("[HARD-DELETE][API] Solicitação de exclusão definitiva da candidatura ID: {}", id);
        adminCandidateService.deleteCandidate(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping({"/archive/cleanup", "/archive/purge", "/cleanup", "/purge", "/candidates/archive/cleanup"})
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WEBMASTER')")
    public ResponseEntity<Map<String, Object>> cleanupArchive(
            @RequestParam(required = false, defaultValue = "true") boolean all) {
        Map<String, Object> response = adminCandidateService.purgeArchive(all);
        return ResponseEntity.ok(response);
    }
}
