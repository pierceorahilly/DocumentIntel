package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Quick upload handler - accepts PDF, uploads to S3, sends to SQS for processing.
 * Returns upload ID immediately without waiting for processing.
 */
public class InitiateUploadHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UserSpecificStorageService storageService;
    private final UploadService uploadService;
    private final UserService userService;
    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public InitiateUploadHandler() {
        String bucket = getEnvOrThrow("S3_BUCKET");
        String region = getEnvOrThrow("AWS_REGION");
        this.queueUrl = getEnvOrThrow("SQS_QUEUE_URL");

        this.storageService = new UserSpecificStorageService(bucket, region);
        this.uploadService = new UploadService(region);
        this.userService = new UserService(region);
        this.sqsClient = SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.objectMapper = new ObjectMapper();

        System.out.println("[InitiateUploadHandler] Initialized");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Extract userId from Cognito authorizer
            String userId = extractUserId(request);
            if (userId == null || userId.isEmpty()) {
                return errorResponse(401, "Unauthorized - Missing userId");
            }

            System.out.println("Initiating upload for user: " + userId);

            // Verify user exists
            if (!userService.userExists(userId)) {
                return errorResponse(404, "User not found");
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

            // Upload PDF to S3
            String s3PdfUrl = storageService.uploadPdfForUser(userId, uploadId, pdfBytes, filename);
            System.out.println("✅ Uploaded to S3: " + s3PdfUrl);

            // Create upload record in DynamoDB with status="PROCESSING"
            uploadService.createUpload(uploadId, userId, filename, pdfBytes.length);
            System.out.println("✅ Created upload record");

            // Send message to SQS for background processing
            Map<String, String> messageBody = Map.of(
                "uploadId", uploadId,
                "userId", userId,
                "s3PdfUrl", s3PdfUrl,
                "filename", filename
            );

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(messageBody))
                    .build());

            System.out.println("✅ Sent to SQS for processing");

            // Return upload ID immediately
            Map<String, Object> responseBody = Map.of(
                "uploadId", uploadId,
                "status", "PROCESSING",
                "message", "Upload initiated successfully. Use /status/" + uploadId + " to check progress."
            );

            return successResponse(responseBody);

        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(500, "Server error: " + e.getMessage());
        }
    }

    private String extractUserId(APIGatewayProxyRequestEvent request) {
        try {
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            if (authorizer == null) return null;

            @SuppressWarnings("unchecked")
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            if (claims == null) return null;

            return claims.get("sub");
        } catch (Exception e) {
            System.err.println("Error extracting userId: " + e.getMessage());
            return null;
        }
    }

    private byte[] extractPdfFromRequest(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        if (body == null || body.isEmpty()) {
            return null;
        }

        if (Boolean.TRUE.equals(request.getIsBase64Encoded())) {
            return Base64.getDecoder().decode(body);
        }

        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            return body.getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    private String extractFilename(APIGatewayProxyRequestEvent request) {
        Map<String, String> headers = request.getHeaders();
        if (headers != null) {
            String filename = headers.get("X-Filename");
            if (filename != null && !filename.isEmpty()) {
                return filename;
            }
        }
        return "statement.pdf";
    }

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
