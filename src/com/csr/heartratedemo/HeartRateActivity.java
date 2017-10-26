/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2014
 *
 *  This software is provided to the customer for evaluation
 *  purposes only and, as such early feedback on performance and operation
 *  is anticipated. The software source code is subject to change and
 *  not intended for production. Use of developmental release software is
 *  at the user's own risk. This software is provided "as is," and CSR
 *  cautions users to determine for themselves the suitability of using the
 *  beta release version of this software. CSR makes no warranty or
 *  representation whatsoever of merchantability or fitness of the product
 *  for any particular purpose or use. In no event shall CSR be liable for
 *  any consequential, incidental or special damages whatsoever arising out
 *  of the use of or inability to use this software, even if the user has
 *  advised CSR of the possibility of such damages.
 *
 ******************************************************************************/

package com.csr.heartratedemo;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.UUID;
import com.csr.btsmart.BtSmartService;
import com.csr.btsmart.BtSmartService.BtSmartUuid;
import com.csr.view.DataView;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.text.format.Time;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

public class HeartRateActivity extends Activity {

    private BluetoothDevice mDeviceToConnect = null;
    private BtSmartService mService = null;
    private boolean mConnected = false;

    // For connect timeout.
    private static Handler mHandler = new Handler();
    
    // Data Views to update on the display.
    DataView heartRateData = null;
    DataView rrData = null;
    DataView energyData = null;
    DataView locationData = null;
    DataView positionLeftData = null;
    DataView positionRightData = null;
    DataView respirationLeftData = null;
    DataView currentTimeData = null;
    DataView tempData = null;

    private String mManufacturer;
    private String mHardwareRev;
    private String mFwRev;
    private String mSwRev;
    private String mSerialNo;
    private String mBatteryPercent;

    private static final int REQUEST_MANUFACTURER = 0;
    private static final int REQUEST_BATTERY = 1;
    private static final int REQUEST_HEART_RATE = 2;
    private static final int REQUEST_LOCATION = 3;
    private static final int REQUEST_HARDWARE_REV = 4;
    private static final int REQUEST_FW_REV = 5;
    private static final int REQUEST_SW_REV = 6;
    private static final int REQUEST_SERIAL_NO = 7;
    private static final int REQUEST_POS_SENSOR = 8;

    private static final int CONNECT_TIMEOUT_MILLIS = 5000;

    public static final String EXTRA_MANUFACTURER = "MANUF";
    public static final String EXTRA_HARDWARE_REV = "HWREV";
    public static final String EXTRA_FW_REV = "FWREV";
    public static final String EXTRA_SW_REV = "SWREV";
    public static final String EXTRA_SERIAL = "SERIALNO";
    public static final String EXTRA_BATTERY = "BATTERY";

