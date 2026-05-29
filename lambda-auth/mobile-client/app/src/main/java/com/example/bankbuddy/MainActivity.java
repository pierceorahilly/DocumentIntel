package com.example.bankbuddy;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bankbuddy.api.BankBuddyApiClient;
import com.example.bankbuddy.models.Models.*;
import com.example.bankbuddy.utils.TokenManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Main activity for uploading bank statement PDFs.
 */
public class MainActivity extends AppCompatActivity {

    private Button selectPdfButton, uploadButton, logoutButton;
    private TextView selectedFileText, statusText;
    private ProgressBar progressBar;

    private BankBuddyApiClient apiClient;
    private TokenManager tokenManager;

    private File selectedPdfFile = null;
    private String selectedFilename = null;

    private final ActivityResultLauncher<String> pdfPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    handlePdfSelected(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("BankBuddy");
        }

        // Initialize API client
        apiClient = new BankBuddyApiClient(this);
        tokenManager = new TokenManager(this);

        // Check if logged in
        if (!tokenManager.isLoggedIn()) {
            navigateToLogin();
            return;
        }

        // Initialize views
        selectPdfButton = findViewById(R.id.selectPdfButton);
        uploadButton = findViewById(R.id.uploadButton);
        logoutButton = findViewById(R.id.logoutButton);
        selectedFileText = findViewById(R.id.selectedFileText);
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);

        // Set welcome message
        String email = tokenManager.getUserEmail();
        if (email != null) {
            statusText.setText("Welcome, " + email + "!\n\nUpload your bank statement PDF to get personalized savings advice.");
        }

        // Set click listeners
        selectPdfButton.setOnClickListener(v -> selectPdf());
        uploadButton.setOnClickListener(v -> uploadPdf());
        logoutButton.setOnClickListener(v -> logout());

        // Initially disable upload button
        uploadButton.setEnabled(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_history) {
            // TODO: Navigate to history activity
            Toast.makeText(this, "History coming soon!", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_logout) {
            logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void selectPdf() {
        pdfPicker.launch("application/pdf");
    }

    private void handlePdfSelected(Uri uri) {
        try {
            // Get filename
            String filename = getFileNameFromUri(uri);
            selectedFilename = filename;

            // Copy URI content to temporary file
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Failed to read PDF", Toast.LENGTH_SHORT).show();
                return;
            }

            File tempFile = File.createTempFile("statement", ".pdf", getCacheDir());
            FileOutputStream outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            selectedPdfFile = tempFile;

            // Update UI
            selectedFileText.setText("Selected: " + filename);
            selectedFileText.setVisibility(View.VISIBLE);
            uploadButton.setEnabled(true);

        } catch (Exception e) {
            Toast.makeText(this, "Error reading PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String path = uri.getPath();
        if (path != null && path.contains("/")) {
            return path.substring(path.lastIndexOf("/") + 1);
        }
        return "statement.pdf";
    }

    private void uploadPdf() {
        if (selectedPdfFile == null) {
            Toast.makeText(this, "Please select a PDF first", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        statusText.setText("Uploading and processing your bank statement...\nThis may take 10-15 seconds.");

        try {
            apiClient.uploadPdf(selectedPdfFile, selectedFilename).enqueue(new Callback<UploadResponse>() {
                @Override
                public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                    setLoading(false);

                    if (response.isSuccessful() && response.body() != null) {
                        UploadResponse uploadResponse = response.body();

                        Toast.makeText(MainActivity.this,
                                "Success! Found " + uploadResponse.transactionCount + " transactions",
                                Toast.LENGTH_SHORT).show();

                        // Navigate to results activity
                        Intent intent = new Intent(MainActivity.this, ResultsActivity.class);
                        intent.putExtra("uploadResponse", new com.google.gson.Gson().toJson(uploadResponse));
                        startActivity(intent);

                        // Reset UI
                        resetUploadForm();

                    } else {
                        String error = "Upload failed";
                        if (response.code() == 401) {
                            error = "Session expired. Please login again.";
                            logout();
                        } else if (response.code() == 415) {
                            error = "Invalid PDF file";
                        }
                        Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        statusText.setText("Upload failed. Please try again.");
                    }
                }

                @Override
                public void onFailure(Call<UploadResponse> call, Throwable t) {
                    setLoading(false);
                    Toast.makeText(MainActivity.this,
                            "Network error: " + t.getMessage(),
                            Toast.LENGTH_LONG).show();
                    statusText.setText("Upload failed. Check your internet connection.");
                }
            });

        } catch (Exception e) {
            setLoading(false);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void resetUploadForm() {
        selectedPdfFile = null;
        selectedFilename = null;
        selectedFileText.setVisibility(View.GONE);
        uploadButton.setEnabled(false);
        statusText.setText("Upload another statement or view your history.");
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        selectPdfButton.setEnabled(!loading);
        uploadButton.setEnabled(!loading && selectedPdfFile != null);
        logoutButton.setEnabled(!loading);
    }

    private void logout() {
        tokenManager.clearTokens();
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
