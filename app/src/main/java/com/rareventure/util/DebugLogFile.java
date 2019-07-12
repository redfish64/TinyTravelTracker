package com.rareventure.util;

import android.os.Environment;
import android.util.Log;

import com.rareventure.gps2.GTG;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

/**
 * a small utility for writing permenant log files to the sdcard for debugging purposes.
 */
public class DebugLogFile {
    private static BufferedWriter debugOut;
    private static boolean triedOpeningAlready = false;

    public static void log(String msg)
    {
        if(!openIfNeeded()) return;

        try {
            debugOut.write(new Date().toString());
            debugOut.write(": ");
            debugOut.write(msg);
            debugOut.write("\n");
            debugOut.flush();;
        } catch (IOException e) {
            Log.e(GTG.TAG, "Couldn't write to debug log file", e);
        }
    }

    private static boolean openIfNeeded() {
        Log.e(GTG.TAG, "Open If Needed OIN");
        if(!GTG.prefs.writeFileLogDebug) return false;
        if (triedOpeningAlready) return false;

        if(debugOut == null)
        {
            triedOpeningAlready = true;
            try {
                debugOut = new BufferedWriter(new FileWriter(Environment.getExternalStorageDirectory()+"/ttt_debug_log.txt", true));
                log("Log file initialzation");
            } catch (IOException e) {
                Log.e(GTG.TAG, "Couldn't open /sdcard/ttt_debug_log.txt", e);
                return false;
            }
        }

        return true;
    }
}
