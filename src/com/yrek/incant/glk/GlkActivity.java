package com.yrek.incant.glk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URLEncoder;

import com.yrek.ifstd.blorb.Blorb;
import com.yrek.ifstd.glk.Glk;
import com.yrek.ifstd.glk.GlkByteArray;
import com.yrek.ifstd.glk.GlkDispatch;
import com.yrek.ifstd.glk.GlkEvent;
import com.yrek.ifstd.glk.GlkFile;
import com.yrek.ifstd.glk.GlkGestalt;
import com.yrek.ifstd.glk.GlkIntArray;
import com.yrek.ifstd.glk.GlkStream;
import com.yrek.ifstd.glk.GlkStreamMemory;
import com.yrek.ifstd.glk.GlkStreamMemoryUnicode;
import com.yrek.ifstd.glk.GlkWindow;
import com.yrek.ifstd.glk.UnicodeString;
import com.yrek.incant.R;

public class GlkActivity extends Activity {
    public static final String TAG = GlkActivity.class.getSimpleName();
    public static final String GLK_MAIN = "GLK_MAIN";
    private static final String SUSPEND_STATE = "SUSPEND_STATE";
    GlkMain main;
    private GlkDispatch glkDispatch;
    private Serializable suspendState;

    private FrameLayout frameLayout;
    int charWidth = 0;
    int charHeight = 0;
    int charHMargin = 0;
    int charVMargin = 0;
    private Button nextButton;
    Input input;
    Speech speech;
    Window rootWindow;
    private GlkStream currentStream;
    private long lastTimerEvent = 0L;
    private long timerInterval = 0L;

