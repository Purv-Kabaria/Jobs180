package com.companytracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String storageType,
        long uploadMaxBytes,
        long idempotencyTtlSeconds,
        boolean signupsEnabled,
        String corsAllowedOrigins,
        String displayZone,
        S3 s3,
        RateLimit rateLimit
) {
    public record S3(
            String endpoint,
            String bucket,
            String accessKey,
            String secretKey,
            String region,
            boolean pathStyle
    ) {}

    public record RateLimit(
            int loginPerMin,
            int signupPerMin,
            int writePerMin,
            int uploadPerMin,
            int ipPerMin
    ) {}
}
