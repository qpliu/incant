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

class WindowTextBuffer extends Window {
    private static final long serialVersionUID = 0L;

    WindowTextBuffer(int rock) {
        super(rock);
    }

    @Override
    View createView() {
        throw new RuntimeException("unimplemented");
    }

    @Override
    boolean hasPendingEvent() {
        throw new RuntimeException("unimplemented");
    }

    @Override
    GlkEvent getEvent(long timeout, boolean polling) throws InterruptedException {
        throw new RuntimeException("unimplemented");
    }

    // Run in IO thread.  Recursively descend window tree.
    // Return whether there is pending asynchronous output.
    // If there is asynchronous output pending, post continueOutput
    // to the IO thread once the asynchronous output has completed.
    // Examples of asynchronous output:
    // - text to speech
    // - waiting for the user to hit the next button (when output
    //   will scroll out of view (if text to speech is being skipped))
    @Override
    boolean updatePendingOutput(Runnable continueOutput, boolean doSpeech) {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public GlkWindowStream getStream() {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public GlkStreamResult close() throws IOException {
        throw new RuntimeException("unimplemented");
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
        return GlkWindow.TypeTextBuffer;
    }

    @Override
    public void clear() throws IOException {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public void moveCursor(int x, int y) throws IOException {
        throw new RuntimeException("unimplemented");
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
        throw new RuntimeException("unimplemented");
    }

    @Override
    public void requestCharEvent() {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public void requestMouseEvent() {
    }

    @Override
    public GlkEvent cancelLineEvent() {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public void cancelCharEvent() {
        throw new RuntimeException("unimplemented");
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
        throw new RuntimeException("unimplemented");
    }

    @Override
    public void eraseRect(int left, int top, int width, int height) {
    }

    @Override
    public void fillRect(int color, int left, int top, int width, int height) {
    }

    @Override
    public void setBackgroundColor(int color) {
        throw new RuntimeException("unimplemented");
    }
}
