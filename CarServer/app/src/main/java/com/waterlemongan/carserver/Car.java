package com.waterlemongan.carserver;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

class Car {
    private OutputStream out;
    private Handler handler;
    private static final String TAG = "Car";

    Car(OutputStream out) {
        this.out = out;

        HandlerThread thread = new HandlerThread("CarBluetooth");
        thread.start();
        handler = new Handler(thread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                try {
                    Car.this.out.write(((String) msg.obj).getBytes());
                } catch (IOException e) {
                    Log.d(TAG, "send bt message failed");
                    e.printStackTrace();
                }
            }
        };

    }

    private void write(String buf) {
        Message msg = Message.obtain();
        msg.obj = buf;
        handler.sendMessage(msg);
    }

    public void forward() {
        write("f");
    }

    public void backward() {
        write("b");
    }

    public void left() {
        write("l");
    }

    public void right() {
        write("r");
    }

    public void stop() {
        write("s");
    }
}
