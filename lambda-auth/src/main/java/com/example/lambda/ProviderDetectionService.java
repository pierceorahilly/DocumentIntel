package com.example.lambda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Detects service providers (telephone, energy, internet, etc.) from transaction descriptions.
 * Automatically populates user's provider fields based on recurring payments.
 */
public class ProviderDetectionService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Provider patterns (case-insensitive matching)
    private static final Map<String, List<Pattern>> PROVIDER_PATTERNS = new HashMap<>() {{
        // Telephone providers
        put("telephoneProvider", Arrays.asList(
                Pattern.compile("vodafone", Pattern.CASE_INSENSITIVE),
                Pattern.compile("o2|o 2", Pattern.CASE_INSENSITIVE),
                Pattern.compile("ee mobile|ee ltd", Pattern.CASE_INSENSITIVE),
                Pattern.compile("three|3 mobile", Pattern.CASE_INSENSITIVE),
                Pattern.compile("giffgaff", Pattern.CASE_INSENSITIVE),
                Pattern.compile("virgin mobile", Pattern.CASE_INSENSITIVE),
                Pattern.compile("sky mobile", Pattern.CASE_INSENSITIVE),
                Pattern.compile("tesco mobile", Pattern.CASE_INSENSITIVE)
        ));

        // Energy providers (electricity & gas)
        put("energyProvider", Arrays.asList(
                Pattern.compile("british gas", Pattern.CASE_INSENSITIVE),
                Pattern.compile("eon|e\\.on", Pattern.CASE_INSENSITIVE),
                Pattern.compile("edf energy", Pattern.CASE_INSENSITIVE),
                Pattern.compile("scottish power", Pattern.CASE_INSENSITIVE),
                Pattern.compile("sse energy", Pattern.CASE_INSENSITIVE),
                Pattern.compile("npower", Pattern.CASE_INSENSITIVE),
                Pattern.compile("octopus energy", Pattern.CASE_INSENSITIVE),
                Pattern.compile("bulb energy", Pattern.CASE_INSENSITIVE),
                Pattern.compile("ovo energy", Pattern.CASE_INSENSITIVE),
                Pattern.compile("shell energy", Pattern.CASE_INSENSITIVE)
        ));

        // Internet/Broadband providers
        put("internetProvider", Arrays.asList(
                Pattern.compile("bt broadband|bt internet", Pattern.CASE_INSENSITIVE),
                Pattern.compile("sky broadband|sky internet", Pattern.CASE_INSENSITIVE),
                Pattern.compile("virgin media", Pattern.CASE_INSENSITIVE),
                Pattern.compile("talktalk", Pattern.CASE_INSENSITIVE),
                Pattern.compile("plusnet", Pattern.CASE_INSENSITIVE),
                Pattern.compile("now broadband", Pattern.CASE_INSENSITIVE),
                Pattern.compile("hyperoptic", Pattern.CASE_INSENSITIVE),
                Pattern.compile("community fibre", Pattern.CASE_INSENSITIVE)
        ));

        // Water providers
        put("waterProvider", Arrays.asList(
                Pattern.compile("thames water", Pattern.CASE_INSENSITIVE),
                Pattern.compile("severn trent", Pattern.CASE_INSENSITIVE),
                Pattern.compile("united utilities", Pattern.CASE_INSENSITIVE),
                Pattern.compile("yorkshire water", Pattern.CASE_INSENSITIVE),
                Pattern.compile("anglian water", Pattern.CASE_INSENSITIVE),
                Pattern.compile("south west water", Pattern.CASE_INSENSITIVE),
                Pattern.compile("southern water", Pattern.CASE_INSENSITIVE),
                Pattern.compile("wessex water", Pattern.CASE_INSENSITIVE)
        ));

        // Gas providers (separate from general energy)
        put("gasProvider", Arrays.asList(
                Pattern.compile("british gas", Pattern.CASE_INSENSITIVE),
                Pattern.compile("calor gas", Pattern.CASE_INSENSITIVE),
                Pattern.compile("flogas", Pattern.CASE_INSENSITIVE)
        ));

        // Bank names
        put("bankName", Arrays.asList(
                Pattern.compile("barclays", Pattern.CASE_INSENSITIVE),
                Pattern.compile("hsbc", Pattern.CASE_INSENSITIVE),
                Pattern.compile("lloyds", Pattern.CASE_INSENSITIVE),
                Pattern.compile("natwest|nat west", Pattern.CASE_INSENSITIVE),
                Pattern.compile("santander", Pattern.CASE_INSENSITIVE),
                Pattern.compile("nationwide", Pattern.CASE_INSENSITIVE),
                Pattern.compile("halifax", Pattern.CASE_INSENSITIVE),
                Pattern.compile("rbs|royal bank of scotland", Pattern.CASE_INSENSITIVE),
                Pattern.compile("tsb", Pattern.CASE_INSENSITIVE),
                Pattern.compile("first direct", Pattern.CASE_INSENSITIVE),
                Pattern.compile("monzo", Pattern.CASE_INSENSITIVE),
                Pattern.compile("starling", Pattern.CASE_INSENSITIVE),
                Pattern.compile("revolut", Pattern.CASE_INSENSITIVE)
        ));
    }};

    /**
     * Detect providers from transaction JSON array.
     * Returns map of provider type -> provider name (e.g., "telephoneProvider" -> "Vodafone")
     */
    public static Map<String, String> detectProviders(String transactionsJson) {
        Map<String, String> detectedProviders = new HashMap<>();

        try {
            JsonNode transactions = objectMapper.readTree(transactionsJson);

            // Iterate through each transaction
            for (JsonNode txn : transactions) {
                String description = txn.has("description") ? txn.get("description").asText() : "";

                // Check against all provider patterns
                for (Map.Entry<String, List<Pattern>> entry : PROVIDER_PATTERNS.entrySet()) {
                    String providerType = entry.getKey();
                    List<Pattern> patterns = entry.getValue();

                    // Skip if already detected
                    if (detectedProviders.containsKey(providerType)) {
                        continue;
                    }

                    // Check if description matches any pattern
                    for (Pattern pattern : patterns) {
                        if (pattern.matcher(description).find()) {
                            // Extract clean provider name from description
                            String providerName = extractProviderName(description, pattern);
                            detectedProviders.put(providerType, providerName);
                            System.out.println("✓ Detected " + providerType + ": " + providerName);
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error detecting providers: " + e.getMessage());
        }

        return detectedProviders;
    }

    /**
     * Extract clean provider name from transaction description.
     * Example: "VODAFONE DD PAYMENT" -> "Vodafone"
     */
    private static String extractProviderName(String description, Pattern pattern) {
        java.util.regex.Matcher matcher = pattern.matcher(description);
        if (matcher.find()) {
            String match = matcher.group();
            // Capitalize first letter of each word
            return Arrays.stream(match.split("\\s+"))
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                    .reduce((a, b) -> a + " " + b)
                    .orElse(match);
        }
        return description.substring(0, Math.min(description.length(), 30)).trim();
    }

    /**
     * Get last N transactions from full transaction list as JSON string.
     * Used to store recent transactions in Users table.
     */
    public static String getRecentTransactionsJson(String allTransactionsJson, int limit) {
        try {
            JsonNode allTransactions = objectMapper.readTree(allTransactionsJson);

            if (allTransactions.size() <= limit) {
                return allTransactionsJson;
            }

            // Take last N transactions (most recent)
            List<JsonNode> recentList = new ArrayList<>();
            for (int i = Math.max(0, allTransactions.size() - limit); i < allTransactions.size(); i++) {
                recentList.add(allTransactions.get(i));
            }

            return objectMapper.writeValueAsString(recentList);

        } catch (Exception e) {
            System.err.println("Error extracting recent transactions: " + e.getMessage());
            return "[]";
        }
    }

    /**
     * Calculate total spending from transactions.
     * Returns negative value (e.g., -1234.56 means spent £1234.56)
     */
    public static double calculateTotalSpending(String transactionsJson) {
        double total = 0.0;

        try {
            JsonNode transactions = objectMapper.readTree(transactionsJson);

            for (JsonNode txn : transactions) {
                if (txn.has("amount")) {
                    String amountStr = txn.get("amount").asText().replace(",", "");
                    try {
                        double amount = Double.parseDouble(amountStr);
                        // Only count negative amounts (spending, not income)
                        if (amount < 0) {
                            total += amount;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error calculating total spending: " + e.getMessage());
        }

        return total;
    }
}
