package com.example.android.bluetoothchat;

import android.app.Notification;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class NotificationListener extends NotificationListenerService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "NotificationListener";
    private BluetoothChatService mService;
    private SimpleDateFormat mDateFormat;
    private SharedPreferences mPrefs;
    private BluetoothDevice mDevice = null;

    @Override
    public void onCreate() {
        super.onCreate();
        mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            mService = new BluetoothChatService(this);
            mPrefs = getSharedPreferences("BTPREFS", Context.MODE_PRIVATE);
            mPrefs.registerOnSharedPreferenceChangeListener(this);
            String address = mPrefs.getString("ConnectedDevice", "");
            if (!"".equals(address)) {
                // Get the BluetoothDevice object
                mDevice = adapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mService.connect(mDevice);
            }
        }
    }

    @Override
    public void onDestroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        mService.stop();
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged: " + key);
        switch (key) {
            case "ConnectedDevice":
                String address = mPrefs.getString("ConnectedDevice", "");
                if (!"".equals(address)) {
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    // Get the BluetoothDevice object
                    mDevice = adapter.getRemoteDevice(address);
                    // Attempt to connect to the device
                    mService.connect(mDevice);
                }
                break;
            case "FindWatch":
                ensureConnected();
                mService.write("ATLUWH\n".getBytes());
        }

    }


    @Override
    public void onListenerConnected() {
        Log.d(TAG, "onListenerConnected");
        super.onListenerConnected();
    }

    @Override
    public void onListenerDisconnected() {
        Log.d(TAG, "onListenerDisconnected");
        super.onListenerDisconnected();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: " + intent.toString());
        return super.onBind(intent);
    }

    private static final class WakeLockWrapper implements AutoCloseable
    {
        private PowerManager.WakeLock mWakeLock;
        public WakeLockWrapper(PowerManager.WakeLock wakeLock)
        {
            mWakeLock = wakeLock;
            mWakeLock.acquire();
        }

        @Override
        public void close() {
            mWakeLock.release();
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try (WakeLockWrapper wakeLock = new WakeLockWrapper(
                ((PowerManager)getSystemService(Context.POWER_SERVICE))
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BluetoothChat:"+TAG))) {
            Log.d(TAG, "onNotificationPosted: flags=" + sbn.getNotification().flags);
            Notification not = sbn.getNotification();
            if ((not.flags & Notification.FLAG_ONGOING_EVENT) != 0)
                return;
            ensureConnected();

            String text;
            if (not.tickerText != null)
                text = not.tickerText.toString();
            else
                text = not.extras.getString(NotificationCompat.EXTRA_TEXT);

            if (text == null)
                return;

            byte textbytes[] = text.getBytes(StandardCharsets.UTF_8);
            if (textbytes.length > 150) {
                byte tmp[] = new byte[150];
                System.arraycopy(textbytes, 0, tmp, 0, 150);
                textbytes = tmp;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("ATMNMI,");
            PackageManager pm = getPackageManager();
            Log.d(TAG, sbn.getPackageName());
            try {
                sb.append(pm.getPackageInfo(sbn.getPackageName(), 0).applicationInfo.loadLabel(pm));
            } catch (PackageManager.NameNotFoundException e) {
            }
            sb.append(",");
            long when = not.when;
            if (when == 0)
                when = new Date().getTime();
            sb.append(mDateFormat.format(new Date(when)));
            sb.append(",");
            sb.append(textbytes.length);
            sb.append(",");
            sb.append(new String(textbytes, StandardCharsets.UTF_8));
            mService.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        //super.onNotificationPosted(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "onNotificationRemoved");
        //super.onNotificationRemoved(sbn);
    }

    private void ensureConnected() {
        if (mService.getState() == BluetoothChatService.STATE_NONE) {
            mService.connect(mDevice);
            for (int i = 0; i < 10 && mService.getState() != BluetoothChatService.STATE_CONNECTED; ++i)
            {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e)
                {
                    Log.e(TAG, "Interrupted", e);
                }
            }
        }
    }
}
