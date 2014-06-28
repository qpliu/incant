package com.yrek.incant.glk;

import android.content.Context;

import java.io.File;
import java.io.Serializable;

import com.yrek.ifstd.blorb.Blorb;
import com.yrek.ifstd.glk.GlkDispatch;

public interface GlkMain extends Serializable {
    public String name();
    public void init(Context context, GlkDispatch glk, Serializable suspendState);
    public void start(Runnable waitForInit);
    public void requestSuspend();
    public Serializable suspend();
    public boolean finished();
    public Blorb getBlorb(Context context);
    public File getSaveFile(Context context);
    public File getDir(Context context);

    public int getGlkLayout();
    public int getFrameLayout();
    public int getNextButton();
    public int getKeyboardButton();
    public int getEditText();
    public int getSkipButton();
    public int getOneByOneMeasurer();
    public int getTwoByTwoMeasurer();

    public int getTextBufferStyle(int style);
    public int getTextGridStyle(int style);
    public Integer getStyleForegroundColor(int style);
    public Integer getStyleBackgroundColor(int style);
}