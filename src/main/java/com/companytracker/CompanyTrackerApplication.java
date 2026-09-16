package com.companytracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

@SpringBootApplication
@EnableCaching
@EnableRedisIndexedHttpSession
@ConfigurationPropertiesScan
public class CompanyTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompanyTrackerApplication.class, args);
    }
}
