package com.wbscouting.api.controller.publicapi;

import com.wbscouting.api.dto.candidate.CandidateApplyRequestDto;
import com.wbscouting.api.dto.candidate.CandidateApplyResponseDto;
import com.wbscouting.api.service.candidate.CandidateApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping({"/submissions", "/api/v1/submissions"})
@RequiredArgsConstructor
public class CandidatePublicController {

    private final CandidateApplicationService applicationService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CandidateApplyResponseDto> submit(
            @RequestPart("data") @Valid CandidateApplyRequestDto data,
            @RequestPart("facePhoto") MultipartFile facePhoto,
            @RequestPart("profilePhoto") MultipartFile profilePhoto,
            @RequestPart("fullBodyPhoto") MultipartFile fullBodyPhoto,
            @RequestPart(value = "photos", required = false) List<MultipartFile> additionalPhotos
    ) {
        log.info("Recebida requisição de candidatura pública em /submissions para: {}", data.getFullName());

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

        CandidateApplyResponseDto response = applicationService.apply(data, photos);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