    private static final int INFO_ACTIVITY_REQUEST = 1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent screen rotation.
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        // Display back button in action bar.
        getActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.activity_heart_rate);

        heartRateData = (DataView) findViewById(R.id.heartRateData);
        rrData = (DataView) findViewById(R.id.RRData);
        energyData = (DataView) findViewById(R.id.energyData);
        locationData = (DataView) findViewById(R.id.locationData);
        positionLeftData = (DataView) findViewById(R.id.positionLeft);
        positionRightData = (DataView) findViewById(R.id.positionRight);
        respirationLeftData = (DataView) findViewById(R.id.respirationLeft);
        currentTimeData = (DataView) findViewById(R.id.currentTime);
        tempData = (DataView) findViewById(R.id.temperatureData);

        // Get the device to connect to that was passed to us by the scan results Activity.
        Intent intent = getIntent();
        if (intent != null) {
            mDeviceToConnect = intent.getExtras().getParcelable(BluetoothDevice.EXTRA_DEVICE);
                
            // Make a connection to BtSmartService to enable us to use its services.
            Intent bindIntent = new Intent(this, BtSmartService.class);
            bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.heart_rate, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
        case R.id.action_info:
            Intent intent = new Intent(this, DeviceInfoActivity.class);
            intent.putExtra(EXTRA_MANUFACTURER, mManufacturer);
            intent.putExtra(EXTRA_HARDWARE_REV, mHardwareRev);
            intent.putExtra(EXTRA_FW_REV, mFwRev);
            intent.putExtra(EXTRA_SW_REV, mSwRev);
            intent.putExtra(EXTRA_SERIAL, mSerialNo);
            intent.putExtra(EXTRA_BATTERY, mBatteryPercent);
            // Start with startActivityForResult so that we can kill it using the request id if Bluetooth disconnects.
            this.startActivityForResult(intent, INFO_ACTIVITY_REQUEST);
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onDestroy() {
        // Disconnect Bluetooth connection.
        if (mService != null) {
            mService.disconnect();
        }
        unbindService(mServiceConnection);
        Toast.makeText(this, "Disconnected from heart rate sensor.", Toast.LENGTH_SHORT).show();
        finishActivity(INFO_ACTIVITY_REQUEST);
        super.onDestroy();
    }

    /**
     * Callbacks for changes to the state of the connection to BtSmartService.
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((BtSmartService.LocalBinder) rawBinder).getService();
            if (mService != null) {
                // We have a connection to BtSmartService so now we can connect and register the device handler.
                mService.connectAsClient(mDeviceToConnect, mDeviceHandler);
                startConnectTimer();
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
        }
    };

    /**
     * Start a timer to close the Activity after a fixed length of time. Used to prevent waiting for the connection to
     * happen forever.
     */
    private void startConnectTimer() {
        mHandler.postDelayed(onConnectTimeout, CONNECT_TIMEOUT_MILLIS);        
    }

    private Runnable onConnectTimeout = new Runnable() {
        @Override
        public void run() {
            if (!mConnected) finish();            
        }
    };

    /**
     * This is the handler for general messages about the connection.
     */
    private final DeviceHandler mDeviceHandler = new DeviceHandler(this);

    private static class DeviceHandler extends Handler {
        private final WeakReference<HeartRateActivity> mActivity;

        public DeviceHandler(HeartRateActivity activity) {
            mActivity = new WeakReference<HeartRateActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            HeartRateActivity parentActivity = mActivity.get();
            if (parentActivity != null) {
                BtSmartService smartService = parentActivity.mService;
                switch (msg.what) {
                case BtSmartService.MESSAGE_CONNECTED: {

                    if (parentActivity != null) {
                        parentActivity.mConnected = true;

                        // Cancel the connect timer.
                        mHandler.removeCallbacks(parentActivity.onConnectTimeout);
                        
                        parentActivity.setProgressBarIndeterminateVisibility(false);
                        // Get the device information - this will come back to
                        // us in a MESSAGE_CHARACTERISTIC_VALUE event.
                        smartService.requestCharacteristicValue(REQUEST_MANUFACTURER,
                                BtSmartUuid.DEVICE_INFORMATION_SERVICE.getUuid(),
                                BtSmartUuid.MANUFACTURER_NAME.getUuid(), parentActivity.mHeartHandler);

                        smartService.requestCharacteristicValue(REQUEST_LOCATION, BtSmartUuid.HRP_SERVICE.getUuid(),
                                BtSmartUuid.HEART_RATE_LOCATION.getUuid(), parentActivity.mHeartHandler);

                        smartService.requestCharacteristicValue(REQUEST_HARDWARE_REV,
                                BtSmartUuid.DEVICE_INFORMATION_SERVICE.getUuid(),
                                BtSmartUuid.HARDWARE_REVISION.getUuid(), parentActivity.mHeartHandler);

                        smartService.requestCharacteristicValue(REQUEST_FW_REV,
                                BtSmartUuid.DEVICE_INFORMATION_SERVICE.getUuid(),
                                BtSmartUuid.FIRMWARE_REVISION.getUuid(), parentActivity.mHeartHandler);

                        smartService.requestCharacteristicValue(REQUEST_SW_REV,
                                BtSmartUuid.DEVICE_INFORMATION_SERVICE.getUuid(),
                                BtSmartUuid.SOFTWARE_REVISION.getUuid(), parentActivity.mHeartHandler);

                        smartService.requestCharacteristicValue(REQUEST_SERIAL_NO,
                                BtSmartUuid.DEVICE_INFORMATION_SERVICE.getUuid(), BtSmartUuid.SERIAL_NUMBER.getUuid(),
                                parentActivity.mHeartHandler);

                        // Register to be told about battery level.
                        smartService.requestCharacteristicNotification(REQUEST_BATTERY,
                                BtSmartUuid.BATTERY_SERVICE.getUuid(), BtSmartUuid.BATTERY_LEVEL.getUuid(),
                                parentActivity.mHeartHandler);

                        // Register to be told about heart rate values.
                        smartService.requestCharacteristicNotification(REQUEST_HEART_RATE,
                                BtSmartUuid.HRP_SERVICE.getUuid(), BtSmartUuid.HEART_RATE_MEASUREMENT.getUuid(),
                                parentActivity.mHeartHandler);

                        // JDP Register to be told about position left value.
                        smartService.requestCharacteristicNotification(REQUEST_POS_SENSOR,
                                BtSmartUuid.POS_SENSOR_SERVICE.getUuid(), BtSmartUuid.POS_SENSOR_VALUE.getUuid(),
                                parentActivity.mHeartHandler);
                    }
                    break;
                }
                case BtSmartService.MESSAGE_DISCONNECTED: {
                    // End this activity and go back to scan results view.
                    mActivity.get().finish();
                    break;
                }
                }
            }
        }
    };

    /**
     * This is the handler for characteristic value updates.
     */
    private final Handler mHeartHandler = new HeartRateHandler(this);

    private static class HeartRateHandler extends Handler {
        private final WeakReference<HeartRateActivity> mActivity;

        public HeartRateHandler(HeartRateActivity activity) {
            mActivity = new WeakReference<HeartRateActivity>(activity);
        }

        public void handleMessage(Message msg) {
            HeartRateActivity parentActivity = mActivity.get();
            if (parentActivity != null) {
                switch (msg.what) {
                case BtSmartService.MESSAGE_REQUEST_FAILED: {
                    // The request id tells us what failed.
                    int requestId = msg.getData().getInt(BtSmartService.EXTRA_CLIENT_REQUEST_ID);
                    switch (requestId) {
                    case REQUEST_HEART_RATE:
                        Toast.makeText(parentActivity, "Failed to register for heart rate notifications.",
                                Toast.LENGTH_SHORT).show();
                        parentActivity.finish();
                        break;
                    default:
                        break;
                    }
                    break;
                }
                case BtSmartService.MESSAGE_CHARACTERISTIC_VALUE: {
                    Bundle msgExtra = msg.getData();
                    UUID serviceUuid =
                            ((ParcelUuid) msgExtra.getParcelable(BtSmartService.EXTRA_SERVICE_UUID)).getUuid();
                    UUID characteristicUuid =
                            ((ParcelUuid) msgExtra.getParcelable(BtSmartService.EXTRA_CHARACTERISTIC_UUID)).getUuid();

                    // Heart rate notification.
                   /* if (serviceUuid.compareTo(BtSmartUuid.HRP_SERVICE.getUuid()) == 0
                            && characteristicUuid.compareTo(BtSmartUuid.HEART_RATE_MEASUREMENT.getUuid()) == 0) {
                        parentActivity.heartRateHandler(msgExtra.getByteArray(BtSmartService.EXTRA_VALUE));
                    }
                    // Device information
                    else */ if (serviceUuid.compareTo(BtSmartUuid.DEVICE_INFORMATION_SERVICE.getUuid()) == 0) {
                        String value;
                        try {
                            value = new String(msgExtra.getByteArray(BtSmartService.EXTRA_VALUE), "UTF-8");
                        }
                        catch (UnsupportedEncodingException e) {
                            value = "--";
                        }
                        if (characteristicUuid.compareTo(BtSmartUuid.MANUFACTURER_NAME.getUuid()) == 0) {
                            parentActivity.mManufacturer = value;
                        }
                        else if (characteristicUuid.compareTo(BtSmartUuid.HARDWARE_REVISION.getUuid()) == 0) {
                            parentActivity.mHardwareRev = value;
                        }
                        else if (characteristicUuid.compareTo(BtSmartUuid.FIRMWARE_REVISION.getUuid()) == 0) {
                            parentActivity.mFwRev = value;
                        }
                        else if (characteristicUuid.compareTo(BtSmartUuid.SOFTWARE_REVISION.getUuid()) == 0) {
                            parentActivity.mSwRev = value;
                        }
                        else if (characteristicUuid.compareTo(BtSmartUuid.SERIAL_NUMBER.getUuid()) == 0) {
                            parentActivity.mSerialNo = value;
                        }

                    }
                    // JDP Position Left notification.
                    /*else if (serviceUuid.compareTo(BtSmartUuid.ALERT_NOTIFICATION_SERVICE.getUuid()) == 0
                            && characteristicUuid.compareTo(BtSmartUuid.ALERT_LEVEL.getUuid()) == 0) {
                        parentActivity.positionLeftNotificationHandler(msgExtra.getByteArray(BtSmartService.EXTRA_VALUE));
                    }*/
                    else if (serviceUuid.compareTo(BtSmartUuid.POS_SENSOR_SERVICE.getUuid()) == 0
                            && characteristicUuid.compareTo(BtSmartUuid.POS_SENSOR_VALUE.getUuid()) == 0) {
                        parentActivity.positionLeftNotificationHandler(msgExtra.getByteArray(BtSmartService.EXTRA_VALUE));
                    }

                    // Battery level notification.
                    else if (serviceUuid.compareTo(BtSmartUuid.BATTERY_SERVICE.getUuid()) == 0
                            && characteristicUuid.compareTo(BtSmartUuid.BATTERY_LEVEL.getUuid()) == 0) {
                        parentActivity.batteryNotificationHandler(msgExtra.getByteArray(BtSmartService.EXTRA_VALUE)[0]);
                    }
                    // Sensor location
                    else if (serviceUuid.compareTo(BtSmartUuid.HRP_SERVICE.getUuid()) == 0
                            && characteristicUuid.compareTo(BtSmartUuid.HEART_RATE_LOCATION.getUuid()) == 0) {
                        parentActivity.sensorLocationHandler(msgExtra.getByteArray(BtSmartService.EXTRA_VALUE)[0]);
                    }

                    break;
                }
                }
            }
        }
    };

    /**
     * Do something with the battery level received in a notification.
     * 
     * @param value
     *            The battery percentage value.
     */
    private void batteryNotificationHandler(byte value) {
        mBatteryPercent = String.valueOf(value + "%");
    }

    /**
     * Use the value received in the sensor location characteristic to display the location.
     * @param locationIndex Value received in location characteristic - indexes into locations array.
     */
    private void sensorLocationHandler(int locationIndex) {
        final String[] locations = { "Other", "Chest", "Wrist", "Finger", "Hand", "Ear lobe", "Foot" };

        String location = "Not recognised";
        if (locationIndex > 0 && locationIndex < locations.length) {
            location = locations[locationIndex];
        }
        locationData.setValueText(location);
    }

    /**
     * Clock utility.
     */
    public class Clock {

        /**
         * Get current time in human-readable form.
         * @return current time as a string.
         */
        public String getNow() {
            Time now = new Time();
            now.setToNow();
            String sTime = now.format("%Y_%m_%d %T");
            return sTime;
        }
        /**
         * Get current time in human-readable form without spaces and special characters.
         * The returned value may be used to compose a file name.
         * @return current time as a string.
         */
        public String getTimeStamp() {
            Time now = new Time();
            now.setToNow();
            String sTime = now.format("%Y_%m_%d_%H_%M_%S");
            return sTime;
        }

    }

    /**
     * Do something with the position level received in a notification.
     *
     * @param value
     *            The position level value.
     */
    private void positionLeftNotificationHandler(byte[] value) {
        final byte INDEX_FLAGS = 0;
        final byte INDEX_POS_VALUE = 1;

        // Re-create the characteristic with the received value.
        BluetoothGattCharacteristic characteristic =
                //new BluetoothGattCharacteristic(BtSmartUuid.ALERT_NOTIFICATION_SERVICE.getUuid(), 0, 0);
                new BluetoothGattCharacteristic(BtSmartUuid.POS_SENSOR_SERVICE.getUuid(), 0, 0);
        characteristic.setValue(value);

        byte flags = characteristic.getValue()[INDEX_FLAGS];

        // Check the flags of the characteristic to find if the position number format is UINT16 or UINT8.
        //JDP We only do lengths of 2 which are UINT16 values!
        int pos_sensor_left = 0,pos_sensor_right=0,resp_sensor_left=0,temp_sensor_data=0;

        //pos_sensor = characteristic.getValue()[1];
        pos_sensor_left = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0/* INDEX_POS_VALUE*/);
        //    hrm = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, INDEX_HRM_VALUE);
        //pos_sensor_right = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16,2);
        pos_sensor_right = 0;//just for testing
        temp_sensor_data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16,4);

        //resp_sensor_left = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16,6);
        resp_sensor_left = 0;//just for testing
        
        /*Get time to include in the reading to the database*/
        //int time = (int) (System.currentTimeMillis());
        //Timestamp tsTemp = new Timestamp(time);
        //String ts =  tsTemp.toString();
        Clock newClock = new Clock();
        String currentTimeStamp = newClock.getNow();

        currentTimeData.setValueText(currentTimeStamp);

        positionLeftData.setValueText(String.valueOf(pos_sensor_left + "mV"));
        positionRightData.setValueText(String.valueOf(pos_sensor_right + "mV"));
        respirationLeftData.setValueText(String.valueOf(resp_sensor_left + "mV"));
        tempData.setValueText(String.valueOf(temp_sensor_data + "C"));
    }

    /**
     * Extract the various values from the heart rate characteristic and display in the UI.
     * 
     * @param value
     *            Value received in the characteristic notification.
     */
    private void heartRateHandler(byte[] value) {
        final byte INDEX_FLAGS = 0;
        final byte INDEX_HRM_VALUE = 1;
        final byte INDEX_ENERGY_VALUE = 2;
         
        byte energyIndexOffset = 0;

        final byte FLAG_HRM_FORMAT = 0x01;
        final byte FLAG_ENERGY_PRESENT = (0x01 << 3);
        final byte FLAG_RR_PRESENT = (0x01 << 4);
        
        final byte SIZEOF_UINT16 = 2;

        // Re-create the characteristic with the received value.
        BluetoothGattCharacteristic characteristic =
                new BluetoothGattCharacteristic(BtSmartUuid.HEART_RATE_MEASUREMENT.getUuid(), 0, 0);
        characteristic.setValue(value);

        byte flags = characteristic.getValue()[INDEX_FLAGS];

        // Check the flags of the characteristic to find if the heart rate number format is UINT16 or UINT8.
        int hrm = 0;
        if ((flags & FLAG_HRM_FORMAT) != 0) {
            hrm = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, INDEX_HRM_VALUE);
            // Need to offset all the energy value index by 1 as the heart rate value is taking up two bytes.
            energyIndexOffset++;
        }
        else {
            hrm = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, INDEX_HRM_VALUE);
        }
        /*heartRateData.setValueText(String.valueOf(hrm));*/
        heartRateData.setValueText(String.valueOf(0));

        // Get the expended energy if present.
        int energyExpended = 0;
        if ((flags & FLAG_ENERGY_PRESENT) != 0) {
            energyExpended =
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, INDEX_ENERGY_VALUE
                            + energyIndexOffset);
            /*energyData.setValueText(String.valueOf(energyExpended));*/
            energyData.setValueText(String.valueOf(0));
        }

        // Get RR interval values if present.
        if ((flags & FLAG_RR_PRESENT) != 0) {
            int lastRR = 0;
            // There can be multiple RR values - just get the most recent which will be the last uint16 in the array.
            lastRR =
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, value.length - SIZEOF_UINT16);
            /*rrData.setValueText(String.valueOf(lastRR));*/
            rrData.setValueText(String.valueOf(0));
        }
    }

}
