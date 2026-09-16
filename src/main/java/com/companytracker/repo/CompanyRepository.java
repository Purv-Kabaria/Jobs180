package com.companytracker.repo;

import com.companytracker.domain.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    @Query("select c from Company c where c.id = :id and c.deletedAt is null")
    Optional<Company> findActiveById(@Param("id") Long id);

    @Query("select c from Company c where c.owner.id = :ownerId and c.deletedAt is null and (:q is null or lower(c.name) like lower(concat('%', :q, '%')))")
    Page<Company> searchActiveByOwner(@Param("ownerId") Long ownerId, @Param("q") String q, Pageable pageable);

    @Query("select c from Company c where c.deletedAt is null and (:q is null or lower(c.name) like lower(concat('%', :q, '%')))")
    Page<Company> searchActiveAll(@Param("q") String q, Pageable pageable);

    @Query("select c from Company c where c.owner.id = :ownerId and c.deletedAt is not null")
    List<Company> findDeletedByOwner(@Param("ownerId") Long ownerId);

    @Query("select c from Company c where c.owner.id = :ownerId")
    List<Company> findAllByOwner(@Param("ownerId") Long ownerId);

    List<Company> findByOwner_IdAndDeletionBatchId(Long ownerId, UUID deletionBatchId);
}
