package com.example.lambda;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing transaction records in DynamoDB.
 */
public class TransactionService {

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public TransactionService(String region) {
        this.tableName = System.getenv("DYNAMODB_TRANSACTIONS_TABLE");
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalStateException("DYNAMODB_TRANSACTIONS_TABLE environment variable is required");
        }

        this.dynamoDb = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        System.out.println("[TransactionService] Initialized - Table: " + tableName);
    }

    /**
     * Save transactions in batch (up to 25 at a time due to DynamoDB limit).
     */
    public void saveTransactions(String userId, String uploadId, List<Map<String, String>> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            System.out.println("No transactions to save");
            return;
        }

        String uploadDate = Instant.now().toString();

        // Filter out transactions with empty dates (DynamoDB GSI doesn't allow empty strings)
        List<Map<String, String>> validTransactions = transactions.stream()
                .filter(txn -> txn.get("date") != null && !txn.get("date").trim().isEmpty())
                .collect(Collectors.toList());

        if (validTransactions.isEmpty()) {
            System.out.println("⚠️ No valid transactions to save (all have empty dates)");
            return;
        }

        if (validTransactions.size() < transactions.size()) {
            System.out.println("⚠️ Skipped " + (transactions.size() - validTransactions.size()) + " transactions with empty dates");
        }

        // Split into batches of 25 (DynamoDB BatchWriteItem limit)
        List<List<Map<String, String>>> batches = partition(validTransactions, 25);

        for (List<Map<String, String>> batch : batches) {
            List<WriteRequest> writeRequests = batch.stream()
                    .map(txn -> createWriteRequest(userId, uploadId, txn, uploadDate))
                    .collect(Collectors.toList());

            dynamoDb.batchWriteItem(BatchWriteItemRequest.builder()
                    .requestItems(Map.of(tableName, writeRequests))
                    .build());
        }

        System.out.println("✅ Saved " + validTransactions.size() + " transactions");
    }

    private WriteRequest createWriteRequest(String userId, String uploadId,
                                           Map<String, String> txn, String uploadDate) {
        String transactionId = "txn-" + UUID.randomUUID();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("transactionId", AttributeValue.builder().s(transactionId).build());
        item.put("userId", AttributeValue.builder().s(userId).build());
        item.put("uploadId", AttributeValue.builder().s(uploadId).build());
        item.put("uploadDate", AttributeValue.builder().s(uploadDate).build());

        // Add transaction fields
        item.put("date", AttributeValue.builder().s(txn.getOrDefault("date", "")).build());
        item.put("description", AttributeValue.builder().s(txn.getOrDefault("description", "")).build());
        item.put("amount", AttributeValue.builder().s(txn.getOrDefault("amount", "")).build());
        item.put("balance", AttributeValue.builder().s(txn.getOrDefault("balance", "")).build());

        // Optional: categorize transaction (simple keyword matching)
        String category = categorizeTransaction(txn.getOrDefault("description", ""));
        item.put("category", AttributeValue.builder().s(category).build());

        return WriteRequest.builder()
                .putRequest(PutRequest.builder().item(item).build())
                .build();
    }

    /**
     * Get all transactions for a user, sorted by date (most recent first).
     */
    public List<Map<String, AttributeValue>> getUserTransactions(String userId, int limit) {
        QueryResponse response = dynamoDb.query(QueryRequest.builder()
                .tableName(tableName)
                .indexName("UserTransactionsIndex")
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
     * Get all transactions for a specific upload.
     */
    public List<Map<String, AttributeValue>> getUploadTransactions(String uploadId) {
        QueryResponse response = dynamoDb.query(QueryRequest.builder()
                .tableName(tableName)
                .indexName("UploadTransactionsIndex")
                .keyConditionExpression("uploadId = :uploadId")
                .expressionAttributeValues(Map.of(
                        ":uploadId", AttributeValue.builder().s(uploadId).build()
                ))
                .build());

        return response.items();
    }

    /**
     * Transaction categorization based on description keywords (15 categories, synced with TransactionCategorizer).
     */
    private String categorizeTransaction(String description) {
        String desc = description.toLowerCase();

        // Groceries
        if (desc.contains("tesco") || desc.contains("lidl") || desc.contains("aldi") || desc.contains("supervalu")
                || desc.contains("dunnes") || desc.contains("spar") || desc.contains("centra") || desc.contains("m&s food")
                || desc.contains("supermarket") || desc.contains("grocery"))
            return "Groceries";

        // Eating Out
        if (desc.contains("just eat") || desc.contains("deliveroo") || desc.contains("uber eats")
                || desc.contains("supermacs") || desc.contains("dominos") || desc.contains("pizza")
                || desc.contains("restaurant") || desc.contains("bistro") || desc.contains("takeaway")
                || desc.contains("starbucks") || desc.contains("costa") || desc.contains("coffee") || desc.contains("cafe"))
            return "Eating Out";

        // Transport
        if (desc.contains("circle k") || desc.contains("top oil") || desc.contains("shell") || desc.contains("fuel")
                || desc.contains("petrol") || desc.contains("diesel") || desc.contains("leap card")
                || desc.contains("irish rail") || desc.contains("bus eireann") || desc.contains("luas")
                || desc.contains("taxi") || desc.contains("freenow"))
            return "Transport";

        // Shopping
        if (desc.contains("amazon") || desc.contains("ebay") || desc.contains("penneys") || desc.contains("primark")
                || desc.contains("tk maxx") || desc.contains("zara") || desc.contains("h&m") || desc.contains("asos")
                || desc.contains("shein") || desc.contains("soundstore"))
            return "Shopping";

        // Bills & Utilities
        if (desc.contains("electric ireland") || desc.contains("bord gais") || desc.contains("sse airtricity")
                || desc.contains("irish water") || desc.contains("virgin media") || desc.contains("utility")
                || desc.contains("bank fee") || desc.contains("monthly fee") || desc.contains("overdraft")
                || desc.contains("debit card charge"))
            return "Bills & Utilities";

        // Subscriptions
        if (desc.contains("netflix") || desc.contains("spotify") || desc.contains("disney") || desc.contains("prime")
                || desc.contains("adobe") || desc.contains("microsoft") || desc.contains("apple")
                || desc.contains("subscription") || desc.contains("vodafone") || desc.contains("three")
                || desc.contains("gym"))
            return "Subscriptions";

        // Entertainment
        if (desc.contains("bar") || desc.contains("pub") || desc.contains("nightclub") || desc.contains("cinema")
                || desc.contains("movie") || desc.contains("theatre") || desc.contains("ticketmaster")
                || desc.contains("concert") || desc.contains("bowling"))
            return "Entertainment";

        // Health
        if (desc.contains("boots") || desc.contains("pharmacy") || desc.contains("chemist") || desc.contains("doctor")
                || desc.contains("dentist") || desc.contains("vhi") || desc.contains("laya") || desc.contains("hospital"))
            return "Health";

        // Housing
        if (desc.contains("rent") || desc.contains("mortgage") || desc.contains("home insurance")
                || desc.contains("property") || desc.contains("landlord"))
            return "Housing";

        // Travel
        if (desc.contains("ryanair") || desc.contains("aer lingus") || desc.contains("booking.com")
                || desc.contains("airbnb") || desc.contains("hotel") || desc.contains("hostel")
                || desc.contains("airport") || desc.contains("flight"))
            return "Travel";

        // Personal Care
        if (desc.contains("barber") || desc.contains("hairdresser") || desc.contains("beauty")
                || desc.contains("salon") || desc.contains("spa"))
            return "Personal Care";

        // Education
        if (desc.contains("college") || desc.contains("university") || desc.contains("udemy")
                || desc.contains("coursera") || desc.contains("tuition") || desc.contains("bookshop"))
            return "Education";

        // Investment
        if (desc.contains("trading 212") || desc.contains("degiro") || desc.contains("etoro")
                || desc.contains("coinbase") || desc.contains("binance") || desc.contains("pension")
                || desc.contains("shares"))
            return "Investment";

        // Transfers
        if (desc.contains("atm") || desc.contains("withdrawal") || desc.contains("transfer")
                || desc.contains("revolut") || desc.contains("credit transfer"))
            return "Transfers";

        return "Other";
    }

    /**
     * Helper: Partition list into chunks.
     */
    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    /**
     * Convert DynamoDB items to JSON-friendly format.
     */
    public static List<Map<String, Object>> toJsonList(List<Map<String, AttributeValue>> items) {
        return items.stream()
                .map(TransactionService::toJson)
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
