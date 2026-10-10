package com.wbscouting.api.controller.admin;

import com.wbscouting.api.dto.admin.candidate.CandidateApplicationSummaryDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDecisionRequestDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto;
import com.wbscouting.api.entity.Admin;
import com.wbscouting.api.entity.CandidateSubmission;
import com.wbscouting.api.enums.SubmissionGender;
import com.wbscouting.api.enums.SubmissionStatus;
import com.wbscouting.api.exception.ResourceNotFoundException;
import com.wbscouting.api.repository.CandidateSubmissionRepository;
import com.wbscouting.api.specification.CandidateSubmissionSpecification;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import com.wbscouting.api.dto.ApiResponse;
import com.wbscouting.api.service.submission.CandidateSubmissionAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import com.wbscouting.api.security.audit.AuditAction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/admin/applications", "/admin/applications"})
@PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'ADMIN')")
@RequiredArgsConstructor
public class AdminApplicationController {

    private final CandidateSubmissionRepository submissionRepository;
    private final CandidateSubmissionAdminService submissionAdminService;

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
        log.info("Consulta administrativa tabular de candidaturas canônicas (candidate_submissions). Status: {}, Search: {}, isMinor: {}, Page: {}, Size: {}",
                status, search, isMinor, page, size);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String mappedSortBy = switch (sortBy) {
            case "height" -> "height";
            case "fullName" -> "fullName";
            case "city" -> "city";
            case "age" -> "age";
            case "status" -> "status";
            default -> "createdAt";
        };

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(direction, mappedSortBy));

        Specification<CandidateSubmission> spec = CandidateSubmissionSpecification.filter(
                search, status, gender, isMinor, null, null, null, null, includePromoted
        );

        Page<CandidateApplicationSummaryDto> result = submissionRepository.findAll(spec, pageable)
                .map(CandidateApplicationSummaryDto::fromEntity);
        return ResponseEntity.ok(result);
    }

    @Transactional(readOnly = true)
    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> getCounts() {
        long pending = submissionRepository.countByStatusAndConvertedToModelIdIsNull(SubmissionStatus.PENDING);
        long approved = submissionRepository.countByStatusAndConvertedToModelIdIsNull(SubmissionStatus.APPROVED);
        long rejected = submissionRepository.countByStatusAndConvertedToModelIdIsNull(SubmissionStatus.REJECTED)
                + submissionRepository.countByStatusAndConvertedToModelIdIsNull(SubmissionStatus.DECLINED);
        long archived = submissionRepository.countByStatus(SubmissionStatus.ARCHIVED)
                + submissionRepository.countByStatus(SubmissionStatus.PROMOTED);
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
        CandidateSubmission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura", "id", id));
        return ResponseEntity.ok(CandidateDetailResponseDto.fromEntity(submission));
    }

    @Transactional
    @PatchMapping("/{id}/decision")
    @AuditAction(action = "DECISION", resource = "SCOUTING_CANDIDATE", description = "Triagem de candidatura de modelo")
    public ResponseEntity<CandidateDetailResponseDto> updateDecision(
            @PathVariable UUID id,
            @Valid @RequestBody CandidateDecisionRequestDto decisionDto,
            Authentication authentication
    ) {
        log.info("Decisão de triagem para candidatura canônica ID: {}, Status: {}", id, decisionDto.getStatus());
        String reviewerName = extractReviewerName(authentication);

        CandidateSubmission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura", "id", id));

        submission.setStatus(decisionDto.getStatus());
        if (decisionDto.getInternalNotes() != null) {
            submission.setFeedbackNotes(decisionDto.getInternalNotes());
        }
        submission.setReviewedBy(reviewerName);
        submission.setReviewedAt(OffsetDateTime.now());

        CandidateSubmission savedSubmission = submissionRepository.save(submission);
        return ResponseEntity.ok(CandidateDetailResponseDto.fromEntity(savedSubmission));
    }

    @PostMapping({"/{id}/promote", "/{id}/promote-to-model"})
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WEBMASTER', 'CONTENT_ADMIN')")
    public ResponseEntity<ApiResponse<com.wbscouting.api.dto.model.ModelResponseDto>> promote(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "true") Boolean activateImmediately,
            @AuthenticationPrincipal UserDetails userDetails,
            Authentication authentication) {
        String adminEmail = userDetails != null ? userDetails.getUsername() : extractReviewerName(authentication);
        com.wbscouting.api.dto.model.ModelResponseDto model = submissionAdminService.promoteCandidateToModel(id, adminEmail, activateImmediately);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success("Candidato promovido a modelo com sucesso.", model));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WEBMASTER', 'CONTENT_ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            Authentication authentication) {
        String adminEmail = userDetails != null ? userDetails.getUsername() : extractReviewerName(authentication);
        submissionAdminService.deletePermanently(id, adminEmail);
        return ResponseEntity.noContent().build();
    }

    private String extractReviewerName(Authentication authentication) {
        Authentication auth = authentication != null ? authentication : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            if (auth.getPrincipal() instanceof Admin admin) {
                return admin.getName() != null ? admin.getName() : admin.getEmail();
            } else if (auth.getName() != null) {
                return auth.getName();
            }
        }
        return "Booker / Scouting Desk";
    }
}
