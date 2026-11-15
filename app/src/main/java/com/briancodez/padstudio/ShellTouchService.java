package com.briancodez.padstudio;

import android.os.Build;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** A narrowly scoped Shizuku user service for primary-display multitouch injection. */
public final class ShellTouchService extends IShellTouch.Stub {
    private final Map<String, Point> points = new LinkedHashMap<>();
    private Object manager;
    private Method inject;
    private String initializationError;
    private long downTime, lastSync;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private static final class Point {
        int id; float x, y;
        Point(int id, float x, float y) { this.id = id; this.x = x; this.y = y; }
    }
    public ShellTouchService() {
        try {
            Class<?> type = Class.forName(Build.VERSION.SDK_INT >= 34 ? "android.hardware.input.InputManagerGlobal" : "android.hardware.input.InputManager");
            manager = type.getMethod("getInstance").invoke(null);
            inject = type.getMethod("injectInputEvent", InputEvent.class, int.class);
        } catch (Exception error) { initializationError = "Input injection unavailable: " + error.getClass().getSimpleName(); }
        watchdog.scheduleAtFixedRate(() -> {
            synchronized (this) { if (!points.isEmpty() && SystemClock.uptimeMillis() - lastSync > 1500) clear(); }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }
    @Override public String status() { return initializationError == null ? "Ready" : initializationError; }
    @Override public synchronized boolean sync(String[] ids, float[] x, float[] y) {
        if (inject == null || ids == null || x == null || y == null || ids.length != x.length || ids.length != y.length || ids.length > 10) return false;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == null || !Float.isFinite(x[i]) || !Float.isFinite(y[i]) || x[i] < 0 || y[i] < 0) return false;
        }
        lastSync = SystemClock.uptimeMillis();
        List<String> requested = Arrays.asList(ids);
        try {
            for (String previous : new ArrayList<>(points.keySet())) if (!requested.contains(previous)) remove(previous);
            boolean moved = false;
            for (int i = 0; i < ids.length; i++) {
                Point point = points.get(ids[i]);
                if (point != null) { moved |= point.x != x[i] || point.y != y[i]; point.x = x[i]; point.y = y[i]; }
            }
            if (moved && !points.isEmpty()) event(MotionEvent.ACTION_MOVE, -1);
            for (int i = 0; i < ids.length; i++) if (!points.containsKey(ids[i])) {
                if (points.isEmpty()) downTime = lastSync;
                points.put(ids[i], new Point(nextId(), x[i], y[i]));
                int index = points.size() - 1;
                event(points.size() == 1 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_POINTER_DOWN | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT), -1);
            }
            return true;
        } catch (Exception error) { clear(); return false; }
    }
    private int nextId() {
        for (int candidate = 0; candidate < 10; candidate++) {
            boolean used = false;
            for (Point point : points.values()) if (point.id == candidate) used = true;
            if (!used) return candidate;
        }
        throw new IllegalStateException("Too many contacts");
    }
    private void remove(String id) throws Exception {
        int index = new ArrayList<>(points.keySet()).indexOf(id);
        event(points.size() == 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_POINTER_UP | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT), index);
        points.remove(id);
    }
    private void event(int action, int liftedIndex) throws Exception {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[points.size()];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[points.size()];
        int index = 0;
        for (Point point : points.values()) {
            properties[index] = new MotionEvent.PointerProperties(); properties[index].id = point.id; properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[index] = new MotionEvent.PointerCoords(); coordinates[index].x = point.x; coordinates[index].y = point.y;
            coordinates[index].pressure = index == liftedIndex ? 0 : 1; coordinates[index].size = 1; index++;
        }
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, points.size(), properties, coordinates,
            0, 0, 1, 1, -1, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { if (!Boolean.TRUE.equals(inject.invoke(manager, event, 0))) throw new IllegalStateException("Input event rejected"); }
        finally { event.recycle(); }
    }
    @Override public synchronized void clear() {
        for (String id : new ArrayList<>(points.keySet())) {
            try { remove(id); } catch (Exception ignored) { points.remove(id); }
        }
    }
    @Override public void destroy() { clear(); watchdog.shutdownNow(); System.exit(0); }
}
