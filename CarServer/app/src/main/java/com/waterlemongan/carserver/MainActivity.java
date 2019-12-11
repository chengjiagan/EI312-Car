package com.waterlemongan.carserver;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.Image;
import android.media.ImageReader;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private ServerThread serverThread;
    private Car car;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private TextureView textureView;
    private CaptureRequest.Builder previewRequestBuilder;
    private InetAddress clientAddress;
    private boolean transferVideo = false;
    private ServerHandler handler = new ServerHandler(this);

    private static final int videoPort = 10002;
    private static final int controlPort = 10001;
    private static final String TAG = "CarServer";
    private static final int PERMISSIONS_REQUEST_CODE = 2345;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serverThread = new ServerThread(controlPort, handler);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                // nothing to do
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "create wifi p2p group failed");
                Toast.makeText(MainActivity.this, "create group failed", Toast.LENGTH_LONG).show();
            }
        });

//        car = new Car(this);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CODE);
        }

        getSupportActionBar().hide();
        textureView = (TextureView) findViewById(R.id.textureView);
        textureView.setKeepScreenOn(true);
        textureView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                startCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                // nothing to do
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (cameraDevice != null) {
                    cameraDevice.close();
                    cameraDevice = null;
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // nothing to do
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        serverThread.setRunning(true);
        serverThread.start();

        TextView textView = (TextView) findViewById(R.id.textView);

        StringBuilder s = new StringBuilder();
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            s.append(String.format("product id: %s, vendor id: %d\n", device.getProductName(), device.getVendorId()));
        }
        textView.setText(s.toString());
    }

    @Override
    protected void onPause() {
        serverThread.setRunning(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        manager.removeGroup(channel, null);
        super.onDestroy();

    }

    private void startCamera() {
        HandlerThread thread = new HandlerThread("Camera");
        thread.start();
        cameraHandler = new Handler(thread.getLooper());

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String cameraId = Integer.toString(CameraCharacteristics.LENS_FACING_FRONT);

        try {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "lack of camera permission");
                finish();
            }
            cameraManager.openCamera(cameraId, stateCallback, cameraHandler);
        } catch (CameraAccessException e) {
            Log.d(TAG, "open camera failed");
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            Log.d(TAG, "camera failure: " + error);
        }
    };

    private void startPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(textureView.getWidth(), textureView.getHeight());
            Surface surface = new Surface(texture);

            ImageReader reader =
                    ImageReader.newInstance(3456, 4608, ImageFormat.JPEG, 50);
            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireNextImage();
                    if (img != null) {
                        if (transferVideo) {
                            try {
                                Socket s = new Socket(clientAddress, videoPort);
                                s.getOutputStream().write(img.getPlanes()[0].getBuffer().array());
                            } catch (IOException e) {
                                Log.d(TAG, "send img failed");
                                e.printStackTrace();
                            }
                        }
                        img.close();
                    }
                }
            }, cameraHandler);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.addTarget(reader.getSurface());
            cameraDevice.createCaptureSession(Arrays.asList(surface, reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            CaptureRequest request = previewRequestBuilder.build();
                            try {
                                session.setRepeatingRequest(request, null, cameraHandler);
                            } catch (CameraAccessException e) {
                                Log.d(TAG, "camera request failed");
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.d(TAG, "open camera capture session failed");
                        }
                    }, cameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void forward() {
        Log.d(TAG, "forward");
        car.forward();
    }

    private void backward() {
        Log.d(TAG, "backward");
        car.backward();
    }

    private void left() {
        Log.d(TAG, "left");
        car.left();
    }

    private void right() {
        Log.d(TAG, "right");
        car.right();
    }

    private void stop() {
        Log.d(TAG, "stop");
        car.stop();
    }

    private static class ServerThread extends Thread {
        private ServerSocket serverSocket;
        private Handler handler;
        private int port;
        private boolean running;

        ServerThread(int port, Handler handler) {
            this.port = port;
            this.handler = handler;
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(port);
                while (running) {
                    Socket s = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

                    Bundle b = new Bundle();
                    b.putString("action", in.readLine());
                    b.putSerializable("address", s.getInetAddress());

                    Message msg = Message.obtain();
                    msg.setData(b);
                    handler.sendMessage(msg);
                    in.close();
                    s.close();
                }
            } catch (IOException e) {
                Log.d(TAG, "server socket failed");
                e.printStackTrace();
            }

            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.d(TAG, "close server socket failed");
                e.printStackTrace();
            }
        }

        public void setRunning(boolean running) {
            this.running = running;
        }
    }

    private static class ServerHandler extends Handler {
        private MainActivity activity;

        ServerHandler(MainActivity activity) {
            super();
            this.activity = activity;
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Bundle b = msg.getData();
            String action = b.getString("action");
            if (action.equals("forward")) {
                activity.forward();
            } else if (action.equals("backward")) {
                activity.backward();
            } else if (action.equals("left")) {
                activity.left();
            } else if (action.equals("right")) {
                activity.right();
            } else if (action.equals("stop")) {
                activity.stop();
            } else if (action.equals("connectCamera")) {
                activity.clientAddress = (InetAddress) b.getSerializable("address");
                activity.transferVideo = true;
            } else if (action.equals("disconnectCamera")) {
                activity.transferVideo = false;
            }
        }
    }
}
