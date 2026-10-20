package com.puter.unofficial;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.CookieManager;

/**
 * Manages the persistent authentication state for Puter Unofficial.
 * This class ensures that once a user signs in via the browser/popup, the app 
 * remembers that state across restarts using SharedPreferences and CookieManager.
 * 
 * CORE ANALYSIS UPDATE: 
 * Synchronizes with Puter's redirect endpoints and Web OS desktop routing (/app)
 * to ensure native Android UI transitions reliably between "Sign In" and "Sign Out".
 */
public class AuthManager {

    private static final String PREF_NAME = "PuterPrefs";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_AUTH_TOKEN = "puter_auth_token"; // Key for extracted SDK token
    private static AuthManager instance;
    private final SharedPreferences prefs;

    /**
     * Private constructor for Singleton pattern.
     * Accesses the shared preference file dedicated to Puter settings.
     */
    private AuthManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Gets the global instance of the AuthManager.
     * Uses synchronized block to ensure thread safety.
     * 
     * @param context The application context.
     * @return The singleton instance of AuthManager.
     */
    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }

    /**
     * Saves the current authentication status.
     * 
     * @param status true if the user is authenticated, false otherwise.
     */
    public void setLoggedIn(boolean status) {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, status).apply();
    }

    /**
     * Checks if the user is currently considered logged in.
     * Used by SplashActivity and MainActivity to determine initial UI state.
     * 
     * @return true if status is saved as logged in.
     */
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /**
     * Persists the authentication token string extracted from the login popup.
     * Used as a secondary backup to the browser's native cookie storage.
     * 
     * @param token The session token string.
     */
    public void setAuthToken(String token) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
    }

    /**
     * Alias method for setAuthToken to ensure compatibility across callers.
     * 
     * @param token The session token string.
     */
    public void saveAuthToken(String token) {
        setAuthToken(token);
    }

    /**
     * Handles redirect callback URLs, extracting and saving any token parameters
     * before persisting the logged-in flag. Also inspects CookieManager for session tokens.
     * 
     * @param url The redirect URL intercepted from OAuth.
     */
    public void handleAuthCallback(String url) {
        boolean tokenFound = false;
        if (url != null && url.contains("token=")) {
            try {
                Uri uri = Uri.parse(url);
                String token = uri.getQueryParameter("token");
                if (token != null && !token.isEmpty()) {
                    setAuthToken(token);
                    tokenFound = true;
                }
            } catch (Exception ignored) {}
        }

        // Secondary extraction from session cookies if URL query was tokenless
        if (!tokenFound) {
            try {
                CookieManager cm = CookieManager.getInstance();
                String cookies = cm.getCookie("https://puter.com");
                if (cookies != null) {
                    for (String cookie : cookies.split(";")) {
                        String trimmed = cookie.trim();
                        if (trimmed.startsWith("puter_auth=") || trimmed.startsWith("token=") || trimmed.startsWith("auth_token=")) {
                            String[] parts = trimmed.split("=", 2);
                            if (parts.length > 1 && !parts[1].isEmpty()) {
                                setAuthToken(parts[1]);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        setLoggedIn(true);
    }

    /**
     * Retrieves the saved authentication token.
     * 
     * @return The saved token string or null if not available.
     */
    public String getAuthToken() {
        return prefs.getString(KEY_AUTH_TOKEN, null);
    }

    /**
     * Clears the authentication state.
     * Triggered when the user selects "Sign Out".
     * Ensures the login flag, backup token, and WebView cookies are wiped clean.
     */
    public void logout() {
        prefs.edit()
             .putBoolean(KEY_IS_LOGGED_IN, false)
             .remove(KEY_AUTH_TOKEN)
             .apply();

        try {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
        } catch (Exception ignored) {}
    }

    /**
     * Helper to verify if a specific URL is a Puter authentication success callback.
     * Intercepts standard token redirects and Web OS desktop routing (/app) upon login.
     * 
     * @param url The URL being intercepted in the WebView.
     * @return true if the URL indicates a successful authentication.
     */
    public boolean isAuthCallback(String url) {
        if (url == null) return false;

        /* 
         * Puter redirects back to the main domain, a callback parameter, or the 
         * Web OS desktop dashboard (/app) once login completes successfully.
         */
        return (url.contains(AppConstants.AUTH_TOKEN_PARAM) || 
                url.contains(AppConstants.AUTH_SUCCESS_MARKER) ||
                url.contains("auth_success") ||
                url.contains("signed_in=true") ||
                url.contains("puter.com/app") ||
                url.contains("/app/") ||
                url.endsWith("/app") ||
                url.contains("/app#"));
    }
}