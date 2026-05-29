package com.example.lambda;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * S3 storage service with user-specific folder organization.
 *
 * Structure: s3://bucket/users/{userId}/pdfs/...
 *                            /users/{userId}/results/...
 */
public class UserSpecificStorageService {

    private final S3Client s3;
    private final String bucket;

    public UserSpecificStorageService(String bucket, String region) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3 bucket is required");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("AWS region is required");
        }

        this.bucket = bucket.trim();

        this.s3 = S3Client.builder()
                .region(Region.of(region.trim()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        System.out.println("[UserSpecificStorageService] Initialized - Bucket: " + bucket);
    }

    /**
     * Upload PDF for a specific user.
     * Path: users/{userId}/pdfs/YYYY/MM/DD/{uploadId}-{filename}
     */
    public String uploadPdfForUser(String userId, String uploadId, byte[] pdfBytes, String filename) {
        String safeFilename = sanitizeFilename(filename);
        String datePrefix = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String key = String.format("users/%s/pdfs/%s/%s-%s", userId, datePrefix, uploadId, safeFilename);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/pdf")
                .contentLength((long) pdfBytes.length)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .acl(ObjectCannedACL.PRIVATE)
                .build();

        s3.putObject(putRequest, RequestBody.fromBytes(pdfBytes));

        return "s3://" + bucket + "/" + key;
    }

    /**
     * Save processing results (transactions/advice JSON) for a specific user.
     * Path: users/{userId}/results/{uploadId}/{filename}
     */
    public String saveResultForUser(String userId, String uploadId, String filename, String content) {
        String key = String.format("users/%s/results/%s/%s", userId, uploadId, filename);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/json")
                .serverSideEncryption(ServerSideEncryption.AES256)
                .acl(ObjectCannedACL.PRIVATE)
                .build();

        s3.putObject(putRequest, RequestBody.fromString(content, StandardCharsets.UTF_8));

        return "s3://" + bucket + "/" + key;
    }

    /**
     * Sanitize filename to prevent path traversal and special characters.
     */
    private static String sanitizeFilename(String name) {
        String base = Path.of(name).getFileName().toString();
        return base.replaceAll("[^A-Za-z0-9._()\\- ]", "_")
                .trim()
                .replaceAll("\\s+", "_");
    }
}
