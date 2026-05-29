package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * AWS Lambda handler for Cognito authentication operations.
 * Handles signup, login, email confirmation, password reset, etc.
 */
public class AuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final CognitoIdentityProviderClient cognitoClient;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final String userPoolId;
    private final String clientId;

    public AuthHandler() {
        String region = getEnvOrThrow("AWS_REGION");
        this.userPoolId = getEnvOrThrow("COGNITO_USER_POOL_ID");
        this.clientId = getEnvOrThrow("COGNITO_CLIENT_ID");

        this.cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.userService = new UserService(region);
        this.objectMapper = new ObjectMapper();

        System.out.println("[AuthHandler] Initialized - UserPool: " + userPoolId);
        System.out.println("[AuthHandler] Deploy test - " + Instant.now());
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String path = request.getPath();
        System.out.println("Auth request: " + path);

        try {
            return switch (path) {
                case "/auth/signup" -> handleSignup(request);
                case "/auth/login" -> handleLogin(request);
                case "/auth/confirm" -> handleConfirm(request);
                case "/auth/refresh" -> handleRefreshToken(request);
                case "/auth/forgot-password" -> handleForgotPassword(request);
                case "/auth/reset-password" -> handleResetPassword(request);
                default -> errorResponse(404, "Endpoint not found");
            };
        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /auth/signup
     * Body: { "email": "user@example.com", "password": "SecurePass123!", "name": "John Doe",
     *         "dateOfBirth": "1990-01-15", "address": "123 Main St, City, Country" }
     */
    private APIGatewayProxyResponseEvent handleSignup(APIGatewayProxyRequestEvent request) {
        try {
            JsonNode body = objectMapper.readTree(request.getBody());
            String email = body.get("email").asText();
            String password = body.get("password").asText();
            String name = body.has("name") ? body.get("name").asText() : "";
            String dateOfBirth = body.has("dateOfBirth") ? body.get("dateOfBirth").asText() : "";
            String address = body.has("address") ? body.get("address").asText() : "";

            // Create user in Cognito with all attributes
            SignUpResponse signUpResponse = cognitoClient.signUp(SignUpRequest.builder()
                    .clientId(clientId)
                    .username(email)
                    .password(password)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("name").value(name).build(),
                            AttributeType.builder().name("birthdate").value(dateOfBirth).build(),
                            AttributeType.builder().name("address").value(address).build()
                    )
                    .build());

            String userId = signUpResponse.userSub();

            // Create user record in DynamoDB with additional fields
            String s3Prefix = "users/" + userId + "/";
            userService.createUser(userId, email, name, dateOfBirth, address, s3Prefix);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "User created successfully. Please check your email to confirm.");
            response.put("userId", userId);
            response.put("userConfirmed", signUpResponse.userConfirmed());

            return successResponse(response);

        } catch (UsernameExistsException e) {
            return errorResponse(400, "User with this email already exists");
        } catch (InvalidPasswordException e) {
            return errorResponse(400, "Invalid password: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(500, "Signup failed: " + e.getMessage());
        }
    }

    /**
     * POST /auth/login
     * Body: { "email": "user@example.com", "password": "SecurePass123!" }
     */
    private APIGatewayProxyResponseEvent handleLogin(APIGatewayProxyRequestEvent request) {
        try {
            JsonNode body = objectMapper.readTree(request.getBody());
            String email = body.get("email").asText();
            String password = body.get("password").asText();

            // Authenticate with Cognito using USER_PASSWORD_AUTH flow
            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .clientId(clientId)
                    .authParameters(Map.of(
                            "USERNAME", email,
                            "PASSWORD", password
                    ))
                    .build());

            AuthenticationResultType authResult = authResponse.authenticationResult();

            // Update last login in DynamoDB
            String userId = extractUserIdFromToken(authResult.idToken());
            userService.updateLastLogin(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("idToken", authResult.idToken());
            response.put("accessToken", authResult.accessToken());
            response.put("refreshToken", authResult.refreshToken());
            response.put("expiresIn", authResult.expiresIn());
            response.put("tokenType", authResult.tokenType());

            return successResponse(response);

        } catch (NotAuthorizedException e) {
            return errorResponse(401, "Invalid email or password");
        } catch (UserNotConfirmedException e) {
            return errorResponse(403, "Email not confirmed. Please check your email.");
        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(500, "Login failed: " + e.getMessage());
        }
    }

    /**
     * POST /auth/confirm
     * Body: { "email": "user@example.com", "code": "123456" }
     */
    private APIGatewayProxyResponseEvent handleConfirm(APIGatewayProxyRequestEvent request) {
        try {
            JsonNode body = objectMapper.readTree(request.getBody());
            String email = body.get("email").asText();
            String code = body.get("code").asText();

            cognitoClient.confirmSignUp(ConfirmSignUpRequest.builder()
                    .clientId(clientId)
                    .username(email)
                    .confirmationCode(code)
                    .build());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Email confirmed successfully");

            return successResponse(response);

        } catch (CodeMismatchException e) {
            return errorResponse(400, "Invalid confirmation code");
        } catch (ExpiredCodeException e) {
            return errorResponse(400, "Confirmation code expired. Please request a new one.");
        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(500, "Confirmation failed: " + e.getMessage());
        }
    }

    /**
     * POST /auth/refresh
     * Body: { "refreshToken": "eyJjdHki..." }
     */
    private APIGatewayProxyResponseEvent handleRefreshToken(APIGatewayProxyRequestEvent request) {
        try {
            JsonNode body = objectMapper.readTree(request.getBody());
            String refreshToken = body.get("refreshToken").asText();

            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                    .clientId(clientId)
                    .authParameters(Map.of("REFRESH_TOKEN", refreshToken))
                    .build());

            AuthenticationResultType authResult = authResponse.authenticationResult();

            Map<String, Object> response = new HashMap<>();
            response.put("idToken", authResult.idToken());
            response.put("accessToken", authResult.accessToken());
            response.put("expiresIn", authResult.expiresIn());
            response.put("tokenType", authResult.tokenType());

            return successResponse(response);

        } catch (NotAuthorizedException e) {
            return errorResponse(401, "Invalid or expired refresh token");
        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(500, "Token refresh failed: " + e.getMessage());
        }
    }

    /**
     * POST /auth/forgot-password
     * Body: { "email": "user@example.com" }
     */
    private APIGatewayProxyResponseEvent handleForgotPassword(APIGatewayProxyRequestEvent request) {
        try {
            JsonNode body = objectMapper.readTree(request.getBody());
            String email = body.get("email").asText();

            cognitoClient.forgotPassword(ForgotPasswordRequest.builder()
                    .clientId(clientId)
                    .username(email)
                    .build());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Password reset code sent to your email");

            return successResponse(response);

        } catch (UserNotFoundException e) {
            // Don't reveal if user exists for security
            Map<String, Object> response = new HashMap<>();
            response.put("message", "If this email exists, a reset code has been sent");
            return successResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(500, "Password reset request failed: " + e.getMessage());
        }
    }

    /**
     * POST /auth/reset-password
     * Body: { "email": "user@example.com", "code": "123456", "newPassword": "NewPass123!" }
     */
    private APIGatewayProxyResponseEvent handleResetPassword(APIGatewayProxyRequestEvent request) {
        try {
            JsonNode body = objectMapper.readTree(request.getBody());
            String email = body.get("email").asText();
            String code = body.get("code").asText();
            String newPassword = body.get("newPassword").asText();

            cognitoClient.confirmForgotPassword(ConfirmForgotPasswordRequest.builder()
                    .clientId(clientId)
                    .username(email)
                    .confirmationCode(code)
                    .password(newPassword)
                    .build());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Password reset successfully");

            return successResponse(response);

        } catch (CodeMismatchException e) {
            return errorResponse(400, "Invalid reset code");
        } catch (ExpiredCodeException e) {
            return errorResponse(400, "Reset code expired. Please request a new one.");
        } catch (InvalidPasswordException e) {
            return errorResponse(400, "Invalid password: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(500, "Password reset failed: " + e.getMessage());
        }
    }

    /**
     * Extract userId (sub claim) from JWT token (simple base64 decode, no verification needed)
     */
    private String extractUserIdFromToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = objectMapper.readTree(payload);
            return claims.get("sub").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract userId from token", e);
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
