package com.wbscouting.api.specification;

import com.wbscouting.api.entity.Model;
import com.wbscouting.api.enums.GenderType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class PublicModelSpecification {

    public static Specification<Model> filter(GenderType gender, Boolean isStar, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Predicado fixo: apenas modelos ativos e não arquivados
            predicates.add(cb.equal(cb.coalesce(root.get("isActive"), true), true));
            predicates.add(cb.isNull(root.get("archivedAt")));

            // Apenas modelos do catálogo (CAST_PUBLIC, CAST_STAR, CAST_INTERNAL ou status IS NULL)
            // Exclui candidaturas de scouting que ainda não foram aprovadas/promovidas a casting
            predicates.add(cb.or(
                    root.get("status").in(
                            com.wbscouting.api.enums.TalentLifecycleStatus.CAST_PUBLIC,
                            com.wbscouting.api.enums.TalentLifecycleStatus.CAST_STAR,
                            com.wbscouting.api.enums.TalentLifecycleStatus.CAST_INTERNAL
                    ),
                    cb.isNull(root.get("status"))
            ));

            // Predicado opcional: gender
            if (gender != null) {
                predicates.add(cb.equal(root.get("gender"), gender));
            }

            // Predicado opcional: isStar
            if (isStar != null) {
                predicates.add(cb.equal(root.get("isStar"), isStar));
            }

            // Predicado opcional: search em stage_name ou fullName (case-insensitive)
            if (StringUtils.hasText(search)) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate stageNameMatch = cb.like(cb.lower(cb.coalesce(root.get("stageName"), "")), searchPattern);
                Predicate fullNameMatch = cb.like(cb.lower(cb.coalesce(root.get("fullName"), "")), searchPattern);
                predicates.add(cb.or(stageNameMatch, fullNameMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
