package com.briancodez.padstudio;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputMathTest {
    @Test public void deadzoneSuppressesDriftAndRescalesTravel() {
        assertEquals(0f, InputMath.response(.08f, .10f, 1, "Linear"), .0001f);
        assertEquals(.5f, InputMath.response(.55f, .10f, 1, "Linear"), .0001f);
        assertEquals(1f, InputMath.response(1f, .10f, 1, "Linear"), .0001f);
    }
    @Test public void diagonalInputUsesCircularDeadzoneAndBoundedMagnitude() {
        float[] value = InputMath.stick(1, 1, .10f, 1, "Linear");
        assertEquals(1, Math.hypot(value[0], value[1]), .0001);
        assertEquals(value[0], value[1], .0001f);
        assertArrayEquals(new float[]{0, 0}, InputMath.stick(.05f, .05f, .10f, 1, "Linear"), .0001f);
    }
    @Test public void curvesPreserveDirectionAndRejectNonFiniteAxes() {
        assertTrue(InputMath.response(.5f, 0, 1, "Precision") < .5f);
        assertTrue(InputMath.response(.5f, 0, 1, "Aggressive") > .5f);
        float[] negative = InputMath.stick(-.5f, 0, 0, 1, "Linear");
        assertEquals(-.5f, negative[0], .0001f);
        assertArrayEquals(new float[]{0, 0}, InputMath.stick(Float.NaN, 0, .1f, 1, "Linear"), .0001f);
    }
}
