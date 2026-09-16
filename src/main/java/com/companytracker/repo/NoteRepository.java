package com.companytracker.repo;

import com.companytracker.domain.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, Long> {

    @Query("select n from Note n where n.id = :id and n.deletedAt is null")
    Optional<Note> findActiveById(@Param("id") Long id);

    @Query("select n from Note n where n.company.id = :companyId and n.deletedAt is null order by n.updatedAt desc")
    List<Note> findActiveByCompany(@Param("companyId") Long companyId);

    @Query("select n from Note n where n.application.id = :applicationId and n.deletedAt is null order by n.updatedAt desc")
    List<Note> findActiveByApplication(@Param("applicationId") Long applicationId);

    @Query("select n from Note n where n.round.id = :roundId and n.deletedAt is null order by n.updatedAt desc")
    List<Note> findActiveByRound(@Param("roundId") Long roundId);

    List<Note> findByOwner_IdAndDeletionBatchId(Long ownerId, UUID deletionBatchId);

    @Query("select n from Note n where n.owner.id = :ownerId")
    List<Note> findAllByOwner(@Param("ownerId") Long ownerId);
}
