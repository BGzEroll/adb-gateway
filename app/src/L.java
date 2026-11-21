package com.dsh.adbkeep;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;

final class L {
    static void d(Context c, String m) {
        android.util.Log.d("adbgw", m);
        if (c == null) return;
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) dir = c.getFilesDir();
            FileWriter w = new FileWriter(new File(dir, "adbkeep.log"), true);
            w.write(System.currentTimeMillis() + " " + m + "\n");
            w.close();
        } catch (Throwable ignored) { }
    }
}
