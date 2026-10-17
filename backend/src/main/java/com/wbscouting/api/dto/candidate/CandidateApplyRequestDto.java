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
    @Pattern(regexp = "^[\\p{L}0-9 .'-]+$", message = "O nome contém caracteres inválidos.")
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
    private String guardianCpf;
    private Boolean isMinor;

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

    @JsonProperty("height")
    public void setHeight(Object val) {
        if (val != null && this.heightCm == null) {
            try {
                double d = Double.parseDouble(val.toString());
                if (d < 3.0) {
                    this.heightCm = (int) Math.round(d * 100);
                } else {
                    this.heightCm = (int) Math.round(d);
                }
            } catch (Exception ignored) {}
        }
    }

    @JsonProperty("bust")
    public void setBust(BigDecimal val) {
        if (this.bustChestCm == null) {
            this.bustChestCm = val;
        }
    }

    @JsonProperty("waist")
    public void setWaist(BigDecimal val) {
        if (this.waistCm == null) {
            this.waistCm = val;
        }
    }

    @JsonProperty("hips")
    public void setHips(BigDecimal val) {
        if (this.hipsCm == null) {
            this.hipsCm = val;
        }
    }

    @JsonProperty("shoes")
    public void setShoes(String val) {
        if (this.shoeSize == null) {
            this.shoeSize = val;
        }
    }

    @JsonProperty("eyes")
    public void setEyes(String val) {
        if (this.eyesColor == null) {
            this.eyesColor = val;
        }
    }

    @JsonProperty("eyeColor")
    public void setEyeColor(String val) {
        if (this.eyesColor == null) {
            this.eyesColor = val;
        }
    }

    @JsonProperty("hair")
    public void setHair(String val) {
        if (this.hairColor == null) {
            this.hairColor = val;
        }
    }

    @JsonProperty("instagram")
    public void setInstagram(String val) {
        if (this.instagramHandle == null) {
            this.instagramHandle = val;
        }
    }

    @JsonProperty("lgpd_accepted")
    public void setLgpdAccepted(Boolean val) {
        if (this.lgpdConsent == null) {
            this.lgpdConsent = val;
        }
    }

    @JsonProperty("gender")
    public void setGender(Object val) {
        if (val instanceof GenderType gt) {
            this.gender = gt;
        } else if (val instanceof String s) {
            String up = s.trim().toUpperCase();
            if (up.contains("FEM") || up.equals("F")) {
                this.gender = GenderType.FEMALE;
            } else if (up.contains("MAS") || up.equals("M") || up.contains("MAL")) {
                this.gender = GenderType.MALE;
            } else {
                this.gender = GenderType.FEMALE;
            }
        }
    }
}
