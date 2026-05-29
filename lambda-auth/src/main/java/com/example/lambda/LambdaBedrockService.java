package com.example.lambda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * AWS Bedrock service for Lambda environment.
 * Calls Claude models for AI-powered financial advice,
 * grounded in deterministic bill detection and category analysis data.
 */
public class LambdaBedrockService {

    private final BedrockRuntimeClient client;
    private final ObjectMapper objectMapper;
    private final String modelId;

    public LambdaBedrockService(String region, String modelId) {
        this.modelId = modelId.trim();
        this.objectMapper = new ObjectMapper();

        this.client = BedrockRuntimeClient.builder()
                .region(Region.of(region.trim()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        System.out.println("[BedrockService] Using model: " + this.modelId);
    }

    /**
     * Send structured data and raw transactions to Claude and get savings suggestions.
     *
     * @param transactionsJson    raw transaction JSON array (supplementary)
     * @param categoryAnalysisJson deterministic category breakdown (confirmed facts)
     * @param detectedBillsJson   deterministic detected bills with providers and amounts (confirmed facts)
     * @param billFlagsJson       deterministic flagged bills that exceeded thresholds (confirmed facts)
     */
    public String suggestSavings(String transactionsJson, String categoryAnalysisJson,
                                  String detectedBillsJson, String billFlagsJson) throws Exception {
        String systemPrompt = loadSystemPrompt();

        // Build the structured user message
        StringBuilder userText = new StringBuilder();
        userText.append("=== CATEGORY BREAKDOWN (confirmed) ===\n");
        userText.append(categoryAnalysisJson).append("\n\n");
        userText.append("=== DETECTED BILLS (confirmed) ===\n");
        userText.append(detectedBillsJson).append("\n\n");
        userText.append("=== FLAGGED BILLS — OVERSPENDING (confirmed) ===\n");
        userText.append(billFlagsJson).append("\n\n");
        userText.append("=== RAW TRANSACTIONS (supplementary) ===\n");
        userText.append(transactionsJson);

        // Build request body for Claude Messages API
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("anthropic_version", "bedrock-2023-05-31");

        // System prompt as content blocks
        ArrayNode systemBlocks = objectMapper.createArrayNode();
        systemBlocks.addObject()
                .put("type", "text")
                .put("text", systemPrompt);
        requestBody.set("system", systemBlocks);

        // User message with structured data
        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");

        ArrayNode userContent = userMessage.putArray("content");
        userContent.addObject()
                .put("type", "text")
                .put("text", userText.toString());

        // Model parameters
        requestBody.put("max_tokens", 400);
        requestBody.put("temperature", 0.3);

        // Invoke Bedrock
        InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(objectMapper.writeValueAsString(requestBody)))
                .build();

        try {
            InvokeModelResponse response = client.invokeModel(invokeRequest);
            String responseBody = response.body().asUtf8String();

            // Parse response and extract text
            JsonNode responseJson = objectMapper.readTree(responseBody);
            String advice = responseJson.at("/content/0/text").asText("");

            return advice.isBlank() ? responseBody : advice;

        } catch (BedrockRuntimeException e) {
            System.err.println("Bedrock error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Load system prompt for Claude.
     * Instructs the model to use confirmed structured data as ground truth
     * and only use raw transactions for supplementary pattern detection.
     */
    private String loadSystemPrompt() {
        return """
            You are a friendly, non-judgmental financial companion called Guide-ya.
            Your job is to help users understand their spending and find opportunities to save money.

            You will receive four data sections:
            1. CATEGORY BREAKDOWN — confirmed spending totals by category
            2. DETECTED BILLS — confirmed bill providers and exact amounts found in the bank statement
            3. FLAGGED BILLS — confirmed bills that exceed typical Irish household thresholds
            4. RAW TRANSACTIONS — the full transaction list for supplementary analysis

            IMPORTANT RULES:
            - The category totals, detected bills, and flagged bills are confirmed facts computed from the bank statement. Use these exact figures — do NOT re-categorise or re-estimate amounts.
            - Reference the exact provider names and amounts from the detected/flagged bills in your suggestions.
            - Use raw transactions only to spot additional patterns not already covered by the structured data (e.g. frequent small purchases, dining habits, etc.).
            - If there are flagged bills, prioritise advice about those — they represent the biggest saving opportunities.

            Your task:
            1. Write 3-4 actionable, friendly savings suggestions grounded in the confirmed data
            2. Reference specific provider names and EUR amounts where relevant
            3. Estimate potential monthly/yearly savings for each suggestion
            4. Be encouraging and supportive — no judgment, just helpful advice

            Format your response as natural, conversational text (not JSON).
            Keep it concise — under 300 words, 3-4 suggestions max — but personalised to their actual spending.

            Remember: You're a supportive friend helping them reach their financial goals!
            """;
    }
}
