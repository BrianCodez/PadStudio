package com.briancodez.padstudio;

import android.view.MotionEvent;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RuntimeState {
    private RuntimeState() {}
    public static boolean testing;
    public static boolean playground;
    public static int layer;
    public static String lastInput = "Connect a controller and press a button";
    public static String status = "Mapping paused";
    public static float leftX, leftY, rightX, rightY, leftTrigger, rightTrigger;
    public static final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    public static void notifyChanged() { for (Runnable listener : listeners) listener.run(); }
    public static void motion(MotionEvent event) {
        leftX = event.getAxisValue(MotionEvent.AXIS_X); leftY = event.getAxisValue(MotionEvent.AXIS_Y);
        rightX = event.getAxisValue(MotionEvent.AXIS_Z); rightY = event.getAxisValue(MotionEvent.AXIS_RZ);
        leftTrigger = Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE));
        rightTrigger = Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS));
        notifyChanged();
    }
}
