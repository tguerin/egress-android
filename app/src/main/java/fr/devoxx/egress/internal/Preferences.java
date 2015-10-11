package fr.devoxx.egress.internal;

import android.content.Context;
import android.content.SharedPreferences;

import fr.devoxx.egress.R;

public class Preferences {

    private static final String KEY_PLAYER_ID = "playerId";

    private Preferences() {

    }

    public static void setPlayerId(Context context, String playerId) {
        getAppPreferences(context).edit().
                putString(KEY_PLAYER_ID, playerId).
                apply();
    }

    public static String getPlayerId(Context context) {
        return getAppPreferences(context).getString(KEY_PLAYER_ID, null);
    }

    private static SharedPreferences getAppPreferences(Context context) {
        return context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE);
    }

    public static boolean hasPlayerId(Context context) {
        return getPlayerId(context) != null;
    }
}
