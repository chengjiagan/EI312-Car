package com.waterlemongan.carclient;

import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ImageView;

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
                if (e1.getX() - e2.getX() > FLIP_DISTANCE) {
                    Log.i(TAG, "向左滑...");
                    img.setImageDrawable(getDrawable(R.drawable.caret_left));
                    carServer.left();
                    return true;
                }
                if (e2.getX() - e1.getX() > FLIP_DISTANCE) {
                    Log.i(TAG, "向右滑...");
                    img.setImageDrawable(getDrawable(R.drawable.caret_right));
                    carServer.right();
                    return true;
                }
                if (e1.getY() - e2.getY() > FLIP_DISTANCE) {
                    Log.i(TAG, "向上滑...");
                    img.setImageDrawable(getDrawable(R.drawable.caret_up));
                    carServer.forward();
                    return true;
                }
                if (e2.getY() - e1.getY() > FLIP_DISTANCE) {
                    Log.i(TAG, "向下滑...");
                    img.setImageDrawable(getDrawable(R.drawable.ic_stop));
                    carServer.stop();
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
