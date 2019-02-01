package com.tanguyantoine.react;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

public class MusicControlService extends Service {
    public class Binder extends android.os.Binder {
        public MusicControlService getService() {
            return MusicControlService.this;
        }
    }

    private Binder mBinder;

    @Override
    public IBinder onBind(Intent intent) {
        //Log.e("TEST", "onBind");
        if(mBinder==null) {
            mBinder = new Binder();
        }
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //Log.e("TEST", "onUnbind");
        shutdown();
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.e("TEST", "onStartCommand");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        //Log.e("TEST", "onDestroy");
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        //Log.e("TEST", "onTaskRemoved");
        shutdown();
    }

    private void shutdown() {
        // Destroy the notification and sessions when the task is removed (closed, killed, etc)
        if(MusicControlModule.INSTANCE != null) {
            MusicControlModule.INSTANCE.destroy();
        }
        mBinder = null;
        stopForeground(true);
        stopSelf(); // Stop the service as we won't need it anymore
    }



    public static class Connection implements ServiceConnection {
        private MusicControlService mService;

        public MusicControlService getService() {
            return mService;
        }

        public boolean isConnected() {
            return mService!=null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if(service instanceof MusicControlService.Binder) {
                mService = ((MusicControlService.Binder) service).getService();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            mService = null;
        }
    }
}
