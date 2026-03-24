package com.evefrontier.vault;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Server configuration for Stillness (production) and Utopia (test) EVE Frontier servers.
 * Both run on sui:testnet.
 */
public class ServerConfig {

    public static final String STILLNESS = "stillness";
    public static final String UTOPIA = "utopia";

    // Stillness (production)
    static final String STILLNESS_AUTH = "https://auth.evefrontier.com";
    static final String STILLNESS_CLIENT_ID = "583ebc6d-abd8-4057-8c77-78405628e42d";
    static final String STILLNESS_WORLD_API = "https://world-api-stillness.live.tech.evefrontier.com";

    // Utopia (test/hackathon)
    static final String UTOPIA_AUTH = "https://test.auth.evefrontier.com";
    static final String UTOPIA_CLIENT_ID = "00d3ce5b-4cab-4970-a9dc-e122fc1d30ce";
    static final String UTOPIA_WORLD_API = "https://world-api-utopia.uat.pub.evefrontier.com";

    private static final String PREFS_NAME = "evm_server";
    private static final String KEY_SERVER = "selected_server";

    public static String getSelectedServer(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SERVER, STILLNESS);
    }

    public static void setSelectedServer(Context ctx, String server) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_SERVER, server).apply();
    }

    public static String getAuthUrl(Context ctx) {
        return UTOPIA.equals(getSelectedServer(ctx)) ? UTOPIA_AUTH : STILLNESS_AUTH;
    }

    public static String getClientId(Context ctx) {
        return UTOPIA.equals(getSelectedServer(ctx)) ? UTOPIA_CLIENT_ID : STILLNESS_CLIENT_ID;
    }

    public static String getWorldApi(Context ctx) {
        return UTOPIA.equals(getSelectedServer(ctx)) ? UTOPIA_WORLD_API : STILLNESS_WORLD_API;
    }

    /** Get the auth base URL that the web build was compiled with (Stillness) */
    public static String getBuildAuthUrl() {
        return STILLNESS_AUTH;
    }

    public static String getBuildClientId() {
        return STILLNESS_CLIENT_ID;
    }
}
