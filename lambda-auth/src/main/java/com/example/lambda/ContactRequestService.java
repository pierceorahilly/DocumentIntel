package com.example.lambda;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing bill-related contact requests in DynamoDB.
 * Stores one record per user per upload, containing all flagged bills.
 * Records can be exported (e.g. weekly) and handed to a support company.
 */
public class ContactRequestService {

    private final DynamoDbClient dynamoDb;
    private final String tableName;
    private final ObjectMapper objectMapper;

    public ContactRequestService(String region) {
        this.tableName = System.getenv("DYNAMODB_CONTACT_REQUESTS_TABLE");
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalStateException("DYNAMODB_CONTACT_REQUESTS_TABLE environment variable is required");
        }

        this.dynamoDb = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.objectMapper = new ObjectMapper();

        System.out.println("[ContactRequestService] Initialized - Table: " + tableName);
    }

    /**
     * Create a contact request for a user with one or more flagged bills.
     *
     * @param userId       Cognito user ID
     * @param name         user's full name
     * @param email        user's email address
     * @param age          user's age
     * @param flaggedBills list of flagged bills from BillFlagService
     */
    public void createContactRequest(String userId, String name, String email,
                                     int age, List<BillFlagService.BillFlag> flaggedBills) {
        String requestId = "cr-" + UUID.randomUUID();
        String now = Instant.now().toString();

        try {
            // Convert flagged bills to JSON string for storage
            String flaggedBillsJson = objectMapper.writeValueAsString(flaggedBills);

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("requestId", AttributeValue.builder().s(requestId).build());
            item.put("userId", AttributeValue.builder().s(userId).build());
            item.put("name", AttributeValue.builder().s(name).build());
            item.put("email", AttributeValue.builder().s(email).build());
            item.put("age", AttributeValue.builder().n(String.valueOf(age)).build());
            item.put("flaggedBills", AttributeValue.builder().s(flaggedBillsJson).build());
            item.put("requestedAt", AttributeValue.builder().s(now).build());
            item.put("status", AttributeValue.builder().s("pending").build());

            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build());

            System.out.println("✅ Created contact request " + requestId
                    + " for user " + userId + " with " + flaggedBills.size() + " flagged bill(s)");

        } catch (Exception e) {
            System.err.println("Error creating contact request: " + e.getMessage());
        }
    }

    /**
     * Get all contact requests with a given status.
     * Use status "pending" to get the export list.
     */
    public List<Map<String, AttributeValue>> getRequestsByStatus(String status) {
        ScanResponse response = dynamoDb.scan(ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#s = :status")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s(status).build()
                ))
                .build());

        return response.items();
    }

    /**
     * Mark a contact request as exported (after handing list to company).
     */
    public void markAsExported(String requestId) {
        String now = Instant.now().toString();

        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("requestId", AttributeValue.builder().s(requestId).build()))
                .updateExpression("SET #s = :status, exportedAt = :exportedAt")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s("exported").build(),
                        ":exportedAt", AttributeValue.builder().s(now).build()
                ))
                .build());

        System.out.println("✅ Marked contact request " + requestId + " as exported");
    }
}
