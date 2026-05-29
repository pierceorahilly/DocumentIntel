package com.example.lambda;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing upload records in DynamoDB.
 */
public class UploadService {

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public UploadService(String region) {
        this.tableName = System.getenv("DYNAMODB_UPLOADS_TABLE");
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalStateException("DYNAMODB_UPLOADS_TABLE environment variable is required");
        }

        this.dynamoDb = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        System.out.println("[UploadService] Initialized - Table: " + tableName);
    }

    /**
     * Create a new upload record with status "processing".
     */
    public void createUpload(String uploadId, String userId, String filename, long fileSize) {
        String now = Instant.now().toString();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("uploadId", AttributeValue.builder().s(uploadId).build());
        item.put("userId", AttributeValue.builder().s(userId).build());
        item.put("filename", AttributeValue.builder().s(filename).build());
        item.put("uploadDate", AttributeValue.builder().s(now).build());
        item.put("status", AttributeValue.builder().s("processing").build());
        item.put("fileSize", AttributeValue.builder().n(String.valueOf(fileSize)).build());

        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());

        System.out.println("✅ Created upload record: " + uploadId);
    }

    /**
     * Update upload status to "completed" with S3 URLs and processing details.
     */
    public void completeUpload(String uploadId, String s3PdfUrl, String s3TransactionsUrl,
                               String s3AdviceUrl, String s3CategoryUrl, int transactionCount, double processingTime) {
        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("uploadId", AttributeValue.builder().s(uploadId).build()))
                .updateExpression("SET #status = :completed, " +
                                  "s3PdfUrl = :pdfUrl, " +
                                  "s3TransactionsUrl = :txnUrl, " +
                                  "s3AdviceUrl = :adviceUrl, " +
                                  "s3CategoryUrl = :categoryUrl, " +
                                  "transactionCount = :count, " +
                                  "processingTime = :time")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":completed", AttributeValue.builder().s("completed").build(),
                        ":pdfUrl", AttributeValue.builder().s(s3PdfUrl).build(),
                        ":txnUrl", AttributeValue.builder().s(s3TransactionsUrl).build(),
                        ":adviceUrl", AttributeValue.builder().s(s3AdviceUrl).build(),
                        ":categoryUrl", AttributeValue.builder().s(s3CategoryUrl).build(),
                        ":count", AttributeValue.builder().n(String.valueOf(transactionCount)).build(),
                        ":time", AttributeValue.builder().n(String.valueOf(processingTime)).build()
                ))
                .build());

        System.out.println("✅ Completed upload: " + uploadId);
    }

    /**
     * Store the S3 URL for bill flags on a completed upload.
     */
    public void updateBillFlagsUrl(String uploadId, String s3BillFlagsUrl) {
        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("uploadId", AttributeValue.builder().s(uploadId).build()))
                .updateExpression("SET s3BillFlagsUrl = :url")
                .expressionAttributeValues(Map.of(
                        ":url", AttributeValue.builder().s(s3BillFlagsUrl).build()
                ))
                .build());

        System.out.println("✅ Stored bill flags URL for upload: " + uploadId);
    }

    /**
     * Mark upload as failed with error message.
     */
    public void failUpload(String uploadId, String errorMessage) {
        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("uploadId", AttributeValue.builder().s(uploadId).build()))
                .updateExpression("SET #status = :failed, #error = :msg")
                .expressionAttributeNames(Map.of(
                        "#status", "status",
                        "#error", "error"
                ))
                .expressionAttributeValues(Map.of(
                        ":failed", AttributeValue.builder().s("failed").build(),
                        ":msg", AttributeValue.builder().s(errorMessage).build()
                ))
                .build());

        System.err.println("❌ Failed upload: " + uploadId + " - " + errorMessage);
    }

    /**
     * Get all uploads for a user, sorted by date (most recent first).
     */
    public List<Map<String, AttributeValue>> getUserUploads(String userId, int limit) {
        QueryResponse response = dynamoDb.query(QueryRequest.builder()
                .tableName(tableName)
                .indexName("UserUploadsIndex")
                .keyConditionExpression("userId = :userId")
                .expressionAttributeValues(Map.of(
                        ":userId", AttributeValue.builder().s(userId).build()
                ))
                .scanIndexForward(false)  // Descending order (newest first)
                .limit(limit)
                .build());

        return response.items();
    }

    /**
     * Get a single upload by ID.
     */
    public Map<String, AttributeValue> getUpload(String uploadId) {
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("uploadId", AttributeValue.builder().s(uploadId).build()))
                .build());

        return response.item();
    }

    /**
     * Convert a single upload to JSON-friendly format.
     */
    public Map<String, Object> uploadToJson(Map<String, AttributeValue> item) {
        return toJson(item);
    }

    /**
     * Convert DynamoDB items to JSON-friendly format.
     */
    public static List<Map<String, Object>> toJsonList(List<Map<String, AttributeValue>> items) {
        return items.stream()
                .map(UploadService::toJson)
                .collect(Collectors.toList());
    }

    private static Map<String, Object> toJson(Map<String, AttributeValue> item) {
        Map<String, Object> json = new HashMap<>();
        item.forEach((key, value) -> {
            if (value.s() != null) {
                json.put(key, value.s());
            } else if (value.n() != null) {
                json.put(key, Double.parseDouble(value.n()));
            } else if (value.bool() != null) {
                json.put(key, value.bool());
            }
        });
        return json;
    }
}
