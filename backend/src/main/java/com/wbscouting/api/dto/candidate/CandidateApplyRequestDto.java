package com.wbscouting.api.dto.candidate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wbscouting.api.enums.GenderType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CandidateApplyRequestDto {

    @NotBlank(message = "O nome completo é obrigatório.")
    @Size(min = 3, max = 120, message = "O nome completo deve possuir entre 3 e 120 caracteres.")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "O nome contém caracteres inválidos.")
    private String fullName;

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Formato de e-mail inválido.")
    private String email;

    @NotBlank(message = "O telefone de contato é obrigatório.")
    private String phone;

    @NotNull(message = "A data de nascimento é obrigatória.")
    @Past(message = "A data de nascimento deve ser uma data no passado.")
    private LocalDate birthDate;

    @NotNull(message = "O gênero é obrigatório (FEMALE ou MALE).")
    private GenderType gender;

    @NotNull(message = "A altura em centímetros é obrigatória.")
    @Min(value = 50, message = "A altura mínima permitida é de 50 cm.")
    @Max(value = 250, message = "A altura máxima permitida é de 250 cm.")
    private Integer heightCm;

    @NotBlank(message = "A cidade é obrigatória.")
    private String city;

    @NotBlank(message = "O estado (UF) é obrigatório.")
    private String state;

    private Integer age;

    private BigDecimal weightKg;

    // Campos de Responsável Legal (obrigatórios se menor de 18 anos)
    private String legalGuardianName;
    private String legalGuardianContact;
    private String guardianName;
    private String guardianPhone;
    private String guardianEmail;

    // Medidas corporais opcionais
    private BigDecimal bustChestCm;
    private BigDecimal waistCm;
    private BigDecimal hipsCm;
    private String shoeSize;
    private String dressSize;
    private String hairColor;
    private String eyesColor;

    // Redes sociais e portfólio opcionais
    private String instagramHandle;
    private String portfolioUrl;
    private Boolean lgpdConsent;

    public String getResolvedGuardianName() {
        if (legalGuardianName != null && !legalGuardianName.isBlank()) return legalGuardianName.trim();
        if (guardianName != null && !guardianName.isBlank()) return guardianName.trim();
        return null;
    }

    public String getResolvedGuardianContact() {
        if (legalGuardianContact != null && !legalGuardianContact.isBlank()) return legalGuardianContact.trim();
        if (guardianPhone != null && !guardianPhone.isBlank()) return guardianPhone.trim();
        if (guardianEmail != null && !guardianEmail.isBlank()) return guardianEmail.trim();
        return null;
    }

    public void setGender(Object val) {
        if (val instanceof GenderType gt) {
            this.gender = gt;
        } else if (val instanceof String s) {
            String up = s.trim().toUpperCase();
            if (up.contains("FEM") || up.equals("F")) {
                this.gender = GenderType.FEMALE;
            } else {
                this.gender = GenderType.MALE;
            }
        }
    }
}
