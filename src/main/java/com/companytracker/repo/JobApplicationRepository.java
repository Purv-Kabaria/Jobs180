package com.companytracker.repo;

import com.companytracker.domain.ApplicationStatus;
import com.companytracker.domain.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    @Query("select a from JobApplication a join fetch a.company where a.id = :id and a.deletedAt is null")
    Optional<JobApplication> findActiveById(@Param("id") Long id);

    @Query("select a from JobApplication a where a.company.id = :companyId and a.deletedAt is null order by a.updatedAt desc")
    List<JobApplication> findActiveByCompany(@Param("companyId") Long companyId);

    @Query("""
            select a from JobApplication a join fetch a.company c
            where a.deletedAt is null
              and (:ownerId is null or a.owner.id = :ownerId)
              and (:company is null or lower(c.name) like lower(concat('%', :company, '%')))
              and (:status is null or a.status = :status)
              and (:fromDate is null or a.appliedDate >= :fromDate)
              and (:toDate is null or a.appliedDate <= :toDate)
            """)
    Page<JobApplication> search(@Param("ownerId") Long ownerId,
                                @Param("company") String company,
                                @Param("status") ApplicationStatus status,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                Pageable pageable);

    @Query("select a.status as status, count(a) as cnt from JobApplication a where a.deletedAt is null and a.owner.id = :ownerId group by a.status")
    List<StatusCount> countByStatusForOwner(@Param("ownerId") Long ownerId);

    @Query("select a.status as status, count(a) as cnt from JobApplication a where a.deletedAt is null group by a.status")
    List<StatusCount> countByStatusAll();

    List<JobApplication> findByCompany_IdAndDeletionBatchId(Long companyId, UUID deletionBatchId);

    List<JobApplication> findByOwner_IdAndDeletionBatchId(Long ownerId, UUID deletionBatchId);

    @Query("select a from JobApplication a where a.owner.id = :ownerId")
    List<JobApplication> findAllByOwner(@Param("ownerId") Long ownerId);

    @Query("select a from JobApplication a where a.company.id = :companyId")
    List<JobApplication> findAllByCompanyId(@Param("companyId") Long companyId);

    interface StatusCount {
        ApplicationStatus getStatus();
        long getCnt();
    }
}
