package com.blitz.aquarius.ui;

/**
 * Created by blitz on 11/07/17.
 */

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;

import com.blitz.aquarius.Constants;
import com.blitz.aquarius.R;
import com.blitz.aquarius.bluetooth.BleAdapterService;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;

import static java.lang.Boolean.TRUE;
import static java.lang.Boolean.valueOf;

public class PeripheralControlActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_ID = "id";

    private BleAdapterService bluetooth_le_adapter;

    private String device_name;
    private String device_address;
    private Timer mTimer;
    private boolean sound_alarm_on_disconnect = false;
    private int alert_level;
    private boolean back_requested = false;
    private boolean share_with_server = false;
    private Switch share_switch;
    private static volatile int CommListIndex = 0;
    private static volatile int LogReadingIndex = 0;

    static final int TIME_DIALOG_ID = 1111;
    private TextView timeAlarm1Hour;
    private TextView timeAlarm1Minute;
    private TextView timeAlarm2Hour;
    private TextView timeAlarm2Minute;
    private CheckBox checkBoxAlarm1;
    private CheckBox checkBoxAlarm2;

    private int hr;
    private int min;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peripheral_control);

        checkBoxAlarm1 = (CheckBox) PeripheralControlActivity.this.findViewById(R.id.alarmCheckBox1);
        checkBoxAlarm2 = (CheckBox) PeripheralControlActivity.this.findViewById(R.id.alarmCheckBox2);
        timeAlarm1Hour = (TextView) PeripheralControlActivity.this.findViewById(R.id.alarm1Hours);
        timeAlarm1Minute = (TextView) PeripheralControlActivity.this.findViewById(R.id.alarm1Minutes);
        timeAlarm2Hour = (TextView) PeripheralControlActivity.this.findViewById(R.id.alarm2Hours);
        timeAlarm2Minute = (TextView) PeripheralControlActivity.this.findViewById(R.id.alarm2Minutes);


        //read intent data
        final Intent intent = getIntent();
        device_name = intent.getStringExtra(EXTRA_NAME);
        device_address = intent.getStringExtra(EXTRA_ID);


        // connect to the Bluetooth adapter service
        Intent gattServiceIntent = new Intent(this, BleAdapterService.class);
        bindService(gattServiceIntent, service_connection, BIND_AUTO_CREATE);
        showMsg("READY");
    }

    private void showMsg(final String msg) {
        Log.d(Constants.TAG, msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.msgTextView)).setText(msg);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(service_connection);
        bluetooth_le_adapter = null;
    }

    private final ServiceConnection service_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetooth_le_adapter = ((BleAdapterService.LocalBinder) service).getService();
            bluetooth_le_adapter.setActivityHandler(message_handler);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetooth_le_adapter = null;
        }
    };

    private Handler message_handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle;
            String service_uuid = "";
            String characteristic_uuid = "";
            byte[] b = null;
            //message handling logic
            switch (msg.what) {
                case BleAdapterService.MESSAGE:
                    bundle = msg.getData();
                    String text = bundle.getString(BleAdapterService.PARCEL_TEXT);
                    showMsg(text);
                    break;

                case BleAdapterService.GATT_CONNECTED:
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.connectButton)).setEnabled(false);
                    //we're connected
                    showMsg("CONNECTED");
                    // enable the LOW/MID/HIGH alert level selection buttons
                    Log.i("GATT Server","connected");
                    bluetooth_le_adapter.discoverServices();
                    break;

                case BleAdapterService.GATT_DISCONNECT:
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.connectButton)).setEnabled(true);
                    //we're disconnected
                    showMsg("DISCONNECTED");

                    if (back_requested) {
                        PeripheralControlActivity.this.finish();
                    }
                    break;

                case BleAdapterService.GATT_SERVICES_DISCOVERED:
                    //validate services and if ok...
                    List<BluetoothGattService> slist = bluetooth_le_adapter.getSupportedGattServices();
                    boolean aquarius_service_present = false;

                    for (BluetoothGattService svc : slist) {
                        Log.d(Constants.TAG, "UUID=" + svc.getUuid().toString().toUpperCase() + "INSTANCE=" + svc.getInstanceId());
                        String serviceUuid = svc.getUuid().toString().toUpperCase();
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.AQUARIUS_SERVICE_UUID)) {
                            aquarius_service_present = true;
                            continue;
                        }
                    }

                    if (aquarius_service_present) {
                        showMsg("Device has expected services");

                        //enable buttons
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.connectButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.timeButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.timepointButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.startButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.stopButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.disconnectButton)).setEnabled(true);


                    } else {
                        showMsg("Device does not have expected GATT services");
                    }
                    break;

                case BleAdapterService.GATT_CHARACTERISTIC_READ:
                    bundle = msg.getData();
                    Log.d(Constants.TAG, "Service=" + bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString().toUpperCase() + " Characteristic=" + bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase());
                    if (bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase().equals(BleAdapterService.BATTERY_LEVEL_CHARACTERISTIC_UUID)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        if (b.length > 0) {
                            showMsg("Received " + b.toString() + "from Pebble.");
                            showMsg("Battery characteristic non-empty = " + (int) b[0]);
                        } else {
                            showMsg("Battery characteristic empty");
                        }
                    }
                    if (bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase().equals(BleAdapterService.LOG_CHARACTERISTIC_UUID)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        if (b.length > 0) {
                            long date = ((16777216 * bluetooth_le_adapter.convertByteToInt(b[0])) + (65536 * bluetooth_le_adapter.convertByteToInt(b[1])) + (256 * bluetooth_le_adapter.convertByteToInt(b[2])) + bluetooth_le_adapter.convertByteToInt(b[3]));
                            showMsg("date = " + date);
                            Date eventDate = new Date(date * 1000); //Arduino provides seconds since 1 Jan 1970. Android uses milliseconds since 1 Jan 1970. So, multiplying by 1000.

                            showMsg(eventDate.toString());
                        } else {
                            showMsg("No log data");
                        }
                    }


                    break;

                case BleAdapterService.GATT_CHARACTERISTIC_WRITTEN:
                    bundle = msg.getData();

                    if (bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString()
                            .toUpperCase().equals(BleAdapterService.NEW_WATERING_TIME_POINT_CHARACTERISTIC_UUID)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        if (b.length > 0) {
                            showMsg("Received ack");
                            CommListIndex++;
                            if (CommListIndex < 2) {
                                onSendTPDynamic(findViewById(R.id.alarmCheckBox1));
                            } else {
                                CommListIndex = 0;

                            }
                        }
                    }
                    if (bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString()
                            .toUpperCase().equals(BleAdapterService.LOG_CHARACTERISTIC_UUID)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        if (b.length > 0) {
                            showMsg("Read characteristic : " + String.valueOf(LogReadingIndex));
                            if (bluetooth_le_adapter.readCharacteristic(
                                    BleAdapterService.AQUARIUS_SERVICE_UUID,
                                    BleAdapterService.LOG_CHARACTERISTIC_UUID
                            ) == TRUE) {
                                showMsg("Log Event Read");

                            } else {
                                showMsg("Log Event Read Failed");
                            }
                        }
                    }

                    break;
            }
        }
    };


    public void onSetTime(View view) {
        Calendar calendar = Calendar.getInstance();

        //Set present time as data packet
        long systemTimeMillis = System.currentTimeMillis();
        int systemTime = (int) (systemTimeMillis / 1000);

        /*byte hours = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        byte minutes = (byte) calendar.get(Calendar.MINUTE);
        byte seconds = (byte) calendar.get(Calendar.SECOND);
        byte DATE = (byte) calendar.get(Calendar.DAY_OF_MONTH);
        byte MONTH = (byte) ((calendar.get(Calendar.MONTH)) + 1);
        int iYEARMSB = (calendar.get(Calendar.YEAR) / 256);
        int iYEARLSB = (calendar.get(Calendar.YEAR) % 256);
        byte bYEARMSB = (byte) iYEARMSB;
        byte bYEARLSB = (byte) iYEARLSB;*/

        byte currentTimeH = (byte) ((systemTime & 0xFF000000)>>24);
        byte currentTimeMH = (byte) ((systemTime & 0x00FF0000)>>16);
        byte currentTimeML = (byte) ((systemTime & 0x0000FF00)>>8);
        byte currentTimeL = (byte) (systemTime & 0x000000FF);

        //Set 1,2,3,4,5,6,7 as data packet
        /*byte hours = (byte) 1;
        byte minutes = (byte) 2;
        byte seconds = (byte) 3;
        byte DATE = (byte) 4;
        byte MONTH = (byte) 5;
        //int iYEARMSB = (calendar.get(Calendar.YEAR) / 256);
        //int iYEARLSB = (calendar.get(Calendar.YEAR) % 256);
        //byte bYEARMSB = (byte) iYEARMSB;
        //byte bYEARLSB = (byte) iYEARLSB;
        byte bYEARMSB = (byte) 6;
        byte bYEARLSB = (byte) 7;*/

        //byte[] currentTime = {hours, minutes, seconds, DATE, MONTH, bYEARMSB, bYEARLSB};
        byte[] currentTime = {currentTimeH, currentTimeMH, currentTimeML, currentTimeL};

        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.CURRENT_TIME_SERVICE_SERVICE_UUID,
                BleAdapterService.CURRENT_TIME_CHARACTERISTIC_UUID, currentTime
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.CURRENT_TIME_CHARACTERISTIC_UUID, currentTime
        );
    }

    public void onSetPots(View view) {
        byte numberOfPots = (byte) 5;
        byte[] pots = {numberOfPots};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.POTS_SERVICE_SERVICE_UUID,
                BleAdapterService.POTS_CHARACTERISTIC_UUID, pots
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.POTS_CHARACTERISTIC_UUID, pots
        );
    }

    public void onFlushOpen(View view) {
        byte[] valveCommand = {1};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onFlushClose(View view) {
        byte[] valveCommand = {5};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onStart(View view) {
        byte[] valveCommand = {2};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onStop(View view) {
        byte[] valveCommand = {3};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onDisconnect(View view) {
        byte[] valveCommand = {4};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onPause(View view) {
        byte[] valveCommand = {4};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onSendTP(View view) {

        /*byte hours = (byte) 1;
        byte minutes = (byte) 2;
        byte seconds = (byte) 3;
        byte dayOfTheWeek = (byte) 4;
        byte durationMsb = (byte) 5;
        byte durationLsb = (byte) 6;
        byte volumeMsb = (byte) 7;
        byte volumeLsb = (byte) 8;*/

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 2);

        //Set present time as data packet
        byte index = (byte) CommListIndex;
        byte dayOfTheWeek = (byte) calendar.get(Calendar.DAY_OF_WEEK);
        byte hours = (byte) calendar.get(Calendar.HOUR);
        if (calendar.get(Calendar.AM_PM) == 1) {
            hours = (byte) (calendar.get(Calendar.HOUR) + 12);
        } else {
            hours = (byte) (calendar.get(Calendar.HOUR));
        }
        byte minutes = (byte) calendar.get(Calendar.MINUTE);
        byte seconds = (byte) calendar.get(Calendar.SECOND);
        int duration = 555;
        int volume = 555;
        int iDurationMSB = (duration / 256);
        int iDurationLSB = (duration % 256);
        byte bDurationMSB = (byte) iDurationMSB;
        byte bDurationLSB = (byte) iDurationLSB;
        int iVolumeMSB = (volume / 256);
        int iVolumeLSB = (volume % 256);
        byte bVolumeMSB = (byte) iVolumeMSB;
        byte bVolumeLSB = (byte) iVolumeLSB;

        byte[] timePoint = {index, dayOfTheWeek, hours, minutes, seconds, bDurationMSB, bDurationLSB, bVolumeMSB, bVolumeLSB};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.TIME_POINT_SERVICE_SERVICE_UUID,
                BleAdapterService.NEW_WATERING_TIME_POINT_CHARACTERISTIC_UUID, timePoint
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.NEW_WATERING_TIME_POINT_CHARACTERISTIC_UUID, timePoint
        );
    }

    public void onSendTPDynamic(View view) {

        /*byte hours = (byte) 1;
        byte minutes = (byte) 2;
        byte seconds = (byte) 3;
        byte dayOfTheWeek = (byte) 4;
        byte durationMsb = (byte) 5;
        byte durationLsb = (byte) 6;
        byte volumeMsb = (byte) 7;
        byte volumeLsb = (byte) 8;*/

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 2);

        //Set present time as data packet
        byte index = (byte) CommListIndex;
        byte hours = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        byte minutes = (byte) calendar.get(Calendar.MINUTE);
        if(CommListIndex == 0) {
            index = checkBoxAlarm1.isChecked() ? (byte) 1 : (byte) 0;
            if(checkBoxAlarm1.isChecked()) {
                hours = (byte) Integer.parseInt(timeAlarm1Hour.getText().toString());
                minutes = (byte) Integer.parseInt(timeAlarm1Minute.getText().toString());
            }

        }
        if(CommListIndex == 1) {
            index = checkBoxAlarm2.isChecked() ? (byte) 1 : (byte) 0;
            if(checkBoxAlarm2.isChecked()) {
                hours = (byte) Integer.parseInt(timeAlarm2Hour.getText().toString());
                minutes = (byte) Integer.parseInt(timeAlarm2Minute.getText().toString());
            }
        }
        byte dayOfTheWeek = (byte) CommListIndex;
        byte seconds = 0;
        int duration = 0;
        int volume = 0;
        int iDurationMSB = (duration / 256);
        int iDurationLSB = (duration % 256);
        byte bDurationMSB = (byte) iDurationMSB;
        byte bDurationLSB = (byte) iDurationLSB;
        int iVolumeMSB = (volume / 256);
        int iVolumeLSB = (volume % 256);
        byte bVolumeMSB = (byte) iVolumeMSB;
        byte bVolumeLSB = (byte) iVolumeLSB;

        byte[] timePoint = {index, dayOfTheWeek, hours, minutes, seconds, bDurationMSB, bDurationLSB, bVolumeMSB, bVolumeLSB};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.TIME_POINT_SERVICE_SERVICE_UUID,
                BleAdapterService.NEW_WATERING_TIME_POINT_CHARACTERISTIC_UUID, timePoint
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.NEW_WATERING_TIME_POINT_CHARACTERISTIC_UUID, timePoint
        );
    }

    public void onBattery(View view) {
        if (bluetooth_le_adapter.readCharacteristic(
                //BleAdapterService.BATTERY_SERVICE_SERVICE_UUID,
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.BATTERY_LEVEL_CHARACTERISTIC_UUID
        ) == TRUE) {
            showMsg("Battery Level Read");

        } else {
            showMsg("Reading Battery Level failed");
        }
    }

    public void onReadLog(View view) {
        byte readingIndex0 = (byte) 2;
        byte readingIndex1 = (byte) 83;
        byte[] readingIndex = {readingIndex0, readingIndex1};
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.AQUARIUS_SERVICE_UUID,
                BleAdapterService.LOG_CHARACTERISTIC_UUID, readingIndex
        );
    }

    public void onNoise(View view) {

    }

    public void onConnect(View view) {
        showMsg("onConnect");
        if (bluetooth_le_adapter != null) {
            if (bluetooth_le_adapter.connect(device_address)) {
                ((Button) PeripheralControlActivity.this
                        .findViewById(R.id.connectButton)).setEnabled(false);
            } else {
                showMsg("onConnect: failed to connect");
            }
        } else {
            showMsg("onConnect: bluetooth_le_adapter=null");
        }
    }

    public void onBackPressed() {
        Log.d(Constants.TAG, "onBackPressed");
        back_requested = true;
        if (bluetooth_le_adapter.isConnected()) {
            try {
                bluetooth_le_adapter.disconnect();
            } catch (Exception e) {

            }
        } else {
            finish();
        }
    }

    protected Dialog createdDialog (int id) {
        switch (id) {
            case 1:
                return new TimePickerDialog(this, timePickerListener, hr, min, false);

            case 2:
                return new TimePickerDialog(this, timePickerListener, hr, min, false);

            default:
                return null;

        }
    }

    private TimePickerDialog.OnTimeSetListener timePickerListener = new TimePickerDialog.OnTimeSetListener() {
        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minutes) {
            //TODO Auto-generated method stub
            hr = hourOfDay;
            min = minutes;
            updateTime(1, hr, min);
        }
    };

    private static String utilTime(int value) {
        if (value < 10) return "0" + String.valueOf(value); else return String.valueOf(value);
    }

    private void updateTime (int alarmId, int hours, int mins) {
        String timeSet = "";
        if (hours > 12) {
            hours -= 12;
            timeSet = "PM";
        } else if (hours == 0) {
            hours += 12;
            timeSet = "AM";
        } else if (hours == 12) {
            timeSet = "PM";
        } else {
            timeSet = "AM";
        }
        String minutes = "";
        if (mins < 10)
            minutes = "0" + mins;
        else
            minutes = String.valueOf(mins);
        String aTime = new StringBuilder().append(hours).append(':').append(minutes).append(" ").append(timeSet).toString();
        if (alarmId == 1)
            timeAlarm1Hour.setText(aTime);
        else if (alarmId == 2)
            timeAlarm2Minute.setText(aTime);
    }

    public void setAlarm1 (View view) {
        if (checkBoxAlarm1.isChecked()==true) {
            Calendar mCurrentTime = Calendar.getInstance();
            int hour = mCurrentTime.get(Calendar.HOUR_OF_DAY);
            int minute = mCurrentTime.get(Calendar.MINUTE);
            TimePickerDialog mTimePicker;
            mTimePicker = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                    timeAlarm1Hour.setText(String.valueOf(selectedHour));
                    timeAlarm1Minute.setText(String.valueOf(selectedMinute));
                }
            }, hour, minute, true);
            mTimePicker.setTitle("Select Time");
            mTimePicker.show();
        } else {
            timeAlarm1Hour.setText("xx");
            timeAlarm1Minute.setText("yy");
        }
    }

    public void setAlarm2 (View view) {
        if (checkBoxAlarm2.isChecked()==true) {
            Calendar mCurrentTime = Calendar.getInstance();
            int hour = mCurrentTime.get(Calendar.HOUR_OF_DAY);
            int minute = mCurrentTime.get(Calendar.MINUTE);
            TimePickerDialog mTimePicker;
            mTimePicker = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                    timeAlarm2Hour.setText(String.valueOf(selectedHour));
                    timeAlarm2Minute.setText(String.valueOf(selectedMinute));
                }
            }, hour, minute, true);
            mTimePicker.setTitle("Select Time");
            mTimePicker.show();
        } else {
            timeAlarm2Hour.setText("xx");
            timeAlarm2Minute.setText("yy");
        }
    }

}
