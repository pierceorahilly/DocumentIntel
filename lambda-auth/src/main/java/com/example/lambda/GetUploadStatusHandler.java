package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Returns the status and results of an upload by ID.
 */
public class GetUploadStatusHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UploadService uploadService;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    public GetUploadStatusHandler() {
        String region = getEnvOrThrow("AWS_REGION");
        this.uploadService = new UploadService(region);
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.objectMapper = new ObjectMapper();

        System.out.println("[GetUploadStatusHandler] Initialized");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Extract upload ID from path parameter
            Map<String, String> pathParams = request.getPathParameters();
            if (pathParams == null || !pathParams.containsKey("uploadId")) {
                return errorResponse(400, "Missing uploadId path parameter");
            }

            String uploadId = pathParams.get("uploadId");
            System.out.println("Checking status for upload: " + uploadId);

            // Get upload record from DynamoDB
            Map<String, AttributeValue> upload = uploadService.getUpload(uploadId);

            if (upload == null || upload.isEmpty()) {
                return errorResponse(404, "Upload not found");
            }

            // Build response with upload status and data
            Map<String, Object> responseBody = uploadService.uploadToJson(upload);

            // If upload is completed, fetch advice and transactions from S3
            String status = upload.get("status") != null ? upload.get("status").s() : null;
            if ("completed".equals(status)) {
                try {
                    // Fetch advice from S3
                    String s3AdviceUrl = upload.get("s3AdviceUrl") != null ? upload.get("s3AdviceUrl").s() : null;
                    if (s3AdviceUrl != null) {
                        String advice = fetchFromS3(s3AdviceUrl);
                        responseBody.put("advice", advice);
                    }

                    // Fetch transactions from S3
                    String s3TransactionsUrl = upload.get("s3TransactionsUrl") != null ? upload.get("s3TransactionsUrl").s() : null;
                    if (s3TransactionsUrl != null) {
                        String transactionsJson = fetchFromS3(s3TransactionsUrl);
                        Object transactions = objectMapper.readValue(transactionsJson, Object.class);
                        responseBody.put("transactions", transactions);
                    }

                    // Fetch category analysis from S3
                    String s3CategoryUrl = upload.get("s3CategoryUrl") != null ? upload.get("s3CategoryUrl").s() : null;
                    if (s3CategoryUrl != null) {
                        String categoryJson = fetchFromS3(s3CategoryUrl);
                        Object categoryAnalysis = objectMapper.readValue(categoryJson, Object.class);
                        responseBody.put("categoryAnalysis", categoryAnalysis);
                    }

                    // Fetch bill flags from S3 (if any were flagged)
                    String s3BillFlagsUrl = upload.get("s3BillFlagsUrl") != null ? upload.get("s3BillFlagsUrl").s() : null;
                    if (s3BillFlagsUrl != null) {
                        String billFlagsJson = fetchFromS3(s3BillFlagsUrl);
                        Object billFlags = objectMapper.readValue(billFlagsJson, Object.class);
                        responseBody.put("billFlags", billFlags);
                        responseBody.put("contactPrompt", true);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to fetch results from S3: " + e.getMessage());
                    // Continue without results - status is still returned
                }
            }

            return successResponse(responseBody);

        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(500, "Server error: " + e.getMessage());
        }
    }

    private String fetchFromS3(String s3Url) throws Exception {
        // Parse s3://bucket/key format
        String[] parts = s3Url.substring("s3://".length()).split("/", 2);
        String bucket = parts[0];
        String key = parts[1];

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()
        );

        return new BufferedReader(new InputStreamReader(response, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
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
