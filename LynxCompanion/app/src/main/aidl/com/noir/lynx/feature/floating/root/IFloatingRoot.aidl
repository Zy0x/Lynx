package com.noir.lynx.feature.floating.root;

import android.os.Bundle;
import android.os.IBinder;
import android.view.Surface;
import android.view.MotionEvent;

/** Private, authenticated connection to the APK-owned root process. */
interface IFloatingRoot {
    Bundle open(in IBinder owner, in Surface surface, String component, int userId,
                int width, int height, int density);
    Bundle resize(int displayId, int width, int height, int density);
    boolean touch(int displayId, in MotionEvent event);
    boolean back(int displayId);
    boolean visible(int displayId, boolean visible);
    void close(int displayId);
}
