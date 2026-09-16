package com.companytracker.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppLockRepository {

    private final JdbcTemplate jdbcTemplate;

    public AppLockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockFirstAdmin() {
        jdbcTemplate.queryForObject(
                "SELECT lock_name FROM app_locks WHERE lock_name = 'first_admin' FOR UPDATE",
                String.class
        );
    }
}
