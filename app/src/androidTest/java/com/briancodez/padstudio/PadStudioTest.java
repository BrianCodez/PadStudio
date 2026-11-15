package com.briancodez.padstudio;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PadStudioTest {
    private Instrumentation instrumentation;
    private UiAutomation automation;
    private Store store;
    private ActivityScenario<MainActivity> scenario;

    @Before public void connectService() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        store = new Store(instrumentation.getTargetContext());
        shell("settings put global hidden_api_policy 1");
        main(() -> store.save(Model.Settings.defaults()));
        shell("settings put secure enabled_accessibility_services com.briancodez.padstudio/.MappingService");
        shell("settings put secure accessibility_enabled 1");
        await(() -> MappingService.instance != null, "Accessibility service did not connect");
    }
    @After public void cleanup() {
        if (scenario != null) scenario.close();
        main(() -> store.save(Model.Settings.defaults()));
    }
    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = automation.executeShellCommand(command);
             InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] bytes = new byte[1024]; while (input.read(bytes) != -1) { }
        }
        instrumentation.waitForIdleSync();
    }
    private void main(Runnable runnable) { instrumentation.runOnMainSync(runnable); }
    private void await(BooleanSupplier condition, String message) {
        long end = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < end) { if (condition.getAsBoolean()) return; SystemClock.sleep(40); }
        fail(message);
    }
    private View find(View view, String label) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(label)) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = find(group.getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }
    private void click(String label) {
        scenario.onActivity(activity -> {
            View view = find(activity.getWindow().getDecorView(), label);
            assertNotNull("Missing control " + label, view); assertTrue(view.performClick());
        });
        instrumentation.waitForIdleSync();
    }
    private void key(int code, boolean down) {
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now, now, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
            code, 0, 0, -1, 0, 0, InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_KEYBOARD);
        injectToAccessibility(event);
    }
    private void injectToAccessibility(android.view.InputEvent event) {
        if (android.os.Build.VERSION.SDK_INT == 30) {
            // Android 11 lacks the input-filter TestApi. Exercise the service callback
            // directly, then verify its actual system-delivered touch gesture.
            main(() -> MappingService.instance.onKeyEvent((KeyEvent) event));
            return;
        }
        // Normal UiAutomation injection bypasses accessibility to prevent feedback loops.
        // This framework TestApi is used only by instrumentation, never by the shipped app.
        try {
            UiAutomation.class.getMethod("injectInputEventToInputFilter", android.view.InputEvent.class).invoke(automation, event);
        } catch (ReflectiveOperationException error) { throw new AssertionError("Accessibility test injection unavailable", error); }
        instrumentation.waitForIdleSync();
    }
    private int received() {
        int[] value = new int[1]; scenario.onActivity(activity -> value[0] = activity.playgroundTouches); return value[0];
    }
    private void openPlayground(Model.Action action) {
        Model.Settings config = store.load(); config.enabled = true;
        config.active().binding(0, "A").action = action;
        main(() -> store.save(config));
        scenario = ActivityScenario.launch(MainActivity.class); click("Touch playground");
        scenario.onActivity(activity -> {
            TextView target = (TextView) find(activity.getWindow().getDecorView(), "Touches received · 0");
            assertNotNull(target);
            Rect visible = new Rect(); assertTrue(target.getGlobalVisibleRect(visible));
            Rect display = activity.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            Model.Settings saved = store.load();
            saved.active().binding(0, "A").x = (float) visible.centerX() / display.width();
            saved.active().binding(0, "A").y = (float) visible.centerY() / display.height();
            saved.active().binding(0, "B").x = (float) (visible.centerX() + 60) / display.width();
            saved.active().binding(0, "B").y = (float) visible.centerY() / display.height();
            store.save(saved);
        });
        instrumentation.waitForIdleSync(); SystemClock.sleep(250);
    }
    @Test public void profileBackupRoundTripAndValidation() throws Exception {
        Model.Settings original = Model.Settings.defaults(); original.enabled = true; original.captureAnalog = true;
        original.active().binding(1, "A").action = Model.Action.TOUCH_HOLD;
        original.active().binding(1, "A").x = .37f;
        Model.Settings restored = Model.Settings.read(original.json().toString(), false);
        assertEquals(Model.Action.TOUCH_HOLD, restored.active().binding(1, "A").action);
        assertEquals(.37f, restored.active().binding(1, "A").x, .001f);
        assertEquals(Model.Action.PASS, restored.active().binding(0, "A").action);
        Model.Settings imported = Model.Settings.read(original.json().toString(), true);
        assertFalse(imported.enabled); assertFalse(imported.captureAnalog);
        JSONObject invalid = original.json(); invalid.getJSONArray("profiles").getJSONObject(0).put("triggerSoft", 1);
        try { Model.Settings.read(invalid.toString(), true); fail("Invalid thresholds accepted"); } catch (org.json.JSONException expected) { }
        invalid = original.json(); invalid.put("version", 99);
        try { Model.Settings.read(invalid.toString(), true); fail("Unknown backup version accepted"); } catch (org.json.JSONException expected) { }
    }
    @Test public void nativeEditorKeepsAssignmentsAcrossRecreation() {
        Model.Settings config = store.load(); config.active().binding(0, "A").action = Model.Action.TAP;
        main(() -> store.save(config));
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> assertNotNull(find(activity.getWindow().getDecorView(), "Screen tap")));
        click("B");
        scenario.onActivity(activity -> assertNotNull(find(activity.getWindow().getDecorView(), "B · assignment")));
        scenario.recreate();
        scenario.onActivity(activity -> assertNotNull(find(activity.getWindow().getDecorView(), "B · assignment")));
        assertEquals(Model.Action.TAP, store.load().active().binding(0, "A").action);
        assertEquals(Model.Action.PASS, store.load().active().binding(0, "B").action);
    }
    @Test public void controllerButtonDeliversARealScreenTap() {
        openPlayground(Model.Action.TAP);
        key(KeyEvent.KEYCODE_BUTTON_A, true); SystemClock.sleep(140); key(KeyEvent.KEYCODE_BUTTON_A, false);
        await(() -> received() == 1, "Mapped controller tap did not reach the target. " + RuntimeState.status);
    }
    @Test public void heldTouchAndSecondButtonRemainIndependent() {
        openPlayground(Model.Action.TOUCH_HOLD);
        Model.Settings config = store.load(); config.active().binding(0, "B").action = Model.Action.TAP;
        main(() -> store.save(config)); SystemClock.sleep(100);
        key(KeyEvent.KEYCODE_BUTTON_A, true); SystemClock.sleep(150);
        assertEquals(0, received());
        key(KeyEvent.KEYCODE_BUTTON_B, true); SystemClock.sleep(150); key(KeyEvent.KEYCODE_BUTTON_B, false);
        await(() -> received() >= 1, "Second button cancelled or failed during held touch");
        key(KeyEvent.KEYCODE_BUTTON_A, false);
        await(() -> received() == 2, "Held touch did not release independently. " + RuntimeState.status);
    }
    @Test public void holdActivationWaitsAndRepeatStopsOnRelease() {
        openPlayground(Model.Action.TAP);
        Model.Settings config = store.load();
        config.active().binding(0, "A").activation = Model.Activation.HOLD;
        config.active().binding(0, "A").repeat = true;
        main(() -> store.save(config)); SystemClock.sleep(100);
        key(KeyEvent.KEYCODE_BUTTON_A, true); SystemClock.sleep(150);
        assertEquals(0, received());
        await(() -> received() >= 2, "Held repeat did not fire");
        key(KeyEvent.KEYCODE_BUTTON_A, false); SystemClock.sleep(200);
        int stopped = received(); SystemClock.sleep(300); assertEquals(stopped, received());
    }
    @Test public void doublePressAndReleaseActivationsUseTheirOwnTiming() {
        openPlayground(Model.Action.TAP);
        Model.Settings config = store.load(); config.active().binding(0, "A").activation = Model.Activation.DOUBLE;
        main(() -> store.save(config)); SystemClock.sleep(100);
        key(KeyEvent.KEYCODE_BUTTON_A, true); key(KeyEvent.KEYCODE_BUTTON_A, false);
        SystemClock.sleep(80); assertEquals(0, received());
        key(KeyEvent.KEYCODE_BUTTON_A, true); key(KeyEvent.KEYCODE_BUTTON_A, false);
        await(() -> received() == 1, "Double press did not fire");
        config.active().binding(0, "A").activation = Model.Activation.RELEASE;
        main(() -> store.save(config)); SystemClock.sleep(100);
        key(KeyEvent.KEYCODE_BUTTON_A, true); SystemClock.sleep(150); assertEquals(1, received());
        key(KeyEvent.KEYCODE_BUTTON_A, false);
        await(() -> received() == 2, "Release activation did not fire");
    }
    @Test public void pausingMappingReleasesAnActiveContact() {
        openPlayground(Model.Action.TOUCH_HOLD);
        key(KeyEvent.KEYCODE_BUTTON_A, true); SystemClock.sleep(150); assertEquals(0, received());
        Model.Settings config = store.load(); config.enabled = false;
        main(() -> store.save(config));
        await(() -> received() == 1, "Pausing left an active touch");
        key(KeyEvent.KEYCODE_BUTTON_A, false); SystemClock.sleep(150);
        assertEquals(1, received());
    }
    @Test @SdkSuppress(minSdkVersion = 34) public void analogStickCreatesAndReleasesTouch() {
        openPlayground(Model.Action.PASS);
        Model.Settings config = store.load(); config.captureAnalog = true;
        config.active().left.mode = Model.StickMode.JOYSTICK;
        config.active().left.x = config.active().binding(0, "A").x;
        config.active().left.y = config.active().binding(0, "A").y;
        main(() -> store.save(config)); SystemClock.sleep(150);
        motion(.7f); await(() -> RuntimeState.leftX > .6f, "Joystick axis did not reach accessibility service");
        SystemClock.sleep(250); motion(0);
        await(() -> received() == 1, "Analog touch did not release. " + RuntimeState.status);
    }
    private void motion(float x) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties(); properties.id = 0; properties.toolType = MotionEvent.TOOL_TYPE_UNKNOWN;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords(); coordinates.setAxisValue(MotionEvent.AXIS_X, x);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1,
            new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coordinates},
            0, 0, 1, 1, -1, 0, InputDevice.SOURCE_JOYSTICK, 0);
        injectToAccessibility(event); event.recycle();
    }
    @Test public void targetPickerSavesNormalizedCoordinates() {
        scenario = ActivityScenario.launch(MainActivity.class);
        Model.Settings config = store.load();
        main(() -> MappingService.instance.showPicker(config.activeId, 0, "A"));
        instrumentation.waitForIdleSync(); SystemClock.sleep(200);
        Rect size = instrumentation.getTargetContext().getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, size.width() * .6f, size.height() * .65f, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN); automation.injectInputEvent(down, true); down.recycle();
        MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, size.width() * .6f, size.height() * .65f, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN); automation.injectInputEvent(up, true); up.recycle();
        await(() -> Math.abs(store.load().active().binding(0, "A").x - .6f) < .03f, "Picker did not save X");
        assertEquals(.65f, store.load().active().binding(0, "A").y, .03f);
    }
    @Test public void capturePortraitAndLandscapeEditor() throws Exception {
        scenario = ActivityScenario.launch(MainActivity.class); SystemClock.sleep(300);
        screenshot("editor-portrait.png");
        scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        await(() -> instrumentation.getTargetContext().getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE, "Landscape did not activate");
        SystemClock.sleep(400); screenshot("editor-landscape.png");
        scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    }
    private void screenshot(String name) throws Exception {
        String testOutput = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir");
        File directory = testOutput == null ? instrumentation.getTargetContext().getExternalFilesDir(null) : new File(testOutput);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File output = new File(directory, name);
        Bitmap bitmap = automation.takeScreenshot(); assertNotNull(bitmap);
        try (FileOutputStream stream = new FileOutputStream(output)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        bitmap.recycle();
    }
}
