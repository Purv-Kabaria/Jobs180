package com.companytracker.repo;

import com.companytracker.domain.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoundRepository extends JpaRepository<Round, Long> {

    @Query("select r from Round r join fetch r.application a join fetch a.company where r.id = :id and r.deletedAt is null")
    Optional<Round> findActiveById(@Param("id") Long id);

    @Query("select r from Round r where r.application.id = :applicationId and r.deletedAt is null order by r.scheduledAt nulls last")
    List<Round> findActiveByApplication(@Param("applicationId") Long applicationId);

    @Query("""
            select r from Round r join fetch r.application a join fetch a.company
            where r.deletedAt is null
              and r.scheduledAt is not null
              and r.scheduledAt >= :from
              and r.scheduledAt < :to
              and (:ownerId is null or r.owner.id = :ownerId)
            order by r.scheduledAt
            """)
    List<Round> findUpcoming(@Param("ownerId") Long ownerId, @Param("from") Instant from, @Param("to") Instant to);

    List<Round> findByApplication_IdAndDeletionBatchId(Long applicationId, UUID deletionBatchId);

    List<Round> findByOwner_IdAndDeletionBatchId(Long ownerId, UUID deletionBatchId);

    @Query("select r from Round r where r.owner.id = :ownerId")
    List<Round> findAllByOwner(@Param("ownerId") Long ownerId);

    @Query("select r from Round r where r.application.id = :applicationId")
    List<Round> findAllByApplicationId(@Param("applicationId") Long applicationId);

    @Modifying
    @Query("update Round r set r.resume = null where r.resume.id = :resumeId")
    int clearResumeReferences(@Param("resumeId") Long resumeId);
}
