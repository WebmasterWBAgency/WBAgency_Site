package com.wbscouting.api.controller.admin;

import com.wbscouting.api.dto.ApiResponse;
import com.wbscouting.api.dto.admin.candidate.CandidateApplicationSummaryDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDecisionRequestDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto;
import com.wbscouting.api.enums.SubmissionGender;
import com.wbscouting.api.security.audit.AuditAction;
import com.wbscouting.api.service.candidate.CandidateSubmissionAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/admin/applications", "/admin/applications", "/api/v1/admin/submissions", "/admin/submissions", "/api/v1/admin/candidates", "/admin/candidates"})
@PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'ADMIN')")
@RequiredArgsConstructor
public class AdminApplicationController {

    private final CandidateSubmissionAdminService candidateSubmissionAdminService;

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<Page<CandidateApplicationSummaryDto>> listApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubmissionGender gender,
            @RequestParam(required = false) Boolean isMinor,
            @RequestParam(required = false, defaultValue = "false") Boolean includePromoted,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        log.info("Consulta administrativa tabular de candidaturas (candidate_submissions). Status: {}, Search: {}, isMinor: {}, Page: {}, Size: {}",
                status, search, isMinor, page, size);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String mappedSortBy = switch (sortBy) {
            case "height" -> "height";
            case "fullName", "name" -> "fullName";
            case "city" -> "city";
            case "status" -> "status";
            default -> "createdAt";
        };

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(direction, mappedSortBy));

        String statusStr = (status != null && !status.isBlank()) ? status.trim() : null;
        String genderStr = gender != null ? gender.name() : null;

        Page<CandidateApplicationSummaryDto> result = candidateSubmissionAdminService.listApplications(
                statusStr, search, genderStr, isMinor, pageable
        );

        return ResponseEntity.ok(result);
    }

    @Transactional(readOnly = true)
    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> getCounts() {
        return ResponseEntity.ok(candidateSubmissionAdminService.getCounts());
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ResponseEntity<CandidateDetailResponseDto> getApplicationById(@PathVariable UUID id) {
        return ResponseEntity.ok(candidateSubmissionAdminService.getApplicationById(id));
    }

    @Transactional
    @RequestMapping(value = {"/{id}/status", "/{id}/decision"}, method = {RequestMethod.PATCH, RequestMethod.PUT})
    @AuditAction(action = "DECISION", resource = "SCOUTING_CANDIDATE", description = "Triagem de candidatura de modelo")
    public ResponseEntity<CandidateDetailResponseDto> updateDecision(
            @PathVariable UUID id,
            @Valid @RequestBody CandidateDecisionRequestDto decisionDto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(candidateSubmissionAdminService.updateDecision(id, decisionDto));
    }

    @PostMapping({"/{id}/promote", "/{id}/promote-to-model", "/{id}/convert-to-model", "/{id}/convert"})
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WEBMASTER', 'CONTENT_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> promote(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "true") Boolean activateImmediately) {
        UUID promotedId = candidateSubmissionAdminService.promoteToModel(id, activateImmediately);
        Map<String, Object> data = new HashMap<>();
        data.put("id", promotedId != null ? promotedId.toString() : id.toString());
        data.put("status", Boolean.TRUE.equals(activateImmediately) ? "CAST_PUBLIC" : "CAST_INTERNAL");
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success("Candidato promovido a modelo com sucesso.", data));
    }

    @PatchMapping({"/{id}/restore", "/{id}/unarchive"})
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WEBMASTER', 'CONTENT_ADMIN')")
    @AuditAction(action = "RESTORE", resource = "SCOUTING_CANDIDATE", description = "Restauração de candidatura do arquivo morto")
    public ResponseEntity<CandidateDetailResponseDto> restore(@PathVariable UUID id) {
        log.info("Restaurando candidatura ID: {} do arquivo morto", id);
        return ResponseEntity.ok(candidateSubmissionAdminService.restoreApplication(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WEBMASTER', 'CONTENT_ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "false") boolean permanent) {
        candidateSubmissionAdminService.deleteApplication(id, permanent);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/archive/cleanup", "/archive/purge", "/cleanup", "/purge", "/candidates/archive/cleanup"})
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WEBMASTER')")
    public ResponseEntity<Map<String, Object>> cleanupArchive(
            @RequestParam(required = false, defaultValue = "true") boolean all) {
        Map<String, Object> response = candidateSubmissionAdminService.purgeArchive(all);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping({"/archive/cleanup", "/archive/purge", "/cleanup", "/purge", "/candidates/archive/cleanup"})
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WEBMASTER')")
    public ResponseEntity<Map<String, Object>> purgeArchive(
            @RequestParam(required = false, defaultValue = "true") boolean all) {
        return cleanupArchive(all);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<org.springframework.http.ProblemDetail> handleIllegalStateException(IllegalStateException ex) {
        log.warn("[ADMIN_APPLICATION] Conflito de estado de candidatura: {}", ex.getMessage());
        org.springframework.http.ProblemDetail problemDetail = org.springframework.http.ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problemDetail.setTitle("Conflito de Estado de Negócio");
        problemDetail.setType(java.net.URI.create("https://wbscouting.com/errors/conflict-state"));
        problemDetail.setProperty("timestamp", java.time.Instant.now());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(problemDetail);
    }
}
