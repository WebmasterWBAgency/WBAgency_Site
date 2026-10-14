package com.wbscouting.api.dto.media;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.wbscouting.api.enums.MediaType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMediaTypeRequestDto {

    @NotNull(message = "O tipo de mídia (mediaType) é obrigatório.")
    @JsonAlias({"type"})
    private MediaType mediaType;
}
