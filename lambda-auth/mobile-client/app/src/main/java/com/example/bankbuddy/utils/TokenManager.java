package com.example.bankbuddy.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Securely stores JWT tokens using EncryptedSharedPreferences.
 */
public class TokenManager {

    private static final String PREFS_NAME = "bankbuddy_secure_prefs";
    private static final String KEY_ID_TOKEN = "id_token";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_EMAIL = "user_email";

    private final SharedPreferences sharedPreferences;

    public TokenManager(Context context) {
        try {
            // Create MasterKey for encryption
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            // Create EncryptedSharedPreferences
            this.sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize TokenManager", e);
        }
    }

    public void saveTokens(String idToken, String accessToken, String refreshToken) {
        sharedPreferences.edit()
                .putString(KEY_ID_TOKEN, idToken)
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    public void saveUserEmail(String email) {
        sharedPreferences.edit()
                .putString(KEY_USER_EMAIL, email)
                .apply();
    }

    public String getIdToken() {
        return sharedPreferences.getString(KEY_ID_TOKEN, null);
    }

    public String getAccessToken() {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, null);
    }

    public boolean isLoggedIn() {
        return getIdToken() != null;
    }

    public void clearTokens() {
        sharedPreferences.edit()
                .remove(KEY_ID_TOKEN)
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_EMAIL)
                .apply();
    }
}
