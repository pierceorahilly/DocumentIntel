package com.example.bankbuddy.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * All API request/response models in one file.
 */
public class Models {

    // ========== Request Models ==========

    public static class SignupRequest {
        public String email;
        public String password;
        public String name;

        public SignupRequest(String email, String password, String name) {
            this.email = email;
            this.password = password;
            this.name = name;
        }
    }

    public static class LoginRequest {
        public String email;
        public String password;

        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    public static class ConfirmEmailRequest {
        public String email;
        public String code;

        public ConfirmEmailRequest(String email, String code) {
            this.email = email;
            this.code = code;
        }
    }

    public static class RefreshTokenRequest {
        public String refreshToken;

        public RefreshTokenRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    // ========== Response Models ==========

    public static class LoginResponse {
        @SerializedName("idToken")
        public String idToken;

        @SerializedName("accessToken")
        public String accessToken;

        @SerializedName("refreshToken")
        public String refreshToken;

        @SerializedName("expiresIn")
        public int expiresIn;

        @SerializedName("tokenType")
        public String tokenType;

        @SerializedName("message")
        public String message;

        @SerializedName("userId")
        public String userId;

        @SerializedName("userConfirmed")
        public boolean userConfirmed;
    }

    public static class MessageResponse {
        @SerializedName("message")
        public String message;
    }

    public static class UploadResponse {
        @SerializedName("uploadId")
        public String uploadId;

        @SerializedName("s3PdfUrl")
        public String s3PdfUrl;

        @SerializedName("transactionCount")
        public int transactionCount;

        @SerializedName("processingTime")
        public double processingTime;

        @SerializedName("transactions")
        public List<Transaction> transactions;

        @SerializedName("advice")
        public String advice;
    }

    public static class Transaction {
        @SerializedName("date")
        public String date;

        @SerializedName("description")
        public String description;

        @SerializedName("amount")
        public String amount;

        @SerializedName("balance")
        public String balance;

        @SerializedName("category")
        public String category;
    }

    public static class ErrorResponse {
        @SerializedName("error")
        public String error;
    }
}
