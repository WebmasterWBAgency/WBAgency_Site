package com.wbscouting.api.controller.publicapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wbscouting.api.dto.ApiResponse;
import com.wbscouting.api.dto.candidate.CandidateApplyRequestDto;
import com.wbscouting.api.dto.candidate.CandidateApplyResponseDto;
import com.wbscouting.api.service.candidate.CandidateApplicationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping({"/public/candidates", "/api/v1/public/candidates", "/submissions", "/api/v1/submissions"})
public class PublicCandidateController {

    private final CandidateApplicationService candidateApplicationService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public PublicCandidateController(CandidateApplicationService candidateApplicationService,
                                     ObjectMapper objectMapper,
                                     Validator validator) {
        this.candidateApplicationService = candidateApplicationService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping(value = {"", "/", "/apply"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CandidateApplyResponseDto> apply(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "facePhoto", required = false) MultipartFile facePhoto,
            @RequestPart(value = "profilePhoto", required = false) MultipartFile profilePhoto,
            @RequestPart(value = "fullBodyPhoto", required = false) MultipartFile fullBodyPhoto,
            @RequestPart(value = "photos", required = false) List<MultipartFile> additionalPhotos
    ) {
        CandidateApplyRequestDto dto;
        try {
            dto = objectMapper.readValue(
                    dataJson,
                    CandidateApplyRequestDto.class
            );
        } catch (com.fasterxml.jackson.core.JacksonException ex) {
            log.error("[APPLY] Falha de desserialização no part 'data': {}", ex.getMessage());
            throw new com.wbscouting.api.exception.InvalidApplicationException(
                    "Payload JSON do formulário está inválido. Revise os campos e tente novamente."
            );
        }

        Set<ConstraintViolation<CandidateApplyRequestDto>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String firstMsg = violations.iterator().next().getMessage();
            log.warn("[APPLY] Payload inválido no part 'data': {} violações - primeira: {}",
                    violations.size(), firstMsg);
            throw new com.wbscouting.api.exception.InvalidApplicationException(firstMsg);
        }

        log.info("[APPLY] Candidatura recebida via /apply: nome='{}' email='{}'",
                dto.getFullName(), dto.getEmail());

        List<MultipartFile> photos = new ArrayList<>();
        if (facePhoto != null && !facePhoto.isEmpty()) photos.add(facePhoto);
        if (profilePhoto != null && !profilePhoto.isEmpty()) photos.add(profilePhoto);
        if (fullBodyPhoto != null && !fullBodyPhoto.isEmpty()) photos.add(fullBodyPhoto);
        if (additionalPhotos != null) {
            for (MultipartFile f : additionalPhotos) {
                if (f != null && !f.isEmpty()) {
                    photos.add(f);
                }
            }
        }

        CandidateApplyResponseDto response = candidateApplicationService.apply(dto, photos);

        CandidateApplyResponseDto sanitizedResponse = CandidateApplyResponseDto.builder()
                .id(response.getId())
                .protocol(response.getProtocol())
                .status("PENDING")
                .build();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(sanitizedResponse);
    }
}
