package com.briancodez.padstudio;

import android.app.Application;

public final class PadApplication extends Application {
    @Override public void onCreate() { super.onCreate(); ShellBridge.initialize(this); }
}
