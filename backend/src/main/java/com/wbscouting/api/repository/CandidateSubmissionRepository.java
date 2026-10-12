package com.wbscouting.api.repository;

import com.wbscouting.api.entity.CandidateSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateSubmissionRepository extends JpaRepository<CandidateSubmission, UUID>, JpaSpecificationExecutor<CandidateSubmission> {

    Optional<CandidateSubmission> findByProtocol(String protocol);

    boolean existsByProtocol(String protocol);

    Page<CandidateSubmission> findByStatusAndArchivedAtIsNull(String status, Pageable pageable);

    @Query("SELECT cs FROM CandidateSubmission cs WHERE cs.status IN :statuses AND cs.archivedAt IS NULL")
    Page<CandidateSubmission> findByStatusInAndArchivedAtIsNull(@Param("statuses") List<String> statuses, Pageable pageable);

    @Query("SELECT cs FROM CandidateSubmission cs WHERE cs.archivedAt IS NULL AND (cs.status IS NULL OR cs.status <> 'ARCHIVED')")
    Page<CandidateSubmission> findByArchivedAtIsNull(Pageable pageable);

    @Query(
            value = "SELECT cs FROM CandidateSubmission cs WHERE cs.archivedAt IS NOT NULL OR cs.status = 'ARCHIVED'",
            countQuery = "SELECT count(cs) FROM CandidateSubmission cs WHERE cs.archivedAt IS NOT NULL OR cs.status = 'ARCHIVED'"
    )
    Page<CandidateSubmission> findArchivedSubmissions(Pageable pageable);

    long countByStatusAndArchivedAtIsNull(String status);

    @Query("SELECT count(cs) FROM CandidateSubmission cs WHERE cs.status IN :statuses AND cs.archivedAt IS NULL")
    long countByStatusInAndArchivedAtIsNull(@Param("statuses") List<String> statuses);

    long countByArchivedAtIsNotNull();

    @Query("SELECT count(cs) FROM CandidateSubmission cs WHERE cs.archivedAt IS NOT NULL OR cs.status = 'ARCHIVED'")
    long countArchivedSubmissions();

    @Query("SELECT cs FROM CandidateSubmission cs WHERE cs.archivedAt IS NOT NULL OR cs.status = 'ARCHIVED'")
    List<CandidateSubmission> findAllArchivedSubmissions();

    @Query("SELECT cs FROM CandidateSubmission cs WHERE cs.archivedAt IS NOT NULL AND cs.archivedAt < :threshold")
    List<CandidateSubmission> findByArchivedAtBefore(@Param("threshold") OffsetDateTime threshold);
}
