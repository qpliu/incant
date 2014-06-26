package com.yrek.incant;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import com.yrek.ifstd.blorb.Blorb;
import com.yrek.ifstd.glk.GlkDispatch;
import com.yrek.ifstd.glulx.Glulx;
import com.yrek.incant.glk.GlkMain;

class GlulxStory implements GlkMain {
    private static final long serialVersionUID = 0L;
    private static final String TAG = GlulxStory.class.getSimpleName();
    final Story story;
    final String name;
    transient Thread thread = null;
    transient Glulx glulx = null;

    GlulxStory(Story story, String name) {
        this.story = story;
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void init(Context context, GlkDispatch glk, Serializable suspendState) {
        if (true) { //... tmp
            try {
                glulx = new Glulx(story.getGlulxFile(context), glk);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        } //... tmp
        throw new RuntimeException("unimplemented");
    }

    @Override
    public void start() {
        thread = new Thread("glk") {
            @Override public void run() {
                glulx.run();
            }
        };
        thread.start();
    }

    @Override
    public void requestSuspend() {
        try {
            glulx.suspend(false);
        } catch (InterruptedException e) {
            Log.wtf(TAG,e);
        }
    }

    @Override
    public Serializable suspend() {
        try {
            glulx.suspend(false);
            thread.interrupt();
            glulx.suspend(true);
        } catch (InterruptedException e) {
            Log.wtf(TAG,e);
        }
        return glulx;
    }

    @Override
    public boolean finished() {
        return !glulx.suspended();
    }

    @Override
    public Blorb getBlorb(Context context) {
        File blorbFile = story.getBlorbFile(context);
        if (blorbFile.exists()) {
            try {
                return Blorb.from(blorbFile);
            } catch (IOException e) {
                Log.wtf(TAG,e);
            }
        }
        return null;
    }

    @Override
    public File getSaveFile(Context context) {
        return story.getSaveFile(context);
    }

    @Override
    public File getDir(Context context) {
        return story.getDir(context);
    }
}
