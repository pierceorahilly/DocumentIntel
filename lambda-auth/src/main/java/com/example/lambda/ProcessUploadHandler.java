package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQS-triggered handler that processes uploaded PDFs in the background.
 * Runs Textract, Bedrock, and saves results to DynamoDB.
 */
public class ProcessUploadHandler implements RequestHandler<SQSEvent, Void> {

    private final LambdaTextractService textractService;
    private final LambdaBedrockService bedrockService;
    private final UserService userService;
    private final UploadService uploadService;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;
    private final String bucket;

    public ProcessUploadHandler() {
        this.bucket = getEnvOrThrow("S3_BUCKET");
        String region = getEnvOrThrow("AWS_REGION");
        String modelId = getEnvOrThrow("BEDROCK_MODEL_ID");

        this.textractService = new LambdaTextractService(region);
        this.bedrockService = new LambdaBedrockService(region, modelId);
        this.userService = new UserService(region);
        this.uploadService = new UploadService(region);
        this.transactionService = new TransactionService(region);
        this.objectMapper = new ObjectMapper();

        System.out.println("[ProcessUploadHandler] Initialized");
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                processMessage(message.getBody());
            } catch (Exception e) {
                System.err.println("Failed to process message: " + e.getMessage());
                e.printStackTrace();
                // Note: Message will be retried by SQS or sent to DLQ
            }
        }
        return null;
    }

    private void processMessage(String messageBody) throws Exception {
        long startTime = System.currentTimeMillis();

        // Parse message
        @SuppressWarnings("unchecked")
        Map<String, String> message = objectMapper.readValue(messageBody, Map.class);

        String uploadId = message.get("uploadId");
        String userId = message.get("userId");
        String s3PdfUrl = message.get("s3PdfUrl");
        String filename = message.get("filename");

        System.out.println("Processing upload: " + uploadId + " for user: " + userId);

        try {
            // Extract bucket and key from S3 URL
            String[] parts = s3PdfUrl.substring("s3://".length()).split("/", 2);
            String bucket = parts[0];
            String key = parts[1];

            // 1. Extract transactions with Textract
            List<List<List<String>>> tables = textractService.analyzeTables(bucket, key);
            List<Map<String, String>> transactions = LambdaTextractService.mapTransactions(tables);

            String transactionsJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(transactions);

            System.out.println("✅ Extracted " + transactions.size() + " transactions");

            // 2. Categorize transactions and detect subscriptions
            TransactionCategorizer.CategoryAnalysis categoryAnalysis =
                    TransactionCategorizer.categorizeTransactions(transactionsJson);
            String categoryAnalysisJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(categoryAnalysis);
            System.out.println("✅ Categorized transactions - Biggest category: " + categoryAnalysis.biggestCategory);

            // 2b. LLM fallback: DISABLED — was timing out; transactions stay as "Other"
            // int otherCount = categoryAnalysis.categoryCounts.getOrDefault("Other", 0);
            // if (otherCount > 0) {
            //     System.out.println("⏳ Running LLM fallback for " + otherCount + " uncategorized transactions...");
            //     TransactionCategorizer.recategorizeWithLLM(categoryAnalysis, bedrockService);
            //     // Re-serialize after LLM recategorization
            //     categoryAnalysisJson = objectMapper.writerWithDefaultPrettyPrinter()
            //             .writeValueAsString(categoryAnalysis);
            // }

            // 3. Detect bills and flag overspending (deterministic — must run before Bedrock)
            Map<String, AttributeValue> userData = userService.getUser(userId);
            String dateOfBirth = userData.containsKey("dateOfBirth") ? userData.get("dateOfBirth").s() : "";

            List<BillFlagService.DetectedBill> detectedBills = BillFlagService.detectAllBills(transactionsJson);
            String detectedBillsJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(detectedBills);

            List<BillFlagService.BillFlag> billFlags = BillFlagService.flagBills(transactionsJson, dateOfBirth);
            String billFlagsJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(billFlags);

            System.out.println("✅ Detected " + detectedBills.size() + " bill(s), flagged " + billFlags.size());

            // 4. Get savings advice from Claude (DISABLED — using deterministic services only)
            String advice = "LLM disabled — using deterministic bill detection and categorisation only.";
            // TODO: Re-enable when ready:
            // try {
            //     advice = bedrockService.suggestSavings(transactionsJson, categoryAnalysisJson, detectedBillsJson, billFlagsJson);
            //     System.out.println("✅ Generated savings advice");
            // } catch (Exception e) {
            //     System.err.println("⚠️ Bedrock failed: " + e.getMessage());
            //     advice = "{\"error\":\"bedrock_failed\",\"message\":\"" + e.getMessage() + "\"}";
            // }

            // 5. Save results to S3
            UserSpecificStorageService storageService = new UserSpecificStorageService(this.bucket, "eu-west-1");
            String s3TransactionsUrl = storageService.saveResultForUser(userId, uploadId, "transactions.json", transactionsJson);
            String s3AdviceUrl = storageService.saveResultForUser(userId, uploadId, "advice.json", advice);
            String s3CategoryUrl = storageService.saveResultForUser(userId, uploadId, "category-analysis.json", categoryAnalysisJson);

            System.out.println("✅ Saved results to S3 (including category analysis)");

            // 6. Save transactions to DynamoDB
            transactionService.saveTransactions(userId, uploadId, transactions);

            // 7. Update upload record (status: completed)
            double processingTime = (System.currentTimeMillis() - startTime) / 1000.0;
            uploadService.completeUpload(uploadId, s3PdfUrl, s3TransactionsUrl, s3AdviceUrl, s3CategoryUrl,
                    transactions.size(), processingTime);

            // 8. Update user counters
            userService.incrementCounters(userId, transactions.size());

            // 9. Detect and update providers
            Map<String, String> detectedProviders = ProviderDetectionService.detectProviders(transactionsJson);
            if (!detectedProviders.isEmpty()) {
                userService.updateProviders(userId, detectedProviders);
            }

            // 10. Save bill flags to S3 (already computed in step 3)
            if (!billFlags.isEmpty()) {
                String s3BillFlagsUrl = storageService.saveResultForUser(userId, uploadId, "bill-flags.json", billFlagsJson);
                uploadService.updateBillFlagsUrl(uploadId, s3BillFlagsUrl);
                System.out.println("⚠️ " + billFlags.size() + " bill(s) flagged for user " + userId);
            }

            // 11. Store recent transactions
            String recentTransactionsJson = ProviderDetectionService.getRecentTransactionsJson(transactionsJson, 100);
            userService.updateRecentTransactions(userId, recentTransactionsJson);

            // 12. Calculate and update total spending
            double totalSpending = ProviderDetectionService.calculateTotalSpending(transactionsJson);
            if (totalSpending != 0) {
                userService.updateTotalSpending(userId, totalSpending);
            }

            System.out.println("✅ Successfully processed upload: " + uploadId + " in " + processingTime + "s");

        } catch (Exception e) {
            // Mark upload as failed
            uploadService.failUpload(uploadId, e.getMessage());
            throw e;
        }
    }

    private static String getEnvOrThrow(String key) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}
