package com.yrek.incant.glk;

import android.view.View;

import java.io.IOException;

import com.yrek.ifstd.glk.GlkEvent;
import com.yrek.ifstd.glk.GlkStreamResult;
import com.yrek.ifstd.glk.GlkWindow;
import com.yrek.ifstd.glk.GlkWindowArrangement;
import com.yrek.ifstd.glk.GlkWindowSize;

class WindowPair extends Window {
    private static final long serialVersionUID = 0L;
    int method;
    int size;
    Window child1;
    Window child2;
    Window key;
    private boolean updateView;

    WindowPair(int method, int size, Window child1, Window child2) {
        super(0);
        this.method = method;
        this.size = size;
        this.child1 = child1;
        this.child2 = child2;
        this.key = child2;
    }

    @Override
    View createView() {
        // LinearLayout
        throw new RuntimeException("unimplemented");
    }

    @Override
    void initActivity(GlkActivity activity) {
        super.initActivity(activity);
        child1.initActivity(activity);
        child2.initActivity(activity);
    }

    @Override
    boolean hasPendingEvent() {
        return child1.hasPendingEvent() || child2.hasPendingEvent();
    }

    @Override
    synchronized GlkEvent getEvent(long timeout, boolean polling) throws InterruptedException {
        // ... window arrangement events
        if (child1.hasPendingEvent()) {
            return child1.getEvent(timeout, polling);
        } else if (child2.hasPendingEvent()) {
            return child2.getEvent(timeout, polling);
        } else {
            assert polling;
            return null;
        }
    }

    @Override
    synchronized boolean updatePendingOutput(Runnable continueOutput, boolean doSpeech) {
        if (updateView) {
            // ...
            updateView = false;
        }
        return child1.updatePendingOutput(continueOutput, doSpeech) || child2.updatePendingOutput(continueOutput, doSpeech);
    }

    synchronized void replaceChild(Window oldChild, Window newChild) {
        if (oldChild == child1) {
            child1 = newChild;
        } else if (oldChild == child2) {
            child2 = newChild;
        } else {
            throw new IllegalArgumentException();
        }
        updateView = true;
    }

    @Override
    public GlkStreamResult close() throws IOException {
        super.close();
        //... close and destroy children
        destroy();
        return new GlkStreamResult(0, 0);
    }

    @Override
    public GlkWindowSize getSize() {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public void setArrangement(int method, int size, GlkWindow key) {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public GlkWindowArrangement getArrangement() {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public int getType() {
        return GlkWindow.TypePair;
    }
}
