package com.briancodez.padstudio;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import rikka.shizuku.Shizuku;

public final class ShellBridge {
    private ShellBridge() {}
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static IShellTouch remote;
    private static boolean initialized, binding;
    private static Context application;
    private static String status = "Shizuku not connected";
    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            binding = false;
            IShellTouch service = IShellTouch.Stub.asInterface(binder);
            try {
                String result = service.status();
                if (!result.equals("Ready")) { update(result); return; }
                binder.linkToDeath(() -> MAIN.post(ShellBridge::lost), 0);
                remote = service; update("Shizuku touch engine connected");
                if (MappingService.instance != null) MappingService.instance.refreshCapture();
            } catch (Exception error) { remote = null; update("Shizuku connection failed: " + error.getMessage()); }
        }
        @Override public void onServiceDisconnected(ComponentName name) { lost(); }
    };
    public static void initialize(Context context) {
        if (initialized) return;
        initialized = true; application = context.getApplicationContext();
        Shizuku.addBinderReceivedListenerSticky(() -> connect(false));
        Shizuku.addBinderDeadListener(ShellBridge::lost);
        Shizuku.addRequestPermissionResultListener((request, grant) -> {
            if (request == 84 && grant == PackageManager.PERMISSION_GRANTED) connect(false);
            else if (request == 84) update("Shizuku permission was not granted");
        });
    }
    public static String status() { return status; }
    public static boolean ready() { return remote != null && remote.asBinder().isBinderAlive(); }
    public static void connect(boolean requestPermission) {
        if (ready() || binding) return;
        try {
            if (!Shizuku.pingBinder()) { update("Start Shizuku, then connect here"); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (requestPermission) Shizuku.requestPermission(84);
                update("Authorize PadStudio in Shizuku"); return;
            }
            binding = true; update("Connecting Shizuku touch engine…");
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(new ComponentName(application, ShellTouchService.class))
                .daemon(true).processNameSuffix("touch").version(1);
            Shizuku.bindUserService(args, CONNECTION);
        } catch (RuntimeException error) { binding = false; update("Shizuku unavailable: " + error.getMessage()); }
    }
    private static void lost() {
        remote = null; binding = false; update("Shizuku disconnected. Analog mapping paused.");
        if (MappingService.instance != null) MappingService.instance.refreshCapture();
    }
    private static void update(String message) {
        status = message;
        MAIN.post(RuntimeState::notifyChanged);
    }
    public static boolean sync(String[] ids, float[] x, float[] y) {
        if (!ready()) return false;
        try { return remote.sync(ids, x, y); }
        catch (Exception error) { lost(); return false; }
    }
    public static void clear() {
        if (!ready()) return;
        try { remote.clear(); } catch (Exception error) { lost(); }
    }
}
