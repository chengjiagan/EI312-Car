package com.waterlemongan.carclient;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.net.InetAddress;

public class TouchActivity extends AppCompatActivity {
    protected static final float FLIP_DISTANCE = 100;
    private InetAddress address;
    private CarServer carServer;
    private GestureDetector mDetector;
    private ImageView img;

    private static final String TAG = "TouchActivity";

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
                intent = new Intent(this, FaceActivity.class);
                intent.putExtra(WifiActivity.EXTRA_DEVICE_ADDRESS, address);
                startActivity(intent);
                return true;

            case R.id.atn_touch:
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_touch);

        address = (InetAddress) getIntent().getSerializableExtra(WifiActivity.EXTRA_DEVICE_ADDRESS);
        Log.d(TAG, "device address: " + address.getHostAddress());
        carServer = new CarServer(address);

        img = findViewById(R.id.touchImage);

        mDetector = new GestureDetector(this, new GestureDetector.OnGestureListener() {

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public void onShowPress(MotionEvent e) {
                // TODO Auto-generated method stub

            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                // TODO Auto-generated method stub

            }

            /**
             *
             * e1 The first down motion event that started the fling. e2 The
             * move motion event that triggered the current onFling.
             */
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1.getX() - e2.getX() >= 0 ) {

                    if (e1.getY() - e2.getY() > e1.getX() - e2.getX()) {
                        Log.i(TAG, "向上滑...");
                        img.setImageDrawable(getDrawable(R.drawable.caret_up));
                        carServer.forward();
                        return true;
                    }

                    if (e2.getY() - e1.getY() > e1.getX() - e2.getX()) {
                        Log.i(TAG, "向下滑...");
                        img.setImageDrawable(getDrawable(R.drawable.ic_stop));
                        carServer.stop();
                        return true;
                    }

                    Log.i(TAG, "向左滑...");
                    img.setImageDrawable(getDrawable(R.drawable.caret_left));
                    carServer.left();
                    return true;
                }
                if (e2.getX() - e1.getX() > 0) {
                    if (e1.getY() - e2.getY() > e2.getX() - e1.getX()) {
                        Log.i(TAG, "向上滑...");
                        img.setImageDrawable(getDrawable(R.drawable.caret_up));
                        carServer.forward();
                        return true;
                    }

                    if (e2.getY() - e1.getY() > e2.getX() - e1.getX()) {
                        Log.i(TAG, "向下滑...");
                        img.setImageDrawable(getDrawable(R.drawable.ic_stop));
                        carServer.stop();
                        return true;
                    }

                    Log.i(TAG, "向右滑...");
                    img.setImageDrawable(getDrawable(R.drawable.caret_right));
                    carServer.right();
                    return true;
                }

                Log.d("TAG", e2.getX() + " " + e2.getY());

                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                // TODO Auto-generated method stub
                return false;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mDetector.onTouchEvent(event);
    }
}
