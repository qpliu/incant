package com.yrek.incant.glk;

import android.content.Context;

import java.io.File;
import java.io.Serializable;

import com.yrek.ifstd.blorb.Blorb;
import com.yrek.ifstd.glk.GlkDispatch;

public interface GlkMain extends Serializable {
    public String name();
    public void init(Context context, GlkDispatch glk, Serializable suspendState);
    public void start();
    public void requestSuspend();
    public Serializable suspend();
    public boolean finished();
    public Blorb getBlorb(Context context);
    public File getSaveFile(Context context);
    public File getDir(Context context);
}
