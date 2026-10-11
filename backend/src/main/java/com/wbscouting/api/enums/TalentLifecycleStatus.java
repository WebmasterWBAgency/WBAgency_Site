package com.wbscouting.api.enums;

/**
 * Máquina de estados unificada para o ciclo de vida dos talentos (Single-Table Lifecycle).
 * Mapeia estritamente os valores suportados no banco de dados para a entidade Model.
 */
public enum TalentLifecycleStatus {
    /** Candidatura recém-enviada aguardando triagem pela equipe de scouting */
    APPLICANT_PENDING,

    /** Candidatura aprovada pela equipe de scouting */
    APPLICANT_APPROVED,

    /** Candidatura rejeitada ou não aprovada */
    APPLICANT_DECLINED,

    /** Modelo contratado/ativo internamente, não exibido no catálogo público */
    CAST_INTERNAL,

    /** Modelo ativo no catálogo público oficial da agência */
    CAST_PUBLIC,

    /** Modelo destaque (Star) exibido com prioridade no topo do catálogo e Home */
    CAST_STAR,

    /** Modelo arquivado ou inativo historicamente */
    ARCHIVED;

    /**
     * Retorna se este status confere visibilidade no catálogo público.
     */
    public boolean isPubliclyVisible() {
        return this == CAST_PUBLIC || this == CAST_STAR;
    }
}
