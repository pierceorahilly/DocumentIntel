package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AWS Lambda handler for authenticated PDF upload and processing.
 * Requires Cognito authorization - userId extracted from JWT.
 */
public class AuthenticatedUploadHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UserSpecificStorageService storageService;
    private final LambdaTextractService textractService;
    private final LambdaBedrockService bedrockService;
    private final UserService userService;
    private final UploadService uploadService;
    private final TransactionService transactionService;
    private final ContactRequestService contactRequestService;
    private final ObjectMapper objectMapper;

    public AuthenticatedUploadHandler() {
        String bucket = getEnvOrThrow("S3_BUCKET");
        String region = getEnvOrThrow("AWS_REGION");
        String modelId = getEnvOrThrow("BEDROCK_MODEL_ID");

        this.storageService = new UserSpecificStorageService(bucket, region);
        this.textractService = new LambdaTextractService(region);
        this.bedrockService = new LambdaBedrockService(region, modelId);
        this.userService = new UserService(region);
        this.uploadService = new UploadService(region);
        this.transactionService = new TransactionService(region);
        this.contactRequestService = new ContactRequestService(region);
        this.objectMapper = new ObjectMapper();

        System.out.println("[AuthenticatedUploadHandler] Initialized");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        long startTime = System.currentTimeMillis();

        try {
            // Extract userId from Cognito authorizer context
            String userId = extractUserId(request);
            if (userId == null || userId.isEmpty()) {
                return errorResponse(401, "Unauthorized - Missing userId");
            }

            System.out.println("Processing upload for user: " + userId);

            // Verify user exists in DynamoDB
            if (!userService.userExists(userId)) {
                return errorResponse(404, "User not found. Please contact support.");
            }

            // Extract PDF from request
            byte[] pdfBytes = extractPdfFromRequest(request);
            if (pdfBytes == null || pdfBytes.length == 0) {
                return errorResponse(400, "No PDF file received or file is empty");
            }

            // Validate PDF
            if (!isPdf(pdfBytes)) {
                return errorResponse(415, "File is not a valid PDF");
            }

            String filename = extractFilename(request);
            String uploadId = "upload-" + UUID.randomUUID();

            System.out.println("Upload ID: " + uploadId + ", File: " + filename + " (" + pdfBytes.length + " bytes)");

            // Create upload record in DynamoDB (status: processing)
            uploadService.createUpload(uploadId, userId, filename, pdfBytes.length);

            try {
                // 1. Upload PDF to user-specific S3 path
                String s3PdfUrl = storageService.uploadPdfForUser(userId, uploadId, pdfBytes, filename);
                System.out.println("✅ Uploaded to S3: " + s3PdfUrl);

                // 2. Extract S3 bucket and key
                String[] parts = s3PdfUrl.substring("s3://".length()).split("/", 2);
                String bucket = parts[0];
                String key = parts[1];

                // 3. Extract transactions with Textract
                List<List<List<String>>> tables = textractService.analyzeTables(bucket, key);
                List<Map<String, String>> transactions = LambdaTextractService.mapTransactions(tables);

                String transactionsJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(transactions);

                System.out.println("✅ Extracted " + transactions.size() + " transactions");

                // 4. Categorize transactions and detect subscriptions
                TransactionCategorizer.CategoryAnalysis categoryAnalysis =
                        TransactionCategorizer.categorizeTransactions(transactionsJson);
                String categoryAnalysisJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(categoryAnalysis);
                System.out.println("✅ Categorized transactions - Biggest category: " + categoryAnalysis.biggestCategory);

                // 5. Detect bills and flag overspending (deterministic — must run before Bedrock)
                Map<String, AttributeValue> userData = userService.getUser(userId);
                String dateOfBirth = userData.containsKey("dateOfBirth") ? userData.get("dateOfBirth").s() : "";

                List<BillFlagService.DetectedBill> detectedBills = BillFlagService.detectAllBills(transactionsJson);
                String detectedBillsJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(detectedBills);

                List<BillFlagService.BillFlag> billFlags = BillFlagService.flagBills(transactionsJson, dateOfBirth);
                String billFlagsJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(billFlags);

                System.out.println("✅ Detected " + detectedBills.size() + " bill(s), flagged " + billFlags.size());

                // 6. Get savings advice from Claude (DISABLED — using deterministic services only)
                String advice = "LLM disabled — using deterministic bill detection and categorisation only.";
                // TODO: Re-enable when ready:
                // try {
                //     advice = bedrockService.suggestSavings(transactionsJson, categoryAnalysisJson, detectedBillsJson, billFlagsJson);
                //     System.out.println("✅ Generated savings advice");
                // } catch (Exception e) {
                //     System.err.println("⚠️ Bedrock failed: " + e.getMessage());
                //     advice = "{\"error\":\"bedrock_failed\",\"message\":\"" + e.getMessage() + "\"}";
                // }

                // 7. Save results to S3
                String s3TransactionsUrl = storageService.saveResultForUser(userId, uploadId, "transactions.json", transactionsJson);
                String s3AdviceUrl = storageService.saveResultForUser(userId, uploadId, "advice.json", advice);
                String s3CategoryUrl = storageService.saveResultForUser(userId, uploadId, "category-analysis.json", categoryAnalysisJson);

                System.out.println("✅ Saved results to S3 (including category analysis)");

                // 8. Save transactions to DynamoDB
                transactionService.saveTransactions(userId, uploadId, transactions);

                // 9. Update upload record (status: completed)
                double processingTime = (System.currentTimeMillis() - startTime) / 1000.0;
                uploadService.completeUpload(uploadId, s3PdfUrl, s3TransactionsUrl, s3AdviceUrl, s3CategoryUrl,
                        transactions.size(), processingTime);

                // 10. Update user counters
                userService.incrementCounters(userId, transactions.size());

                // 11. Detect and update providers (telephone, energy, internet, etc.)
                Map<String, String> detectedProviders = ProviderDetectionService.detectProviders(transactionsJson);
                if (!detectedProviders.isEmpty()) {
                    userService.updateProviders(userId, detectedProviders);
                }

                // 12. Save bill flags to S3 (already computed in step 5)
                if (!billFlags.isEmpty()) {
                    String s3BillFlagsUrl = storageService.saveResultForUser(userId, uploadId, "bill-flags.json", billFlagsJson);
                    uploadService.updateBillFlagsUrl(uploadId, s3BillFlagsUrl);
                    System.out.println("⚠️ " + billFlags.size() + " bill(s) flagged for user " + userId);
                }

                // 13. Store recent 100 transactions in Users table for quick access
                String recentTransactionsJson = ProviderDetectionService.getRecentTransactionsJson(transactionsJson, 100);
                userService.updateRecentTransactions(userId, recentTransactionsJson);

                // 14. Calculate and update total spending
                double totalSpending = ProviderDetectionService.calculateTotalSpending(transactionsJson);
                if (totalSpending != 0) {
                    userService.updateTotalSpending(userId, totalSpending);
                }

                // 15. Build response
                Map<String, Object> responseBody = new LinkedHashMap<>();
                responseBody.put("uploadId", uploadId);
                responseBody.put("s3PdfUrl", s3PdfUrl);
                responseBody.put("transactionCount", transactions.size());
                responseBody.put("processingTime", processingTime);
                responseBody.put("transactions", transactions);
                responseBody.put("advice", advice);

                // Include bill flags so the UI can show targeted advice + contact prompt
                if (!billFlags.isEmpty()) {
                    responseBody.put("billFlags", billFlags);
                    responseBody.put("contactPrompt", true);
                }

                return successResponse(responseBody);

            } catch (Exception e) {
                // Mark upload as failed
                uploadService.failUpload(uploadId, e.getMessage());
                throw e;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorResponse(500, "Processing interrupted: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(500, "Server error: " + e.getMessage());
        }
    }

    /**
     * Extract userId (sub claim) from Cognito authorizer context.
     */
    private String extractUserId(APIGatewayProxyRequestEvent request) {
        try {
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            if (authorizer == null) {
                System.err.println("No authorizer context found");
                return null;
            }

            // Cognito authorizer puts claims in authorizer.claims
            @SuppressWarnings("unchecked")
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            if (claims == null) {
                System.err.println("No claims found in authorizer");
                return null;
            }

            return claims.get("sub");  // Cognito user ID

        } catch (Exception e) {
            System.err.println("Error extracting userId: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract PDF bytes from API Gateway request.
     */
    private byte[] extractPdfFromRequest(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        if (body == null || body.isEmpty()) {
            return null;
        }

        // If request is base64 encoded
        if (Boolean.TRUE.equals(request.getIsBase64Encoded())) {
            return Base64.getDecoder().decode(body);
        }

        // Try to decode as base64
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            // Assume raw bytes
            return body.getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * Extract filename from request headers or generate default.
     */
    private String extractFilename(APIGatewayProxyRequestEvent request) {
        Map<String, String> headers = request.getHeaders();
        if (headers != null) {
            String contentDisposition = headers.get("Content-Disposition");
            if (contentDisposition != null && contentDisposition.contains("filename=")) {
                String[] parts = contentDisposition.split("filename=");
                if (parts.length > 1) {
                    return parts[1].replaceAll("\"", "").trim();
                }
            }

            String filename = headers.get("X-Filename");
            if (filename != null && !filename.isEmpty()) {
                return filename;
            }
        }

        return "statement.pdf";
    }

    /**
     * Check if bytes start with PDF magic header.
     */
    private boolean isPdf(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }

    private APIGatewayProxyResponseEvent successResponse(Object body) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "Access-Control-Allow-Origin", "*"
                    ))
                    .withBody(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            return errorResponse(500, "Failed to serialize response: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent errorResponse(int statusCode, String message) {
        Map<String, String> error = Map.of("error", message);
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "Access-Control-Allow-Origin", "*"
                    ))
                    .withBody(objectMapper.writeValueAsString(error));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withBody("{\"error\":\"" + message + "\"}");
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
