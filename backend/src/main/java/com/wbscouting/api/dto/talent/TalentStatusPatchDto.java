package com.wbscouting.api.dto.talent;

import com.wbscouting.api.enums.TalentLifecycleStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TalentStatusPatchDto {

    @NotNull(message = "O novo status do ciclo de vida é obrigatório.")
    private TalentLifecycleStatus status;

    private String internalNotes;
}
