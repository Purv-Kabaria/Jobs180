package com.companytracker.repo;

import com.companytracker.domain.ResourceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceItemRepository extends JpaRepository<ResourceItem, Long> {

    @Query("select r from ResourceItem r where r.id = :id and r.deletedAt is null")
    Optional<ResourceItem> findActiveById(@Param("id") Long id);

    @Query("select r from ResourceItem r where r.company.id = :companyId and r.deletedAt is null order by r.updatedAt desc")
    List<ResourceItem> findActiveByCompany(@Param("companyId") Long companyId);

    @Query("select r from ResourceItem r where r.application.id = :applicationId and r.deletedAt is null order by r.updatedAt desc")
    List<ResourceItem> findActiveByApplication(@Param("applicationId") Long applicationId);

    @Query("select r from ResourceItem r where r.round.id = :roundId and r.deletedAt is null order by r.updatedAt desc")
    List<ResourceItem> findActiveByRound(@Param("roundId") Long roundId);

    List<ResourceItem> findByOwner_IdAndDeletionBatchId(Long ownerId, UUID deletionBatchId);

    @Query("select r from ResourceItem r where r.owner.id = :ownerId")
    List<ResourceItem> findAllByOwner(@Param("ownerId") Long ownerId);
}
