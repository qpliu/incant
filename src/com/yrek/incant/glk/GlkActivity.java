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

import java.io.Serializable;

import com.yrek.ifstd.glk.Glk;
import com.yrek.ifstd.glk.GlkDispatch;
import com.yrek.incant.R;

public class GlkActivity extends Activity {
    public static final String TAG = GlkActivity.class.getSimpleName();
    public static final String GLK_MAIN = "GLK_MAIN";
    private static final String SUSPEND_STATE = "SUSPEND_STATE";
    private GlkMain main;
    private GlkDispatch glkDispatch;
    private Serializable suspendState;

    private FrameLayout frame;
    private Button keyboardButton;
    private Button skipButton;
    private Button nextButton;
    private EditText editText;
    private InputMethodManager inputMethodManager;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.glk);
        keyboardButton = (Button) findViewById(R.id.keyboard);
        skipButton = (Button) findViewById(R.id.skip);
        nextButton = (Button) findViewById(R.id.next);
        editText = (EditText) findViewById(R.id.edit);
        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        main = (GlkMain) getIntent().getSerializableExtra(GLK_MAIN);
        suspendState = savedInstanceState.getSerializable(SUSPEND_STATE);
        //... restore window heirarchy, streams, files, schannels
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
        speechRecognizer.destroy();
        speechRecognizer = null;
        textToSpeech.shutdown();
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
}
