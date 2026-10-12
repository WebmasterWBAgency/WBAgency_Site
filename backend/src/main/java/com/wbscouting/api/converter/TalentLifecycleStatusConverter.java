package com.wbscouting.api.converter;

import com.wbscouting.api.enums.TalentLifecycleStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * Conversor JPA resiliente para TalentLifecycleStatus.
 * Garante que qualquer valor textual existente ou legado na coluna status
 * (como "APPLICANT", "PENDING", "ACTIVE", etc.) seja convertido com segurança
 * para um valor válido do enum sem lançar IllegalArgumentException.
 */
@Slf4j
@Converter(autoApply = true)
public class TalentLifecycleStatusConverter implements AttributeConverter<TalentLifecycleStatus, String> {

    @Override
    public String convertToDatabaseColumn(TalentLifecycleStatus attribute) {
        if (attribute == null) {
            return TalentLifecycleStatus.CAST_PUBLIC.name();
        }
        return attribute.name();
    }

    @Override
    public TalentLifecycleStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return TalentLifecycleStatus.CAST_PUBLIC;
        }
        return TalentLifecycleStatus.fromStringSafely(dbData);
    }
}
