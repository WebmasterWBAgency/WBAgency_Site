package com.wbscouting.api.repository;

import com.wbscouting.api.entity.Model;
import com.wbscouting.api.enums.GenderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModelRepository extends JpaRepository<Model, UUID>, JpaSpecificationExecutor<Model> {

    // ========================================================================
    // 🗄️ CONSULTAS DE ARQUIVO MORTO (SOFT DELETE / LIMPEZA 30 DIAS)
    // ========================================================================
    // Busca modelos ativos/gerais (fora do arquivo morto)
    Page<Model> findByArchivedAtIsNull(Pageable pageable);

    // Busca modelos no arquivo morto
    Page<Model> findByArchivedAtIsNotNull(Pageable pageable);

    @Query(
            value = "SELECT m FROM Model m WHERE m.status = com.wbscouting.api.enums.TalentLifecycleStatus.ARCHIVED OR m.archivedAt IS NOT NULL",
            countQuery = "SELECT count(m) FROM Model m WHERE m.status = com.wbscouting.api.enums.TalentLifecycleStatus.ARCHIVED OR m.archivedAt IS NOT NULL"
    )
    Page<Model> findArchivedModels(Pageable pageable);

    @Query("SELECT m FROM Model m WHERE m.status = com.wbscouting.api.enums.TalentLifecycleStatus.ARCHIVED OR m.archivedAt IS NOT NULL")
    List<Model> findAllArchivedModels();

    // Busca modelos arquivados com tempo expirado para limpeza
    List<Model> findByArchivedAtBefore(OffsetDateTime threshold);

    // ========================================================================
    // ✅ LISTAGENS PAGINADAS (PUBLICAS OU ADMIN)
    // ========================================================================
    Page<Model> findByIsActiveTrue(Pageable pageable);

    Page<Model> findByGenderAndIsActiveTrue(GenderType gender, Pageable pageable);

    Page<Model> findByIsStarTrueAndIsActiveTrue(Pageable pageable);

    // ========================================================================
    // ✅ LISTAGEM SIMPLES (NAO PAGINADA)
    // ========================================================================
    @EntityGraph(attributePaths = {"media"})
    @Query("SELECT m FROM Model m WHERE m.isActive = true AND m.isFeaturedHome = true ORDER BY m.featuredOrder ASC NULLS LAST, m.stageName ASC")
    List<Model> findFeaturedHomeModels();

    @EntityGraph(attributePaths = {"media"})
    List<Model> findByIsActiveTrueAndIsFeaturedHomeTrueOrderByFeaturedOrderAscStageNameAsc();

    @Override
    Page<Model> findAll(Specification<Model> spec, Pageable pageable);

    @Override
    Page<Model> findAll(Pageable pageable);

    // ========================================================================
    // 🔥🔥 METODOS DEDICADOS EXCLUSIVAMENTE PARA ADMIN — JPQL PURO, SEM JOINS
    //    Desacoplam COMPLETAMENTE a listagem administrativa de qualquer:
    //      - Specification (nao ha join fetch acoplado via spec)
    //      - EntityGraph (nao ha media carregada)
    //      - FetchMode.JOIN em collections
    //    countQuery eh EXPLICITA (evita HHH90003004 e count totalElements quebrado).
    // ========================================================================

    /**
     * Query ADMIN sem nenhum filtro. JPQL pura = SELECT m FROM Model m.
     * Gera SQL nativo: SELECT * FROM public.models ORDER BY ... LIMIT ? OFFSET ?
     * countQuery: SELECT count(m) FROM Model m → SELECT count(*) FROM public.models
     */
    @Query(
            value = "SELECT m FROM Model m",
            countQuery = "SELECT count(m) FROM Model m"
    )
    Page<Model> findAllAdminPure(Pageable pageable);

    default Page<Model> findModelsWithFilters(
            GenderType gender,
            Boolean isStar,
            Boolean isActive,
            String search,
            Pageable pageable
    ) {
        return findAll(com.wbscouting.api.repository.specification.ModelSpecification.filter(gender, isStar, isActive, null, search), pageable);
    }

    default Page<Model> findModelsWithFilters(
            GenderType gender,
            Boolean isStar,
            Boolean isActive,
            Boolean archived,
            String search,
            Pageable pageable
    ) {
        return findAll(com.wbscouting.api.repository.specification.ModelSpecification.filter(gender, isStar, isActive, archived, search), pageable);
    }

    default Page<Model> findAdminWithFilters(
            GenderType gender,
            Boolean isStar,
            Boolean isActive,
            String search,
            Pageable pageable
    ) {
        return findAll(com.wbscouting.api.repository.specification.ModelSpecification.filter(gender, isStar, isActive, null, search), pageable);
    }

    default Page<Model> findAdminWithFilters(
            GenderType gender,
            Boolean isStar,
            Boolean isActive,
            com.wbscouting.api.enums.TalentLifecycleStatus status,
            String search,
            Pageable pageable
    ) {
        return findAll(com.wbscouting.api.repository.specification.ModelSpecification.filter(gender, isStar, isActive, null, search), pageable);
    }

    @Query(
            value = "SELECT m FROM Model m WHERE " +
                    "(:gender IS NULL OR m.gender = :gender) AND " +
                    "(:isStar IS NULL OR COALESCE(m.isStar, false) = :isStar) AND " +
                    "(:isActive IS NULL OR COALESCE(m.isActive, false) = :isActive) AND " +
                    "(:statuses IS NULL OR (m.status IS NOT NULL AND m.status IN :statuses) OR (m.status IS NULL AND (:isActive IS NULL OR COALESCE(m.isActive, false) = :isActive))) AND " +
                    "(:search IS NULL OR LOWER(m.stageName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(COALESCE(m.fullName, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))",
            countQuery = "SELECT count(m) FROM Model m WHERE " +
                    "(:gender IS NULL OR m.gender = :gender) AND " +
                    "(:isStar IS NULL OR COALESCE(m.isStar, false) = :isStar) AND " +
                    "(:isActive IS NULL OR COALESCE(m.isActive, false) = :isActive) AND " +
                    "(:statuses IS NULL OR (m.status IS NOT NULL AND m.status IN :statuses) OR (m.status IS NULL AND (:isActive IS NULL OR COALESCE(m.isActive, false) = :isActive))) AND " +
                    "(:search IS NULL OR LOWER(m.stageName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(COALESCE(m.fullName, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))"
    )
    Page<Model> findAdminWithFiltersAndStatuses(
            @org.springframework.data.repository.query.Param("gender") GenderType gender,
            @org.springframework.data.repository.query.Param("isStar") Boolean isStar,
            @org.springframework.data.repository.query.Param("isActive") Boolean isActive,
            @org.springframework.data.repository.query.Param("statuses") List<com.wbscouting.api.enums.TalentLifecycleStatus> statuses,
            @org.springframework.data.repository.query.Param("search") String search,
            Pageable pageable
    );

    @Query(
            value = "SELECT m FROM Model m WHERE m.status IN :statuses",
            countQuery = "SELECT count(m) FROM Model m WHERE m.status IN :statuses"
    )
    Page<Model> findByStatusIn(
            @org.springframework.data.repository.query.Param("statuses") List<com.wbscouting.api.enums.TalentLifecycleStatus> statuses,
            Pageable pageable
    );

    // ========================================================================
    // ✅ BUSCAS INDIVIDUAIS (DETALHE) — Consulta simples e desacoplada
    // ========================================================================
    Optional<Model> findByIdAndIsActiveTrue(UUID id);

    boolean existsByProtocol(String protocol);

    Optional<Model> findByProtocol(String protocol);

    long countByStatus(com.wbscouting.api.enums.TalentLifecycleStatus status);

    @Query("SELECT m FROM Model m WHERE (m.status = com.wbscouting.api.enums.TalentLifecycleStatus.ARCHIVED OR m.archivedAt IS NOT NULL) AND m.archivedAt IS NOT NULL AND m.archivedAt < :threshold")
    List<Model> findByStatusArchivedBefore(@org.springframework.data.repository.query.Param("threshold") java.time.OffsetDateTime threshold);

    default List<Model> findByStatusArchivedBefore(java.time.LocalDateTime threshold) {
        java.time.ZoneOffset offset = java.time.OffsetDateTime.now().getOffset();
        return findByStatusArchivedBefore(threshold.atOffset(offset));
    }
}


