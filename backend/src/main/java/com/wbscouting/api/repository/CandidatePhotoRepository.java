package com.wbscouting.api.repository;

import com.wbscouting.api.entity.CandidatePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CandidatePhotoRepository extends JpaRepository<CandidatePhoto, UUID> {

    @Query("SELECT cp FROM CandidatePhoto cp WHERE cp.submission.id = :submissionId ORDER BY cp.displayOrder ASC")
    List<CandidatePhoto> findBySubmissionIdOrderByDisplayOrderAsc(@Param("submissionId") UUID submissionId);

    @Query("SELECT cp FROM CandidatePhoto cp WHERE cp.submission.id = :candidateId ORDER BY cp.displayOrder ASC")
    List<CandidatePhoto> findByCandidateIdOrderByDisplayOrderAsc(@Param("candidateId") UUID candidateId);

    @Query("SELECT cp FROM CandidatePhoto cp WHERE cp.submission.id = :candidateId ORDER BY cp.displayOrder ASC")
    List<CandidatePhoto> findByCandidate_IdOrderByDisplayOrderAsc(@Param("candidateId") UUID candidateId);

    @Query("SELECT cp.submission.id, COUNT(cp) FROM CandidatePhoto cp WHERE cp.submission.id IN :candidateIds GROUP BY cp.submission.id")
    List<Object[]> countPhotosByCandidateIds(@Param("candidateIds") List<UUID> candidateIds);

    @Query("SELECT cp FROM CandidatePhoto cp WHERE cp.submission.id IN :submissionIds ORDER BY cp.displayOrder ASC")
    List<CandidatePhoto> findBySubmissionIdInOrderByDisplayOrderAsc(@Param("submissionIds") List<UUID> submissionIds);
}
