package com.wbscouting.api.repository.specification;

import com.wbscouting.api.entity.Model;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ModelSpecification {

    /**
     * Specification PADRAO de filtros para Model (genero, star, ativo, busca stageName).
     * <p>
     * <strong>REGRAS DE SEGURANÇA — NUNCA QUEBRAR (causa HHH90003004 + totalElements=0):</strong>
     * <ul>
     *   <li>NUNCA adicionar {@code root.fetch("media", ...)} ou fetch em collections.</li>
     *   <li>NUNCA adicionar {@code root.join("media", JoinType.LEFT).fetch(...)}</li>
     *   <li>Esta classe eh usada em consultas PAGINADAS (Pageable). Qualquer FETCH em
     *       colecoes causa warning HHH90003004 e o Spring Data Page retorna
     *       totalElements=0 INJUSTIFICADAMENTE (bug Hibernate + Spring Data).</li>
     *   <li>Para carregar a colecao media (1:N) em listas: use DTO projections / query
     *       a parte / buscar por ID individual (onde EntityGraph eh permitido).</li>
     * </ul>
     */
    public static Specification<Model> filter(GenderType gender, Boolean isStar, Boolean isActive, String search) {
        return filter(gender, isStar, isActive, null, search);
    }

    public static Specification<Model> filter(GenderType gender, Boolean isStar, Boolean isActive, Boolean archived, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Arquivo Morto / Inativos
            if (Boolean.TRUE.equals(archived)) {
                // Modo arquivo morto explícito
                predicates.add(cb.or(
                        cb.isNotNull(root.get("archivedAt")),
                        cb.equal(root.get("status"), TalentLifecycleStatus.ARCHIVED)
                ));
            } else if (Boolean.FALSE.equals(isActive)) {
                // Aba Inativos / Rascunhos: modelos inativos, arquivados ou declinados
                predicates.add(cb.or(
                        cb.equal(cb.coalesce(root.get("isActive"), true), false),
                        cb.isNotNull(root.get("archivedAt")),
                        cb.equal(root.get("status"), TalentLifecycleStatus.ARCHIVED),
                        cb.equal(root.get("status"), TalentLifecycleStatus.APPLICANT_DECLINED)
                ));
            } else {
                // Para abas ativas (Todos, Elenco Principal, Stars):
                // Modelos não arquivados
                predicates.add(cb.and(
                        cb.isNull(root.get("archivedAt")),
                        cb.or(
                                cb.isNull(root.get("status")),
                                cb.notEqual(root.get("status"), TalentLifecycleStatus.ARCHIVED)
                        )
                ));

                if (Boolean.TRUE.equals(isActive)) {
                    predicates.add(cb.equal(cb.coalesce(root.get("isActive"), true), true));
                }
            }

            // 2. Stars vs Elenco Principal
            if (Boolean.TRUE.equals(isStar)) {
                // Aba Stars: isStar = true ou status = CAST_STAR
                predicates.add(cb.or(
                        cb.equal(cb.coalesce(root.get("isStar"), false), true),
                        cb.equal(root.get("status"), TalentLifecycleStatus.CAST_STAR)
                ));
            } else if (Boolean.FALSE.equals(isStar)) {
                // Aba Elenco Principal: não é star
                predicates.add(cb.and(
                        cb.equal(cb.coalesce(root.get("isStar"), false), false),
                        cb.or(
                                cb.isNull(root.get("status")),
                                cb.notEqual(root.get("status"), TalentLifecycleStatus.CAST_STAR)
                        )
                ));
            }

            // 3. Filtro por Gênero
            if (gender != null) {
                predicates.add(cb.equal(root.get("gender"), gender));
            }

            // 4. Busca textual
            if (StringUtils.hasText(search)) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate stageNameMatch = cb.like(cb.lower(root.get("stageName")), searchPattern);
                Predicate fullNameMatch = cb.like(cb.lower(cb.coalesce(root.get("fullName"), "")), searchPattern);
                predicates.add(cb.or(stageNameMatch, fullNameMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
