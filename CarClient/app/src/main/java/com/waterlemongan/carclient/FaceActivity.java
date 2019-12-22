package com.waterlemongan.carclient;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static java.lang.System.out;

public class FaceActivity extends AppCompatActivity {
    private Button takePictureButton;
    private TextureView textureView;

    private Size imageDimension;
    private CameraDevice cameraDevice;
    private Surface surface;
    private Handler cameraHandler;
    private CameraCaptureSession captureSession;

    private static final String IMAGE_NAME = "face.jpg";
    private static final String TAG = "FaceActivity";
    // webapi 接口地址
    private static final String URL = "http://tupapi.xfyun.cn/v1/expression";
    // 应用ID(必须为webapi类型应用,并人脸特征分析服务,参考帖子如何创建一个webapi应用：http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=36481)
    private static final String APPID = "5df09d43";
    // 接口密钥(webapi类型应用开通人脸特征分析服务后，控制台--我的应用---人脸特征分析---服务的apikey
    private static final String API_KEY = "1aeb43080be1d5af3d8e4ed2baed9ed7";
    private InetAddress address;
    private CarServer carServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face);

        address = (InetAddress) getIntent().getSerializableExtra(WifiActivity.EXTRA_DEVICE_ADDRESS);
        Log.d(TAG, "device address: " + address.getHostAddress());
        carServer = new CarServer(address);
        carServer.stop();

        HandlerThread thread = new HandlerThread("FaceActivity/Camera");
        thread.start();
        cameraHandler = new Handler(thread.getLooper());

        textureView = findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

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

            }
        });

        takePictureButton = findViewById(R.id.btn_takepicture);
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        takePicture();
                    }
                }).start();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.atn_basic:
                intent = new Intent(this, MainActivity.class);
                intent.putExtra(WifiActivity.EXTRA_DEVICE_ADDRESS, address);
                startActivity(intent);
                return true;

            case R.id.atn_audio:
                intent = new Intent(this, AudioActivity.class);
                intent.putExtra(WifiActivity.EXTRA_DEVICE_ADDRESS, address);
                startActivity(intent);
                return true;

            case R.id.atn_face:
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = Integer.toString(CameraCharacteristics.LENS_FACING_BACK);

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                finish();
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
            Log.d(TAG, "camera open");
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            if (camera != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            Log.d(TAG, "camera disconnect");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            if (camera != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            Log.d(TAG, "camera error");
        }
    };

    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            surface = new Surface(texture);

            cameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                captureSession = session;

                                CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                builder.addTarget(surface);
                                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

                                captureSession.setRepeatingRequest(builder.build(), null, cameraHandler);
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

    private void takePicture() {
        Bitmap bmp = textureView.getBitmap();

        Matrix matrix = new Matrix();
        matrix.setScale(0.5f, 0.5f);
        //bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        Log.d(TAG, "picture size: " + baos.size());

        String result = doPost(URL, buildHttpHeader(), baos.toByteArray());
        Log.d(TAG, result);

        try {
            JSONObject json = new JSONObject(new JSONTokener(result));

            int code = json.getInt("code");
            if (code != 0) {
                // no human
                Log.d(TAG, "stop");
                carServer.stop();
            } else {
                JSONArray fileList = json.getJSONObject("data").getJSONArray("fileList");
                boolean review = fileList.getJSONObject(0).getBoolean("review");
                if (review) {
                    // other emotion
                    Log.d(TAG, "forward");
                carServer.forward();
                } else {
                    // smile
                    Log.d(TAG, "backward");
                carServer.backward();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> buildHttpHeader() {
        String curTime = System.currentTimeMillis() / 1000L + "";
        String param = "{\"image_name\":\"" + IMAGE_NAME + "\"}";
        String paramBase64 = new String(Base64.encodeBase64(param.getBytes()));
        String checkSum = DigestUtils.md5Hex(API_KEY + curTime + paramBase64);
        Map<String, String> header = new HashMap<String, String>();
        header.put("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        header.put("X-Param", paramBase64);
        header.put("X-CurTime", curTime);
        header.put("X-CheckSum", checkSum);
        header.put("X-Appid", APPID);
        return header;
    }

    public static String doPost(String url, Map<String, String> header, byte[] body) {
        String result = "";
        BufferedReader in = null;
        try {
            // 设置 url
            URL realUrl = new URL(url);
            URLConnection connection = realUrl.openConnection();
            HttpURLConnection httpURLConnection = (HttpURLConnection) connection;
            // 设置 header
            for (String key : header.keySet()) {
                httpURLConnection.setRequestProperty(key, header.get(key));
            }
            // 设置请求 body
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setRequestProperty("Content-Type", "binary/octet-stream");

            OutputStream out = httpURLConnection.getOutputStream();
            out.write(body);
            out.flush();
            out.close();
            if (HttpURLConnection.HTTP_OK != httpURLConnection.getResponseCode()) {
                System.out.println("http connection failed：" + httpURLConnection.getResponseCode());
                return null;
            }

            // 获取响应body
            in = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result += line;
            }
        } catch (Exception e) {
            return null;
        }
        return result;
    }
}
