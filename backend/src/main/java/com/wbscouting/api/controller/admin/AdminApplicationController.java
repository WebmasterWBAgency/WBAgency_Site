package com.wbscouting.api.controller.admin;

import com.wbscouting.api.dto.ApiResponse;
import com.wbscouting.api.dto.admin.candidate.CandidateApplicationSummaryDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDecisionRequestDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto;
import com.wbscouting.api.entity.Admin;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.SubmissionGender;
import com.wbscouting.api.enums.SubmissionStatus;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.exception.ResourceNotFoundException;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.security.audit.AuditAction;
import com.wbscouting.api.service.candidate.AdminCandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping({"/api/v1/admin/applications", "/admin/applications"})
@PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'ADMIN')")
@RequiredArgsConstructor
public class AdminApplicationController {

    private final ModelRepository modelRepository;
    private final ModelMediaRepository modelMediaRepository;
    private final AdminCandidateService adminCandidateService;

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<Page<CandidateApplicationSummaryDto>> listApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubmissionGender gender,
            @RequestParam(required = false) Boolean isMinor,
            @RequestParam(required = false, defaultValue = "false") Boolean includePromoted,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        log.info("Consulta administrativa tabular de candidaturas canônicas (models). Status: {}, Search: {}, isMinor: {}, Page: {}, Size: {}",
                status, search, isMinor, page, size);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String mappedSortBy = switch (sortBy) {
            case "height" -> "height";
            case "fullName", "name" -> "name";
            case "city" -> "city";
            case "status" -> "status";
            default -> "createdAt";
        };

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(direction, mappedSortBy));

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
            } else if (!Boolean.TRUE.equals(includePromoted)) {
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

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Model> pageResult = modelRepository.findAll(spec, pageable);
        List<UUID> modelIds = pageResult.getContent().stream()
                .map(Model::getId)
                .toList();

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
    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> getCounts() {
        long pending = modelRepository.countByStatus(TalentLifecycleStatus.APPLICANT_PENDING);
        long approved = modelRepository.countByStatus(TalentLifecycleStatus.APPLICANT_APPROVED);
        long rejected = modelRepository.countByStatus(TalentLifecycleStatus.APPLICANT_DECLINED);
        long archived = modelRepository.countByStatus(TalentLifecycleStatus.ARCHIVED);
        long total = pending + approved + rejected;

        return ResponseEntity.ok(Map.of(
                "pending", pending,
                "approved", approved,
                "rejected", rejected,
                "archived", archived,
                "total", total
        ));
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ResponseEntity<CandidateDetailResponseDto> getApplicationById(@PathVariable UUID id) {
        log.info("Buscando detalhes da candidatura ID: {}", id);
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura", "id", id));
        List<ModelMedia> media = modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(id);
        return ResponseEntity.ok(CandidateDetailResponseDto.fromModel(model, media));
    }

    @Transactional
    @PatchMapping("/{id}/decision")
    @AuditAction(action = "DECISION", resource = "SCOUTING_CANDIDATE", description = "Triagem de candidatura de modelo")
    public ResponseEntity<CandidateDetailResponseDto> updateDecision(
            @PathVariable UUID id,
            @Valid @RequestBody CandidateDecisionRequestDto decisionDto,
            Authentication authentication
    ) {
        log.info("Decisão de triagem para candidatura ID: {}, Status: {}", id, decisionDto.getStatus());

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura", "id", id));

        if (decisionDto.getStatus() != null) {
            TalentLifecycleStatus targetStatus = switch (decisionDto.getStatus()) {
                case APPROVED -> TalentLifecycleStatus.APPLICANT_APPROVED;
                case REJECTED, DECLINED -> TalentLifecycleStatus.APPLICANT_DECLINED;
                case ARCHIVED -> TalentLifecycleStatus.ARCHIVED;
                case PROMOTED -> TalentLifecycleStatus.CAST_PUBLIC;
                default -> TalentLifecycleStatus.APPLICANT_PENDING;
            };
            model.setStatus(targetStatus);
        }

        if (decisionDto.getInternalNotes() != null) {
            model.setInternalNotes(decisionDto.getInternalNotes());
        }

        Model saved = modelRepository.save(model);
        List<ModelMedia> media = modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(id);
        return ResponseEntity.ok(CandidateDetailResponseDto.fromModel(saved, media));
    }

    @PostMapping({"/{id}/promote", "/{id}/promote-to-model"})
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WEBMASTER', 'CONTENT_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> promote(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "true") Boolean activateImmediately) {
        UUID promotedId = adminCandidateService.promoteToModel(id, activateImmediately);
        Map<String, Object> data = new HashMap<>();
        data.put("id", promotedId != null ? promotedId.toString() : id.toString());
        data.put("status", Boolean.TRUE.equals(activateImmediately) ? "CAST_PUBLIC" : "CAST_INTERNAL");
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success("Candidato promovido a modelo com sucesso.", data));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WEBMASTER', 'CONTENT_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminCandidateService.deleteCandidate(id);
        return ResponseEntity.noContent().build();
    }
}
