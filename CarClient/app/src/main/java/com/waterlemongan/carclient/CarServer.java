package com.waterlemongan.carclient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

class CarServer {
    private static final int controlPort = 10001;
    private static final int videoPort = 10002;
    private InetAddress address;
    private VideoTask videoTask;

    public static final String TAG = "CarServer";

    CarServer(InetAddress address) {
        this.address = address;
    }

    public void connectVideo(VideoUpdateListener listener) {
        new SendThread(address, "connectCamera\n").start();
        videoTask = new VideoTask(listener);
        videoTask.execute();
    }

    public void closeVideo() {
        new SendThread(address, "disconnectCamera\n").start();
        videoTask.setRunning(false);
        videoTask.cancel(false);
    }

    public void forward() {
        Log.d(TAG, "forward");
        new SendThread(address, "forward\n").start();
    }

    public void backward() {
        Log.d(TAG, "backward");
        new SendThread(address, "backward\n").start();
    }

    public void left() {
        Log.d(TAG, "left");
        new SendThread(address, "left\n").start();
    }

    public void right() {
        Log.d(TAG, "right");
        new SendThread(address, "right\n").start();
    }

    public void stop() {
        Log.d(TAG, "stop");
        new SendThread(address, "stop\n").start();
    }

    private static class SendThread extends Thread {
        private InetAddress address;
        private String buf;

        SendThread(InetAddress address, String msg) {
            this.address = address;
            this.buf = msg;
        }

        @Override
        public void run() {
            try {
                Socket s = new Socket(address, controlPort);
                OutputStreamWriter os = new OutputStreamWriter(s.getOutputStream());
                os.write(buf);
                os.flush();
                os.close();
                s.close();
            } catch (IOException e) {
                Log.d(TAG, "send message failed");
                e.printStackTrace();
            }
        }
    }

    private static class VideoTask extends AsyncTask<Void, Bitmap, Void> {
        private boolean running;
        private ServerSocket serverSocket;
        private VideoUpdateListener listener;

        VideoTask(VideoUpdateListener listener) {
            this.listener = listener;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            running = true;
            try {
                serverSocket = new ServerSocket(videoPort);
                while (running) {
                    Socket s = serverSocket.accept();
                    Bitmap bmp = BitmapFactory.decodeStream(new BufferedInputStream(s.getInputStream()));
                    if (bmp != null)
                        publishProgress(bmp);
                    s.close();
                }
            } catch (IOException e) {
                Log.d(TAG, "get video image failed");
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.d(TAG, "close server socket failed");
                e.printStackTrace();
            }
        }

        @Override
        protected void onProgressUpdate(Bitmap... values) {
            super.onProgressUpdate(values);
            listener.onVideoUpdate(values[0]);
        }

        public void setRunning(boolean running) {
            this.running = running;
        }
    }

    public interface VideoUpdateListener {
        void onVideoUpdate(Bitmap bmp);
    }
}
