package com.companytracker.repo;

import com.companytracker.domain.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    @Query("select r from Resume r where r.id = :id and r.deletedAt is null")
    Optional<Resume> findActiveById(@Param("id") Long id);

    @Query("select r from Resume r where r.owner.id = :ownerId and r.deletedAt is null order by r.label")
    List<Resume> findActiveByOwner(@Param("ownerId") Long ownerId);

    @Query("select r from Resume r where r.owner.id = :ownerId")
    List<Resume> findAllByOwner(@Param("ownerId") Long ownerId);

    List<Resume> findByOwner_IdAndDeletionBatchId(Long ownerId, UUID deletionBatchId);
}
