package com.example.bankbuddy.api;

import android.content.Context;
import android.util.Base64;

import com.example.bankbuddy.BuildConfig;
import com.example.bankbuddy.models.*;
import com.example.bankbuddy.utils.TokenManager;

import okhttp3.*;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * API client for BankBuddy AWS backend.
 * Handles authentication and PDF upload.
 */
public class BankBuddyApiClient {

    private final BankBuddyApiService apiService;
    private final TokenManager tokenManager;
    private final OkHttpClient httpClient;

    public BankBuddyApiClient(Context context) {
        this.tokenManager = new TokenManager(context);

        // Create OkHttpClient with interceptor for auth token
        this.httpClient = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)  // Textract can take time
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // Create Retrofit instance
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.apiService = retrofit.create(BankBuddyApiService.class);
    }

    /**
     * Interceptor to add Authorization header to requests.
     */
    private class AuthInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request original = chain.request();

            // Add Authorization header if token exists and not on auth endpoints
            String path = original.url().encodedPath();
            if (!path.contains("/auth/") && tokenManager.getIdToken() != null) {
                Request.Builder builder = original.newBuilder()
                        .header("Authorization", "Bearer " + tokenManager.getIdToken())
                        .method(original.method(), original.body());
                return chain.proceed(builder.build());
            }

            return chain.proceed(original);
        }
    }

    // ========== Authentication Endpoints ==========

    public Call<LoginResponse> signup(SignupRequest request) {
        return apiService.signup(request);
    }

    public Call<LoginResponse> login(LoginRequest request) {
        return apiService.login(request);
    }

    public Call<MessageResponse> confirmEmail(ConfirmEmailRequest request) {
        return apiService.confirmEmail(request);
    }

    public Call<LoginResponse> refreshToken(RefreshTokenRequest request) {
        return apiService.refreshToken(request);
    }

    // ========== PDF Upload Endpoint ==========

    public Call<UploadResponse> uploadPdf(File pdfFile, String filename) throws IOException {
        // Read PDF file
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());

        // Base64 encode
        String base64Pdf = Base64.encodeToString(pdfBytes, Base64.NO_WRAP);

        // Create request body
        RequestBody body = RequestBody.create(
                base64Pdf,
                MediaType.parse("application/pdf")
        );

        // Add filename header
        return apiService.uploadPdf(filename, body);
    }

    // ========== Retrofit Service Interface ==========

    public interface BankBuddyApiService {

        @POST("/auth/signup")
        Call<LoginResponse> signup(@Body SignupRequest request);

        @POST("/auth/login")
        Call<LoginResponse> login(@Body LoginRequest request);

        @POST("/auth/confirm")
        Call<MessageResponse> confirmEmail(@Body ConfirmEmailRequest request);

        @POST("/auth/refresh")
        Call<LoginResponse> refreshToken(@Body RefreshTokenRequest request);

        @POST("/upload")
        @Headers("Content-Type: application/pdf")
        Call<UploadResponse> uploadPdf(
                @Header("X-Filename") String filename,
                @Body RequestBody pdfData
        );
    }
}
