package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda handler for contact request operations.
 * - POST /contact-request  → user accepts the contact prompt (saves flagged bills to DB)
 */
public class ContactRequestHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UserService userService;
    private final ContactRequestService contactRequestService;
    private final ObjectMapper objectMapper;

    public ContactRequestHandler() {
        String region = System.getenv("AWS_REGION");
        this.userService = new UserService(region);
        this.contactRequestService = new ContactRequestService(region);
        this.objectMapper = new ObjectMapper();

        System.out.println("[ContactRequestHandler] Initialized");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Extract userId from Cognito authorizer
            String userId = extractUserId(request);
            if (userId == null || userId.isEmpty()) {
                return errorResponse(401, "Unauthorized - Missing userId");
            }

            // Parse request body — expects { "flaggedBills": [...] }
            String body = request.getBody();
            if (body == null || body.isEmpty()) {
                return errorResponse(400, "Request body is required");
            }

            Map<String, Object> requestBody = objectMapper.readValue(body, new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> flaggedBillMaps = (List<Map<String, Object>>) requestBody.get("flaggedBills");
            if (flaggedBillMaps == null || flaggedBillMaps.isEmpty()) {
                return errorResponse(400, "No flagged bills provided");
            }

            // Convert maps back to BillFlag objects
            List<BillFlagService.BillFlag> flaggedBills = flaggedBillMaps.stream()
                    .map(m -> new BillFlagService.BillFlag(
                            (String) m.get("billType"),
                            (String) m.get("providerName"),
                            ((Number) m.get("amount")).doubleValue(),
                            (String) m.get("reason"),
                            (String) m.get("advice")
                    ))
                    .toList();

            // Get user details for the contact request record
            Map<String, AttributeValue> userData = userService.getUser(userId);
            if (userData == null || userData.isEmpty()) {
                return errorResponse(404, "User not found");
            }

            String name = userData.containsKey("name") ? userData.get("name").s() : "";
            String email = userData.containsKey("email") ? userData.get("email").s() : "";
            String dateOfBirth = userData.containsKey("dateOfBirth") ? userData.get("dateOfBirth").s() : "";
            int age = BillFlagService.calculateAge(dateOfBirth);

            // Save the contact request
            contactRequestService.createContactRequest(userId, name, email, age, flaggedBills);

            // Response
            Map<String, Object> responseBody = new LinkedHashMap<>();
            responseBody.put("message", "Your request has been submitted. A support advisor will be in touch to help with your bills.");
            responseBody.put("flaggedBillCount", flaggedBills.size());

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
            return null;
        }
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
            return errorResponse(500, "Failed to serialize response");
        }
    }

    private APIGatewayProxyResponseEvent errorResponse(int statusCode, String message) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of(
                        "Content-Type", "application/json",
                        "Access-Control-Allow-Origin", "*"
                ))
                .withBody("{\"error\":\"" + message + "\"}");
    }
}
