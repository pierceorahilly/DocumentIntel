package com.example.desktop.utils;

/**
 * Application configuration.
 *
 * AWS deployment configuration - Updated from deployment.
 */
public class Config {

    // AWS API Gateway URL
    public static final String API_BASE_URL = "https://vz1307i8t2.execute-api.eu-west-1.amazonaws.com/prod";

    // AWS Cognito Configuration (for reference)
    public static final String COGNITO_USER_POOL_ID = "eu-west-1_BSofFOCGg";
    public static final String COGNITO_CLIENT_ID = "1aiq9l331h0locean5mnut4nlk";
    public static final String AWS_REGION = "eu-west-1";

    // Demo mode: Set to false to connect to real AWS backend
    public static final boolean DEMO_MODE = false;

    public static String getFullUrl(String endpoint) {
        return API_BASE_URL + endpoint;
    }
}
