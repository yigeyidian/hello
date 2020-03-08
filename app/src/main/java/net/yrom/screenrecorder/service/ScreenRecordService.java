package net.yrom.screenrecorder.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import net.yrom.screenrecorder.R;
import net.yrom.screenrecorder.core.RESAudioClient;
import net.yrom.screenrecorder.core.RESCoreParameters;
import net.yrom.screenrecorder.rtmp.RESFlvData;
import net.yrom.screenrecorder.rtmp.RESFlvDataCollecter;
import net.yrom.screenrecorder.task.RtmpStreamingSender;
import net.yrom.screenrecorder.task.ScreenRecorder;
import net.yrom.screenrecorder.tools.LogTools;
import net.yrom.screenrecorder.ui.activity.MainActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * author : raomengyang on 2016/12/29.
 */

public class ScreenRecordService extends Service {

    private static final String TAG = ScreenRecordService.class.getSimpleName();

    private NotificationCompat.Builder builder;
    private Handler handler = new Handler();
    private RtmpStreamingSender streamingSender;
    private MediaProjectionManager mMediaProjectionManager;
    private ScreenRecorder mVideoRecorder;
    private ExecutorService executorService;

    @Override
    public void onCreate() {
        super.onCreate();
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initNotification();
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            initRecoder(intent);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void initRecoder(Intent intent) {
        String url = intent.getStringExtra("url");
        if (TextUtils.isEmpty(url)) {
            return;
        }
        int resultCode = intent.getIntExtra("resultCode", -1);
        MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, intent);
        streamingSender = new RtmpStreamingSender();
        streamingSender.sendStart(url);
        RESFlvDataCollecter collecter = new RESFlvDataCollecter() {
            @Override
            public void collect(RESFlvData flvData, int type) {
                if (streamingSender != null) {
                    streamingSender.sendFood(flvData, type);
                }

            }
        };
        RESCoreParameters coreParameters = new RESCoreParameters();

        RESAudioClient audioClient = new RESAudioClient(coreParameters);

        if (!audioClient.prepare()) {
            LogTools.d("!!!!!audioClient.prepare()failed");
            return;
        }

        mVideoRecorder = new ScreenRecorder(collecter, RESFlvData.VIDEO_WIDTH, RESFlvData.VIDEO_HEIGHT, RESFlvData.VIDEO_BITRATE, 1, mediaProjection);
        mVideoRecorder.start();
        audioClient.start(collecter);

        executorService = Executors.newCachedThreadPool();
        executorService.execute(streamingSender);
    }

    private void initNotification() {

        String CHANNEL_ID = "hello";
        String CHANNEL_NAME = "TEST";
        NotificationChannel notificationChannel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("state", "1");
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID).
                setContentTitle(getResources().getString(R.string.app_name)).
                setContentText("正在录屏").
                setWhen(System.currentTimeMillis()).
                setSmallIcon(R.drawable.ic_launcher).
                setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher)).
                setContentIntent(pendingIntent).
                build();
        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScreenRecord();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }

    }

    private void stopScreenRecord() {
        mVideoRecorder.quit();
        mVideoRecorder = null;
        if (streamingSender != null) {
            streamingSender.sendStop();
            streamingSender.quit();
            streamingSender = null;
        }
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


}
