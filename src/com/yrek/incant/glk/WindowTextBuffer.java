package com.yrek.incant.glk;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
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

class WindowTextBuffer extends Window {
    private static final long serialVersionUID = 0L;
    private static final String TAG = WindowTextBuffer.class.getSimpleName();

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
        if (polling) {
            return null;
        } else if (lineEventRequested) {
            activity.hideProgressBar();
            activity.speech.resetSkip();
            //... timeout unimplemented
            String line = activity.input.getInput();
            lineEventRequested = false;
            int count = lineEventBuffer == null ? 0 : Math.min(lineEventBuffer.getArrayLength(), line.length());
            for (int i = 0; i < count; i++) {
                lineEventBuffer.setByteElementAt(i, line.charAt(i));
            }
            if (echoLineEvent) {
                int saveStyle = updateQueueLast(true).style;
                try {
                    stream.setStyle(GlkStream.StyleInput);
                    stream.putString(line);
                    updates.peekLast().mute = true;
                    stream.setStyle(saveStyle);
                    stream.putChar(10);
                } catch (IOException e) {
                    Log.wtf(TAG,e);
                    stream.setStyle(saveStyle);
                }
            }
            activity.showProgressBar();
            return new GlkEvent(GlkEvent.TypeLineInput, this, count, 0);
        } else if (charEventRequested) {
            activity.hideProgressBar();
            activity.speech.resetSkip();
            //... timeout unimplemented
            int ch = activity.input.getCharInput();
            charEventRequested = false;
            activity.showProgressBar();
            return new GlkEvent(GlkEvent.TypeCharInput, this, ch, 0);
        } else {
            return null;
        }
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
        if (!doSpeech) {
            return false;
        }
        if (updates.size() == 0 || (updates.size() == 1 && updates.peekFirst().string.length() == 0)) {
            return false;
        }
        activity.hideProgressBar();
        final ScrollView scrollView = (ScrollView) view;
        TextView textView = (TextView) scrollView.getChildAt(0);
        int currentStyle = GlkStream.StyleNormal;
        SpannableStringBuilder screenOutput = new SpannableStringBuilder();
        StringBuilder speechOutput = new StringBuilder();
        int endParagraphState = 0;
        loop:
        for (;;) {
            Update update = updates.peekFirst();
            if (update == null) {
                break;
            }
            if (update.clear) {
                if (speechOutput.length() > 0 || screenOutput.length() > 0) {
                    break;
                }
                textView.setText("");
                update.clear = false;
            }
            if (update.flowBreak) {
                if (speechOutput.length() > 0 || screenOutput.length() > 0) {
                    break;
                }
                update.flowBreak = false;
            }
            currentStyle = update.style;
            int start = screenOutput.length();
            // Break at paragraph breaks.
            for (int i = 0; i < update.string.length(); i++) {
                if (update.string.charAt(i) != '\n') {
                    endParagraphState = 1;
                } else {
                    switch (endParagraphState) {
                    case 0: break;
                    case 1: endParagraphState = 2; break;
                    case 2:
                        styleText(screenOutput, start, screenOutput.length(), currentStyle);
                        if (!update.mute) {
                            speechOutput.append(update.string, 0, i);
                        }
                        update.string.delete(0, i);
                        break loop;
                    }
                }
                screenOutput.append(update.string.charAt(i));
            }
            if (!update.mute) {
                speechOutput.append(update.string);
            }
            styleText(screenOutput, start, screenOutput.length(), currentStyle);
            if (update.image != null) {
                screenOutput.setSpan(new ImageSpan(activity, update.image), start, screenOutput.length(), 0);
            }
            updates.removeFirst();
        }
        if (screenOutput.length() > 0) {
            textView.append(screenOutput);
            post(new Runnable() {
                @Override public void run() {
                    scrollView.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
        if (currentStyle != GlkStream.StyleNormal && updates.size() == 0) {
            stream.setStyle(currentStyle);
        }
        if (speechOutput.length() == 0) {
            return false;
        } else {
            activity.speech.speak(speechOutput.toString(), continueOutput);
            return true;
        }
    }

    @Override
    TextAppearanceSpan getSpanForStyle(int style) {
        return new TextAppearanceSpan(activity, activity.main.getTextBufferStyle(style));
    }

    static class Update {
        boolean clear = false;
        boolean flowBreak = false;
        boolean mute = false;
        Bitmap image = null;
        int imageAlign = 0;
        int style = GlkStream.StyleNormal;
        StringBuilder string = new StringBuilder();
    }

    Update updateQueueLast(boolean continueString) {
        Update latest = updates.peekLast();
        if (latest != null && (continueString || latest.string.length() == 0) && !latest.mute) {
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
    int getPixelWidth(int size) {
        activity.waitForTextMeasurer();
        return size*activity.charWidth+activity.charHMargin;
    }

    @Override
    int getPixelHeight(int size) {
        activity.waitForTextMeasurer();
        return size*activity.charHeight+activity.charVMargin;
    }

    @Override
    void onWindowSizeChanged(int width, int height) {
        Log.d(TAG,"screenSize="+((width-activity.charHMargin)/activity.charWidth)+"x"+((height-activity.charVMargin)/activity.charHeight));
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
        activity.waitForTextMeasurer();
        // Do not waitForWindowMeasurer() because Glk.select() might not
        // have been called yet.
        if (activity.charWidth > 0 && activity.charHeight > 0 && windowWidth > 0 && windowHeight > 0) {
            return new GlkWindowSize((windowWidth - activity.charHMargin)/activity.charWidth, (windowHeight - activity.charVMargin)/activity.charHeight);
        } else {
            return new GlkWindowSize(40, 25);
        }
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
        return activity.main.getTextBufferStyle(style1) != activity.main.getTextBufferStyle(style2);
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
    public boolean drawImage(int resourceId, int val1, int val2) throws IOException {
        Bitmap image = activity.getImageResource(resourceId);
        if (image == null) {
            return false;
        }
        Update update = updateQueueLast(false);
        update.image = image;
        update.imageAlign = val1;
        update.mute = true;
        update.string.append("[image " + resourceId + "]");
        return true;
    }

    @Override
    public boolean drawScaledImage(int resourceId, int val1, int val2, int width, int height) throws IOException {
        Bitmap image = activity.getImageResource(resourceId);
        if (image == null) {
            return false;
        }
        Update update = updateQueueLast(false);
        update.image = Bitmap.createScaledBitmap(image, width, height, true);
        update.imageAlign = val1;
        update.mute = true;
        update.string.append("[image " + resourceId + "]");
        return true;
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
