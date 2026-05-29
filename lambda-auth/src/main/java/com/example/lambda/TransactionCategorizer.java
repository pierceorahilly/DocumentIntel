package com.example.lambda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Categorizes transactions into spending categories and detects recurring subscriptions.
 * Provides structured spending analysis for UI display (pie charts, summaries, etc.)
 */
public class TransactionCategorizer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Category patterns (case-insensitive matching) — 15 categories
    private static final Map<String, List<Pattern>> CATEGORY_PATTERNS = new LinkedHashMap<>() {{
        // 1. Groceries
        put("Groceries", Arrays.asList(
                Pattern.compile("\\bspar\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bcentra\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("raheen\\s*super", Pattern.CASE_INSENSITIVE),
                Pattern.compile("dunnes", Pattern.CASE_INSENSITIVE),
                Pattern.compile("tesco|lidl|aldi|supervalu", Pattern.CASE_INSENSITIVE),
                Pattern.compile("m&s\\s*food|marks.*spencer.*food", Pattern.CASE_INSENSITIVE),
                Pattern.compile("supermarket|grocery", Pattern.CASE_INSENSITIVE)
        ));

        // 2. Eating Out (merged: Dining & Takeaway + Coffee & Snacks)
        put("Eating Out", Arrays.asList(
                Pattern.compile("just\\s*eat", Pattern.CASE_INSENSITIVE),
                Pattern.compile("deliveroo|uber\\s*eats", Pattern.CASE_INSENSITIVE),
                Pattern.compile("pizza|pizzer", Pattern.CASE_INSENSITIVE),
                Pattern.compile("supermacs", Pattern.CASE_INSENSITIVE),
                Pattern.compile("dominos|apache\\s*pizza", Pattern.CASE_INSENSITIVE),
                Pattern.compile("chippy|chipper", Pattern.CASE_INSENSITIVE),
                Pattern.compile("restaurant|bistro", Pattern.CASE_INSENSITIVE),
                Pattern.compile("azzurro", Pattern.CASE_INSENSITIVE),
                Pattern.compile("doolys", Pattern.CASE_INSENSITIVE),
                Pattern.compile("takeaway|take\\s*away", Pattern.CASE_INSENSITIVE),
                Pattern.compile("aramark", Pattern.CASE_INSENSITIVE),
                Pattern.compile("planters\\s*cof", Pattern.CASE_INSENSITIVE),
                Pattern.compile("arena\\s*5", Pattern.CASE_INSENSITIVE),
                Pattern.compile("starbucks|costa|nero", Pattern.CASE_INSENSITIVE),
                Pattern.compile("coffee|\\bcafe\\b", Pattern.CASE_INSENSITIVE)
        ));

        // 3. Transport (renamed from Fuel & Transport, expanded)
        put("Transport", Arrays.asList(
                Pattern.compile("top\\s*oil", Pattern.CASE_INSENSITIVE),
                Pattern.compile("circle\\s*k", Pattern.CASE_INSENSITIVE),
                Pattern.compile("inver\\s*energy", Pattern.CASE_INSENSITIVE),
                Pattern.compile("emo\\s*filling", Pattern.CASE_INSENSITIVE),
                Pattern.compile("petrol|diesel|fuel|gas\\s*station", Pattern.CASE_INSENSITIVE),
                Pattern.compile("shell|\\bbp\\b|texaco|esso", Pattern.CASE_INSENSITIVE),
                Pattern.compile("leap\\s*card", Pattern.CASE_INSENSITIVE),
                Pattern.compile("irish\\s*rail|iarnrod", Pattern.CASE_INSENSITIVE),
                Pattern.compile("bus\\s*eireann|dublin\\s*bus", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bluas\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("freenow|free\\s*now|\\btaxi\\b", Pattern.CASE_INSENSITIVE)
        ));

        // 4. Shopping (renamed from Shopping & Retail, expanded)
        put("Shopping", Arrays.asList(
                Pattern.compile("john\\s*david", Pattern.CASE_INSENSITIVE),
                Pattern.compile("body\\s*bui", Pattern.CASE_INSENSITIVE),
                Pattern.compile("fine\\s*wines", Pattern.CASE_INSENSITIVE),
                Pattern.compile("south\\s*court", Pattern.CASE_INSENSITIVE),
                Pattern.compile("amazon|ebay", Pattern.CASE_INSENSITIVE),
                Pattern.compile("penneys|primark", Pattern.CASE_INSENSITIVE),
                Pattern.compile("tk\\s*maxx", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bzara\\b|\\bh&m\\b|\\basos\\b|shein", Pattern.CASE_INSENSITIVE),
                Pattern.compile("soundstore", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bshop\\b|retail", Pattern.CASE_INSENSITIVE)
        ));

        // 5. Bills & Utilities (merged: Bank Fees into this)
        put("Bills & Utilities", Arrays.asList(
                Pattern.compile("electric\\s*ireland", Pattern.CASE_INSENSITIVE),
                Pattern.compile("bord\\s*gais|bord\\s*g.is", Pattern.CASE_INSENSITIVE),
                Pattern.compile("sse\\s*airtricity", Pattern.CASE_INSENSITIVE),
                Pattern.compile("irish\\s*water", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\beir\\b|virgin\\s*media", Pattern.CASE_INSENSITIVE),
                Pattern.compile("utility|electric|\\bgas\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("monthly\\s*fee|bank\\s*fee", Pattern.CASE_INSENSITIVE),
                Pattern.compile("explore\\s*monthly", Pattern.CASE_INSENSITIVE),
                Pattern.compile("debit\\s*card\\s*charge", Pattern.CASE_INSENSITIVE),
                Pattern.compile("overdraft|interest\\s*charge", Pattern.CASE_INSENSITIVE)
        ));

        // 6. Subscriptions
        put("Subscriptions", Arrays.asList(
                Pattern.compile("netflix|spotify|disney|prime", Pattern.CASE_INSENSITIVE),
                Pattern.compile("serato|adobe|microsoft|apple", Pattern.CASE_INSENSITIVE),
                Pattern.compile("gym\\s*membership|\\bgym\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("subscription", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bdd\\b|direct\\s*debit", Pattern.CASE_INSENSITIVE),
                Pattern.compile("vodafone|o2|three|meteor", Pattern.CASE_INSENSITIVE)
        ));

        // 7. Entertainment (merged: Bars & Nightlife + Entertainment & Recreation)
        put("Entertainment", Arrays.asList(
                Pattern.compile("russell'?s?\\s*bar", Pattern.CASE_INSENSITIVE),
                Pattern.compile("westward\\s*ho", Pattern.CASE_INSENSITIVE),
                Pattern.compile("stables\\s*club", Pattern.CASE_INSENSITIVE),
                Pattern.compile("crew\\s*(bar|brewing)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("powers\\s*bar", Pattern.CASE_INSENSITIVE),
                Pattern.compile("the\\s*black\\s*ra", Pattern.CASE_INSENSITIVE),
                Pattern.compile("the\\s*strand\\s*i", Pattern.CASE_INSENSITIVE),
                Pattern.compile("the\\s*hurl", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bbar\\b|\\bpub\\b|nightclub", Pattern.CASE_INSENSITIVE),
                Pattern.compile("molly\\s*malone", Pattern.CASE_INSENSITIVE),
                Pattern.compile("garryowen", Pattern.CASE_INSENSITIVE),
                Pattern.compile("cinema|movie|theatre", Pattern.CASE_INSENSITIVE),
                Pattern.compile("ticketmaster|concert|gig", Pattern.CASE_INSENSITIVE),
                Pattern.compile("bowling|arcade", Pattern.CASE_INSENSITIVE)
        ));

        // 8. Health (new)
        put("Health", Arrays.asList(
                Pattern.compile("boots|pharmacy|chemist", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bgp\\b|doctor|medical", Pattern.CASE_INSENSITIVE),
                Pattern.compile("dentist|dental", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bvhi\\b|laya|irish\\s*life\\s*health", Pattern.CASE_INSENSITIVE),
                Pattern.compile("hospital|clinic", Pattern.CASE_INSENSITIVE),
                Pattern.compile("optician|specsavers", Pattern.CASE_INSENSITIVE)
        ));

        // 9. Housing (new)
        put("Housing", Arrays.asList(
                Pattern.compile("\\brent\\b|landlord", Pattern.CASE_INSENSITIVE),
                Pattern.compile("mortgage", Pattern.CASE_INSENSITIVE),
                Pattern.compile("home\\s*insurance|house\\s*insurance", Pattern.CASE_INSENSITIVE),
                Pattern.compile("property\\s*management|property\\s*tax", Pattern.CASE_INSENSITIVE),
                Pattern.compile("maintenance|plumber|electrician", Pattern.CASE_INSENSITIVE)
        ));

        // 10. Travel (new)
        put("Travel", Arrays.asList(
                Pattern.compile("ryanair|aer\\s*lingus", Pattern.CASE_INSENSITIVE),
                Pattern.compile("booking\\.com|booking\\s*com", Pattern.CASE_INSENSITIVE),
                Pattern.compile("airbnb|\\bhotel\\b|hostel", Pattern.CASE_INSENSITIVE),
                Pattern.compile("airport|duty\\s*free", Pattern.CASE_INSENSITIVE),
                Pattern.compile("travel|holiday|flight", Pattern.CASE_INSENSITIVE)
        ));

        // 11. Personal Care (new)
        put("Personal Care", Arrays.asList(
                Pattern.compile("barber|hairdresser|hair\\s*salon", Pattern.CASE_INSENSITIVE),
                Pattern.compile("beauty|salon|\\bspa\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("nail|wax|grooming", Pattern.CASE_INSENSITIVE)
        ));

        // 12. Education (new)
        put("Education", Arrays.asList(
                Pattern.compile("college|university|\\bUL\\b|\\bUCD\\b|\\bTUD\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("udemy|coursera|skillshare", Pattern.CASE_INSENSITIVE),
                Pattern.compile("tuition|student|\\bfees\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("books|bookshop|bookstore", Pattern.CASE_INSENSITIVE)
        ));

        // 13. Investment (new)
        put("Investment", Arrays.asList(
                Pattern.compile("trading\\s*212|degiro|etoro", Pattern.CASE_INSENSITIVE),
                Pattern.compile("coinbase|binance|crypto", Pattern.CASE_INSENSITIVE),
                Pattern.compile("tranio\\s*investments", Pattern.CASE_INSENSITIVE),
                Pattern.compile("pension|\\bfund\\b|\\bshares\\b", Pattern.CASE_INSENSITIVE)
        ));

        // 14. Transfers (merged: Cash Withdrawals into this)
        put("Transfers", Arrays.asList(
                Pattern.compile("\\bct\\b|credit\\s*transfer", Pattern.CASE_INSENSITIVE),
                Pattern.compile("philip|dad|mam|mom", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\batm\\b|cash\\s*withdrawal", Pattern.CASE_INSENSITIVE),
                Pattern.compile("123\\s*money", Pattern.CASE_INSENSITIVE),
                Pattern.compile("revolut|bank\\s*transfer", Pattern.CASE_INSENSITIVE),
                Pattern.compile("pos.*\\d{3,}", Pattern.CASE_INSENSITIVE)
        ));
    }};

    /**
     * Categorize transactions and calculate spending analysis.
     * Returns categorized data with totals, subscriptions, and insights.
     */
    public static CategoryAnalysis categorizeTransactions(String transactionsJson) {
        CategoryAnalysis analysis = new CategoryAnalysis();

        try {
            JsonNode transactions = objectMapper.readTree(transactionsJson);
            Map<String, Double> categoryTotals = new LinkedHashMap<>();
            Map<String, Integer> categoryCounts = new LinkedHashMap<>();
            Map<String, MerchantData> merchantTracker = new HashMap<>();

            // Initialize category maps
            Map<String, List<TransactionDetail>> categoryTransactions = new LinkedHashMap<>();
            for (String category : CATEGORY_PATTERNS.keySet()) {
                categoryTotals.put(category, 0.0);
                categoryCounts.put(category, 0);
                categoryTransactions.put(category, new ArrayList<>());
            }
            categoryTotals.put("Other", 0.0);
            categoryCounts.put("Other", 0);
            categoryTransactions.put("Other", new ArrayList<>());

            // Process each transaction
            for (JsonNode txn : transactions) {
                String description = txn.has("description") ? txn.get("description").asText() : "";
                String date = txn.has("date") ? txn.get("date").asText() : "";

                // Look for amount field (Textract normalizes headers to lowercase "amount")
                String amountStr = "";
                if (txn.has("amount")) {
                    amountStr = txn.get("amount").asText();
                } else if (txn.has("Withdrawn")) {
                    amountStr = txn.get("Withdrawn").asText();
                } else if (txn.has("withdrawn")) {
                    amountStr = txn.get("withdrawn").asText();
                } else if (txn.has("debit")) {
                    amountStr = txn.get("debit").asText();
                }

                // Only process debits (withdrawn amounts)
                if (amountStr == null || amountStr.isEmpty() || amountStr.isBlank() || amountStr.equals("null")) {
                    continue;
                }

                double amount;
                try {
                    amount = Double.parseDouble(amountStr.replace(",", ""));
                } catch (NumberFormatException e) {
                    continue;
                }

                // Categorize transaction
                String category = categorizeTransaction(description);
                categoryTotals.put(category, categoryTotals.get(category) + amount);
                categoryCounts.put(category, categoryCounts.get(category) + 1);
                categoryTransactions.get(category).add(new TransactionDetail(description, amount, date, category));

                // Track merchant for subscription detection
                trackMerchant(merchantTracker, description, amount, date);
            }

            // Build analysis
            analysis.categoryTotals = categoryTotals;
            analysis.categoryCounts = categoryCounts;
            analysis.categoryTransactions = categoryTransactions;
            analysis.subscriptions = detectSubscriptions(merchantTracker);
            analysis.biggestCategory = findBiggestCategory(categoryTotals);
            analysis.totalSpent = categoryTotals.values().stream()
                    .mapToDouble(Double::doubleValue).sum();

        } catch (Exception e) {
            System.err.println("Error categorizing transactions: " + e.getMessage());
            e.printStackTrace();
        }

        return analysis;
    }

    /**
     * Categorize a single transaction based on description.
     */
    private static String categorizeTransaction(String description) {
        for (Map.Entry<String, List<Pattern>> entry : CATEGORY_PATTERNS.entrySet()) {
            String category = entry.getKey();
            List<Pattern> patterns = entry.getValue();

            for (Pattern pattern : patterns) {
                if (pattern.matcher(description).find()) {
                    return category;
                }
            }
        }
        return "Other";
    }

    /**
     * Track merchant occurrences for subscription detection.
     */
    private static void trackMerchant(Map<String, MerchantData> tracker, String description, double amount, String date) {
        // Extract merchant name (simplified - take first 20 chars or until special char)
        String merchant = description.replaceAll("[^a-zA-Z0-9\\s]", " ")
                .trim()
                .split("\\s+")[0];

        if (merchant.length() < 3) {
            return; // Skip very short merchant names
        }

        tracker.computeIfAbsent(merchant, k -> new MerchantData(merchant))
                .addTransaction(amount, date);
    }

    /**
     * Detect recurring subscriptions based on merchant patterns.
     */
    private static List<Subscription> detectSubscriptions(Map<String, MerchantData> merchantTracker) {
        List<Subscription> subscriptions = new ArrayList<>();

        for (MerchantData data : merchantTracker.values()) {
            // Consider it a subscription if:
            // 1. Appears 2+ times
            // 2. Average amount is consistent (within 20% variance)
            if (data.count >= 2 && data.hasConsistentAmount()) {
                subscriptions.add(new Subscription(
                        data.merchant,
                        data.getAverageAmount(),
                        data.count,
                        "monthly" // Simplified - could calculate actual frequency
                ));
            }
        }

        // Sort by amount (highest first)
        subscriptions.sort((a, b) -> Double.compare(b.amount, a.amount));

        return subscriptions;
    }

    /**
     * Find the category with the highest spending.
     */
    private static String findBiggestCategory(Map<String, Double> categoryTotals) {
        return categoryTotals.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");
    }

    /**
     * Valid categories for LLM re-categorization (excludes "Other").
     */
    private static final List<String> VALID_CATEGORIES = List.of(
            "Groceries", "Eating Out", "Transport", "Shopping", "Bills & Utilities",
            "Subscriptions", "Entertainment", "Health", "Housing", "Travel",
            "Personal Care", "Education", "Investment", "Transfers"
    );

    /**
     * Re-categorize "Other" transactions using LLM (Bedrock/Claude).
     * Batches uncategorized transactions and asks Claude to assign categories.
     * On failure, transactions remain as "Other" (graceful degradation).
     */
    public static void recategorizeWithLLM(CategoryAnalysis analysis, LambdaBedrockService bedrockService) {
        List<TransactionDetail> otherTxns = analysis.categoryTransactions.get("Other");
        if (otherTxns == null || otherTxns.isEmpty()) {
            System.out.println("[LLM Fallback] No 'Other' transactions to recategorize");
            return;
        }

        System.out.println("[LLM Fallback] Attempting to recategorize " + otherTxns.size() + " transactions");

        try {
            // Build a JSON array of descriptions for Claude
            StringBuilder prompt = new StringBuilder();
            prompt.append("Categorize each of these bank transaction descriptions into exactly one of these categories:\n");
            prompt.append(String.join(", ", VALID_CATEGORIES));
            prompt.append("\n\nReturn ONLY a JSON array where each element has \"description\" and \"category\". ");
            prompt.append("If unsure, use the closest match. Do NOT invent new categories.\n\n");
            prompt.append("Transactions:\n");

            for (int i = 0; i < otherTxns.size(); i++) {
                prompt.append((i + 1) + ". " + otherTxns.get(i).description + "\n");
            }

            // Call Bedrock via the existing service
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");

            ArrayNode systemBlocks = objectMapper.createArrayNode();
            systemBlocks.addObject()
                    .put("type", "text")
                    .put("text", "You are a transaction categorizer. Respond with ONLY valid JSON, no markdown, no explanation.");
            requestBody.set("system", systemBlocks);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            userMessage.putArray("content").addObject()
                    .put("type", "text")
                    .put("text", prompt.toString());

            requestBody.put("max_tokens", 4096);
            requestBody.put("temperature", 0.0);

            // Use reflection-free approach: build and invoke directly
            software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient client =
                    software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient.builder()
                            .region(software.amazon.awssdk.regions.Region.of(System.getenv("AWS_REGION").trim()))
                            .credentialsProvider(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create())
                            .build();

            String modelId = System.getenv("BEDROCK_MODEL_ID").trim();

            software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse response = client.invokeModel(
                    software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest.builder()
                            .modelId(modelId)
                            .contentType("application/json")
                            .accept("application/json")
                            .body(software.amazon.awssdk.core.SdkBytes.fromUtf8String(
                                    objectMapper.writeValueAsString(requestBody)))
                            .build()
            );

            String responseBody = response.body().asUtf8String();
            JsonNode responseJson = objectMapper.readTree(responseBody);
            String llmText = responseJson.at("/content/0/text").asText("");

            // Strip markdown code fences if present
            llmText = llmText.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            // Parse the JSON array response
            JsonNode categorizations = objectMapper.readTree(llmText);
            if (!categorizations.isArray()) {
                System.err.println("[LLM Fallback] Response is not a JSON array, skipping");
                return;
            }

            // Build a lookup: description → new category
            Map<String, String> recategorized = new HashMap<>();
            for (JsonNode item : categorizations) {
                String desc = item.has("description") ? item.get("description").asText() : "";
                String cat = item.has("category") ? item.get("category").asText() : "";
                if (!desc.isEmpty() && !cat.isEmpty() && VALID_CATEGORIES.contains(cat)) {
                    recategorized.put(desc.toLowerCase().trim(), cat);
                }
            }

            // Move transactions from "Other" to their new categories
            List<TransactionDetail> remaining = new ArrayList<>();
            int movedCount = 0;

            for (TransactionDetail txn : otherTxns) {
                String newCategory = recategorized.get(txn.description.toLowerCase().trim());
                if (newCategory != null) {
                    // Move to new category
                    txn.category = newCategory;
                    analysis.categoryTotals.put(newCategory,
                            analysis.categoryTotals.getOrDefault(newCategory, 0.0) + txn.amount);
                    analysis.categoryCounts.put(newCategory,
                            analysis.categoryCounts.getOrDefault(newCategory, 0) + 1);
                    analysis.categoryTransactions.computeIfAbsent(newCategory, k -> new ArrayList<>()).add(txn);

                    // Subtract from Other
                    analysis.categoryTotals.put("Other",
                            analysis.categoryTotals.get("Other") - txn.amount);
                    analysis.categoryCounts.put("Other",
                            analysis.categoryCounts.get("Other") - 1);

                    movedCount++;
                } else {
                    remaining.add(txn);
                }
            }

            // Update Other's transaction list
            analysis.categoryTransactions.put("Other", remaining);

            // Recalculate biggest category
            analysis.biggestCategory = findBiggestCategory(analysis.categoryTotals);

            System.out.println("[LLM Fallback] Recategorized " + movedCount + "/" + otherTxns.size() +
                    " transactions. " + remaining.size() + " remain as Other.");

        } catch (Exception e) {
            System.err.println("[LLM Fallback] Failed (graceful degradation): " + e.getMessage());
            // Transactions stay as "Other" — no data loss
        }
    }

    /**
     * Analysis result containing all categorized data.
     */
    public static class CategoryAnalysis {
        public Map<String, Double> categoryTotals = new LinkedHashMap<>();
        public Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        public Map<String, List<TransactionDetail>> categoryTransactions = new LinkedHashMap<>();
        public List<Subscription> subscriptions = new ArrayList<>();
        public String biggestCategory = "None";
        public double totalSpent = 0.0;
    }

    /**
     * Detail of an individual transaction within a category.
     */
    public static class TransactionDetail {
        public String description;
        public double amount;
        public String date;
        public String category;

        public TransactionDetail(String description, double amount, String date, String category) {
            this.description = description;
            this.amount = amount;
            this.date = date;
            this.category = category;
        }
    }

    /**
     * Detected subscription.
     */
    public static class Subscription {
        public String merchant;
        public double amount;
        public int occurrences;
        public String frequency;

        public Subscription(String merchant, double amount, int occurrences, String frequency) {
            this.merchant = merchant;
            this.amount = amount;
            this.occurrences = occurrences;
            this.frequency = frequency;
        }
    }

    /**
     * Tracks merchant data for subscription detection.
     */
    private static class MerchantData {
        String merchant;
        List<Double> amounts = new ArrayList<>();
        List<String> dates = new ArrayList<>();
        int count = 0;

        MerchantData(String merchant) {
            this.merchant = merchant;
        }

        void addTransaction(double amount, String date) {
            amounts.add(amount);
            dates.add(date);
            count++;
        }

        double getAverageAmount() {
            return amounts.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        boolean hasConsistentAmount() {
            if (amounts.size() < 2) return false;
            double avg = getAverageAmount();
            double maxVariance = avg * 0.2; // 20% variance allowed

            for (double amount : amounts) {
                if (Math.abs(amount - avg) > maxVariance) {
                    return false;
                }
            }
            return true;
        }
    }
}
