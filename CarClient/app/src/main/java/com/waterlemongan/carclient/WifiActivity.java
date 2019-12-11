package com.waterlemongan.carclient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class WifiActivity extends AppCompatActivity {
    private ListView listView;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private List<WifiP2pDevice> deviceList;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if (manager != null) {
                    manager.requestPeers(channel, new WifiP2pManager.PeerListListener() {
                        @Override
                        public void onPeersAvailable(WifiP2pDeviceList peers) {
                            deviceList.clear();
                            deviceList.addAll(peers.getDeviceList());
                            ((WiFiPeerListAdapter) listView.getAdapter()).notifyDataSetChanged();
                            Log.d(TAG, "peers found: " + deviceList.size());
                        }
                    });
                }
            }
        }
    };

    private static final int PERMISSIONS_REQUEST_CODE = 1001;

    public static final String TAG = "WifiActivity";
    public static final String EXTRA_WIFI_DEVICE = "com.waterlemongan.carclient.WIFI_DEVICE";
    public static final String EXTRA_DEVICE_ADDRESS = "com.waterlemongan.carclient.DEVICE_ADDRESS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi);

        deviceList = new ArrayList<WifiP2pDevice>();
        listView = (ListView) findViewById(R.id.listView);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        listView.setAdapter(new WiFiPeerListAdapter(this, R.layout.row_device, deviceList));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                WifiP2pDevice device = deviceList.get(position);
                Log.d(TAG, "connect to device: " + device.deviceName);

                connect(device);
//                Intent intent = new Intent(WifiActivity.this, MainActivity.class);
//                intent.putExtra(EXTRA_WIFI_DEVICE, device);
//                startActivity(intent);
            }
        });

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.CHANGE_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CHANGE_NETWORK_STATE
            }, PERMISSIONS_REQUEST_CODE);
        }
    }

    private void connect(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
                        Toast.makeText(WifiActivity.this, "Connect Successfully", Toast.LENGTH_LONG).show();
                        Log.d(TAG, "get car server address succeed");

                        Intent intent = new Intent(WifiActivity.this, MainActivity.class);
                        intent.putExtra(EXTRA_DEVICE_ADDRESS, info.groupOwnerAddress);
                        startActivity(intent);
                    }
                });
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(WifiActivity.this, "Connect Failed: " + reason, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, new IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_wifi, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.atn_wifi_search:
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        // nothing to do
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d(TAG, "discovery failed" + reason);
                        Toast.makeText(WifiActivity.this, "Discovery Failed: " + reason, Toast.LENGTH_LONG).show();
                    }
                });
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private class WiFiPeerListAdapter extends ArrayAdapter<WifiP2pDevice> {

        private List<WifiP2pDevice> items;

        /**
         * @param context
         * @param textViewResourceId
         * @param objects
         */
        public WiFiPeerListAdapter(Context context, int textViewResourceId,
                                   List<WifiP2pDevice> objects) {
            super(context, textViewResourceId, objects);
            items = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.row_device, null);
            }
            WifiP2pDevice device = items.get(position);
            if (device != null) {
                TextView top = (TextView) v.findViewById(R.id.deviceName);
                TextView bottom = (TextView) v.findViewById(R.id.deviceAddress);

                if (top != null) {
                    top.setText(device.deviceName);
                }
                if (bottom != null) {
                    bottom.setText(device.deviceAddress);
                }
            }
            return v;
        }
    }
}
