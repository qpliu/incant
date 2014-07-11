package com.yrek.incant.glk;

import java.io.IOException;
import java.io.Serializable;

import com.yrek.ifstd.glk.GlkSChannel;

class SChannel extends GlkSChannel implements Serializable {
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
        return false;
    }

    @Override
    public boolean playExt(int resourceId, int repeats, boolean notify) throws IOException {
        return false;
    }

    @Override
    public void stop() throws IOException {
    }

    @Override
    public void pause() throws IOException {
    }

    @Override
    public void unpause() throws IOException {
    }

    @Override
    public void setVolume(int volume) throws IOException {
    }

    @Override
    public void setVolumeExt(int volume, int duration, boolean notify) throws IOException {
    }
}
