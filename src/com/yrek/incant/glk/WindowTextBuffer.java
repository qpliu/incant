package com.yrek.incant.glk;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

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
import com.yrek.incant.R;

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
    View createView(Context context) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(new TextView(context));
        return scrollView;
    }

    @Override
    boolean hasPendingEvent() {
        return lineEventRequested || charEventRequested;
    }

    @Override
    GlkEvent getEvent(long timeout, boolean polling) throws InterruptedException {
        if (true) { //... tmp
            if (hasPendingEvent() && !polling) {
                if (timeout > 0) {
                    Thread.sleep(timeout);
                } else {
                    Thread.sleep(5000L);
                }
            }
            return null;
        } //... tmp
        //... On speech recognizer, get String from SpeechMunger.chooseInput
        //... then if echoLineEvent, save current style;
        //... stream.setStyle(GlkStream.StyleInput); stream.putString(input);
        //... stream.setStyle(the saved current style); stream.putChar(10)
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
        if (true) { //... tmp
            // just dump it all in just to be able to see something to start
            final ScrollView scrollView = (ScrollView) view;
            TextView textView = (TextView) scrollView.getChildAt(0);
            int currentStyle = GlkStream.StyleNormal;
            SpannableStringBuilder sb = new SpannableStringBuilder();
            for (Update update = updates.pollFirst(); update != null; update = updates.pollFirst()) {
                if (update.clear) {
                    sb.clearSpans();
                    sb.clear();
                    textView.setText("");
                }
                int start = sb.length();
                sb.append(update.string);
                if (sb.length() > start) {
                    sb.setSpan(getSpanForStyle(update.style), start, sb.length(), 0);
                }
                currentStyle = update.style;
            }
            textView.append(sb);
            scrollView.postDelayed(new Runnable() {
                @Override public void run() {
                    scrollView.fullScroll(View.FOCUS_DOWN);
                }
            }, 50L);
            stream.setStyle(currentStyle);
            return false;
        } //... tmp
        // Speak one paragraph at a time.
        // Also break on clear and flowBreak.
        // If one paragraph fills more than one screen, break
        // at a sentence if possible, otherwise break at a word if
        // possible, otherwise let all but the end of the monster word
        // scroll off and break after it.
        // If speech is being skipped, then if the output fills more
        // than the screen, break at a word and enable the next button
        // to post continueOutput on click.
        throw new RuntimeException("unimplemented");
    }

    private TextAppearanceSpan getSpanForStyle(int style) {
        switch (style) {
        case GlkStream.StyleEmphasized:
            return new TextAppearanceSpan(activity, R.style.glk_emphasized);
        case GlkStream.StylePreformatted:
            return new TextAppearanceSpan(activity, R.style.glk_preformatted);
        case GlkStream.StyleHeader:
            return new TextAppearanceSpan(activity, R.style.glk_header);
        case GlkStream.StyleSubheader:
            return new TextAppearanceSpan(activity, R.style.glk_subheader);
        case GlkStream.StyleAlert:
            return new TextAppearanceSpan(activity, R.style.glk_alert);
        case GlkStream.StyleNote:
            return new TextAppearanceSpan(activity, R.style.glk_note);
        case GlkStream.StyleBlockQuote:
            return new TextAppearanceSpan(activity, R.style.glk_blockquote);
        case GlkStream.StyleInput:
            return new TextAppearanceSpan(activity, R.style.glk_input);
        default:
            return new TextAppearanceSpan(activity, R.style.glk_normal);
        }
    }

    static class Update {
        boolean clear = false;
        boolean flowBreak = false;
        int style = GlkStream.StyleNormal;
        StringBuilder string = new StringBuilder();
    }

    Update updateQueueLast(boolean continueString) {
        Update latest = updates.peekLast();
        if (latest != null && (continueString || latest.string.length() == 0)) {
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
        if (true) { //... tmp
            return new GlkWindowSize(40, 25);
        } //... tmp
        throw new RuntimeException("unimplemented");
    }

    @Override
    public int getType() {
        return GlkWindow.TypeTextBuffer;
    }

    @Override
    public void clear() throws IOException {
        updateQueueLast(false).clear = true;
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
        if (true) { //... tmp
            return null;
        } //... tmp
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
        updateQueueLast(false).flowBreak = true;
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
            updateQueueLast(true).string.append((char) (ch & 255));
        }

        @Override
        public void putString(CharSequence string) throws IOException {
            super.putString(string);
            writeCount += string.length();
            updateQueueLast(true).string.append(string);
        }

        @Override
        public void putBuffer(GlkByteArray buffer) throws IOException {
            super.putBuffer(buffer);
            writeCount += buffer.getArrayLength();
            Update update = updateQueueLast(true);
            for (int i = 0; i < buffer.getArrayLength(); i++) {
                update.string.append((char) (buffer.getByteElementAt(i) & 255));
            }
        }

        @Override
        public void putCharUni(int ch) throws IOException {
            super.putCharUni(ch);
            writeCount++;
            updateQueueLast(true).string.append(Character.toChars(ch));
        }

        @Override
        public void putStringUni(UnicodeString string) throws IOException {
            super.putStringUni(string);
            writeCount += string.codePointCount();
            updateQueueLast(true).string.append(string);
        }

        @Override
        public void putBufferUni(GlkIntArray buffer) throws IOException {
            super.putBufferUni(buffer);
            writeCount += buffer.getArrayLength();
            Update update = updateQueueLast(true);
            for (int i = 0; i < buffer.getArrayLength(); i++) {
                update.string.append(Character.toChars(buffer.getIntElementAt(i)));
            }
        }

        @Override
        public void setStyle(int style) {
            super.setStyle(style);
            if (updateQueueLast(true).style != style) {
                updateQueueLast(false).style = style;
            }
        }
    };
}
