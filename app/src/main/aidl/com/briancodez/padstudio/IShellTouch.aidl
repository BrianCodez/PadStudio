package com.briancodez.padstudio;

interface IShellTouch {
    void destroy() = 16777114;
    String status() = 1;
    boolean sync(in String[] ids, in float[] x, in float[] y) = 2;
    void clear() = 3;
}
