package com.wbscouting.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Máquina de estados unificada para o ciclo de vida dos talentos (Single-Table Lifecycle).
 * Mapeia os valores suportados no banco de dados para a entidade Model.
 */
public enum TalentLifecycleStatus {
    /** Candidatura recém-enviada aguardando triagem pela equipe de scouting */
    APPLICANT,
    APPLICANT_PENDING,
    PENDING,

    /** Candidatura em revisão */
    REVIEWED,

    /** Candidatura aprovada pela equipe de scouting */
    APPROVED,
    PROMOTED,
    APPLICANT_APPROVED,

    /** Candidatura rejeitada ou não aprovada */
    DECLINED,
    REJECTED,
    APPLICANT_DECLINED,

    /** Modelo contratado/ativo internamente, não exibido no catálogo público */
    CAST_INTERNAL,
    INTERNAL,

    /** Modelo ativo no catálogo público oficial da agência */
    CAST_PUBLIC,
    ACTIVE,

    /** Modelo destaque (Star) exibido com prioridade no topo do catálogo e Home */
    CAST_STAR,
    STAR,

    /** Modelo arquivado ou inativo historicamente */
    ARCHIVED,
    INACTIVE;

    /**
     * Retorna se este status confere visibilidade no catálogo público.
     */
    public boolean isPubliclyVisible() {
        return this == CAST_PUBLIC || this == CAST_STAR || this == ACTIVE || this == STAR;
    }

    public boolean isApplicant() {
        return this == APPLICANT || this == APPLICANT_PENDING || this == PENDING || this == REVIEWED;
    }

    public boolean isApproved() {
        return this == APPROVED || this == PROMOTED || this == APPLICANT_APPROVED;
    }

    public boolean isDeclined() {
        return this == DECLINED || this == REJECTED || this == APPLICANT_DECLINED;
    }

    public boolean isArchived() {
        return this == ARCHIVED || this == INACTIVE;
    }

    @JsonCreator
    public static TalentLifecycleStatus fromStringSafely(String val) {
        if (val == null || val.isBlank()) {
            return CAST_PUBLIC;
        }
        String clean = val.trim().toUpperCase();
        try {
            return TalentLifecycleStatus.valueOf(clean);
        } catch (IllegalArgumentException e) {
            if (clean.contains("APP") || clean.contains("PEND")) return APPLICANT_PENDING;
            if (clean.contains("APPR") || clean.contains("PROM")) return APPLICANT_APPROVED;
            if (clean.contains("DECL") || clean.contains("REJ")) return APPLICANT_DECLINED;
            if (clean.contains("STAR")) return CAST_STAR;
            if (clean.contains("ARCH") || clean.contains("INACT")) return ARCHIVED;
            if (clean.contains("INTERN")) return CAST_INTERNAL;
            return CAST_PUBLIC;
        }
    }
}
