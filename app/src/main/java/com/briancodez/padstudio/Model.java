package com.briancodez.padstudio;

import android.view.KeyEvent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Model {
    private Model() {}

    public enum Action {
        PASS("Pass through"), TAP("Screen tap"), TOUCH_HOLD("Touch while held"),
        SWIPE_UP("Swipe up"), SWIPE_DOWN("Swipe down"), SWIPE_LEFT("Swipe left"), SWIPE_RIGHT("Swipe right"),
        BACK("Android Back"), HOME("Android Home"), RECENTS("Recent apps"), LAYER("Toggle menu layer");
        public final String label;
        Action(String label) { this.label = label; }
        @Override public String toString() { return label; }
        public boolean usesTouch() { return this == TAP || this == TOUCH_HOLD || name().startsWith("SWIPE_"); }
    }
    public enum Activation {
        PRESS("Press"), HOLD("Hold 350 ms"), DOUBLE("Double press"), RELEASE("Release");
        public final String label;
        Activation(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public enum StickMode {
        PASS("Pass through"), JOYSTICK("Touch joystick"), CAMERA("Touch camera");
        public final String label;
        StickMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public static final String[] LAYERS = {"Gameplay", "Menu layer"};
    public static final String[] INPUTS = {"A", "B", "X", "Y", "LB", "RB", "LT", "RT", "L3", "R3", "Up", "Down", "Left", "Right", "View", "Menu", "LT full", "RT full"};
    public static final int[] KEY_CODES = {KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_THUMBL,
        KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_START};

    public static String inputForKey(int keyCode) {
        for (int i = 0; i < KEY_CODES.length; i++) if (KEY_CODES[i] == keyCode) return INPUTS[i];
        return null;
    }

    public static final class Binding {
        public Action action = Action.PASS;
        public Activation activation = Activation.PRESS;
        public float x = .75f, y = .70f;
        public boolean repeat;
        public JSONObject json() throws JSONException {
            return new JSONObject().put("action", action.name()).put("activation", activation.name())
                .put("x", x).put("y", y).put("repeat", repeat);
        }
        static Binding read(JSONObject json) throws JSONException {
            Binding result = new Binding();
            result.action = enumeration(Action.class, json.optString("action", "PASS"));
            result.activation = enumeration(Activation.class, json.optString("activation", "PRESS"));
            result.x = number(json, "x", .75f, 0, 1);
            result.y = number(json, "y", .70f, 0, 1);
            result.repeat = json.optBoolean("repeat", false);
            return result;
        }
    }
    public static final class Stick {
        public StickMode mode = StickMode.PASS;
        public float x, y, radius = .12f, deadzone = .10f, sensitivity = 1f;
        public String curve = "Linear";
        Stick(float x, float y) { this.x = x; this.y = y; }
        JSONObject json() throws JSONException {
            return new JSONObject().put("mode", mode.name()).put("x", x).put("y", y).put("radius", radius)
                .put("deadzone", deadzone).put("sensitivity", sensitivity).put("curve", curve);
        }
        static Stick read(JSONObject json, float defaultX, float defaultY) throws JSONException {
            Stick result = new Stick(defaultX, defaultY);
            result.mode = enumeration(StickMode.class, json.optString("mode", "PASS"));
            result.x = number(json, "x", defaultX, 0, 1); result.y = number(json, "y", defaultY, 0, 1);
            result.radius = number(json, "radius", .12f, .02f, .35f);
            result.deadzone = number(json, "deadzone", .10f, 0, .40f);
            result.sensitivity = number(json, "sensitivity", 1f, .1f, 4f);
            result.curve = json.optString("curve", "Linear");
            if (!List.of("Linear", "Precision", "Aggressive").contains(result.curve)) throw new JSONException("Unknown response curve");
            return result;
        }
    }
    public static final class Profile {
        public String id = UUID.randomUUID().toString();
        public String name = "Default gamepad", appPackage = "";
        public final Map<String, Binding> bindings = new LinkedHashMap<>();
        public Stick left = new Stick(.20f, .75f), right = new Stick(.75f, .50f);
        public float triggerSoft = .20f, triggerFull = .90f;
        public boolean gyroEnabled;
        public float gyroSensitivity = 2f, gyroSmoothing = .15f;
        public String gyroActivation = "While holding LT";
        public Binding binding(int layer, String input) {
            return bindings.computeIfAbsent(layer + ":" + input, key -> new Binding());
        }
        public JSONObject json() throws JSONException {
            JSONObject mapping = new JSONObject();
            for (Map.Entry<String, Binding> entry : bindings.entrySet()) mapping.put(entry.getKey(), entry.getValue().json());
            return new JSONObject().put("id", id).put("name", name).put("appPackage", appPackage).put("bindings", mapping)
                .put("left", left.json()).put("right", right.json()).put("triggerSoft", triggerSoft).put("triggerFull", triggerFull)
                .put("gyroEnabled", gyroEnabled).put("gyroSensitivity", gyroSensitivity)
                .put("gyroSmoothing", gyroSmoothing).put("gyroActivation", gyroActivation);
        }
        static Profile read(JSONObject json) throws JSONException {
            Profile result = new Profile();
            result.id = json.optString("id", result.id);
            result.name = json.optString("name", "Imported layout").trim();
            if (result.id.isEmpty() || result.id.length() > 100 || result.name.isEmpty() || result.name.length() > 80) throw new JSONException("Invalid profile name or ID");
            result.appPackage = json.optString("appPackage", "").trim();
            if (!result.appPackage.isEmpty() && !result.appPackage.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")) throw new JSONException("Invalid app package");
            JSONObject mapping = json.optJSONObject("bindings");
            for (int layer = 0; layer < 2; layer++) for (String input : INPUTS) {
                String key = layer + ":" + input;
                JSONObject item = mapping == null ? null : mapping.optJSONObject(key);
                result.bindings.put(key, item == null ? new Binding() : Binding.read(item));
            }
            result.left = Stick.read(json.optJSONObject("left") == null ? new JSONObject() : json.getJSONObject("left"), .20f, .75f);
            result.right = Stick.read(json.optJSONObject("right") == null ? new JSONObject() : json.getJSONObject("right"), .75f, .50f);
            result.triggerSoft = number(json, "triggerSoft", .20f, .01f, .80f);
            result.triggerFull = number(json, "triggerFull", .90f, .81f, 1f);
            if (result.triggerSoft >= result.triggerFull) throw new JSONException("Soft trigger threshold must be below full press");
            result.gyroEnabled = json.optBoolean("gyroEnabled", false);
            result.gyroSensitivity = number(json, "gyroSensitivity", 2f, .1f, 8f);
            result.gyroSmoothing = number(json, "gyroSmoothing", .15f, 0, .95f);
            result.gyroActivation = json.optString("gyroActivation", "While holding LT");
            if (!List.of("While holding LT", "Always on").contains(result.gyroActivation)) throw new JSONException("Invalid gyro activation");
            return result;
        }
    }
    public static final class Settings {
        public final List<Profile> profiles = new ArrayList<>();
        public String activeId;
        public boolean enabled, captureAnalog;
        public Profile active() {
            for (Profile profile : profiles) if (profile.id.equals(activeId)) return profile;
            return profiles.get(0);
        }
        public JSONObject json() throws JSONException {
            JSONArray list = new JSONArray();
            for (Profile profile : profiles) list.put(profile.json());
            return new JSONObject().put("format", "padstudio").put("version", 1).put("activeId", active().id)
                .put("enabled", enabled).put("captureAnalog", captureAnalog).put("profiles", list);
        }
        public static Settings read(String text, boolean importing) throws JSONException {
            JSONObject json = new JSONObject(text);
            if (!"padstudio".equals(json.optString("format")) || json.optInt("version") != 1) throw new JSONException("Choose a PadStudio v1 profile backup");
            JSONArray list = json.getJSONArray("profiles");
            if (list.length() < 1 || list.length() > 100) throw new JSONException("A backup must contain 1–100 profiles");
            Settings result = new Settings();
            for (int i = 0; i < list.length(); i++) {
                Profile profile = Profile.read(list.getJSONObject(i));
                for (Profile previous : result.profiles) if (previous.id.equals(profile.id)) throw new JSONException("Duplicate profile ID");
                result.profiles.add(profile);
            }
            result.activeId = json.optString("activeId", result.profiles.get(0).id);
            result.activeId = result.active().id;
            result.enabled = !importing && json.optBoolean("enabled", false);
            result.captureAnalog = !importing && json.optBoolean("captureAnalog", false);
            return result;
        }
        public static Settings defaults() {
            Settings settings = new Settings();
            Profile base = new Profile(); settings.profiles.add(base); settings.activeId = base.id;
            Profile touch = new Profile(); touch.name = "Touch shooter";
            for (String input : new String[]{"A", "B", "X", "Y", "RT"}) {
                Binding binding = touch.binding(0, input); binding.action = Action.TAP;
            }
            touch.binding(0, "A").x = .85f; touch.binding(0, "A").y = .75f;
            touch.binding(0, "RT").action = Action.TOUCH_HOLD;
            touch.left.mode = StickMode.JOYSTICK; touch.right.mode = StickMode.CAMERA;
            settings.profiles.add(touch);
            return settings;
        }
    }
    private static float number(JSONObject json, String key, float fallback, float min, float max) throws JSONException {
        double value = json.has(key) ? json.getDouble(key) : fallback;
        if (!Double.isFinite(value) || value < min || value > max) throw new JSONException("Out-of-range value for " + key);
        return (float) value;
    }
    private static <E extends Enum<E>> E enumeration(Class<E> type, String name) throws JSONException {
        try { return Enum.valueOf(type, name); }
        catch (IllegalArgumentException error) { throw new JSONException("Unsupported " + type.getSimpleName()); }
    }
}
