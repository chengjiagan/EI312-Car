package com.waterlemongan.carserver;

import android.Manifest;
import android.app.assist.AssistStructure;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private ServerThread serverThread;
    private Car car;
    private Handler cameraHandler;
    private VideoHandler videoHandler;
    private CameraDevice cameraDevice;
    private TextureView textureView;
    private CaptureRequest.Builder requestBuilder;
    private ServerHandler handler = new ServerHandler(this);

    private static final int videoPort = 10002;
    private static final int controlPort = 10001;
    private static final String TAG = "CarServer";
    private static final int PERMISSIONS_REQUEST_CODE = 2345;
    private Surface surface;
    private Size imageDimension;

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

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.CHANGE_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {Manifest.permission.CAMERA, Manifest.permission.CHANGE_NETWORK_STATE},
                    PERMISSIONS_REQUEST_CODE);
        }

        textureView = findViewById(R.id.textureView);
        textureView.setKeepScreenOn(true);
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
                if (videoHandler.isRunning()) {
                    Bitmap bmp = textureView.getBitmap();
                    videoHandler.setBmp(bmp);
                    videoHandler.sendEmptyMessage(0);
                }
            }
        });

        HandlerThread thread = new HandlerThread("VideoSender");
        thread.start();
        videoHandler = new VideoHandler(thread.getLooper());

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice carDevice = null;

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("BT04-A")) {
                    carDevice = device;
                }
            }
        }

        try {
            BluetoothSocket btSocket = carDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));
            btSocket.connect();
            OutputStream out = btSocket.getOutputStream();
            if (out != null) {
                Log.d(TAG, "failed to get output");
                car = new Car(out);
            }
        } catch (IOException e) {
            Log.d(TAG, "create bluetooth failed");
            e.printStackTrace();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        serverThread.setRunning(true);
        serverThread.start();
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
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
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
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            surface = new Surface(texture);

            requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            requestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                            CaptureRequest request = requestBuilder.build();
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
                InetAddress address = (InetAddress) b.getSerializable("address");
                Log.d(activity.TAG, "connect device: " + address.getHostAddress());
                activity.videoHandler.setClientAddress(address);
                activity.videoHandler.setRunning(true);
            } else if (action.equals("disconnectCamera")) {
                Log.d(activity.TAG, "disconnect device");
                activity.videoHandler.setRunning(false);
            }
        }
    }

    private static class VideoHandler extends Handler {
        private boolean running;
        private Bitmap bmp;
        private InetAddress clientAddress;

        VideoHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            if (running) {
                try {
                    Socket s = new Socket(clientAddress, videoPort);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    baos.flush();
                    baos.close();

                    OutputStream out = s.getOutputStream();
                    out.write(baos.toByteArray());
                    out.flush();
                    out.close();

                    s.close();
                } catch (IOException e) {
                    Log.d(TAG, "send img failed");
                    e.printStackTrace();
                }
            }
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        public void setBmp(Bitmap bmp) {
            this.bmp = bmp;
        }

        public void setClientAddress(InetAddress clientAddress) {
            this.clientAddress = clientAddress;
        }

        public boolean isRunning() {
            return running;
        }
    }
}
