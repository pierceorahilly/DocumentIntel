package com.example.bankbuddy;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bankbuddy.api.BankBuddyApiClient;
import com.example.bankbuddy.models.Models.*;
import com.example.bankbuddy.utils.TokenManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Login and Signup screen.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText emailInput, passwordInput, nameInput, confirmCodeInput;
    private Button loginButton, signupButton, confirmEmailButton;
    private ProgressBar progressBar;
    private TextView toggleModeText, confirmEmailText;
    private View signupFields, confirmEmailFields;

    private BankBuddyApiClient apiClient;
    private TokenManager tokenManager;

    private boolean isSignupMode = false;
    private boolean isConfirmMode = false;
    private String pendingEmail = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize API client
        apiClient = new BankBuddyApiClient(this);
        tokenManager = new TokenManager(this);

        // Check if already logged in
        if (tokenManager.isLoggedIn()) {
            navigateToMain();
            return;
        }

        // Initialize views
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        nameInput = findViewById(R.id.nameInput);
        confirmCodeInput = findViewById(R.id.confirmCodeInput);
        loginButton = findViewById(R.id.loginButton);
        signupButton = findViewById(R.id.signupButton);
        confirmEmailButton = findViewById(R.id.confirmEmailButton);
        progressBar = findViewById(R.id.progressBar);
        toggleModeText = findViewById(R.id.toggleModeText);
        confirmEmailText = findViewById(R.id.confirmEmailText);
        signupFields = findViewById(R.id.signupFields);
        confirmEmailFields = findViewById(R.id.confirmEmailFields);

        // Set click listeners
        loginButton.setOnClickListener(v -> handleLogin());
        signupButton.setOnClickListener(v -> handleSignup());
        confirmEmailButton.setOnClickListener(v -> handleConfirmEmail());
        toggleModeText.setOnClickListener(v -> toggleMode());
    }

    private void handleLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        LoginRequest request = new LoginRequest(email, password);
        apiClient.login(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();

                    // Save tokens
                    tokenManager.saveTokens(
                            loginResponse.idToken,
                            loginResponse.accessToken,
                            loginResponse.refreshToken
                    );
                    tokenManager.saveUserEmail(email);

                    Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                    navigateToMain();

                } else {
                    String error = "Login failed";
                    if (response.code() == 401) {
                        error = "Invalid email or password";
                    } else if (response.code() == 403) {
                        error = "Please confirm your email first";
                        showConfirmEmailMode(email);
                    }
                    Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleSignup() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String name = nameInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 8) {
            Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        SignupRequest request = new SignupRequest(email, password, name);
        apiClient.signup(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse signupResponse = response.body();

                    Toast.makeText(LoginActivity.this,
                            "Account created! Please check your email for confirmation code.",
                            Toast.LENGTH_LONG).show();

                    // Show email confirmation screen
                    showConfirmEmailMode(email);

                } else {
                    String error = "Signup failed";
                    if (response.code() == 400) {
                        error = "User already exists or invalid password";
                    }
                    Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleConfirmEmail() {
        String code = confirmCodeInput.getText().toString().trim();

        if (code.isEmpty() || pendingEmail == null) {
            Toast.makeText(this, "Please enter confirmation code", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        ConfirmEmailRequest request = new ConfirmEmailRequest(pendingEmail, code);
        apiClient.confirmEmail(request).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                setLoading(false);

                if (response.isSuccessful()) {
                    Toast.makeText(LoginActivity.this,
                            "Email confirmed! Please login.",
                            Toast.LENGTH_SHORT).show();

                    // Switch back to login mode
                    showLoginMode();

                } else {
                    String error = "Confirmation failed";
                    if (response.code() == 400) {
                        error = "Invalid or expired code";
                    }
                    Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toggleMode() {
        if (isConfirmMode) {
            showLoginMode();
        } else {
            isSignupMode = !isSignupMode;
            updateUI();
        }
    }

    private void showConfirmEmailMode(String email) {
        isConfirmMode = true;
        pendingEmail = email;
        emailInput.setText(email);
        updateUI();
    }

    private void showLoginMode() {
        isSignupMode = false;
        isConfirmMode = false;
        pendingEmail = null;
        updateUI();
    }

    private void updateUI() {
        if (isConfirmMode) {
            // Show email confirmation fields
            emailInput.setEnabled(false);
            passwordInput.setVisibility(View.GONE);
            signupFields.setVisibility(View.GONE);
            confirmEmailFields.setVisibility(View.VISIBLE);
            loginButton.setVisibility(View.GONE);
            signupButton.setVisibility(View.GONE);
            confirmEmailButton.setVisibility(View.VISIBLE);
            toggleModeText.setText("Back to Login");
            confirmEmailText.setVisibility(View.VISIBLE);

        } else if (isSignupMode) {
            // Show signup mode
            emailInput.setEnabled(true);
            passwordInput.setVisibility(View.VISIBLE);
            signupFields.setVisibility(View.VISIBLE);
            confirmEmailFields.setVisibility(View.GONE);
            loginButton.setVisibility(View.GONE);
            signupButton.setVisibility(View.VISIBLE);
            confirmEmailButton.setVisibility(View.GONE);
            toggleModeText.setText("Already have an account? Login");
            confirmEmailText.setVisibility(View.GONE);

        } else {
            // Show login mode
            emailInput.setEnabled(true);
            passwordInput.setVisibility(View.VISIBLE);
            signupFields.setVisibility(View.GONE);
            confirmEmailFields.setVisibility(View.GONE);
            loginButton.setVisibility(View.VISIBLE);
            signupButton.setVisibility(View.GONE);
            confirmEmailButton.setVisibility(View.GONE);
            toggleModeText.setText("Don't have an account? Sign up");
            confirmEmailText.setVisibility(View.GONE);
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        signupButton.setEnabled(!loading);
        confirmEmailButton.setEnabled(!loading);
        emailInput.setEnabled(!loading && !isConfirmMode);
        passwordInput.setEnabled(!loading);
        nameInput.setEnabled(!loading);
        confirmCodeInput.setEnabled(!loading);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
