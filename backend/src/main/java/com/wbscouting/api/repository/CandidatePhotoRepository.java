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

    @Query("SELECT cp FROM CandidatePhoto cp WHERE cp.submission.id = :candidateId ORDER BY cp.displayOrder ASC")
    List<CandidatePhoto> findByCandidateId(@Param("candidateId") UUID candidateId);

    List<CandidatePhoto> findBySubmissionIdOrderByDisplayOrderAsc(UUID submissionId);
}
