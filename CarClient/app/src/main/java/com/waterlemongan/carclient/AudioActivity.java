package com.waterlemongan.carclient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.InetAddress;
import java.util.ArrayList;

public class AudioActivity extends AppCompatActivity {
    private ImageButton button;
    private SpeechRecognizer mIat;
    private InetAddress address;
    private CarServer carServer;
    private TextView audioText;
    private ImageView img;
    private static final String TAG = "AudioActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);

        address = (InetAddress) getIntent().getSerializableExtra(WifiActivity.EXTRA_DEVICE_ADDRESS);
        Log.d(TAG, "device address: " + address.getHostAddress());
        carServer = new CarServer(address);

        SpeechUtility.createUtility(AudioActivity.this, "appid=5df0d212");
        mIat = SpeechRecognizer.createRecognizer(this, new InitListener() {
            @Override
            public void onInit(int code) {
                Log.d(TAG, "SpeechRecognizer init() code = " + code);
            }
        });

        button = findViewById(R.id.audioButton);
        audioText = findViewById(R.id.audioText);
        img = findViewById(R.id.audioImage);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        int ret = mIat.startListening(mRecognizerListener);
                        if (ret != ErrorCode.SUCCESS) {
                            Log.d(TAG, "error code：" + ret);
                        } else {
                            button.setImageDrawable(getDrawable(R.drawable.ic_audio_fill));
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                        mIat.stopListening();
                        button.setImageDrawable(getDrawable(R.drawable.ic_audio));
                        return false;
                    default:
                        return false;
                }
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
                intent = new Intent(AudioActivity.this, MainActivity.class);
                intent.putExtra(WifiActivity.EXTRA_DEVICE_ADDRESS, address);
                startActivity(intent);
                return true;

            case R.id.atn_audio:
                return true;

            case R.id.atn_face:
                intent = new Intent(AudioActivity.this, FaceActivity.class);
                intent.putExtra(WifiActivity.EXTRA_DEVICE_ADDRESS, address);
                startActivity(intent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            //nothing to do
        }

        @Override
        public void onError(SpeechError error) {
            Log.d(TAG, error.getPlainDescription(true));
        }

        @Override
        public void onEndOfSpeech() {
            // nothing to do
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.d(TAG, results.getResultString());

            StringBuffer ret = new StringBuffer();
            try {
                JSONTokener tokener = new JSONTokener(results.getResultString());
                JSONObject joResult = new JSONObject(tokener);

                JSONArray words = joResult.getJSONArray("ws");
                for (int i = 0; i < words.length(); i++) {
                    // 转写结果词，默认使用第一个结果
                    JSONArray items = words.getJSONObject(i).getJSONArray("cw");
                    JSONObject obj = items.getJSONObject(0);
                    ret.append(obj.getString("w"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            String text = ret.toString();
            audioText.setText(text);
            Log.d(TAG, text);

            if (text.indexOf("前") != -1) {
                img.setImageDrawable(getDrawable(R.drawable.caret_up));
                carServer.forward();
            } else if (text.indexOf("后") != -1) {
                img.setImageDrawable(getDrawable(R.drawable.caret_down));
                carServer.backward();
            } else if (text.indexOf("左") != -1) {
                img.setImageDrawable(getDrawable(R.drawable.caret_left));
                carServer.left();
            } else if (text.indexOf("右") != -1) {
                img.setImageDrawable(getDrawable(R.drawable.caret_right));
                carServer.right();
            } else {
                img.setImageDrawable(getDrawable(R.drawable.ic_stop));
                carServer.stop();
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            // nothing to do
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // nothing to do
        }
    };
}
