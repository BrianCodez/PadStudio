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

/** Continues all active contacts together so a button does not cancel a held stick. */
public final class TouchEngine {
    private static final long FRAME_MS = 32;
    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Contact> contacts = new LinkedHashMap<>();
    private boolean inFlight;
    private int generation;

    private static final class Contact {
        float x, y, lastX, lastY, startX, startY;
        long startAt, endAt;
        boolean active = true;
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

    public TouchEngine(AccessibilityService service) { this.service = service; }

    public void hold(String id, float x, float y) {
        Contact contact = contacts.get(id);
        if (contact == null || !contact.active) {
            if (contacts.size() >= GestureDescription.getMaxStrokeCount()) return;
            contact = new Contact();
            contact.lastX = x; contact.lastY = y;
            contacts.put(id, contact);
        }
        contact.x = x; contact.y = y; contact.active = true;
        pulse();
    }
    public void tap(String id, float x, float y) { slide(id, x, y, x, y, 64); }
    public void slide(String id, float x, float y, float endX, float endY, long duration) {
        if (contacts.containsKey(id)) return;
        if (contacts.size() >= GestureDescription.getMaxStrokeCount()) return;
        Contact contact = new Contact();
        contact.x = endX; contact.y = endY; contact.lastX = x; contact.lastY = y;
        contact.startX = x; contact.startY = y; contact.startAt = SystemClock.uptimeMillis();
        contact.endAt = contact.startAt + duration;
        contacts.put(id, contact); pulse();
    }
    public void release(String id) {
        Contact contact = contacts.get(id);
        if (contact != null) { contact.active = false; pulse(); }
    }
    public void releaseAll() {
        for (Contact contact : contacts.values()) contact.active = false;
        pulse();
    }
    private void pulse() {
        if (inFlight || contacts.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        GestureDescription.Builder builder = new GestureDescription.Builder();
        List<Segment> segments = new ArrayList<>();
        for (Map.Entry<String, Contact> entry : contacts.entrySet()) {
            Contact contact = entry.getValue();
            boolean ending = !contact.active || (contact.endAt > 0 && now + FRAME_MS >= contact.endAt);
            float x = contact.x, y = contact.y;
            if (contact.endAt > 0) {
                float fraction = Math.min(1, (float) (now + FRAME_MS - contact.startAt) / (contact.endAt - contact.startAt));
                x = contact.startX + (contact.x - contact.startX) * fraction;
                y = contact.startY + (contact.y - contact.startY) * fraction;
            }
            Path path = new Path(); path.moveTo(contact.lastX, contact.lastY);
            if (x != contact.lastX || y != contact.lastY) path.lineTo(x, y);
            GestureDescription.StrokeDescription stroke = contact.stroke == null
                ? new GestureDescription.StrokeDescription(path, 0, FRAME_MS, !ending)
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
                        if (segment.ending) contacts.remove(segment.id);
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
        RuntimeState.status = message; RuntimeState.notifyChanged();
    }
}
