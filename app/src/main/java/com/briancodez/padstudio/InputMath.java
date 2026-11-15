package com.briancodez.padstudio;

public final class InputMath {
    private InputMath() {}
    public static float response(float magnitude, float deadzone, float sensitivity, String curve) {
        if (!Float.isFinite(magnitude)) return 0;
        float normalized = Math.max(0, (Math.min(1, Math.abs(magnitude)) - deadzone) / (1 - deadzone));
        double exponent = "Precision".equals(curve) ? 2 : "Aggressive".equals(curve) ? .6 : 1;
        return Math.min(1, (float) Math.pow(normalized, exponent) * sensitivity);
    }
    public static float[] stick(float x, float y, float deadzone, float sensitivity, String curve) {
        float distance = (float) Math.hypot(x, y);
        if (!Float.isFinite(distance) || distance <= deadzone) return new float[]{0, 0};
        float amount = response(distance, deadzone, sensitivity, curve);
        return new float[]{x / distance * amount, y / distance * amount};
    }
}
