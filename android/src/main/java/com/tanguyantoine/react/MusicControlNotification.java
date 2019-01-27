package com.tanguyantoine.react;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.app.NotificationCompat;
import android.view.KeyEvent;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;

import java.util.Map;

public class MusicControlNotification {

    protected static final String REMOVE_NOTIFICATION = "music_control_remove_notification";
    protected static final String MEDIA_BUTTON = "music_control_media_button";
    protected static final String PACKAGE_NAME = "music_control_package_name";

    private final ReactApplicationContext context;
    private final MusicControlModule module;

    private int smallIcon;
    private int customIcon;
    private NotificationCompat.Action play, pause, stop, next, previous, skipForward, skipBackward;

    public MusicControlNotification(MusicControlModule module, ReactApplicationContext context) {
        this.context = context;
        this.module = module;

        Resources r = context.getResources();
        String packageName = context.getPackageName();

        // Optional custom icon with fallback to the play icon
        smallIcon = r.getIdentifier("music_control_icon", "drawable", packageName);
        if(smallIcon == 0) smallIcon = r.getIdentifier("play", "drawable", packageName);
    }

    public synchronized void setCustomNotificationIcon(String resourceName) {
        if(resourceName == null) {
            customIcon = 0;
            return;
        }

        Resources r = context.getResources();
        String packageName = context.getPackageName();

        customIcon = r.getIdentifier(resourceName, "drawable", packageName);
    }

    public synchronized void updateActions(long mask, Map<String, Integer> options) {
        play = createAction("play", "Play", mask, PlaybackStateCompat.ACTION_PLAY, play);
        pause = createAction("pause", "Pause", mask, PlaybackStateCompat.ACTION_PAUSE, pause);
        stop = createAction("stop", "Stop", mask, PlaybackStateCompat.ACTION_STOP, stop);
        next = createAction("next", "Next", mask, PlaybackStateCompat.ACTION_SKIP_TO_NEXT, next);
        previous = createAction("previous", "Previous", mask, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS, previous);

        if (options != null && options.containsKey("skipForward") && (options.get("skipForward") == 10 || options.get("skipForward") == 5 || options.get("skipForward") == 30)) {
            skipForward = createAction("skip_forward_" + options.get("skipForward").toString(), "Skip Forward", mask, PlaybackStateCompat.ACTION_FAST_FORWARD, skipForward);
        } else {
            skipForward = createAction("skip_forward_10", "Skip Forward", mask, PlaybackStateCompat.ACTION_FAST_FORWARD, skipForward);
        }

        if (options != null && options.containsKey("skipBackward") && (options.get("skipBackward") == 10 || options.get("skipBackward") == 5 || options.get("skipBackward") == 30)) {
            skipBackward = createAction("skip_backward_" + options.get("skipBackward").toString(), "Skip Backward", mask, PlaybackStateCompat.ACTION_REWIND, skipBackward);
        } else {
            skipBackward = createAction("skip_backward_10", "Skip Backward", mask, PlaybackStateCompat.ACTION_REWIND, skipBackward);
        }
    }

    public synchronized void show(NotificationCompat.Builder builder, MediaSessionCompat session, boolean isPlaying) {
        // Add the buttons
        try {
            builder.mActions.clear();
        }
        catch (Exception e) {
            //
        }
        int actionCounter = 0;
        int actionPlayPauseIndex = -1;
        int actionNextIndex = -1;
        if(previous != null) {
            builder.addAction(previous);
            actionCounter++;
        }
        if(skipBackward != null) {
            builder.addAction(skipBackward);
            actionCounter++;
        }
        if(play != null && !isPlaying) {
            actionPlayPauseIndex = actionCounter;
            builder.addAction(play);
            actionCounter++;
        }
        if(pause != null && isPlaying) {
            actionPlayPauseIndex = actionCounter;
            builder.addAction(pause);
            actionCounter++;
        }
        if(stop != null) {
            builder.addAction(stop);
            actionCounter++;
        }
        if(next != null) {
            actionNextIndex = actionCounter;
            builder.addAction(next);
            actionCounter++;
        }
        if(skipForward != null) {
            builder.addAction(skipForward);
            actionCounter++;
        }

        // Set whether notification can be closed based on closeNotification control (default PAUSED)
        if(module.notificationClose == MusicControlModule.NotificationClose.ALWAYS) {
            builder.setOngoing(false);
        } else if(module.notificationClose == MusicControlModule.NotificationClose.PAUSED) {
            builder.setOngoing(isPlaying);
        } else { // NotificationClose.NEVER
            builder.setOngoing(true);
        }

        builder.setSmallIcon(customIcon != 0 ? customIcon : smallIcon);

        // Open the app when the notification is clicked
        String packageName = context.getPackageName();
        Intent openApp = context.getPackageManager().getLaunchIntentForPackage(packageName);
        builder.setContentIntent(PendingIntent.getActivity(context, 0, openApp, 0));

        // Remove notification
        Intent remove = new Intent(REMOVE_NOTIFICATION);
        remove.putExtra(PACKAGE_NAME, context.getApplicationInfo().packageName);
        builder.setDeleteIntent(PendingIntent.getBroadcast(context, 0, remove, PendingIntent.FLAG_UPDATE_CURRENT));
        try {
            //set media style
            android.support.v4.media.app.NotificationCompat.MediaStyle mediaStyle = new android.support.v4.media.app.NotificationCompat.MediaStyle();
            if(actionPlayPauseIndex>=0 && actionNextIndex>=0) {
                mediaStyle.setShowActionsInCompactView(actionPlayPauseIndex, actionNextIndex);
            }
            else if(actionPlayPauseIndex>=0) {
                mediaStyle.setShowActionsInCompactView(actionPlayPauseIndex);
            }
            if(session!=null) {
                builder.setStyle(mediaStyle.setMediaSession(session.getSessionToken()));
            }
        }
        catch (Exception e) {
            //
        }

        // Finally show/update the notification
        try {
            module.mServiceConnection.getService().startForeground(100, builder.build());
        }
        catch (Exception e) {
            //
        }
        //NotificationManagerCompat.from(context).notify("MusicControl", 0, builder.build());
    }

