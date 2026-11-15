package com.briancodez.padstudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class MainActivity extends Activity {
    static final int BG = 0xFF101823, SURFACE = 0xFF192432, RAISED = 0xFF243346,
        INK = 0xFFEEF5FC, SOFT = 0xFFA9BBCE, ACCENT = 0xFF66C0F4, LINE = 0xFF33465B;
    private static final int EXPORT = 100, IMPORT = 101;
    private Store store;
    private Model.Settings settings;
    private LinearLayout page;
    private String screen = "editor", tab = "Buttons", selected = "A";
    private int layer, stickIndex;
    private boolean dirty;
    private Typeface regular, semibold;
    private Button saveButton, engineButton;
    private TextView statusText, testInput, testAxes, testDevices;
    private TextView shellStatus;
    private CurveView curve, tester;
    private TextView playgroundCount;
    public int playgroundTouches;
    private final Runnable runtimeListener = () -> runOnUiThread(this::refreshStatus);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this); settings = store.load();
        try {
            regular = Typeface.createFromAsset(getAssets(), "Barlow-Regular.ttf");
            semibold = Typeface.createFromAsset(getAssets(), "Barlow-SemiBold.ttf");
        } catch (RuntimeException error) { regular = Typeface.DEFAULT; semibold = Typeface.DEFAULT_BOLD; }
        if (state != null) {
            screen = state.getString("screen", "editor"); tab = state.getString("tab", "Buttons");
            selected = state.getString("selected", "A"); layer = state.getInt("layer"); stickIndex = state.getInt("stick");
            String draft = state.getString("draft");
            if (draft != null) try { settings = Model.Settings.read(draft, false); dirty = state.getBoolean("dirty"); } catch (Exception ignored) { }
        }
        render();
    }
    @Override protected void onResume() {
        super.onResume();
        if (!dirty) settings = store.load();
        RuntimeState.listeners.addIfAbsent(runtimeListener);
        RuntimeState.testing = screen.equals("test"); RuntimeState.playground = screen.equals("playground");
        if (MappingService.instance != null) MappingService.instance.refreshCapture();
        render();
    }
    @Override protected void onPause() {
        RuntimeState.listeners.remove(runtimeListener);
        RuntimeState.testing = false; RuntimeState.playground = false;
        if (MappingService.instance != null) MappingService.instance.refreshCapture();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("screen", screen); state.putString("tab", tab); state.putString("selected", selected);
        state.putInt("layer", layer); state.putInt("stick", stickIndex); state.putBoolean("dirty", dirty);
        try { state.putString("draft", settings.json().toString()); } catch (Exception ignored) { }
    }
    private Model.Profile profile() { return settings.active(); }
    private boolean wide() {
        android.content.res.Configuration config = getResources().getConfiguration();
        return config.screenWidthDp >= 840 || (config.screenWidthDp >= 600 && config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable result = new GradientDrawable(); result.setColor(color); result.setCornerRadius(dp(radius)); return result;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(bold ? semibold : regular); return view;
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private void gap(LinearLayout parent, int height) { View gap = new View(this); parent.addView(gap, new LinearLayout.LayoutParams(1, dp(height))); }
    private void line(LinearLayout parent) { View line = new View(this); line.setBackgroundColor(LINE); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1)); params.topMargin = dp(15); params.bottomMargin = dp(15); parent.addView(line, params); }
    private Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTypeface(semibold); button.setTextSize(15);
        button.setTextColor(primary ? BG : INK); button.setBackground(background(primary ? ACCENT : RAISED, 7));
        button.setMinHeight(dp(46)); button.setMinimumHeight(dp(46)); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setPadding(dp(14), dp(7), dp(14), dp(7)); button.setOnClickListener(view -> action.run());
        return button;
    }
    private void fullButton(LinearLayout parent, String label, boolean primary, Runnable action) {
        Button control = button(label, primary, action); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(10); parent.addView(control, params);
    }
    private void helper(LinearLayout parent, String message) { TextView view = text(message, 14, SOFT, false); view.setPadding(0, dp(7), 0, dp(3)); parent.addView(view); }
    private void heading(LinearLayout parent, String value) { parent.addView(text(value, 21, INK, true)); }
    private void changed() { dirty = true; refreshSave(); }
    private void refreshSave() {
        if (saveButton != null) { saveButton.setText(dirty ? "Save layout" : "Saved"); saveButton.setEnabled(dirty); saveButton.setAlpha(dirty ? 1 : .65f); }
    }
    private boolean save() {
        try { store.save(settings); dirty = false; refreshSave(); return true; }
        catch (RuntimeException error) { message("Could not save", error.getMessage()); return false; }
    }
    private void navigate(String destination) {
        if (!save()) return;
        screen = destination;
        RuntimeState.testing = destination.equals("test"); RuntimeState.playground = destination.equals("playground");
        if (MappingService.instance != null) MappingService.instance.refreshCapture();
        render();
    }
    private void render() {
        statusText = null; shellStatus = null; testInput = null; testAxes = null; testDevices = null; tester = null; curve = null; saveButton = null; engineButton = null;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        page = column(); page.setPadding(dp(22), dp(16), dp(22), dp(24));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2)); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        scroll.requestApplyInsets();
        if (!screen.equals("editor")) {
            LinearLayout header = row();
            header.addView(button("‹ Layouts", false, () -> navigate("editor")));
            TextView title = text(screen.equals("setup") ? "Setup & profiles" : screen.equals("test") ? "Controller test" : "Touch playground", 25, INK, true);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1); titleParams.leftMargin = dp(14); header.addView(title, titleParams);
            page.addView(header); gap(page, 20);
        }
        switch (screen) {
            case "setup" -> renderSetup();
            case "test" -> renderTest();
            case "playground" -> renderPlayground();
            default -> renderEditor();
        }
        refreshStatus();
    }
    private void renderEditor() {
        boolean wide = wide();
        LinearLayout header = row();
        LinearLayout title = column(); title.addView(text("PadStudio", wide ? 25 : 29, INK, true));
        if (!wide) title.addView(text("Controller layouts", 13, SOFT, false));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        saveButton = button(dirty ? "Save layout" : "Saved", true, this::save);
        LinearLayout tools = row();
        engineButton = button(settings.enabled ? "Pause mapping" : "Start mapping", settings.enabled, this::toggleEngine);
        tools.addView(engineButton, new LinearLayout.LayoutParams(wide ? -2 : 0, -2, wide ? 0 : 1));
        LinearLayout.LayoutParams setupParams = new LinearLayout.LayoutParams(-2, -2); setupParams.leftMargin = dp(9);
        tools.addView(button("Setup", false, () -> navigate("setup")), setupParams);
        if (wide) {
            header.addView(tools);
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-2, -2); saveParams.leftMargin = dp(9); header.addView(saveButton, saveParams);
            page.addView(header);
        } else {
            header.addView(saveButton); page.addView(header); gap(page, 18); page.addView(tools);
        }
        refreshSave();
        statusText = text("", 13, SOFT, false); statusText.setPadding(0, dp(wide ? 5 : 9), 0, 0); page.addView(statusText);
        if (wide) gap(page, 10); else { line(page); page.addView(text("PROFILE", 12, SOFT, true)); }
        LinearLayout profileRow = row();
        String[] names = settings.profiles.stream().map(item -> item.name).toArray(String[]::new);
        int active = settings.profiles.indexOf(profile());
        Spinner profiles = spinner(names, active, index -> {
            if (index == settings.profiles.indexOf(profile())) return;
            settings.activeId = settings.profiles.get(index).id;
            if (save()) { layer = 0; render(); }
        });
        profiles.setContentDescription("Profile");
        profileRow.addView(profiles, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button manage = button("•••", false, this::profileMenu); manage.setContentDescription("Manage profiles");
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(dp(50), dp(46)); manageParams.leftMargin = dp(9); profileRow.addView(manage, manageParams);
        LinearLayout layers = row();
        for (int i = 0; i < Model.LAYERS.length; i++) {
            final int current = i;
            Button control = button(wide && i == 1 ? "Menu" : Model.LAYERS[i], i == layer, () -> { layer = current; render(); });
            if (wide) { control.setTextSize(13); control.setPadding(dp(7), dp(7), dp(7), dp(7)); }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); if (i > 0) params.leftMargin = dp(6); layers.addView(control, params);
        }
        LinearLayout tabs = row();
        for (String name : new String[]{"Buttons", "Sticks", "Triggers", "Gyro"}) {
            Button control = button(name, false, () -> { tab = name; render(); });
            control.setTextColor(name.equals(tab) ? ACCENT : SOFT); control.setBackground(background(name.equals(tab) ? SURFACE : BG, 5));
            control.setPadding(dp(3), dp(9), dp(3), dp(9));
            if (wide) control.setTextSize(13);
            tabs.addView(control, new LinearLayout.LayoutParams(0, -2, 1));
        }
        if (wide) {
            LinearLayout toolbar = row();
            toolbar.addView(profileRow, new LinearLayout.LayoutParams(0, -2, 1.15f));
            LinearLayout.LayoutParams layerParams = new LinearLayout.LayoutParams(0, -2, .8f); layerParams.leftMargin = dp(10); toolbar.addView(layers, layerParams);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, -2, 1.15f); tabParams.leftMargin = dp(10); toolbar.addView(tabs, tabParams);
            page.addView(toolbar); gap(page, 14);
        } else {
            page.addView(profileRow); gap(page, 14); page.addView(layers); gap(page, 15); page.addView(tabs); gap(page, 18);
        }
        switch (tab) {
            case "Sticks" -> renderSticks();
            case "Triggers" -> renderTriggers();
            case "Gyro" -> renderGyro();
            default -> renderButtons();
        }
        gap(page, 12);
        LinearLayout bottom = row();
        bottom.addView(button("Controller test", false, () -> navigate("test")), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(0, -2, 1); playParams.leftMargin = dp(8);
        bottom.addView(button("Touch playground", false, () -> navigate("playground")), playParams); page.addView(bottom);
    }
    private LinearLayout inspector(String title) {
        LinearLayout panel = column(); panel.setPadding(dp(18), dp(18), dp(18), dp(18)); panel.setBackground(background(SURFACE, 11));
        heading(panel, title); gap(panel, 10); return panel;
    }
    private void workspace(LinearLayout visual, LinearLayout inspector) {
        boolean wide = wide();
        if (wide) {
            LinearLayout workspace = new LinearLayout(this); workspace.setOrientation(LinearLayout.HORIZONTAL); workspace.setGravity(Gravity.TOP);
            workspace.addView(visual, new LinearLayout.LayoutParams(0, -2, 1.3f));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); params.leftMargin = dp(23); workspace.addView(inspector, params); page.addView(workspace);
        } else { page.addView(visual); gap(page, 16); page.addView(inspector); }
    }
    private void renderButtons() {
        LinearLayout visual = column(); visual.addView(text("Choose an input", 16, INK, true));
        PadView pad = new PadView(this); pad.selected = selected; pad.onSelect = input -> { selected = input; render(); }; visual.addView(pad, new LinearLayout.LayoutParams(-1, -2));
        GridLayout inputs = new GridLayout(this); inputs.setColumnCount(6);
        for (String input : Model.INPUTS) {
            Button control = button(input, input.equals(selected), () -> { selected = input; render(); });
            control.setTextSize(13); control.setPadding(dp(1), dp(4), dp(1), dp(4));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.width = 0; params.height = dp(43); params.setMargins(dp(2), dp(3), dp(2), dp(3)); inputs.addView(control, params);
        }
        if (!wide()) visual.addView(inputs);
        Model.Binding binding = profile().binding(layer, selected);
        LinearLayout panel = inspector(selected + " · assignment");
        select(panel, "Action", Model.Action.values(), binding.action, value -> { binding.action = (Model.Action) value; changed(); render(); });
        select(panel, "Activation", Model.Activation.values(), binding.activation, value -> {
            binding.activation = (Model.Activation) value;
            if (binding.activation == Model.Activation.RELEASE) binding.repeat = false;
            changed(); render();
        });
        if (binding.action.usesTouch()) {
            range(panel, "Horizontal target", 0, 100, Math.round(binding.x * 100), "%", value -> { binding.x = value / 100f; changed(); });
            range(panel, "Vertical target", 0, 100, Math.round(binding.y * 100), "%", value -> { binding.y = value / 100f; changed(); });
            fullButton(panel, "Place target on screen", false, () -> placeTarget(selected));
            helper(panel, "Targets use the full display. Place them in the orientation you play in.");
        }
        if (binding.action == Model.Action.TAP && binding.activation != Model.Activation.RELEASE) toggle(panel, "Repeat while held", binding.repeat, value -> { binding.repeat = value; changed(); });
        if (binding.action == Model.Action.PASS) helper(panel, "This input reaches the game normally.");
        if (binding.action == Model.Action.TOUCH_HOLD && binding.activation == Model.Activation.RELEASE) helper(panel, "Release activation sends a 500 ms touch.");
        if (selected.endsWith("full")) helper(panel, "Full-stage trigger bindings need analog capture on Android 14 or newer.");
        if (wide()) select(panel, "Input", Model.INPUTS, selected, value -> { selected = value.toString(); render(); });
        workspace(visual, panel);
    }
    private void renderSticks() {
        LinearLayout visual = column();
        select(visual, "Stick", new String[]{"Left stick", "Right stick"}, stickIndex == 0 ? "Left stick" : "Right stick", value -> { stickIndex = value.equals("Left stick") ? 0 : 1; render(); });
        Model.Stick stick = stickIndex == 0 ? profile().left : profile().right;
        curve = new CurveView(this); curve.stick = stick; visual.addView(curve);
        helper(visual, "The curve shows how stick travel becomes touch movement.");
        LinearLayout panel = inspector(stickIndex == 0 ? "Left stick" : "Right stick");
        select(panel, "Output", Model.StickMode.values(), stick.mode, value -> { stick.mode = (Model.StickMode) value; changed(); });
        range(panel, "Deadzone", 0, 40, Math.round(stick.deadzone * 100), "%", value -> { stick.deadzone = value / 100f; changed(); curve.invalidate(); });
        select(panel, "Response curve", new String[]{"Linear", "Precision", "Aggressive"}, stick.curve, value -> { stick.curve = value.toString(); changed(); curve.invalidate(); });
        range(panel, "Sensitivity", 10, 400, Math.round(stick.sensitivity * 100), "%", value -> { stick.sensitivity = value / 100f; changed(); curve.invalidate(); });
        range(panel, "Touch radius", 2, 35, Math.round(stick.radius * 100), "%", value -> { stick.radius = value / 100f; changed(); });
        range(panel, "Anchor X", 0, 100, Math.round(stick.x * 100), "%", value -> { stick.x = value / 100f; changed(); });
        range(panel, "Anchor Y", 0, 100, Math.round(stick.y * 100), "%", value -> { stick.y = value / 100f; changed(); });
        fullButton(panel, "Place stick anchor", false, () -> placeTarget(stickIndex == 0 ? "Left stick" : "Right stick"));
        helper(panel, "Touch outputs require Shizuku and analog capture in Setup, available on Android 14+. Pass-through games should keep analog capture off.");
        workspace(visual, panel);
    }
    private void renderTriggers() {
        LinearLayout panel = inspector("Two-stage triggers");
        helper(panel, "LT and RT activate at soft press. LT full and RT full activate at full press. Assign their actions in Buttons.");
        range(panel, "Soft press threshold", 1, 80, Math.round(profile().triggerSoft * 100), "%", value -> {
            profile().triggerSoft = value / 100f;
            if (profile().triggerFull <= profile().triggerSoft) profile().triggerFull = Math.min(1, profile().triggerSoft + .05f);
            changed();
        });
        range(panel, "Full press threshold", 81, 100, Math.round(profile().triggerFull * 100), "%", value -> { profile().triggerFull = value / 100f; changed(); });
        helper(panel, "Analog thresholds require analog capture on Android 14+. Controllers that send digital L2/R2 buttons can use LT/RT bindings without analog capture.");
        fullButton(panel, "Test trigger travel", false, () -> navigate("test")); page.addView(panel);
    }
    private void renderGyro() {
        LinearLayout panel = inspector("Gyro touch aiming");
        Sensor gyro = getSystemService(SensorManager.class).getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        helper(panel, gyro == null ? "This device does not expose a gyroscope. Settings can still be prepared for your handheld." : "Uses this device’s gyroscope. Aim is sent to the right-stick touch anchor.");
        toggle(panel, "Enable device gyro", profile().gyroEnabled, value -> { profile().gyroEnabled = value; changed(); });
        select(panel, "Activation", new String[]{"While holding LT", "Always on"}, profile().gyroActivation, value -> { profile().gyroActivation = value.toString(); changed(); });
        range(panel, "Sensitivity", 10, 800, Math.round(profile().gyroSensitivity * 100), "%", value -> { profile().gyroSensitivity = value / 100f; changed(); });
        range(panel, "Smoothing", 0, 95, Math.round(profile().gyroSmoothing * 100), "%", value -> { profile().gyroSmoothing = value / 100f; changed(); });
        fullButton(panel, "Place aiming anchor", false, () -> placeTarget("Right stick"));
        helper(panel, "Controller gyroscope protocols and flick stick are planned for a later release. This version reads the handheld or phone’s sensor."); page.addView(panel);
    }
    private void toggleEngine() {
        if (MappingService.instance == null) { navigate("setup"); return; }
        settings.enabled = !settings.enabled;
        if (save()) render();
    }
    private void renderSetup() {
        LinearLayout service = inspector("Controller mapping service");
        statusText = text(MappingService.instance == null ? "Accessibility service is off" : "Accessibility service is connected", 15, ACCENT, true); service.addView(statusText);
        helper(service, "PadStudio intercepts assigned controller buttons and sends screen gestures or Android navigation. Optional foreground app detection switches profiles. Target placement uses an accessibility overlay. Screen content is not read and the app has no network permission.");
        fullButton(service, "Open accessibility settings", true, () -> {
            if (!save()) return;
            new AlertDialog.Builder(this).setTitle("Enable PadStudio mapping")
                .setMessage("In Downloaded apps, choose PadStudio controller mapping and enable it. Android may require Allow restricted settings in App info for sideloaded apps. Enable the service when you want controller mapping.")
                .setPositiveButton("Open settings", (dialog, which) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Cancel", null).show();
        });
        toggle(service, "Capture analog controller input", settings.captureAnalog, value -> {
            if (value && Build.VERSION.SDK_INT < 34) { message("Android 14 required", "Button mappings work on Android 11+. Analog capture needs Android 14 or newer."); render(); return; }
            settings.captureAnalog = value; changed(); save();
        });
        helper(service, "Analog capture requires a connected Shizuku touch engine. It redirects joystick motion to PadStudio, including unmapped axes. Keep it off for native gamepad games.");
        shellStatus = text(ShellBridge.status(), 14, ACCENT, true); service.addView(shellStatus);
        fullButton(service, "Connect Shizuku touch engine", false, () -> {
            ShellBridge.connect(true); refreshStatus();
            if (!ShellBridge.ready() && ShellBridge.status().contains("Start Shizuku")) {
                new AlertDialog.Builder(this).setTitle("Start Shizuku for analog touch output")
                    .setMessage("Start Shizuku using wireless debugging or root, return here, and connect PadStudio. Shizuku keeps controller motion from cancelling mapped touches. Button-only mapping works through accessibility.")
                    .setPositiveButton("Open Shizuku", (dialog, which) -> {
                        Intent shizuku = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                        startActivity(shizuku == null ? new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")) : shizuku);
                    }).setNegativeButton("Later", null).show();
            }
        });
        fullButton(service, "Add quick settings pause tile", false, () -> {
            if (Build.VERSION.SDK_INT >= 33) {
                getSystemService(android.app.StatusBarManager.class).requestAddTileService(
                    new android.content.ComponentName(this, MappingTileService.class), "PadStudio",
                    android.graphics.drawable.Icon.createWithResource(this, com.briancodez.padstudio.R.drawable.ic_tile),
                    getMainExecutor(), result -> toast("Use the PadStudio tile to pause or resume mapping"));
            } else message("Add PadStudio to quick settings", "Open Android quick settings, tap Edit, and drag the PadStudio tile into your active tiles. It pauses or resumes mapping from any app.");
        });
        page.addView(service); gap(page, 18);
        LinearLayout target = inspector("Game profile");
        helper(target, "Linked app · " + (profile().appPackage.isEmpty() ? "Any app, selected manually" : profile().appPackage));
        fullButton(target, "Choose game for this profile", false, this::chooseApp);
        helper(target, "Matching a foreground game activates this profile while mapping is running.");
        fullButton(target, "Launch linked game", false, this::launchGame); page.addView(target); gap(page, 18);
        LinearLayout backups = inspector("Profiles & updates");
        fullButton(backups, "Manage profiles", false, this::profileMenu);
        fullButton(backups, "Export profile backup", false, () -> {
            if (!save()) return;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "padstudio-profiles.json");
            try { startActivityForResult(intent, EXPORT); } catch (RuntimeException error) { message("File picker unavailable", "Install or enable an Android document provider to export profiles."); }
        });
        fullButton(backups, "Import profile backup", false, () -> {
            if (!save()) return;
            try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT); }
            catch (RuntimeException error) { message("File picker unavailable", "Install or enable an Android document provider to import profiles."); }
        });
        fullButton(backups, "GitHub releases / Obtainium", false, () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/BrianCodez/PadStudio/releases"))));
        helper(backups, "PadStudio 0.1.0 · com.briancodez.padstudio"); page.addView(backups);
    }
    private void renderTest() {
        heading(page, "Live controller input");
        testDevices = text(controllerNames(), 14, SOFT, false); page.addView(testDevices); gap(page, 15);
        testInput = text(RuntimeState.lastInput, 23, ACCENT, true); testInput.setMinHeight(dp(50)); page.addView(testInput);
        tester = new CurveView(this); tester.tester = true; page.addView(tester);
        testAxes = text("", 16, INK, false); page.addView(testAxes);
        helper(page, "Mapping is suspended on this screen. Press buttons, move both sticks, and pull triggers to check your controller.");
        helper(page, "A physical controller is needed to validate its axis layout and sensor behavior. Different controllers can expose different right-stick or trigger axes.");
        fullButton(page, "Refresh connected controllers", false, () -> { testDevices.setText(controllerNames()); refreshStatus(); });
    }
    private String controllerNames() {
        List<String> names = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK))) names.add(device.getName());
        }
        return names.isEmpty() ? "No physical controller detected" : String.join("\n", names);
    }
    private void renderPlayground() {
        heading(page, "Verify touch mapping");
        helper(page, "Save a Screen tap binding and start mapping. Place its target on the large area below, then press that controller button. The counter records delivered touches.");
        playgroundCount = text("Touches received · " + playgroundTouches, 24, INK, true); playgroundCount.setGravity(Gravity.CENTER);
        playgroundCount.setBackground(background(RAISED, 14)); playgroundCount.setMinHeight(dp(300));
        playgroundCount.setContentDescription("Touch test target");
        playgroundCount.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                playgroundTouches++; playgroundCount.setText("Touches received · " + playgroundTouches); view.performClick();
            }
            return true;
        });
        page.addView(playgroundCount, new LinearLayout.LayoutParams(-1, dp(330)));
        fullButton(page, "Reset touch counter", false, () -> { playgroundTouches = 0; playgroundCount.setText("Touches received · 0"); });
        statusText = text("", 14, ACCENT, false); page.addView(statusText);
    }
    private void refreshStatus() {
        if (shellStatus != null) shellStatus.setText(ShellBridge.status());
        if (statusText != null) {
            if (MappingService.instance == null) statusText.setText("Enable the accessibility service in Setup");
            else if (screen.equals("setup")) statusText.setText("Accessibility service connected");
            else statusText.setText(RuntimeState.status);
        }
        if (testInput != null) testInput.setText(RuntimeState.lastInput);
        if (testAxes != null) testAxes.setText(String.format(Locale.US, "Left   X %+.2f   Y %+.2f\nRight X %+.2f   Y %+.2f\nLT %.0f%%      RT %.0f%%", RuntimeState.leftX, RuntimeState.leftY, RuntimeState.rightX, RuntimeState.rightY, RuntimeState.leftTrigger * 100, RuntimeState.rightTrigger * 100));
        if (tester != null) tester.invalidate();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (screen.equals("test") && MappingService.isController(event)) {
            String input = Model.inputForKey(event.getKeyCode());
            RuntimeState.lastInput = (input == null ? KeyEvent.keyCodeToString(event.getKeyCode()) : input) + (event.getAction() == KeyEvent.ACTION_DOWN ? " pressed" : " released");
            refreshStatus(); return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (screen.equals("test") && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) { RuntimeState.motion(event); return true; }
        return super.dispatchGenericMotionEvent(event);
    }
    private Spinner spinner(Object[] options, int selectedIndex, IntConsumer consumer) {
        Spinner spinner = new Spinner(this); spinner.setBackground(background(RAISED, 6)); spinner.setContentDescription("Choose an option");
        ArrayAdapter<Object> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options) {
            @Override public View getView(int position, View convertView, ViewGroup parent) { return item(position, false); }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) { return item(position, true); }
            private TextView item(int position, boolean dropdown) {
                TextView view = text(String.valueOf(getItem(position)), 16, INK, false); view.setGravity(Gravity.CENTER_VERTICAL);
                view.setPadding(dp(12), dp(10), dp(12), dp(10)); view.setMinHeight(dp(48)); view.setBackgroundColor(dropdown ? SURFACE : Color.TRANSPARENT); return view;
            }
        };
        spinner.setAdapter(adapter); spinner.setSelection(Math.max(0, selectedIndex));
        final int[] previous = {Math.max(0, selectedIndex)};
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int index, long id) { if (previous[0] != index) { previous[0] = index; consumer.accept(index); } }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        return spinner;
    }
    private void select(LinearLayout parent, String label, Object[] options, Object selectedValue, Consumer<Object> consumer) {
        gap(parent, 10); parent.addView(text(label, 14, SOFT, false)); gap(parent, 6);
        int index = 0; for (int i = 0; i < options.length; i++) if (Objects.equals(options[i], selectedValue)) index = i;
        Spinner spinner = spinner(options, index, position -> consumer.accept(options[position])); spinner.setContentDescription(label);
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));
    }
    private void range(LinearLayout parent, String label, int min, int max, int current, String unit, IntConsumer consumer) {
        gap(parent, 17); LinearLayout labels = row(); labels.addView(text(label, 14, SOFT, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(current + unit, 14, INK, true); labels.addView(value); parent.addView(labels);
        SeekBar seek = new SeekBar(this); seek.setMin(min); seek.setMax(max); seek.setProgress(current); seek.setContentDescription(label);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT)); seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setPadding(dp(8), 0, dp(8), 0); parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(42)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int amount, boolean fromUser) { if (fromUser) { value.setText(amount + unit); consumer.accept(amount); } }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }
    private void toggle(LinearLayout parent, String label, boolean value, Consumer<Boolean> consumer) {
        Switch control = new Switch(this); control.setText(label); control.setTypeface(regular); control.setTextColor(INK); control.setTextSize(16);
        control.setPadding(0, dp(13), 0, dp(10)); control.setMinHeight(dp(52)); control.setChecked(value);
        control.setOnCheckedChangeListener((button, checked) -> consumer.accept(checked)); parent.addView(control);
    }
    private void profileMenu() {
        String[] options = {"New profile", "Duplicate current profile", "Rename current profile", "Delete current profile"};
        new AlertDialog.Builder(this).setTitle("Profiles").setItems(options, (dialog, which) -> {
            if (which == 3) {
                if (settings.profiles.size() == 1) { toast("Keep at least one profile"); return; }
                new AlertDialog.Builder(this).setTitle("Delete " + profile().name + "?").setMessage("The other profiles will remain available.")
                    .setPositiveButton("Delete", (confirmation, button) -> { settings.profiles.remove(profile()); settings.activeId = settings.profiles.get(0).id; save(); render(); })
                    .setNegativeButton("Cancel", null).show();
            } else nameProfile(which);
        }).show();
    }
    private void nameProfile(int action) {
        EditText name = new EditText(this); name.setSingleLine(); name.setMaxLines(1); name.setText(action == 0 ? "New layout" : action == 1 ? profile().name + " copy" : profile().name);
        name.setSelectAllOnFocus(true); name.setPadding(dp(24), dp(16), dp(24), dp(16));
        new AlertDialog.Builder(this).setTitle(action == 2 ? "Rename profile" : "Profile name").setView(name)
            .setPositiveButton("Save", (dialog, which) -> {
                String value = name.getText().toString().trim();
                if (value.isEmpty() || value.length() > 80) { toast("Use a name between 1 and 80 characters"); return; }
                if (settings.profiles.size() >= 100 && action != 2) { toast("A maximum of 100 profiles is supported"); return; }
                try {
                    Model.Profile result = action == 0 ? new Model.Profile() : action == 1 ? Model.Profile.read(profile().json()) : profile();
                    if (action == 1) result.id = java.util.UUID.randomUUID().toString();
                    result.name = value;
                    if (action != 2) { settings.profiles.add(result); settings.activeId = result.id; }
                    if (save()) render();
                } catch (Exception error) { message("Could not create profile", error.getMessage()); }
            }).setNegativeButton("Cancel", null).show();
    }
    private void chooseApp() {
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        apps.sort(Comparator.comparing(info -> info.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT)));
        List<String> labels = new ArrayList<>(); labels.add("Any app · select manually");
        for (ResolveInfo info : apps) labels.add(info.loadLabel(getPackageManager()) + "\n" + info.activityInfo.packageName);
        new AlertDialog.Builder(this).setTitle("Choose game").setItems(labels.toArray(new String[0]), (dialog, which) -> {
            profile().appPackage = which == 0 ? "" : apps.get(which - 1).activityInfo.packageName; changed(); save(); render();
        }).setNeutralButton("Enter package", (dialog, which) -> {
            EditText input = new EditText(this); input.setSingleLine(); input.setText(profile().appPackage); input.setHint("com.example.game");
            new AlertDialog.Builder(this).setTitle("Game package").setView(input).setPositiveButton("Save", (confirmation, button) -> {
                String value = input.getText().toString().trim();
                if (!value.isEmpty() && !value.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")) { toast("Enter a valid Android package name"); return; }
                profile().appPackage = value; changed(); save(); render();
            }).setNegativeButton("Cancel", null).show();
        }).setNegativeButton("Cancel", null).show();
    }
    private void launchGame() {
        if (!save()) return;
        Intent intent = getPackageManager().getLaunchIntentForPackage(profile().appPackage);
        if (intent == null) { toast("Choose an installed game for this profile first"); return; }
        startActivity(intent);
    }
    private void placeTarget(String target) {
        MappingService service = MappingService.instance;
        if (service == null) { message("Enable accessibility", "Open Setup and enable PadStudio controller mapping to place a screen target."); return; }
        if (!save()) return;
        String profileId = profile().id; int targetLayer = layer;
        Intent game = profile().appPackage.isEmpty() ? null : getPackageManager().getLaunchIntentForPackage(profile().appPackage);
        if (game != null) startActivity(game); else moveTaskToBack(true);
        new Handler(Looper.getMainLooper()).postDelayed(() -> { if (MappingService.instance == service) service.showPicker(profileId, targetLayer, target); }, 600);
    }
    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (result != RESULT_OK || intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        try {
            if (request == EXPORT) {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IllegalStateException("Cannot open destination");
                    output.write(settings.json().toString(2).getBytes(StandardCharsets.UTF_8));
                }
                toast("Profile backup exported");
            } else if (request == IMPORT) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Cannot open backup");
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) { bytes.write(buffer, 0, count); if (bytes.size() > 1_048_576) throw new IllegalArgumentException("Backups must be smaller than 1 MB"); }
                }
                Model.Settings imported = Model.Settings.read(bytes.toString(StandardCharsets.UTF_8.name()), true);
                new AlertDialog.Builder(this).setTitle("Import " + imported.profiles.size() + " profiles?")
                    .setMessage("This replaces the saved profile list. Mapping and analog capture will be paused.")
                    .setPositiveButton("Import", (dialog, which) -> { settings = imported; if (save()) { layer = 0; render(); toast("Profiles imported. Mapping is paused."); } })
                    .setNegativeButton("Cancel", null).show();
            }
        } catch (Exception error) { message("Profile transfer failed", error.getMessage()); }
    }
    private void message(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show(); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { if (!screen.equals("editor")) navigate("editor"); else { if (save()) super.onBackPressed(); } }
}
