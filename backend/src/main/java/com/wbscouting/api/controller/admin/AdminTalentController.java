package com.wbscouting.api.controller.admin;

import com.wbscouting.api.dto.model.ModelAdminResponseDto;
import com.wbscouting.api.dto.talent.TalentStatusPatchDto;
import com.wbscouting.api.security.audit.AuditAction;
import com.wbscouting.api.service.model.ModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/admin/talents", "/admin/talents"})
@PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'ADMIN')")
@RequiredArgsConstructor
public class AdminTalentController {

    private final ModelService modelService;

    @PatchMapping("/{id}/status")
    @AuditAction(action = "UPDATE_STATUS", resource = "TALENT", description = "Transição atômica de status do ciclo de vida do talento")
    public ResponseEntity<ModelAdminResponseDto> updateTalentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TalentStatusPatchDto request) {
        log.info("[ADMIN TALENTS] Alterando status do talento ID={} para {}", id, request.getStatus());
        ModelAdminResponseDto response = modelService.updateTalentStatus(id, request);
        return ResponseEntity.ok(response);
    }
}
