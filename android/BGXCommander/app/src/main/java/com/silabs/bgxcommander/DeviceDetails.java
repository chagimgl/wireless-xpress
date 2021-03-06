/*
 * Copyright 2018 Silicon Labs
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * {{ http://www.apache.org/licenses/LICENSE-2.0}}
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.silabs.bgxcommander;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.Image;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.os.Handler;
import android.widget.Toast;

import java.util.List;
import java.util.Queue;
import java.util.UUID;

import static com.silabs.bgxcommander.BGXpressService.BGX_DEVICE_INFO;
import static com.silabs.bgxcommander.TextSource.LOCAL;
import static com.silabs.bgxcommander.TextSource.REMOTE;


public class DeviceDetails extends AppCompatActivity {

    public BluetoothDevice mBluetoothDevice;
    public Handler mHandler;

    public int mDeviceConnectionState;

    private BroadcastReceiver mConnectionBroadcastReceiver;
    private BroadcastReceiver mBondReceiver;
    public final Context mContext = this;


    // UI Elements
    private EditText mStreamEditText;
    private EditText mMessageEditText;
    private RadioButton mStreamRB;
    private RadioButton mCommandRB;
    private Button mSendButton;

    private int mBusMode;

    private TextSource mTextSource = TextSource.UNKNOWN;
    private final int kAutoScrollMessage = 0x5C011;
    private final int kAutoScrollDelay = 800; // the time in ms between adding text and autoscroll.

    private MenuItem mUpdateItem;

    private BGXpressService.BGXPartID mBGXPartID;
    private String mBGXDeviceID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_details);

        mBusMode = BusMode.UNKNOWN_MODE;

        mStreamEditText = (EditText) findViewById(R.id.streamEditText);
        mMessageEditText = (EditText) findViewById(R.id.msgEditText);
        mStreamRB = (RadioButton) findViewById(R.id.streamRB);
        mCommandRB = (RadioButton) findViewById(R.id.commandRB);
        mSendButton = (Button) findViewById(R.id.sendButton);

        final IntentFilter bgxpressServiceFilter = new IntentFilter(BGXpressService.BGX_CONNECTION_STATUS_CHANGE);
        bgxpressServiceFilter.addAction(BGXpressService.BGX_MODE_STATE_CHANGE);
        bgxpressServiceFilter.addAction(BGXpressService.BGX_DATA_RECEIVED);
        bgxpressServiceFilter.addAction(BGXpressService.BGX_DEVICE_INFO);

        mConnectionBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch(intent.getAction()) {
                    case BGXpressService.BGX_CONNECTION_STATUS_CHANGE: {
                        Log.d("debug", "BGX Connection State Change");

                        BGX_CONNECTION_STATUS connectionState = (BGX_CONNECTION_STATUS) intent.getSerializableExtra("bgx-connection-status");
                        switch (connectionState) {
                            case CONNECTED:
                                Log.d("debug", "DeviceDetails - connection state changed to CONNECTED");
                                break;
                            case CONNECTING:
                                Log.d("debug", "DeviceDetails - connection state changed to CONNECTING");
                                break;
                            case DISCONNECTING:
                                Log.d("debug", "DeviceDetails - connection state changed to DISCONNECTING");
                                break;
                            case DISCONNECTED:
                                Log.d("debug", "DeviceDetails - connection state changed to DISCONNECTED");
                                finish();
                                break;
                            case INTERROGATING:
                                Log.d("debug", "DeviceDetails - connection state changed to INTERROGATING");
                                break;
                            default:
                                Log.d("debug", "DeviceDetails - connection state changed to Unknown connection state.");
                                break;
                        }

                    }
                    break;
                    case BGXpressService.BGX_MODE_STATE_CHANGE: {
                        Log.d("debug", "BGX Bus Mode Change");
                        setBusMode(intent.getIntExtra("busmode", BusMode.UNKNOWN_MODE));
                    }
                    break;
                    case BGXpressService.BGX_DATA_RECEIVED: {
                        String stringReceived = intent.getStringExtra("data");
                        processText(stringReceived, REMOTE);

                    }
                    break;
                    case BGXpressService.BGX_DEVICE_INFO: {
                        mBGXDeviceID = intent.getStringExtra("bgx-device-uuid");
                        mBGXPartID = (BGXpressService.BGXPartID) intent.getSerializableExtra("bgx-part-id" );
                    }
                    break;
                }
            }
        };

        registerReceiver(mConnectionBroadcastReceiver, bgxpressServiceFilter);


        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {



                return false;
            }
        });



        mSendButton.setEnabled(true);
        mCommandRB.setEnabled(true);
        mStreamRB.setEnabled(true);

        mStreamRB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (mBusMode != BusMode.STREAM_MODE) {
                        sendBusMode(BusMode.STREAM_MODE);
                        setBusMode(BusMode.STREAM_MODE);
                    }
                }

            }
        });

        mCommandRB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (mBusMode != BusMode.REMOTE_COMMAND_MODE) {
                        sendBusMode(BusMode.REMOTE_COMMAND_MODE);
                        setBusMode(BusMode.REMOTE_COMMAND_MODE);
                    }
                }

            }
        });

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("debug", "Send button clicked.");


                String msgText = mMessageEditText.getText().toString();
                // let's write it.
                Intent writeIntent = new Intent(BGXpressService.ACTION_WRITE_SERIAL_DATA);
                writeIntent.putExtra("value", msgText + "\r\n");
                writeIntent.setClass(mContext, BGXpressService.class);
                startService(writeIntent);

                processText(msgText, LOCAL);
                mMessageEditText.setText("", EditText.BufferType.EDITABLE);
            }
        });

        final ImageButton clearButton = (ImageButton) findViewById(R.id.clearImageButton);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("debug", "clear");
                mStreamEditText.setText("");
            }
        });


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);



        mBluetoothDevice = (BluetoothDevice) getIntent().getExtras().getParcelable("BLUETOOTH_DEVICE");
        String sdeviceName = mBluetoothDevice.getName();
        if (null == sdeviceName) {
            sdeviceName = "No device name";
        }


        android.support.v7.app.ActionBar ab = getSupportActionBar();
        if (null != ab) {
            ab.setTitle(sdeviceName);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(BGXpressService.ACTION_READ_BUS_MODE);
                intent.setClass(mContext, BGXpressService.class);
                startService(intent);
            }
        });


        BGXpressService.getBGXDeviceInfo(this);
    }

    @Override
    protected void onDestroy() {

        Log.d("debug", "Unregistering the connectionBroadcastReceiver");
        unregisterReceiver(mConnectionBroadcastReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.devicedetails, menu);

        mUpdateItem = menu.findItem(R.id.update_menuitem);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {

        switch(mi.getItemId()) {
            case R.id.update_menuitem: {
                Log.d("debug", "Update menu item pressed.");

                if ( null == mBGXPartID || BGXpressService.BGXPartID.BGXInvalid == mBGXPartID ) {
                    Toast.makeText(this, "Invalid BGX Part ID", Toast.LENGTH_LONG).show();
                } else {

                    Intent intent = new Intent(this, FirmwareUpdate.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("bgx-device-uuid", mBGXDeviceID);
                    intent.putExtra("bgx-part-id", mBGXPartID);

                    startActivity(intent);
                }
            }
                break;
        }

        return super.onOptionsItemSelected(mi);
    }

    @Override
    public void onBackPressed() {
        Log.d("debug", "Back button pressed.");
        disconnect();

        super.onBackPressed();
    }

    void disconnect()
    {
        Intent intent = new Intent(BGXpressService.ACTION_BGX_DISCONNECT);
        intent.setClass(mContext, BGXpressService.class);
        startService(intent);
    }





    public void setBusMode(int busMode) {
        if (mBusMode != busMode) {

            mBusMode = busMode;


            mHandler.post(new Runnable() {
                @Override
                public void run() {

                    switch (mBusMode) {
                        case BusMode.UNKNOWN_MODE:
                            mStreamRB.setChecked(false);
                            mCommandRB.setChecked(false);
                            break;
                        case BusMode.STREAM_MODE:
                            mStreamRB.setChecked(true);
                            mCommandRB.setChecked(false);

                            break;
                        case BusMode.LOCAL_COMMAND_MODE:
                        case BusMode.REMOTE_COMMAND_MODE:
                            mStreamRB.setChecked(false);
                            mCommandRB.setChecked(true);
                            break;
                    }
                }
            });

        }
    }

    public void sendBusMode(int busMode)
    {
        Intent intent = new Intent(BGXpressService.ACTION_WRITE_BUS_MODE);
        intent.setClass(this, BGXpressService.class);
        intent.putExtra("busmode", busMode);
        startService(intent);
    }

    public int getBusMode() {
        return mBusMode;
    }


    void processText(String text, TextSource ts ) {

        String newText;

        SpannableStringBuilder ssb = new SpannableStringBuilder();

        switch (ts) {
            case LOCAL: {
                ssb.append("\n>" + text, new ForegroundColorSpan(Color.WHITE), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            break;
            case REMOTE: {
                if (REMOTE != mTextSource) {
                    newText = "\n<" + text;
                } else {
                    newText = text;
                }
                ssb.append(newText, new ForegroundColorSpan(Color.GREEN), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            }
        }

        mStreamEditText.append(ssb);

        mTextSource = ts;

    }

}
