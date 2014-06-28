package com.yrek.incant;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import com.yrek.ifstd.blorb.Blorb;
import com.yrek.ifstd.glk.GlkDispatch;
import com.yrek.ifstd.glk.GlkStream;
import com.yrek.ifstd.glulx.Glulx;
import com.yrek.incant.glk.GlkMain;

class GlulxStory implements GlkMain {
    private static final long serialVersionUID = 0L;
    private static final String TAG = GlulxStory.class.getSimpleName();
    final Story story;
    final String name;
    transient Thread thread = null;
    transient Glulx glulx = null;
    transient GlkDispatch glk = null;

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
        this.glk = glk;
        suspendState = null; //... tmp
        if (suspendState == null) {
            try {
                glulx = new Glulx(story.getGlulxFile(context), glk);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        throw new RuntimeException("unimplemented");
    }

    @Override
    public void start(final Runnable waitForInit) {
        thread = new Thread("glk") {
            @Override public void run() {
                waitForInit.run();
                try {
                    glk.glk.main(glulx);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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


    @Override
    public int getGlkLayout() {
        return R.layout.glk;
    }

    @Override
    public int getFrameLayout() {
        return R.id.frame;
    }

    @Override
    public int getNextButton() {
        return R.id.next;
    }

    @Override
    public int getKeyboardButton() {
        return R.id.keyboard;
    }

    @Override
    public int getEditText() {
        return R.id.edit;
    }

    @Override
    public int getSkipButton() {
        return R.id.skip;
    }

    @Override
    public int getOneByOneMeasurer() {
        return R.id.onexone;
    }

    @Override
    public int getTwoByTwoMeasurer() {
        return R.id.twoxtwo;
    }


    @Override
    public int getTextBufferStyle(int style) {
        switch (style) {
        case GlkStream.StyleEmphasized:
            return R.style.glk_emphasized;
        case GlkStream.StylePreformatted:
            return R.style.glk_preformatted;
        case GlkStream.StyleHeader:
            return R.style.glk_header;
        case GlkStream.StyleSubheader:
            return R.style.glk_subheader;
        case GlkStream.StyleAlert:
            return R.style.glk_alert;
        case GlkStream.StyleNote:
            return R.style.glk_note;
        case GlkStream.StyleBlockQuote:
            return R.style.glk_blockquote;
        case GlkStream.StyleInput:
            return R.style.glk_input;
        default:
            return R.style.glk_normal;
        }
    }

    @Override
    public int getTextGridStyle(int style) {
        switch (style) {
        case GlkStream.StyleEmphasized:
            return R.style.glk_grid_emphasized;
        case GlkStream.StylePreformatted:
            return R.style.glk_grid_preformatted;
        case GlkStream.StyleHeader:
            return R.style.glk_grid_header;
        case GlkStream.StyleSubheader:
            return R.style.glk_grid_subheader;
        case GlkStream.StyleAlert:
            return R.style.glk_grid_alert;
        case GlkStream.StyleNote:
            return R.style.glk_grid_note;
        case GlkStream.StyleBlockQuote:
            return R.style.glk_grid_blockquote;
        case GlkStream.StyleInput:
            return R.style.glk_grid_input;
        default:
            return R.style.glk_grid_normal;
        }
    }

    @Override
    public Integer getStyleForegroundColor(int style) {
        return null;
    }

    @Override
    public Integer getStyleBackgroundColor(int style) {
        return null;
    }
}
