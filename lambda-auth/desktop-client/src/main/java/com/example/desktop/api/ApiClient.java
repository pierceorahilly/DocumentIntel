package com.example.desktop.api;

import com.example.desktop.utils.Config;
import com.example.desktop.utils.TokenManager;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.util.Timeout;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * API client for BankBuddy AWS backend.
 */
public class ApiClient {

    private static final Gson gson = new Gson();

    // ========== Authentication ==========

    public static LoginResponse login(String email, String password) throws IOException, ParseException {
        LoginRequest request = new LoginRequest(email, password);
        String json = gson.toJson(request);

        HttpPost post = new HttpPost(Config.getFullUrl("/auth/login"));
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(json));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {

            String responseBody = EntityUtils.toString(response.getEntity());

            if (response.getCode() != 200) {
                throw new IOException("Login failed: " + responseBody);
            }

            return gson.fromJson(responseBody, LoginResponse.class);
        }
    }

    public static void signup(String email, String password, String name, String dateOfBirth, String address) throws IOException, ParseException {
        SignupRequest request = new SignupRequest(email, password, name, dateOfBirth, address);
        String json = gson.toJson(request);

        HttpPost post = new HttpPost(Config.getFullUrl("/auth/signup"));
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(json));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {

            if (response.getCode() != 200) {
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new IOException("Signup failed: " + responseBody);
            }
        }
    }

    public static void confirmEmail(String email, String code) throws IOException, ParseException {
        ConfirmEmailRequest request = new ConfirmEmailRequest(email, code);
        String json = gson.toJson(request);

        HttpPost post = new HttpPost(Config.getFullUrl("/auth/confirm"));
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(json));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {

            if (response.getCode() != 200) {
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new IOException("Email confirmation failed: " + responseBody);
            }
        }
    }

    // ========== PDF Upload ==========

    public static UploadResponse uploadPdf(File pdfFile) throws IOException, ParseException {
        // Read PDF file and encode to base64
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);

        // Create POST request (returns immediately with uploadId)
        HttpPost post = new HttpPost(Config.getFullUrl("/upload"));
        post.setHeader("Content-Type", "application/pdf");
        post.setHeader("X-Filename", pdfFile.getName());
        post.setHeader("Authorization", "Bearer " + TokenManager.getIdToken());
        post.setEntity(new StringEntity(base64Pdf));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {

            String responseBody = EntityUtils.toString(response.getEntity());

            if (response.getCode() != 200) {
                throw new IOException("Upload failed (HTTP " + response.getCode() + "): " + responseBody);
            }

            return gson.fromJson(responseBody, UploadResponse.class);
        }
    }

    public static UploadResponse getUploadStatus(String uploadId) throws IOException, ParseException {
        org.apache.hc.client5.http.classic.methods.HttpGet get =
            new org.apache.hc.client5.http.classic.methods.HttpGet(Config.getFullUrl("/status/" + uploadId));
        get.setHeader("Authorization", "Bearer " + TokenManager.getIdToken());

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(get)) {

            String responseBody = EntityUtils.toString(response.getEntity());

            if (response.getCode() != 200) {
                throw new IOException("Status check failed (HTTP " + response.getCode() + "): " + responseBody);
            }

            return gson.fromJson(responseBody, UploadResponse.class);
        }
    }

    // ========== Contact Request ==========

    public static void submitContactRequest(List<BillFlag> billFlags) throws IOException, ParseException {
        String json = gson.toJson(Map.of("flaggedBills", billFlags));

        HttpPost post = new HttpPost(Config.getFullUrl("/contact-request"));
        post.setHeader("Content-Type", "application/json");
        post.setHeader("Authorization", "Bearer " + TokenManager.getIdToken());
        post.setEntity(new StringEntity(json));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {

            if (response.getCode() != 200) {
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new IOException("Contact request failed: " + responseBody);
            }
        }
    }

    // ========== Request Models ==========

    public static class LoginRequest {
        public String email;
        public String password;

        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    public static class SignupRequest {
        public String email;
        public String password;
        public String name;
        public String dateOfBirth;
        public String address;

        public SignupRequest(String email, String password, String name, String dateOfBirth, String address) {
            this.email = email;
            this.password = password;
            this.name = name;
            this.dateOfBirth = dateOfBirth;
            this.address = address;
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

    // ========== Response Models ==========

    public static class LoginResponse {
        @SerializedName("idToken")
        public String idToken;

        @SerializedName("accessToken")
        public String accessToken;

        @SerializedName("refreshToken")
        public String refreshToken;
    }

    public static class UploadResponse {
        @SerializedName("uploadId")
        public String uploadId;

        @SerializedName("status")
        public String status;

        @SerializedName("message")
        public String message;

        @SerializedName("transactionCount")
        public Integer transactionCount;

        @SerializedName("processingTime")
        public Double processingTime;

        @SerializedName("transactions")
        public List<Transaction> transactions;

        @SerializedName("advice")
        public String advice;

        @SerializedName("categoryAnalysis")
        public CategoryAnalysis categoryAnalysis;

        @SerializedName("billFlags")
        public List<BillFlag> billFlags;

        @SerializedName("contactPrompt")
        public Boolean contactPrompt;
    }

    public static class CategoryAnalysis {
        @SerializedName("categoryTotals")
        public Map<String, Double> categoryTotals;

        @SerializedName("categoryCounts")
        public Map<String, Integer> categoryCounts;

        @SerializedName("categoryTransactions")
        public Map<String, List<TransactionDetail>> categoryTransactions;

        @SerializedName("subscriptions")
        public List<Subscription> subscriptions;

        @SerializedName("biggestCategory")
        public String biggestCategory;

        @SerializedName("totalSpent")
        public Double totalSpent;
    }

    public static class TransactionDetail {
        @SerializedName("description")
        public String description;

        @SerializedName("amount")
        public Double amount;

        @SerializedName("date")
        public String date;

        @SerializedName("category")
        public String category;
    }

    public static class Subscription {
        @SerializedName("merchant")
        public String merchant;

        @SerializedName("amount")
        public Double amount;

        @SerializedName("occurrences")
        public Integer occurrences;

        @SerializedName("frequency")
        public String frequency;
    }

    public static class BillFlag {
        @SerializedName("billType")
        public String billType;

        @SerializedName("providerName")
        public String providerName;

        @SerializedName("amount")
        public Double amount;

        @SerializedName("reason")
        public String reason;

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
}
