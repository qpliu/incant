package com.yrek.incant.glk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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

import java.io.IOException;
import java.io.Serializable;

import com.yrek.ifstd.glk.Glk;
import com.yrek.ifstd.glk.GlkByteArray;
import com.yrek.ifstd.glk.GlkDispatch;
import com.yrek.ifstd.glk.GlkEvent;
import com.yrek.ifstd.glk.GlkFile;
import com.yrek.ifstd.glk.GlkGestalt;
import com.yrek.ifstd.glk.GlkIntArray;
import com.yrek.ifstd.glk.GlkStream;
import com.yrek.ifstd.glk.GlkWindow;
import com.yrek.ifstd.glk.UnicodeString;
import com.yrek.incant.R;

public class GlkActivity extends Activity {
    public static final String TAG = GlkActivity.class.getSimpleName();
    public static final String GLK_MAIN = "GLK_MAIN";
    private static final String SUSPEND_STATE = "SUSPEND_STATE";
    private GlkMain main;
    private GlkDispatch glkDispatch;
    private Serializable suspendState;

    private FrameLayout frameLayout;
    private Button keyboardButton;
    private Button skipButton;
    private Button nextButton;
    private EditText editText;
    private InputMethodManager inputMethodManager;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private Window rootWindow;
    private Stream currentStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.glk);
        frameLayout = (FrameLayout) findViewById(R.id.frame);
        keyboardButton = (Button) findViewById(R.id.keyboard);
        skipButton = (Button) findViewById(R.id.skip);
        nextButton = (Button) findViewById(R.id.next);
        editText = (EditText) findViewById(R.id.edit);
        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        main = (GlkMain) getIntent().getSerializableExtra(GLK_MAIN);
        suspendState = savedInstanceState.getSerializable(SUSPEND_STATE);

        //... restore window heirarchy, streams, files, schannels
        rootWindow = null;
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
        main.init(glkDispatch, suspendState);
        //... init speechRecognizer and textToSpeech
    }

    @Override
    protected void onStop() {
        super.onStop();
        //... speechRecognizer.destroy();
        speechRecognizer = null;
        //... textToSpeech.shutdown();
        textToSpeech = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.requestSuspend();
        //... generate a window arrangement event
        suspendState = main.suspend();
    }

    private class Exit extends RuntimeException {}

    private final Glk glk = new Glk() {
        @Override
        public void main(Runnable main) throws IOException {
            throw new RuntimeException("unimplemented");
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
            case GlkGestalt.Timer:
            case GlkGestalt.Graphics:
            case GlkGestalt.DrawImage:
            case GlkGestalt.Sound:
            case GlkGestalt.SoundVolume:
            case GlkGestalt.SoundNotify:
            case GlkGestalt.Hyperlinks:
            case GlkGestalt.HyperlinkInput:
            case GlkGestalt.SoundMusic:
            case GlkGestalt.GraphicsTransparency:
                return 0;
            case GlkGestalt.Unicode:
                return 1;
            case GlkGestalt.UnicodeNorm:
            case GlkGestalt.LineInputEcho:
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
            if (rootWindow == null) {
                if (split != null) {
                    throw new IllegalStateException();
                }
                //...
            } else {
                //...
            }
            throw new RuntimeException("unimplemented");
        }


        @Override
        public void setWindow(GlkWindow window) {
            throw new RuntimeException("unimplemented");
        }


        @Override
        public GlkStream streamOpenFile(GlkFile file, int mode, int rock) throws IOException {
            throw new RuntimeException("unimplemented");
        }

        @Override
        public GlkStream streamOpenFileUni(GlkFile file, int mode, int rock) throws IOException {
            throw new RuntimeException("unimplemented");
        }

        @Override
        public GlkStream streamOpenMemory(GlkByteArray memory, int mode, int rock) {
            throw new RuntimeException("unimplemented");
        }

        @Override
        public GlkStream streamOpenMemoryUni(GlkIntArray memory, int mode, int rock) {
            throw new RuntimeException("unimplemented");
        }

        @Override
        public void streamSetCurrent(GlkStream stream) {
            throw new RuntimeException("unimplemented");
        }

        @Override
        public GlkStream streamGetCurrent() {
            throw new RuntimeException("unimplemented");
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
            throw new RuntimeException("unimplemented");
        }

        @Override
        public void styleHintClear(int winType, int style, int hint) {
            throw new RuntimeException("unimplemented");
        }


        @Override
        public GlkFile fileCreateTemp(int usage, int rock) throws IOException {
            throw new RuntimeException("unimplemented");
        }

        @Override
        public GlkFile fileCreateByName(int usage, CharSequence name, int rock) throws IOException {
            throw new RuntimeException("unimplemented");
        }

        @Override
        public GlkFile fileCreateByPrompt(int usage, int mode, int rock) throws IOException {
            throw new RuntimeException("unimplemented");
        }

        @Override
        public GlkFile fileCreateFromFile(int usage, GlkFile file, int rock) throws IOException {
            throw new RuntimeException("unimplemented");
        }


        @Override
        public GlkEvent select() throws IOException {
            throw new RuntimeException("unimplemented");
        }

        @Override
        public GlkEvent selectPoll() throws IOException {
            throw new RuntimeException("unimplemented");
        }


        @Override
        public void requestTimerEvents(int millisecs) {
            throw new RuntimeException("unimplemented");
        }


        @Override
        public boolean imageGetInfo(int resourceId, int[] size) {
            return false;
        }
    };
}
