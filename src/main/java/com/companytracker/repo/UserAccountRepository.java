package com.companytracker.repo;

import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @Query("select u from UserAccount u where lower(u.email) = lower(:email)")
    Optional<UserAccount> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select count(u) from UserAccount u where u.role = com.companytracker.domain.UserRole.ADMIN and u.enabled = true and u.deletedAt is null")
    long countEnabledAdmins();

    @Query("select count(u) from UserAccount u where u.role = com.companytracker.domain.UserRole.ADMIN and u.enabled = true and u.deletedAt is null and u.id <> :excludeId")
    long countEnabledAdminsExcluding(@Param("excludeId") Long excludeId);

    @Query("select u from UserAccount u where u.deletedAt is null order by u.email")
    List<UserAccount> findAllActive();

    @Query("select u from UserAccount u where u.deletedAt is not null order by u.deletedAt desc")
    List<UserAccount> findAllDeleted();

    boolean existsByRoleAndEnabledTrueAndDeletedAtIsNull(UserRole role);
}
