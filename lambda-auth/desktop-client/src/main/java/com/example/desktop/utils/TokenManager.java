package com.example.desktop.utils;

import java.util.prefs.Preferences;

/**
 * Manages JWT tokens using Java Preferences API (stores in OS-specific location).
 */
public class TokenManager {

    private static final Preferences prefs = Preferences.userNodeForPackage(TokenManager.class);

    private static final String KEY_ID_TOKEN = "id_token";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_EMAIL = "user_email";

    public static void saveTokens(String idToken, String accessToken, String refreshToken) {
        prefs.put(KEY_ID_TOKEN, idToken);
        prefs.put(KEY_ACCESS_TOKEN, accessToken);
        prefs.put(KEY_REFRESH_TOKEN, refreshToken);
    }

    public static void saveUserEmail(String email) {
        prefs.put(KEY_USER_EMAIL, email);
    }

    public static String getIdToken() {
        return prefs.get(KEY_ID_TOKEN, null);
    }

    public static String getAccessToken() {
        return prefs.get(KEY_ACCESS_TOKEN, null);
    }

    public static String getRefreshToken() {
        return prefs.get(KEY_REFRESH_TOKEN, null);
    }

    public static String getUserEmail() {
        return prefs.get(KEY_USER_EMAIL, null);
    }

    public static boolean isLoggedIn() {
        return getIdToken() != null;
    }

    public static void clearTokens() {
        prefs.remove(KEY_ID_TOKEN);
        prefs.remove(KEY_ACCESS_TOKEN);
        prefs.remove(KEY_REFRESH_TOKEN);
        prefs.remove(KEY_USER_EMAIL);
    }
}
