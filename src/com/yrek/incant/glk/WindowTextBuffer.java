package com.yrek.incant.glk;

import android.view.View;

import java.io.IOException;
import java.util.ArrayDeque;

import com.yrek.ifstd.glk.GlkByteArray;
import com.yrek.ifstd.glk.GlkEvent;
import com.yrek.ifstd.glk.GlkIntArray;
import com.yrek.ifstd.glk.GlkStream;
import com.yrek.ifstd.glk.GlkStreamResult;
import com.yrek.ifstd.glk.GlkWindow;
import com.yrek.ifstd.glk.GlkWindowSize;
import com.yrek.ifstd.glk.GlkWindowStream;
import com.yrek.ifstd.glk.UnicodeString;

class WindowTextBuffer extends Window {
    private static final long serialVersionUID = 0L;

    ArrayDeque<Update> updates = new ArrayDeque<Update>();
    private boolean lineEventRequested = false;
    private boolean charEventRequested = false;
    private GlkByteArray lineEventBuffer = null;
    private boolean echoLineEvent = true;
    private int writeCount = 0;

    WindowTextBuffer(int rock) {
        super(rock);
    }

    @Override
    View createView() {
        throw new RuntimeException("unimplemented");
    }

    @Override
    boolean hasPendingEvent() {
        return lineEventRequested || charEventRequested;
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

    static class Update {
        boolean clear = false;
        boolean flowBreak = false;
        int style = GlkStream.StyleNormal;
        StringBuilder string = new StringBuilder();
    }

    Update updateQueueLatest(boolean newString) {
        Update latest = updates.peekLast();
        if (latest != null && (!newString || latest.string.length() == 0)) {
            return latest;
        }
        Update newLatest = new Update();
        if (latest != null) {
            newLatest.style = latest.style;
        }
        updates.addLast(newLatest);
        return newLatest;
    }

    @Override
    public GlkWindowStream getStream() {
        return stream;
    }

    @Override
    public GlkStreamResult close() throws IOException {
        super.close();
        stream.destroy();
        return new GlkStreamResult(0, writeCount);
    }

    @Override
    public GlkWindowSize getSize() {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public int getType() {
        return GlkWindow.TypeTextBuffer;
    }

    @Override
    public void clear() throws IOException {
        updateQueueLatest(false).clear = true;
    }

    @Override
    public boolean styleDistinguish(int style1, int style2) {
        switch (style1) {
        case GlkStream.StyleEmphasized:
        case GlkStream.StylePreformatted:
        case GlkStream.StyleHeader:
        case GlkStream.StyleSubheader:
        case GlkStream.StyleAlert:
        case GlkStream.StyleNote:
        case GlkStream.StyleBlockQuote:
        case GlkStream.StyleInput:
            return style1 != style2;
        default:
            break;
        }
        switch (style2) {
        case GlkStream.StyleEmphasized:
        case GlkStream.StylePreformatted:
        case GlkStream.StyleHeader:
        case GlkStream.StyleSubheader:
        case GlkStream.StyleAlert:
        case GlkStream.StyleNote:
        case GlkStream.StyleBlockQuote:
        case GlkStream.StyleInput:
            return style1 != style2;
        default:
            break;
        }
        return false;
    }

    @Override
    public Integer styleMeasure(int style, int hint) {
        throw new RuntimeException("unimplemented");
    }

    @Override
    public void requestLineEvent(GlkByteArray buffer, int initLength) {
        if (lineEventRequested || charEventRequested) {
            throw new IllegalStateException();
        }
        lineEventRequested = true;
        lineEventBuffer = buffer;
    }

    @Override
    public void requestCharEvent() {
        if (lineEventRequested || charEventRequested) {
            throw new IllegalStateException();
        }
        charEventRequested = true;
    }

    @Override
    public GlkEvent cancelLineEvent() {
        if (!lineEventRequested) {
            return new GlkEvent(GlkEvent.TypeNone, this, 0, 0);
        }
        lineEventRequested = false;
        lineEventBuffer = null;
        return new GlkEvent(GlkEvent.TypeLineInput, this, 0, 0);
    }

    @Override
    public void cancelCharEvent() {
        charEventRequested = false;
    }

    @Override
    public void flowBreak() {
        updateQueueLatest(false).flowBreak = true;
    }


    @Override
    public void setEchoLineEvent(boolean echoLineEvent) {
        this.echoLineEvent = echoLineEvent;
    }

    
    private final GlkWindowStream stream = new GlkWindowStream(this) {
        @Override
        public void putChar(int ch) throws IOException {
            super.putChar(ch);
            writeCount++;
            updateQueueLatest(false).string.append((char) (ch & 255));
        }

        @Override
        public void putString(CharSequence string) throws IOException {
            super.putString(string);
            writeCount += string.length();
            updateQueueLatest(false).string.append(string);
        }

        @Override
        public void putBuffer(GlkByteArray buffer) throws IOException {
            super.putBuffer(buffer);
            writeCount += buffer.getArrayLength();
            Update update = updateQueueLatest(false);
            for (int i = 0; i < buffer.getArrayLength(); i++) {
                update.string.append((char) (buffer.getByteElementAt(i) & 255));
            }
        }

        @Override
        public void putCharUni(int ch) throws IOException {
            super.putCharUni(ch);
            writeCount++;
            updateQueueLatest(false).string.append(Character.toChars(ch));
        }

        @Override
        public void putStringUni(UnicodeString string) throws IOException {
            super.putStringUni(string);
            writeCount += string.codePointCount();
            updateQueueLatest(false).string.append(string);
        }

        @Override
        public void putBufferUni(GlkIntArray buffer) throws IOException {
            super.putBufferUni(buffer);
            writeCount += buffer.getArrayLength();
            Update update = updateQueueLatest(false);
            for (int i = 0; i < buffer.getArrayLength(); i++) {
                update.string.append(Character.toChars(buffer.getIntElementAt(i)));
            }
        }

        @Override
        public void setStyle(int style) {
            super.setStyle(style);
            if (updateQueueLatest(false).style != style) {
                updateQueueLatest(true).style = style;
            }
        }
    };
}
