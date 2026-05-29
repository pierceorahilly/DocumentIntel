package com.example.lambda;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing user data in DynamoDB.
 */
public class UserService {

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public UserService(String region) {
        this.tableName = System.getenv("DYNAMODB_USERS_TABLE");
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalStateException("DYNAMODB_USERS_TABLE environment variable is required");
        }

        this.dynamoDb = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        System.out.println("[UserService] Initialized - Table: " + tableName);
    }

    /**
     * Create a new user record in DynamoDB.
     */
    public void createUser(String userId, String email, String name, String dateOfBirth, String address, String s3Prefix) {
        String now = Instant.now().toString();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId", AttributeValue.builder().s(userId).build());
        item.put("email", AttributeValue.builder().s(email).build());
        item.put("name", AttributeValue.builder().s(name).build());
        item.put("dateOfBirth", AttributeValue.builder().s(dateOfBirth).build());
        item.put("address", AttributeValue.builder().s(address).build());
        item.put("s3Prefix", AttributeValue.builder().s(s3Prefix).build());
        item.put("createdAt", AttributeValue.builder().s(now).build());
        item.put("lastLogin", AttributeValue.builder().s(now).build());
        item.put("subscriptionTier", AttributeValue.builder().s("free").build());
        item.put("totalUploads", AttributeValue.builder().n("0").build());
        item.put("totalTransactions", AttributeValue.builder().n("0").build());
        item.put("totalSpending", AttributeValue.builder().n("0.00").build());

        // Provider columns (initially empty, will be populated from transaction analysis)
        item.put("telephoneProvider", AttributeValue.builder().s("").build());
        item.put("energyProvider", AttributeValue.builder().s("").build());
        item.put("internetProvider", AttributeValue.builder().s("").build());
        item.put("waterProvider", AttributeValue.builder().s("").build());
        item.put("gasProvider", AttributeValue.builder().s("").build());
        item.put("bankName", AttributeValue.builder().s("").build());

        // Recent transactions stored as JSON (last 100 for quick access)
        item.put("recentTransactions", AttributeValue.builder().s("[]").build());

        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());

        System.out.println("✅ Created user: " + userId + " (" + email + ")");
    }

    /**
     * Get user by userId.
     */
    public Map<String, AttributeValue> getUser(String userId) {
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                .build());

        return response.item();
    }

    /**
     * Update last login timestamp.
     */
    public void updateLastLogin(String userId) {
        String now = Instant.now().toString();

        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                .updateExpression("SET lastLogin = :now")
                .expressionAttributeValues(Map.of(
                        ":now", AttributeValue.builder().s(now).build()
                ))
                .build());

        System.out.println("✅ Updated last login for user: " + userId);
    }

    /**
     * Increment upload and transaction counters.
     */
    public void incrementCounters(String userId, int transactionCount) {
        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                .updateExpression("SET totalUploads = totalUploads + :one, totalTransactions = totalTransactions + :count")
                .expressionAttributeValues(Map.of(
                        ":one", AttributeValue.builder().n("1").build(),
                        ":count", AttributeValue.builder().n(String.valueOf(transactionCount)).build()
                ))
                .build());

        System.out.println("✅ Incremented counters for user: " + userId);
    }

    /**
     * Get user's S3 prefix (folder path).
     */
    public String getUserS3Prefix(String userId) {
        Map<String, AttributeValue> user = getUser(userId);
        if (user == null || user.isEmpty()) {
            throw new RuntimeException("User not found: " + userId);
        }

        return user.get("s3Prefix").s();
    }

    /**
     * Check if user exists.
     */
    public boolean userExists(String userId) {
        Map<String, AttributeValue> user = getUser(userId);
        return user != null && !user.isEmpty();
    }

    /**
     * Update user's provider information (telephone, energy, internet, etc.)
     */
    public void updateProviders(String userId, Map<String, String> providers) {
        Map<String, AttributeValue> attributeValues = new HashMap<>();
        StringBuilder updateExpression = new StringBuilder("SET ");

        int index = 0;
        for (Map.Entry<String, String> entry : providers.entrySet()) {
            if (index > 0) updateExpression.append(", ");
            String placeholder = ":val" + index;
            updateExpression.append(entry.getKey()).append(" = ").append(placeholder);
            attributeValues.put(placeholder, AttributeValue.builder().s(entry.getValue()).build());
            index++;
        }

        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                .updateExpression(updateExpression.toString())
                .expressionAttributeValues(attributeValues)
                .build());

        System.out.println("✅ Updated providers for user: " + userId);
    }

    /**
     * Update recent transactions JSON (last 100 transactions for quick access)
     */
    public void updateRecentTransactions(String userId, String transactionsJson) {
        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                .updateExpression("SET recentTransactions = :txns")
                .expressionAttributeValues(Map.of(
                        ":txns", AttributeValue.builder().s(transactionsJson).build()
                ))
                .build());

        System.out.println("✅ Updated recent transactions for user: " + userId);
    }

    /**
     * Update total spending (cumulative amount spent)
     */
    public void updateTotalSpending(String userId, double additionalSpending) {
        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                .updateExpression("SET totalSpending = totalSpending + :amount")
                .expressionAttributeValues(Map.of(
                        ":amount", AttributeValue.builder().n(String.valueOf(additionalSpending)).build()
                ))
                .build());

        System.out.println("✅ Updated total spending for user: " + userId);
    }
}