    public void hide() {
        try {
            module.mServiceConnection.getService().stopForeground(true);
        }
        catch (Exception e) {
            //
        }
        //NotificationManagerCompat.from(context).cancel("MusicControl", 0);
    }

    /**
     * Code taken from newer version of the support library located in PlaybackStateCompat.toKeyCode
     * Replace this to PlaybackStateCompat.toKeyCode when React Native updates the support library
     */
    private int toKeyCode(long action) {
        if(action == PlaybackStateCompat.ACTION_PLAY) {
            return KeyEvent.KEYCODE_MEDIA_PLAY;
        } else if(action == PlaybackStateCompat.ACTION_PAUSE) {
            return KeyEvent.KEYCODE_MEDIA_PAUSE;
        } else if(action == PlaybackStateCompat.ACTION_SKIP_TO_NEXT) {
            return KeyEvent.KEYCODE_MEDIA_NEXT;
        } else if(action == PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) {
            return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        } else if(action == PlaybackStateCompat.ACTION_STOP) {
            return KeyEvent.KEYCODE_MEDIA_STOP;
        } else if(action == PlaybackStateCompat.ACTION_FAST_FORWARD) {
            return KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        } else if(action == PlaybackStateCompat.ACTION_REWIND) {
            return KeyEvent.KEYCODE_MEDIA_REWIND;
        } else if(action == PlaybackStateCompat.ACTION_PLAY_PAUSE) {
            return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        }
        return KeyEvent.KEYCODE_UNKNOWN;
    }

    private NotificationCompat.Action createAction(String iconName, String title, long mask, long action, NotificationCompat.Action oldAction) {
        if((mask & action) == 0) return null; // When this action is not enabled, return null
        if(oldAction != null) return oldAction; // If this action was already created, we won't create another instance

        // Finds the icon with the given name
        Resources r = context.getResources();
        String packageName = context.getPackageName();
        int icon = r.getIdentifier(iconName, "drawable", packageName);

        // Creates the intent based on the action
        int keyCode = toKeyCode(action);
        Intent intent = new Intent(MEDIA_BUTTON);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        intent.putExtra(PACKAGE_NAME, packageName);
        PendingIntent i = PendingIntent.getBroadcast(context, keyCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action(icon, title, i);
    }

    public static class NotificationService extends Service {
        public class Binder extends android.os.Binder {
            public NotificationService getService() {
                return NotificationService.this;
            }
        }

        private Binder mBinder;

        @Override
        public IBinder onBind(Intent intent) {
            if(mBinder==null) {
                mBinder = new Binder();
            }
            return mBinder;
        }

        @Override
        public boolean onUnbind(Intent intent) {
            mBinder = null;
            stopForeground(true);
            return false;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            return START_NOT_STICKY;
        }

        @Override
        public void onTaskRemoved(Intent rootIntent) {
            // Destroy the notification and sessions when the task is removed (closed, killed, etc)
            if(MusicControlModule.INSTANCE != null) {
                MusicControlModule.INSTANCE.destroy();
            }
            mBinder = null;
            stopForeground(true);
            stopSelf(); // Stop the service as we won't need it anymore
        }

    }

    public static class NotificationServiceConnection implements ServiceConnection {
        private MusicControlNotification.NotificationService mService;

        public NotificationService getService() {
            return mService;
        }

        public boolean isConnected() {
            return mService!=null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if(service instanceof MusicControlNotification.NotificationService.Binder) {
                mService = ((MusicControlNotification.NotificationService.Binder) service).getService();
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
