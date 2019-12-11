package com.waterlemongan.carclient;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import java.net.InetAddress;

public class MainActivity extends AppCompatActivity {
    private CarServer carServer = null;
    private TextureView textureView;

    public static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        InetAddress address = (InetAddress) getIntent().getSerializableExtra(WifiActivity.EXTRA_DEVICE_ADDRESS);
        Log.d(TAG, "device address: " + address.getHostAddress());
        carServer = new CarServer(address);

        initView();
    }

    @Override
    protected void onPause() {
        super.onPause();
//        carServer.closeVideo();
//        Log.d(TAG, "close video");
    }

    private void initView() {
        ImageButton up = findViewById(R.id.upButton);
        up.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        carServer.forward();
                        return false;
                    case MotionEvent.ACTION_UP:
                        carServer.stop();
                        return false;
                    default:
                        return false;
                }
            }
        });

        ImageButton down = findViewById(R.id.downButton);
        down.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        carServer.backward();
                        return false;
                    case MotionEvent.ACTION_UP:
                        carServer.stop();
                        return false;
                    default:
                        return false;
                }
            }
        });

        ImageButton left = findViewById(R.id.leftButton);
        left.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        carServer.left();
                        return false;
                    case MotionEvent.ACTION_UP:
                        carServer.stop();
                        return false;
                    default:
                        return false;
                }
            }
        });

        ImageButton right = findViewById(R.id.rightButtion);
        right.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        carServer.right();
                        return false;
                    case MotionEvent.ACTION_UP:
                        carServer.stop();
                        return false;
                    default:
                        return false;
                }
            }
        });

        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "connect video");
                carServer.connectVideo(new CarServer.VideoUpdateListener() {
                    @Override
                    public void onVideoUpdate(Bitmap bmp) {
                        Canvas canvas = textureView.lockCanvas();
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        canvas.drawBitmap(bmp, 0, 0, null);
                        textureView.unlockCanvasAndPost(canvas);
                    }
                });
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                // nothing to do
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                carServer.closeVideo();
                Log.d(TAG, "close video");
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }
}
