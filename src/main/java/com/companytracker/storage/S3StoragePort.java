package com.companytracker.storage;

import com.companytracker.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;

@Component
@ConditionalOnProperty(name = "app.storage-type", havingValue = "s3", matchIfMissing = true)
public class S3StoragePort implements StoragePort {

    private final AppProperties properties;
    private S3Client client;
    private String bucket;

    public S3StoragePort(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        AppProperties.S3 s3 = properties.s3();
        this.bucket = s3.bucket();
        S3Configuration.Builder s3Config = S3Configuration.builder();
        if (s3.pathStyle()) {
            s3Config.pathStyleAccessEnabled(true);
        }
        this.client = S3Client.builder()
                .endpointOverride(URI.create(s3.endpoint()))
                .region(Region.of(s3.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())))
                .serviceConfiguration(s3Config.build())
                .build();
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException ex) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (Exception ex) {
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (Exception ignored) {
                // bucket may already exist or init container created it
            }
        }
    }

    @Override
    public StoredObject store(String key, InputStream content, long size, String contentType) {
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(size)
                        .build(),
                RequestBody.fromInputStream(content, size)
        );
        return new StoredObject(key, contentType, size);
    }

    @Override
    public InputStream open(String key) {
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        }
    }

    public String getBucket() {
        return bucket;
    }
}
