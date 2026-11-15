package com.briancodez.padstudio;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;

public final class Store {
    public final SharedPreferences preferences;
    public Store(Context context) { preferences = context.getSharedPreferences("padstudio", Context.MODE_PRIVATE); }
    public Model.Settings load() {
        String text = preferences.getString("settings", null);
        if (text != null) {
            try { return Model.Settings.read(text, false); }
            catch (JSONException error) {
                preferences.edit().putString("recovery_backup", text).apply();
            }
        }
        Model.Settings settings = Model.Settings.defaults();
        save(settings);
        return settings;
    }
    public void save(Model.Settings settings) {
        try {
            String json = settings.json().toString();
            Model.Settings.read(json, false);
            if (!preferences.edit().putString("settings", json).commit()) throw new IllegalStateException("Could not save profiles");
        } catch (JSONException error) { throw new IllegalArgumentException(error.getMessage(), error); }
    }
}
