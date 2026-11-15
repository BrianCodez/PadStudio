package com.briancodez.padstudio;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MappingService extends AccessibilityService implements SensorEventListener {
    public static MappingService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Press> presses = new HashMap<>();
    private final Map<String, Long> lastDown = new HashMap<>();
    private final Set<String> swallowedKeys = new HashSet<>();
    private Store store;
    private Model.Settings settings;
    private TouchEngine touch;
    private SensorManager sensors;
    private String foreground = "";
    private FrameLayout picker;
    private boolean analogTicking;
    private final float[] cameraX = new float[2], cameraY = new float[2];
    private float pendingGyroX, pendingGyroY, smoothedX, smoothedY;
    private long sensorTime;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged = (preferences, key) -> {
        if ("settings".equals(key)) reload();
    };
    private static final class Press {
        final Model.Binding binding;
        final String contact;
        final boolean consumed;
        Runnable delayed, repeat;
        Press(Model.Binding binding, String contact) {
            this.binding = binding; this.contact = contact; consumed = binding.action != Model.Action.PASS;
        }
    }

    @Override protected void onServiceConnected() {
        instance = this;
        store = new Store(this); touch = new TouchEngine(this, () -> settings != null && settings.captureAnalog && ShellBridge.ready());
        sensors = getSystemService(SensorManager.class);
        store.preferences.registerOnSharedPreferenceChangeListener(preferencesChanged);
        reload();
    }
    private void reload() {
        resetInputs(); settings = store.load(); RuntimeState.layer = 0;
        refreshCapture();
    }
    public void refreshCapture() {
        if (settings == null) return;
        if (blocked()) resetInputs();
        if (Build.VERSION.SDK_INT >= 34) {
            AccessibilityServiceInfo info = getServiceInfo();
            info.setMotionEventSources(RuntimeState.testing || (settings.enabled && settings.captureAnalog && ShellBridge.ready())
                ? InputDevice.SOURCE_JOYSTICK : 0);
            setServiceInfo(info);
        }
        sensors.unregisterListener(this);
        sensorTime = 0;
        if (settings.enabled && settings.active().gyroEnabled && !RuntimeState.testing) {
            Sensor gyro = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (gyro != null) sensors.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME);
        }
        RuntimeState.status = settings.enabled
            ? settings.captureAnalog && !ShellBridge.ready() ? "Buttons active · connect Shizuku for analog output" : "Mapping active · " + settings.active().name
            : "Mapping paused";
        RuntimeState.notifyChanged();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null || settings == null) return;
        String app = event.getPackageName().toString();
        foreground = app;
        if (blocked()) { resetInputs(); return; }
        if (app.equals(getPackageName()) || app.equals("com.android.systemui")) return;
        if (settings.enabled) for (Model.Profile profile : settings.profiles) {
            if (!profile.appPackage.isEmpty() && profile.appPackage.equals(app) && !profile.id.equals(settings.activeId)) {
                settings.activeId = profile.id; store.save(settings); break;
            }
        }
        if (blocked()) resetInputs();
    }
    private boolean blocked() {
        return picker != null || RuntimeState.testing || !settings.enabled
            || (foreground.equals(getPackageName()) && !RuntimeState.playground)
            || foreground.equals("com.android.settings") || foreground.equals("com.android.systemui");
    }
    public static boolean isController(KeyEvent event) {
        InputDevice device = event.getDevice();
        return event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
            || (device != null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK)));
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        String input = Model.inputForKey(event.getKeyCode());
        if (input == null || !isController(event) || settings == null) return false;
        RuntimeState.lastInput = input + (event.getAction() == KeyEvent.ACTION_DOWN ? " pressed" : " released");
        RuntimeState.notifyChanged();
        String token = event.getDeviceId() + ":" + input;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (down && event.getRepeatCount() > 0) return swallowedKeys.contains(token);
        boolean swallowedDown = !down && swallowedKeys.remove(token);
        if (blocked()) return swallowedDown;
        boolean consumed = routeInput(event.getDeviceId(), input, down);
        if (down && consumed) swallowedKeys.add(token);
        return consumed || swallowedDown;
    }
    private boolean routeInput(int deviceId, String input, boolean down) {
        String token = deviceId + ":" + input;
        if (down) {
            Press existing = presses.get(token);
            if (existing != null) return existing.consumed;
            Model.Binding binding = settings.active().binding(RuntimeState.layer, input);
            Press press = new Press(binding, "button:" + token);
            presses.put(token, press);
            if (!press.consumed) return false;
            switch (binding.activation) {
                case PRESS -> activate(press);
                case HOLD -> {
                    press.delayed = () -> { if (presses.get(token) == press) activate(press); };
                    handler.postDelayed(press.delayed, 350);
                }
                case DOUBLE -> {
                    long now = SystemClock.uptimeMillis();
                    Long previous = lastDown.remove(token);
                    if (previous != null && now - previous <= 300) activate(press);
                    else lastDown.put(token, now);
                }
                case RELEASE -> { }
            }
            return true;
        }
        Press press = presses.remove(token);
        if (press == null) return false;
        if (press.delayed != null) handler.removeCallbacks(press.delayed);
        if (press.repeat != null) handler.removeCallbacks(press.repeat);
        if (press.consumed && press.binding.activation == Model.Activation.RELEASE) activateOnce(press);
        else if (press.binding.action == Model.Action.TOUCH_HOLD) touch.release(press.contact);
        if (input.equals("LT")) { pendingGyroX = 0; pendingGyroY = 0; sensorTime = 0; }
        return press.consumed;
    }
    private void activate(Press press) {
        activateOnce(press);
        if (press.binding.repeat && press.binding.action == Model.Action.TAP) {
            press.repeat = new Runnable() {
                @Override public void run() {
                    if (!presses.containsValue(press)) return;
                    activateOnce(press); handler.postDelayed(this, 120);
                }
            };
            handler.postDelayed(press.repeat, 120);
        }
    }
    private void activateOnce(Press press) {
        Model.Binding binding = press.binding;
        float[] size = displaySize();
        float x = screen(binding.x * size[0], size[0]), y = screen(binding.y * size[1], size[1]);
        switch (binding.action) {
            case TAP -> touch.tap(press.contact, x, y);
            case TOUCH_HOLD -> {
                if (binding.activation == Model.Activation.RELEASE) touch.slide(press.contact, x, y, x, y, 500);
                else touch.hold(press.contact, x, y);
            }
            case SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT -> {
                float distance = Math.min(size[0], size[1]) * .22f;
                float endX = x + (binding.action == Model.Action.SWIPE_LEFT ? -distance : binding.action == Model.Action.SWIPE_RIGHT ? distance : 0);
                float endY = y + (binding.action == Model.Action.SWIPE_UP ? -distance : binding.action == Model.Action.SWIPE_DOWN ? distance : 0);
                touch.slide(press.contact, x, y, screen(endX, size[0]), screen(endY, size[1]), 240);
            }
            case BACK -> performGlobalAction(GLOBAL_ACTION_BACK);
            case HOME -> performGlobalAction(GLOBAL_ACTION_HOME);
            case RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS);
            case LAYER -> {
                RuntimeState.layer = 1 - RuntimeState.layer;
                RuntimeState.status = "Active layer · " + Model.LAYERS[RuntimeState.layer];
                RuntimeState.notifyChanged();
            }
            case PASS -> { }
        }
    }
    @Override public void onMotionEvent(MotionEvent event) {
        if (settings == null) return;
        RuntimeState.motion(event);
        if (blocked()) return;
        int device = event.getDeviceId();
        float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X), hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        routeInput(device, "Left", hatX < -.5f); routeInput(device, "Right", hatX > .5f);
        routeInput(device, "Up", hatY < -.5f); routeInput(device, "Down", hatY > .5f);
        Model.Profile profile = settings.active();
        routeInput(device, "LT", RuntimeState.leftTrigger >= profile.triggerSoft);
        routeInput(device, "RT", RuntimeState.rightTrigger >= profile.triggerSoft);
        routeInput(device, "LT full", RuntimeState.leftTrigger >= profile.triggerFull);
        routeInput(device, "RT full", RuntimeState.rightTrigger >= profile.triggerFull);
        if (!analogTicking) { analogTicking = true; handler.post(analogTick); }
    }
    private final Runnable analogTick = new Runnable() {
        @Override public void run() {
            if (settings == null || blocked()) {
                if (touch != null) { touch.release("left"); touch.release("right"); }
                analogTicking = false; return;
            }
            Model.Profile profile = settings.active();
            float gyroX = pendingGyroX, gyroY = pendingGyroY;
            pendingGyroX = 0; pendingGyroY = 0;
            boolean left = updateStick("left", profile.left, RuntimeState.leftX, RuntimeState.leftY, 0, 0);
            boolean right = updateStick("right", profile.right, RuntimeState.rightX, RuntimeState.rightY, gyroX, gyroY);
            if (left || right) handler.postDelayed(this, 32);
            else analogTicking = false;
        }
    };
    private boolean updateStick(String id, Model.Stick stick, float rawX, float rawY, float gyroX, float gyroY) {
        int index = id.equals("left") ? 0 : 1;
        float[] value = settings.captureAnalog && stick.mode != Model.StickMode.PASS
            ? InputMath.stick(rawX, rawY, stick.deadzone, stick.sensitivity, stick.curve) : new float[]{0, 0};
        if (value[0] == 0 && value[1] == 0 && gyroX == 0 && gyroY == 0) {
            touch.release(id); cameraX[index] = 0; cameraY[index] = 0; return false;
        }
        float[] size = displaySize();
        float radius = Math.min(size[0], size[1]) * stick.radius;
        if (stick.mode != Model.StickMode.JOYSTICK) {
            cameraX[index] += value[0] * radius * .12f + gyroX;
            cameraY[index] += value[1] * radius * .12f + gyroY;
            if (Math.abs(cameraX[index]) > radius || Math.abs(cameraY[index]) > radius) {
                touch.release(id); cameraX[index] = 0; cameraY[index] = 0; return true;
            }
            touch.hold(id, screen(stick.x * size[0] + cameraX[index], size[0]), screen(stick.y * size[1] + cameraY[index], size[1]));
        } else touch.hold(id, screen(stick.x * size[0] + value[0] * radius + gyroX, size[0]), screen(stick.y * size[1] + value[1] * radius + gyroY, size[1]));
        return true;
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (settings == null || blocked()) { pendingGyroX = 0; pendingGyroY = 0; sensorTime = 0; return; }
        Model.Profile profile = settings.active();
        boolean aiming = profile.gyroActivation.equals("Always on") || presses.keySet().stream().anyMatch(key -> key.endsWith(":LT"));
        if (!profile.gyroEnabled || !aiming) { sensorTime = 0; pendingGyroX = 0; pendingGyroY = 0; return; }
        float dt = sensorTime == 0 ? 0 : Math.min(.05f, (event.timestamp - sensorTime) / 1_000_000_000f);
        sensorTime = event.timestamp;
        float dx = -event.values[1], dy = -event.values[0];
        int rotation = getSystemService(WindowManager.class).getDefaultDisplay().getRotation();
        if (rotation == Surface.ROTATION_90) { float old = dx; dx = -dy; dy = old; }
        if (rotation == Surface.ROTATION_270) { float old = dx; dx = dy; dy = -old; }
        if (rotation == Surface.ROTATION_180) { dx = -dx; dy = -dy; }
        float smoothing = profile.gyroSmoothing;
        smoothedX = smoothedX * smoothing + dx * (1 - smoothing);
        smoothedY = smoothedY * smoothing + dy * (1 - smoothing);
        if (Math.abs(smoothedX) + Math.abs(smoothedY) < .025f) return;
        float[] size = displaySize();
        pendingGyroX += smoothedX * dt * profile.gyroSensitivity * size[0] * .22f;
        pendingGyroY += smoothedY * dt * profile.gyroSensitivity * size[1] * .22f;
        if (!analogTicking) { analogTicking = true; handler.post(analogTick); }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    private float[] displaySize() {
        android.graphics.Rect bounds = getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        return new float[]{bounds.width(), bounds.height()};
    }
    private static float screen(float value, float max) { return Math.max(1, Math.min(max - 2, value)); }

    public void showPicker(String profileId, int layer, String target) {
        closePicker(); resetInputs();
        WindowManager windows = getSystemService(WindowManager.class);
        picker = new FrameLayout(this);
        picker.setBackgroundColor(0x33000000);
        LinearLayout banner = new LinearLayout(this); banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(24, 24, 24, 16); banner.setBackgroundColor(0xF0101823);
        TextView title = new TextView(this); title.setTextColor(Color.WHITE); title.setTextSize(18);
        title.setText("Tap the screen target for " + target);
        banner.addView(title);
        Button cancel = new Button(this); cancel.setText("Cancel placement"); cancel.setOnClickListener(view -> { closePicker(); returnToEditor(); });
        banner.addView(cancel);
        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        picker.addView(banner, bannerParams);
        picker.setOnTouchListener((view, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float[] size = displaySize();
            Model.Settings saved = store.load();
            for (Model.Profile profile : saved.profiles) if (profile.id.equals(profileId)) {
                float x = Math.max(0, Math.min(1, event.getRawX() / size[0]));
                float y = Math.max(0, Math.min(1, event.getRawY() / size[1]));
                if (target.equals("Left stick")) { profile.left.x = x; profile.left.y = y; }
                else if (target.equals("Right stick")) { profile.right.x = x; profile.right.y = y; }
                else { profile.binding(layer, target).x = x; profile.binding(layer, target).y = y; }
                store.save(saved); break;
            }
            closePicker(); returnToEditor(); return true;
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try { windows.addView(picker, params); }
        catch (RuntimeException error) { picker = null; RuntimeState.status = "Could not open target picker"; RuntimeState.notifyChanged(); }
    }
    private void closePicker() {
        if (picker == null) return;
        getSystemService(WindowManager.class).removeView(picker); picker = null;
    }
    private void returnToEditor() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }
    private void resetInputs() {
        for (Press press : presses.values()) {
            if (press.delayed != null) handler.removeCallbacks(press.delayed);
            if (press.repeat != null) handler.removeCallbacks(press.repeat);
        }
        presses.clear(); lastDown.clear();
        handler.removeCallbacks(analogTick); analogTicking = false;
        cameraX[0] = 0; cameraX[1] = 0; cameraY[0] = 0; cameraY[1] = 0;
        pendingGyroX = 0; pendingGyroY = 0; smoothedX = 0; smoothedY = 0;
        if (touch != null) touch.releaseAll();
    }
    @Override public void onInterrupt() { resetInputs(); }
    @Override public void onDestroy() {
        closePicker(); resetInputs();
        ShellBridge.clear();
        if (sensors != null) sensors.unregisterListener(this);
        if (store != null) store.preferences.unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        instance = null; RuntimeState.status = "Accessibility service disconnected"; RuntimeState.notifyChanged();
        super.onDestroy();
    }
}
