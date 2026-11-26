package com.example.mazeapp;

import android.util.Log;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class AppLogger {
    private static final String TAG = "MazeGame";
    private static final Logger logger = Logger.getLogger(TAG);

    static {
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new LogcatHandler());
    }

    public static Logger getLogger() {
        return logger;
    }

    private static class LogcatHandler extends Handler {
        @Override
        public void publish(LogRecord record) {
            String msg = "[" + record.getLevel().getName() + "] " + record.getMessage();
            int levelValue = record.getLevel().intValue();

            if (levelValue >= Level.SEVERE.intValue()) {
                Log.e(TAG, msg, record.getThrown());
            } else if (levelValue >= Level.WARNING.intValue()) {
                Log.w(TAG, msg);
            } else if (levelValue >= Level.INFO.intValue()) {
                Log.i(TAG, msg);
            } else {
                Log.d(TAG, msg);
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}
    }
}