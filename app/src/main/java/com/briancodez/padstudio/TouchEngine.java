package com.briancodez.padstudio;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Continues all active contacts together so a button does not cancel a held stick. */
public final class TouchEngine {
    private static final long FRAME_MS = 32;
    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Contact> contacts = new LinkedHashMap<>();
    private final BooleanSupplier preferShell;
    private boolean inFlight;
    private boolean shellSession, shellScheduled;
    private int generation;

    private static final class Contact {
        float x, y, lastX, lastY, startX, startY;
        long startAt, endAt;
        boolean active = true;
        boolean started;
        boolean restart;
        float restartX, restartY;
        GestureDescription.StrokeDescription stroke;
    }
    private static final class Segment {
        final String id;
        final Contact contact;
        final GestureDescription.StrokeDescription stroke;
        final float x, y;
        final boolean ending;
        Segment(String id, Contact contact, GestureDescription.StrokeDescription stroke, float x, float y, boolean ending) {
            this.id = id; this.contact = contact; this.stroke = stroke; this.x = x; this.y = y; this.ending = ending;
        }
    }

    public TouchEngine(AccessibilityService service, BooleanSupplier preferShell) { this.service = service; this.preferShell = preferShell; }

    public void hold(String id, float x, float y) {
        dragHold(id, x, y, x, y);
    }
    public void dragHold(String id, float startX, float startY, float x, float y) {
        Contact contact = contacts.get(id);
        if (contact != null && !contact.active) {
            contact.restart = true; contact.restartX = x; contact.restartY = y;
            return;
        }
        if (contact == null) {
            if (contacts.isEmpty()) shellSession = preferShell.getAsBoolean();
            if (contacts.size() >= GestureDescription.getMaxStrokeCount()) return;
            contact = new Contact();
            contact.lastX = startX; contact.lastY = startY;
            contacts.put(id, contact);
        }
        contact.x = x; contact.y = y; contact.active = true;
        pulse();
    }
    public void tap(String id, float x, float y) { slide(id, x, y, x, y, 64); }
    public void slide(String id, float x, float y, float endX, float endY, long duration) {
        if (contacts.containsKey(id)) return;
        if (contacts.size() >= GestureDescription.getMaxStrokeCount()) return;
        if (contacts.isEmpty()) shellSession = preferShell.getAsBoolean();
        Contact contact = new Contact();
        contact.x = endX; contact.y = endY; contact.lastX = x; contact.lastY = y;
        contact.startX = x; contact.startY = y; contact.startAt = SystemClock.uptimeMillis();
        contact.endAt = contact.startAt + duration;
        contacts.put(id, contact); pulse();
    }
    public void release(String id) {
        Contact contact = contacts.get(id);
        if (contact != null) { contact.active = false; contact.restart = false; pulse(); }
    }
    public void releaseAll() {
        for (Contact contact : contacts.values()) { contact.active = false; contact.restart = false; }
        pulse();
    }
    private void pulse() {
        if (inFlight || contacts.isEmpty()) return;
        if (shellSession) { pulseShell(); return; }
        long now = SystemClock.uptimeMillis();
        GestureDescription.Builder builder = new GestureDescription.Builder();
        List<Segment> segments = new ArrayList<>();
        boolean continuing = contacts.values().stream().anyMatch(contact -> contact.stroke != null);
        for (Map.Entry<String, Contact> entry : contacts.entrySet()) {
            Contact contact = entry.getValue();
            boolean ending = !contact.active || (contact.endAt > 0 && now + FRAME_MS >= contact.endAt);
            float x = contact.x, y = contact.y;
            if (contact.endAt > 0) {
                float fraction = Math.min(1, (float) (now + FRAME_MS - contact.startAt) / (contact.endAt - contact.startAt));
                x = contact.startX + (contact.x - contact.startX) * fraction;
                y = contact.startY + (contact.y - contact.startY) * fraction;
            }
            // Android drops a continuation containing no MOVE or UP events.
            // One pixel of alternating movement keeps a stationary held contact alive.
            if (!ending && Math.round(x) == Math.round(contact.lastX) && Math.round(y) == Math.round(contact.lastY)) {
                x = contact.x + 1;
            }
            Path path = new Path(); path.moveTo(contact.lastX, contact.lastY);
            if (x != contact.lastX || y != contact.lastY) path.lineTo(x, y);
            // A continuing gesture's first sample must contain exactly its previous
            // fingers. Additional fingers enter in the following sample.
            long start = contact.stroke == null && continuing ? 1 : 0;
            GestureDescription.StrokeDescription stroke = contact.stroke == null
                ? new GestureDescription.StrokeDescription(path, start, FRAME_MS - start, !ending)
                : contact.stroke.continueStroke(path, 0, FRAME_MS, !ending);
            builder.addStroke(stroke);
            segments.add(new Segment(entry.getKey(), contact, stroke, x, y, ending));
        }
        int requestGeneration = generation;
        inFlight = true;
        try {
            boolean accepted = service.dispatchGesture(builder.build(), new AccessibilityService.GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) {
                    if (requestGeneration != generation) return;
                    inFlight = false;
                    for (Segment segment : segments) {
                        if (contacts.get(segment.id) != segment.contact) continue;
                        if (segment.ending) {
                            contacts.remove(segment.id);
                            if (segment.contact.restart) {
                                Contact next = new Contact();
                                next.x = segment.contact.restartX; next.y = segment.contact.restartY;
                                next.lastX = next.x; next.lastY = next.y;
                                contacts.put(segment.id, next);
                            }
                        }
                        else {
                            segment.contact.stroke = segment.stroke;
                            segment.contact.lastX = segment.x; segment.contact.lastY = segment.y;
                        }
                    }
                    handler.post(TouchEngine.this::pulse);
                }
                @Override public void onCancelled(GestureDescription description) {
                    if (requestGeneration != generation) return;
                    cancel("Touch interrupted. Release and press the controller input again.");
                }
            }, handler);
            if (!accepted) cancel("Android declined the touch gesture. Check the accessibility service.");
        } catch (IllegalArgumentException | IllegalStateException error) {
            cancel("Touch mapping stopped: " + error.getMessage());
        }
    }
    private void cancel(String message) {
        generation++; inFlight = false; contacts.clear();
        if (shellSession) ShellBridge.clear();
        RuntimeState.status = message; RuntimeState.notifyChanged();
    }
    private void pulseShell() {
        if (shellScheduled) return;
        long now = SystemClock.uptimeMillis();
        List<String> ids = new ArrayList<>(); List<Float> x = new ArrayList<>(), y = new ArrayList<>();
        Map<String, Contact> restarts = new LinkedHashMap<>();
        for (String id : new ArrayList<>(contacts.keySet())) {
            Contact contact = contacts.get(id);
            if (!contact.active) {
                contacts.remove(id);
                if (contact.restart) {
                    Contact next = new Contact(); next.x = contact.restartX; next.y = contact.restartY;
                    next.lastX = next.x; next.lastY = next.y; restarts.put(id, next);
                }
                continue;
            }
            float currentX = contact.x, currentY = contact.y;
            if (contact.endAt > 0) {
                float fraction = Math.min(1, (float) (now - contact.startAt) / (contact.endAt - contact.startAt));
                currentX = contact.startX + (contact.x - contact.startX) * fraction;
                currentY = contact.startY + (contact.y - contact.startY) * fraction;
                if (fraction == 1) contact.active = false;
            }
            if (!contact.started) {
                currentX = contact.lastX; currentY = contact.lastY; contact.started = true;
            }
            ids.add(id); x.add(currentX); y.add(currentY);
        }
        float[] horizontal = new float[x.size()], vertical = new float[y.size()];
        for (int i = 0; i < x.size(); i++) { horizontal[i] = x.get(i); vertical[i] = y.get(i); }
        if (!ShellBridge.sync(ids.toArray(new String[0]), horizontal, vertical)) {
            cancel("Shizuku touch output stopped. Reconnect it in Setup."); return;
        }
        contacts.putAll(restarts);
        if (!contacts.isEmpty()) {
            shellScheduled = true;
            handler.postDelayed(() -> { shellScheduled = false; pulse(); }, FRAME_MS);
        }
    }
}
