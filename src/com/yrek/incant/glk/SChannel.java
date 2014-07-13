package com.yrek.incant.glk;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import com.yrek.ifstd.glk.GlkSChannel;

class SChannel extends GlkSChannel implements Serializable {
    private static final String TAG = SChannel.class.getSimpleName();

    private static final long serialVersionUID = 0L;
    transient GlkActivity activity;

    SChannel(int rock, GlkActivity activity) {
        super(rock);
        this.activity = activity;
    }

    void restoreActivity(GlkActivity activity) {
        this.activity = activity;
    }

    void onPause() {
    }

    void onResume() {
    }

    @Override
    public void destroyChannel() throws IOException {
    }

    @Override
    public boolean play(int resourceId) throws IOException {
        Log.d(TAG,"play:ch="+this+",resourceId="+resourceId);
        File file = activity.getSoundResourceFile(resourceId);
        if (file == null) {
            return false;
        }
        MediaPlayer player = new MediaPlayer();
        player.setDataSource(file.getPath());
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.prepare();
        player.start();
        return true;
    }

    @Override
    public boolean playExt(int resourceId, int repeats, boolean notify) throws IOException {
        Log.d(TAG,"playExt:ch="+this+",resourceId="+resourceId+",repeats="+repeats+",notify="+notify);
        File file = activity.getSoundResourceFile(resourceId);
        if (file == null) {
            return false;
        }
        MediaPlayer player = new MediaPlayer();
        player.setDataSource(file.getPath());
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.prepare();
        player.start();
        return true;
    }

    @Override
    public void stop() throws IOException {
        Log.d(TAG,"stop:ch="+this);
    }

    @Override
    public void pause() throws IOException {
        Log.d(TAG,"pause:ch="+this);
    }

    @Override
    public void unpause() throws IOException {
        Log.d(TAG,"unpause:ch="+this);
    }

    @Override
    public void setVolume(int volume) throws IOException {
        Log.d(TAG,"setVolume:ch="+this+",volume="+volume);
    }

    @Override
    public void setVolumeExt(int volume, int duration, boolean notify) throws IOException {
        Log.d(TAG,"setVolumeExt:ch="+this+",volume="+volume+",duration="+duration+",notify="+notify);
    }
}
