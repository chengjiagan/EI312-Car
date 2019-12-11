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
    private WifiP2pDevice device;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private WifiP2pConfig config;
    private CarServer carServer = null;
    private TextureView textureView;

    public static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        device = (WifiP2pDevice) getIntent().getParcelableExtra(WifiActivity.EXTRA_WIFI_DEVICE);
//        Toast.makeText(this, "device: " + device.deviceName, Toast.LENGTH_LONG).show();
//
//        config = new WifiP2pConfig();
//        config.deviceAddress = device.deviceAddress;
//        config.groupOwnerIntent = 1;
//        config.wps.setup = WpsInfo.PBC;
//
//        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
//        channel = manager.initialize(this, getMainLooper(), null);
//        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
//            @Override
//            public void onSuccess() {
//                manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
//                    @Override
//                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
//                        Toast.makeText(MainActivity.this, "Connect Successfully", Toast.LENGTH_LONG).show();
//                        Log.d(TAG, "get car server address succeed");
//                        carServer = new CarServer(info.groupOwnerAddress);
//                    }
//                });
//            }
//
//            @Override
//            public void onFailure(int reason) {
//                Toast.makeText(MainActivity.this, "Connect Failed: " + reason, Toast.LENGTH_LONG).show();
//            }
//        });

        InetAddress address = getIntent().getParcelableExtra(WifiActivity.EXTRA_DEVICE_ADDRESS);
        carServer = new CarServer(address);

        initView();
    }

    private void initView() {
        ImageButton up = (ImageButton) findViewById(R.id.upButton);
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

        ImageButton down = (ImageButton) findViewById(R.id.downButton);
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

        ImageButton left = (ImageButton) findViewById(R.id.leftButton);
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

        ImageButton right = (ImageButton) findViewById(R.id.rightButtion);
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

        textureView = (TextureView) findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
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
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }
}