    private Object ioLock = new Object();
    private boolean outputPending = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.glk);
        frameLayout = (FrameLayout) findViewById(R.id.frame);
        nextButton = (Button) findViewById(R.id.next);
        input = new Input(this, (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE), (Button) findViewById(R.id.keyboard), (EditText) findViewById(R.id.edit));
        speech = new Speech(this, (Button) findViewById(R.id.skip));
        findViewById(R.id.onexone).addOnLayoutChangeListener(textMeasurer);
        findViewById(R.id.twoxtwo).addOnLayoutChangeListener(textMeasurer);

        main = (GlkMain) getIntent().getSerializableExtra(GLK_MAIN);
        rootWindow = null;
        if (savedInstanceState != null) {
            suspendState = savedInstanceState.getSerializable(SUSPEND_STATE);
            //... restore window heirarchy, streams, files, schannels
        }

        glkDispatch = new GlkDispatch(glk);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(SUSPEND_STATE, suspendState);
        //... save window heirarchy, streams, files, schannels
    }

    @Override
    protected void onStart() {
        super.onStart();
        //... generate window arrangement and redraw events
        main.init(this, glkDispatch, suspendState);
        input.onStart();
        speech.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        input.onStop();
        speech.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.start(new Runnable() {
            @Override public void run() {
                try {
                    speech.waitForInit();
                } catch (InterruptedException e) {
                    Log.wtf(TAG,e);
                }
                waitForTextMeasurer();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!main.finished()) {
            main.requestSuspend();
            //... generate a window arrangement event
            suspendState = main.suspend();
        }
    }

    void post(Runnable runnable) {
        frameLayout.post(runnable);
    }

    private class Exit extends RuntimeException {}

    private final Glk glk = new Glk() {
        @Override
        public void main(Runnable runMain) throws IOException {
            boolean exited = false;
            try {
                runMain.run();
            } catch (Exit e) {
                exited = true;
            }
            if (exited || main.finished()) {
                post(new Runnable() {
                    @Override public void run() {
                        finish();
                    }
                });
            }
        }

        @Override
        public void exit() {
            throw new Exit();
        }

        @Override
        public void setInterruptHandler(Runnable handler) {
        }

        @Override
        public void tick() {
        }


        @Override
        public int gestalt(int selector, int value) {
            switch (selector) {
            case GlkGestalt.Version:
                return Glk.GlkVersion;
            case GlkGestalt.CharInput:
            case GlkGestalt.LineInput:
            case GlkGestalt.CharOutput:
                return 1;
            case GlkGestalt.MouseInput:
                return 0;
            case GlkGestalt.Timer:
            case GlkGestalt.Graphics:
                return 0;
            case GlkGestalt.DrawImage:
                switch (value) {
                case GlkWindow.TypeGraphics:
                    return 1;
                default:
                    return 0;
                }
            case GlkGestalt.Sound:
            case GlkGestalt.SoundVolume:
            case GlkGestalt.SoundNotify:
            case GlkGestalt.Hyperlinks:
            case GlkGestalt.HyperlinkInput:
            case GlkGestalt.SoundMusic:
                return 0;
            case GlkGestalt.GraphicsTransparency:
                return 1;
            case GlkGestalt.Unicode:
                return 1;
            case GlkGestalt.UnicodeNorm:
                return 0;
            case GlkGestalt.LineInputEcho:
                return 1;
            case GlkGestalt.LineTerminators:
            case GlkGestalt.LineTerminatorKey:
            case GlkGestalt.DateTime:
            case GlkGestalt.Sound2:
            case GlkGestalt.ResourceStream:
            default:
                return 0;
            }
        }

        @Override
        public int gestaltExt(int selector, int value, GlkIntArray array) {
            switch (selector) {
            case GlkGestalt.CharOutput:
                if (value < 256 && (value == 10 || !Character.isISOControl(value))) {
                    array.setIntElement(1);
                    return GlkGestalt.CharOutput_ExactPrint;
                } else {
                    if (Character.isValidCodePoint(value)) {
                        array.setIntElement(1);
                        return GlkGestalt.CharOutput_ApproxPrint;
                    } else {
                        array.setIntElement(0);
                        return GlkGestalt.CharOutput_CannotPrint;
                    }
                }
            default:
                return 0;
            }
        }


        @Override
        public GlkWindow windowGetRoot() {
            return rootWindow;
        }

        @Override
        public GlkWindow windowOpen(GlkWindow split, int method, int size, int winType, int rock) {
            Window window = Window.open(GlkActivity.this, (Window) split, method, size, winType, rock);
            if (window != null && rootWindow == split) {
                rootWindow = window.parent;
                if (rootWindow == null) {
                    rootWindow = window;
                }
            }
            return window;
        }


        @Override
        public void setWindow(GlkWindow window) {
            if (window == null) {
                currentStream = null;
            } else {
                currentStream = window.getStream();
            }
        }


        @Override
        public GlkStream streamOpenFile(GlkFile file, int mode, int rock) throws IOException {
            return new StreamFile((FileRef) file, mode, false, rock);
        }

        @Override
        public GlkStream streamOpenFileUni(GlkFile file, int mode, int rock) throws IOException {
            return new StreamFile((FileRef) file, mode, true, rock);
        }

        @Override
        public GlkStream streamOpenMemory(GlkByteArray memory, int mode, int rock) {
            return new GlkStreamMemory(memory, rock);
        }

        @Override
        public GlkStream streamOpenMemoryUni(GlkIntArray memory, int mode, int rock) {
            return new GlkStreamMemoryUnicode(memory, rock);
        }

        @Override
        public void streamSetCurrent(GlkStream stream) {
            currentStream = stream;
        }

        @Override
        public GlkStream streamGetCurrent() {
            return currentStream;
        }

        @Override
        public void putChar(int ch) throws IOException {
            if (currentStream != null) {
                currentStream.putChar(ch);
            }
        }

        @Override
        public void putString(CharSequence string) throws IOException {
            if (currentStream != null) {
                currentStream.putString(string);
            }
        }

        @Override
        public void putBuffer(GlkByteArray buffer) throws IOException {
            if (currentStream != null) {
                currentStream.putBuffer(buffer);
            }
        }

        @Override
        public void putCharUni(int ch) throws IOException {
            if (currentStream != null) {
                currentStream.putCharUni(ch);
            }
        }

        @Override
        public void putStringUni(UnicodeString string) throws IOException {
            if (currentStream != null) {
                currentStream.putStringUni(string);
            }
        }

        @Override
        public void putBufferUni(GlkIntArray buffer) throws IOException {
            if (currentStream != null) {
                currentStream.putBufferUni(buffer);
            }
        }

        @Override
        public void setStyle(int style) {
            if (currentStream != null) {
                currentStream.setStyle(style);
            }
        }


        @Override
        public void styleHintSet(int winType, int style, int hint, int value) {
            if (true) { //... tmp
                return;
            } //... tmp
            throw new RuntimeException("unimplemented");
        }

        @Override
        public void styleHintClear(int winType, int style, int hint) {
            if (true) { //... tmp
                return;
            } //... tmp
            throw new RuntimeException("unimplemented");
        }


        @Override
        public GlkFile fileCreateTemp(int usage, int rock) throws IOException {
            return new FileRef(File.createTempFile("glk.",".tmp"), usage, GlkFile.ModeReadWrite, rock);
        }

        @Override
        public GlkFile fileCreateByName(int usage, CharSequence name, int rock) throws IOException {
            return new FileRef(new File(main.getDir(GlkActivity.this), URLEncoder.encode("glk."+name, "UTF-8")), usage, GlkFile.ModeReadWrite, rock);
        }

        @Override
        public GlkFile fileCreateByPrompt(int usage, int mode, int rock) throws IOException {
            if (usage == GlkFile.UsageSavedGame) {
                return new FileRef(main.getSaveFile(GlkActivity.this), usage, mode, rock);
            }
            throw new RuntimeException("unimplemented");
        }

        @Override
        public GlkFile fileCreateFromFile(int usage, GlkFile file, int rock) throws IOException {
            throw new RuntimeException("unimplemented");
        }


        private GlkEvent timerEvent() {
            if (timerInterval <= 0) {
                return null;
            }
            if (System.currentTimeMillis() < lastTimerEvent + timerInterval) {
                return null;
            }
            lastTimerEvent = System.currentTimeMillis();
            return new GlkEvent(GlkEvent.TypeTimer, null, 0, 0);
        }

        private long timeToNextTimerEvent() {
            if (timerInterval <= 0) {
                return -1;
            }
            return Math.max(0L, lastTimerEvent + timerInterval - System.currentTimeMillis());
        }

        @Override
        public GlkEvent select() {
            synchronized (ioLock) {
                outputPending = true;
                frameLayout.post(handlePendingOutput);
                while (outputPending) {
                    try {
                        ioLock.wait();
                    } catch (InterruptedException e) {
                        main.requestSuspend();
                        return new GlkEvent(GlkEvent.TypeArrange, null, 0, 0);
                    }
                }
            }
            for (;;) {
                if (rootWindow == null) {
                    throw new IllegalStateException();
                }
                GlkEvent event;
                try {
                    event = rootWindow.getEvent(timeToNextTimerEvent(), false);
                } catch (InterruptedException e) {
                    main.requestSuspend();
                    event = new GlkEvent(GlkEvent.TypeArrange, null, 0, 0);
                }
                if (event != null) {
                    return event;
                }
                event = timerEvent();
                if (event != null) {
                    return event;
                }
            }
        }

        @Override
        public GlkEvent selectPoll() {
            GlkEvent event = null;
            try {
                event = rootWindow.getEvent(0L, true);
            } catch (InterruptedException e) {
                main.requestSuspend();
            }
            if (event != null) {
                return event;
            }
            event = timerEvent();
            if (event != null) {
                return event;
            }
            return new GlkEvent(GlkEvent.TypeNone, null, 0, 0);
        }


        @Override
        public void requestTimerEvents(int millisecs) {
            timerInterval = millisecs;
        }


        @Override
        public boolean imageGetInfo(int resourceId, int[] size) {
            try {
                Blorb blorb = main.getBlorb(GlkActivity.this);
                if (blorb == null) {
                    return false;
                }
                for (Blorb.Resource res : blorb.resources()) {
                    if (res.getUsage() == Blorb.Pict && res.getNumber() == resourceId) {
                        Blorb.Chunk chunk = res.getChunk();
                        if (chunk == null || (chunk.getId() != Blorb.PNG && chunk.getId() != Blorb.JPEG)) {
                            return false;
                        }
                        Bitmap bitmap = BitmapFactory.decodeByteArray(chunk.getContents(), 0, chunk.getLength());
                        size[0] = bitmap.getWidth();
                        size[1] = bitmap.getHeight();
                        return true;
                    }
                }
            } catch (IOException e) {
                Log.wtf(TAG,e);
            }
            return false;
        }
    };

    private final Runnable handlePendingOutput = new Runnable() {
        @Override
        public void run() {
            if (rootWindow == null) {
                frameLayout.removeViews(0, frameLayout.getChildCount());
            } else {
                if (frameLayout.getChildCount() == 0 || frameLayout.getChildAt(0) != rootWindow.getView()) {
                    frameLayout.removeViews(0, frameLayout.getChildCount());
                    frameLayout.addView(rootWindow.getView());
                }
                if (rootWindow.updatePendingOutput(this, false)) {
                    return;
                }
                if (rootWindow.updatePendingOutput(this, true)) {
                    return;
                }
            }
            synchronized (ioLock) {
                outputPending = false;
                ioLock.notifyAll();
            }
        }
    };

    void waitForTextMeasurer() {
        if (charWidth == 0) {
            try {
                synchronized (textMeasurer) {
                    while (charWidth == 0) {
                        textMeasurer.wait();
                    }
                }
            } catch (InterruptedException e) {
                Log.wtf(TAG,e);
            }
        }
    }

    private final View.OnLayoutChangeListener textMeasurer = new View.OnLayoutChangeListener() {
        private int w1 = 0;
        private int h1 = 0;
        private int w2 = 0;
        private int h2 = 0;

        @Override public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            switch (v.getId()) {
            case R.id.onexone: w1 = right - left; h1 = bottom - top; break;
            case R.id.twoxtwo: w2 = right - left; h2 = bottom - top; break;
            default:
            }
            v.setVisibility(View.GONE);
            if (w1 != 0 && w2 != 0) {
                int oldw = charWidth;
                int oldh = charHeight;
                synchronized (this) {
                    charWidth = w2 - w1;
                    charHeight = h2 - h1;
                    charHMargin = w2 - 2*charWidth;
                    charVMargin = h2 - 2*charHeight;
                    this.notifyAll();
                }
                if (charWidth != oldw || charHeight != oldh) {
                    Log.d(TAG,"charSize="+charWidth+"x"+charHeight+",margin="+charHMargin+"x"+charVMargin+",1x="+w1+"x"+h1+",4x="+w2+"x"+h2);
                }
            }
        }
    };
}
