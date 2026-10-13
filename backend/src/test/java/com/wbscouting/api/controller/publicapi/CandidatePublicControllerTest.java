package com.wbscouting.api.controller.publicapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wbscouting.api.dto.candidate.CandidateApplyRequestDto;
import com.wbscouting.api.dto.candidate.CandidateApplyResponseDto;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.security.JwtAuthenticationEntryPoint;
import com.wbscouting.api.security.JwtAuthenticationFilter;
import com.wbscouting.api.security.JwtService;
import com.wbscouting.api.security.JwtTokenProvider;
import com.wbscouting.api.service.candidate.CandidateApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandidatePublicController.class)
@AutoConfigureMockMvc(addFilters = false)
class CandidatePublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CandidateApplicationService applicationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private CandidateApplyRequestDto validDto;
    private MockMultipartFile dataPart;
    private MockMultipartFile facePhoto;
    private MockMultipartFile profilePhoto;
    private MockMultipartFile fullBodyPhoto;

    @BeforeEach
    void setUp() throws Exception {
        validDto = CandidateApplyRequestDto.builder()
                .fullName("Gisele Bundchen")
                .email("gisele@models.com")
                .phone("+5511999998888")
                .birthDate(LocalDate.of(2000, 7, 20))
                .gender(GenderType.FEMALE)
                .heightCm(180)
                .city("Horizontina")
                .state("RS")
                .bustChestCm(new BigDecimal("86"))
                .waistCm(new BigDecimal("60"))
                .hipsCm(new BigDecimal("89"))
                .shoeSize("38")
                .instagramHandle("gisele")
                .build();

        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

        dataPart = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(validDto)
        );

        facePhoto = new MockMultipartFile("facePhoto", "face.jpg", "image/jpeg", jpegBytes);
        profilePhoto = new MockMultipartFile("profilePhoto", "profile.jpg", "image/jpeg", jpegBytes);
        fullBodyPhoto = new MockMultipartFile("fullBodyPhoto", "body.jpg", "image/jpeg", jpegBytes);
    }

    @Test
    @DisplayName("POST /api/v1/submissions - Deve retornar 201 Created quando o payload e fotos forem válidos")
    void shouldReturn201WhenValid() throws Exception {
        UUID generatedId = UUID.randomUUID();
        CandidateApplyResponseDto responseDto = CandidateApplyResponseDto.builder()
                .id(generatedId)
                .candidateId(generatedId)
                .protocol("WB-20260920-A1B2C3")
                .message("Candidatura enviada com sucesso!")
                .status("PENDING")
                .submittedAt(Instant.now())
                .build();

        when(applicationService.apply(any(), any())).thenReturn(responseDto);

        mockMvc.perform(multipart("/api/v1/submissions")
                        .file(dataPart)
                        .file(facePhoto)
                        .file(profilePhoto)
                        .file(fullBodyPhoto))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.candidateId").doesNotExist())
                .andExpect(jsonPath("$.protocol").value("WB-20260920-A1B2C3"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/submissions - Deve retornar 400 Bad Request quando Bean Validation falhar")
    void shouldReturn400WhenBeanValidationFails() throws Exception {
        validDto.setEmail("email-invalido");
        MockMultipartFile invalidDataPart = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(validDto)
        );

        mockMvc.perform(multipart("/api/v1/submissions")
                        .file(invalidDataPart)
                        .file(facePhoto)
                        .file(profilePhoto)
                        .file(fullBodyPhoto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Erro de Validação"));
    }
}
