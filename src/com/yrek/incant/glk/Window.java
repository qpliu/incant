package com.yrek.incant.glk;

import android.content.Context;
import android.view.View;

import java.io.IOException;
import java.io.Serializable;

import com.yrek.ifstd.glk.GlkByteArray;
import com.yrek.ifstd.glk.GlkEvent;
import com.yrek.ifstd.glk.GlkStreamResult;
import com.yrek.ifstd.glk.GlkWindow;
import com.yrek.ifstd.glk.GlkWindowArrangement;
import com.yrek.ifstd.glk.GlkWindowSize;
import com.yrek.ifstd.glk.GlkWindowStream;

abstract class Window extends GlkWindow implements Serializable {
    transient GlkActivity activity;
    transient View view = null;
    WindowPair parent;

    Window(int rock) {
        super(rock);
    }

    abstract View createView(Context context);
    abstract boolean hasPendingEvent();
    abstract GlkEvent getEvent(long timeout, boolean polling) throws InterruptedException;

    // Run in IO thread.  Recursively descend window tree.
    // Return whether there is pending asynchronous output.
    // If there is asynchronous output pending, post continueOutput
    // to the IO thread once the asynchronous output has completed.
    // Examples of asynchronous output:
    // - text to speech
    // - waiting for the user to hit the next button (when output
    //   will scroll out of view (if text to speech is being skipped))
    abstract boolean updatePendingOutput(Runnable continueOutput, boolean doSpeech);

    View getView() {
        if (view == null) {
            view = createView(activity);
        }
        return view;
    }

    void initActivity(GlkActivity activity) {
        this.activity = activity;
    }

    void post(Runnable runnable) {
        activity.post(runnable);
    }

    static Window open(GlkActivity activity, Window split, int method, int size, int winType, int rock) {
        Window newWindow = null;
        switch (winType) {
        case GlkWindow.TypeBlank:
            newWindow = new WindowBlank(rock);
        case GlkWindow.TypeTextBuffer:
            newWindow = new WindowTextBuffer(rock);
            break;
        case GlkWindow.TypeTextGrid:
            throw new RuntimeException("unimplemented");
        case GlkWindow.TypeGraphics:
            throw new RuntimeException("unimplemented");
        default:
            return null;
        }
        newWindow.activity = activity;
        if (split != null) {
            WindowPair oldParent = split.parent;
            WindowPair newParent = new WindowPair(method, size, split, newWindow);
            newParent.activity = activity;
            if (oldParent != null) {
                oldParent.replaceChild(split, newParent);
            }
        }
        return newWindow;
    }

    @Override
    public GlkWindowStream getStream() {
        return null;
    }

    @Override
    public GlkStreamResult close() throws IOException {
        destroy();
        if (parent != null) {
            //... replace parent with sibling
        }
        return new GlkStreamResult(0, 0);
    }

    @Override
    public GlkWindowSize getSize() {
        return new GlkWindowSize(0, 0);
    }

    @Override
    public void setArrangement(int method, int size, GlkWindow key) {
    }

    @Override
    public GlkWindowArrangement getArrangement() {
        throw new IllegalArgumentException();
    }

    @Override
    public GlkWindow getParent() {
        return parent;
    }

    @Override
    public GlkWindow getSibling() {
        if (parent == null) {
            return null;
        }
        if (parent.child1 == this) {
            return parent.child2;
        } else {
            assert parent.child2 == this;
            return parent.child1;
        }
    }

    @Override
    public void clear() throws IOException {
    }

    @Override
    public void moveCursor(int x, int y) throws IOException {
    }

    @Override
    public boolean styleDistinguish(int style1, int style2) {
        return false;
    }

    @Override
    public Integer styleMeasure(int style, int hint) {
        return null;
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
        return new GlkEvent(GlkEvent.TypeNone, this, 0, 0);
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


    @Override
    public void setEchoLineEvent(boolean echoLineEvent) {
    }
}
