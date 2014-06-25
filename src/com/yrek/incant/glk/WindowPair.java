package com.yrek.incant.glk;

import android.view.View;

import java.io.IOException;

import com.yrek.ifstd.glk.GlkByteArray;
import com.yrek.ifstd.glk.GlkEvent;
import com.yrek.ifstd.glk.GlkStreamResult;
import com.yrek.ifstd.glk.GlkWindow;
import com.yrek.ifstd.glk.GlkWindowArrangement;
import com.yrek.ifstd.glk.GlkWindowSize;
import com.yrek.ifstd.glk.GlkWindowStream;

class WindowPair extends Window {
    private static final long serialVersionUID = 0L;
    int method;
    int size;
    Window child1;
    Window child2;
    private boolean updateView;

    WindowPair(int method, int size, Window child1, Window child2) {
        super(0);
        this.method = method;
        this.size = size;
        this.child1 = child1;
        this.child2 = child2;
    }

    @Override
    View createView() {
        // LinearLayout
        throw new RuntimeException("unimplemented");
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
    public GlkWindowStream getStream() {
        return null;
    }

    @Override
    public GlkStreamResult close() throws IOException {
        //... close children
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

    @Override
    public void clear() throws IOException {
    }

    @Override
    public void moveCursor(int x, int y) throws IOException {
    }

    @Override
    public boolean styleDistinguish(int style1, int style2) {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public Integer styleMeasure(int style, int hint) {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public void requestLineEvent(GlkByteArray buffer, int initLength) {
    }

    @Override
    public void requestCharEvent() {
    }

    @Override
    public void requestMouseEvent() {
    }

    @Override
    public GlkEvent cancelLineEvent() {
        return null;
    }

    @Override
    public void cancelCharEvent() {
    }

    @Override
    public void cancelMouseEvent() {
    }

    @Override
    public boolean drawImage(int resourceId, int val1, int val2) throws IOException {
        return false;
    }

    @Override
    public boolean drawScaledImage(int resourceId, int val1, int val2, int width, int height) throws IOException {
        return false;
    }

    @Override
    public void flowBreak() {
    }

    @Override
    public void eraseRect(int left, int top, int width, int height) {
    }

    @Override
    public void fillRect(int color, int left, int top, int width, int height) {
    }

    @Override
    public void setBackgroundColor(int color) {
    }
}
