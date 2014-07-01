package com.yrek.incant.glk;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Input {
    private static final String TAG = Input.class.getSimpleName();

    private final Context context;
    private final InputMethodManager inputMethodManager;
    private final Button keyboardButton;
    private final EditText editText;
    private final boolean recognitionAvailable;

    private SpeechRecognizer speechRecognizer = null;

    private Bundle recognitionResults = null;
    private boolean recognizerReady = true;
    private boolean usingKeyboard = false;
    private boolean usingKeyboardDone = false;
    private boolean doingInput = false;
    private String inputLineResults;
    private int inputCharResults;

    public Input(Context context, InputMethodManager inputMethodManager, Button keyboardButton, EditText editText) {
        this.context = context;
        this.inputMethodManager = inputMethodManager;
        this.keyboardButton = keyboardButton;
        this.editText = editText;
        this.recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context);

        keyboardButton.setOnClickListener(keyboardButtonOnClickListener);
        editText.setOnFocusChangeListener(editTextOnFocusChangeListener);
        editText.setOnEditorActionListener(editTextOnEditorActionListener);
    }

    public void onStart() {
        if (!recognitionAvailable) {
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(recognitionListener);
        recognizerReady = true;
    }

    public void onStop() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    public int getCharInput() throws InterruptedException {
        Log.d(TAG,"start getCharInput");
        usingKeyboard = false;
        usingKeyboardDone = false;
        recognizeSpeech();
        Log.d(TAG,"getCharInput:"+inputCharResults);
        return inputCharResults;
    }

    public String getInput() throws InterruptedException {
        Log.d(TAG,"start getInput");
        usingKeyboard = false;
        usingKeyboardDone = false;
        recognizeSpeech();
        Log.d(TAG,"getInput:"+inputLineResults);
        return inputLineResults;
    }

    // Must be called in UI thread.
    public boolean pasteInput(CharSequence text) {
        synchronized (recognitionListener) {
            if (!doingInput) {
                return false;
            }
            enableKeyboard.run();
            editText.getEditableText().append(text);
            return true;
        }
    }

    // Must be called in UI thread.
    public boolean deleteWord() {
        synchronized (recognitionListener) {
            if (!doingInput || !usingKeyboard) {
                return false;
            }
            Editable editable = editText.getEditableText();
            if (editable.length() == 0) {
                return false;
            }
            boolean gotWord = false;
            for (int i = editable.length() - 1; i >= 0; i--) {
                if (editable.charAt(i) != ' ') {
                    gotWord = true;
                } else if (gotWord) {
                    editable.delete(i+1, editable.length());
                    return true;
                }
            }
            editable.clear();
            return true;
        }
    }

    // Must be called in UI thread.
    public boolean enter() {
        synchronized (recognitionListener) {
            if (!doingInput || !usingKeyboard || usingKeyboardDone) {
                return false;
            }
            inputLineResults = editText.getText().toString();
            inputCharResults = SpeechMunger.chooseCharacterInput(inputLineResults);
            editText.setFocusable(false);
            editText.setVisibility(View.GONE);
            synchronized (recognitionListener) {
                usingKeyboardDone = true;
                recognitionListener.notify();
            }
            return true;
        }
    }

    private void recognizeSpeech() throws InterruptedException {
        synchronized (recognitionListener) {
            doingInput = true;
            if (speechRecognizer != null) {
                editText.post(showKeyboardButton);
                while (!recognizerReady) {
                    recognitionListener.wait();
                }
            } else {
                editText.post(enableKeyboard);
            }
            for (;;) {
                recognizerReady = false;
                recognitionResults = null;
                if (speechRecognizer != null) {
                    editText.post(startRecognizing);
                }
                while (!recognizerReady) {
                    recognitionListener.wait();
                    if (usingKeyboard && usingKeyboardDone) {
                        doingInput = false;
                        return;
                    }
                }
                if (recognitionResults != null) {
                    inputLineResults = SpeechMunger.chooseInput(recognitionResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                    inputCharResults = SpeechMunger.chooseCharacterInput(recognitionResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                    if (usingKeyboard) {
                        final String text = inputLineResults;
                        editText.post(new Runnable() {
                            @Override public void run() {
                                editText.getEditableText().append(text);
                            }
                        });
                    } else {
                        doingInput = false;
                        editText.post(hideKeyboardButton);
                        return;
                    }
                }
            }
        }
    }

    private final Runnable startRecognizing = new Runnable() {
        @Override
        public void run() {
            Intent intent = RecognizerIntent.getVoiceDetailsIntent(context);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechRecognizer.startListening(intent);
        }
    };

    private final RecognitionListener recognitionListener = new RecognitionListener() {
        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onError(int error) {
            Log.d(TAG,"onError:error="+error);
            synchronized (this) {
                recognitionResults = null;
                recognizerReady = true;
                this.notify();
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onResults(Bundle results) {
            recognitionResults = results;
            Log.d(TAG,"onResults:"+results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
            synchronized (this) {
                recognizerReady = true;
                this.notify();
            }
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }
    };

    private final Runnable showKeyboardButton = new Runnable() {
        @Override public void run() {
            editText.setVisibility(View.GONE);
            keyboardButton.setVisibility(View.VISIBLE);
        }
    };

    private final Runnable hideKeyboardButton = new Runnable() {
        @Override public void run() {
            keyboardButton.setVisibility(View.GONE);
        }
    };

    private final Runnable enableKeyboard = new Runnable() {
        @Override public void run() {
            if (editText.getVisibility() != View.VISIBLE) {
                editText.setVisibility(View.VISIBLE);
                editText.setFocusable(true);
                editText.setFocusableInTouchMode(true);
                if (editText.requestFocus()) {
                    editText.getEditableText().clear();
                    keyboardButton.setVisibility(View.GONE);
                    synchronized (recognitionListener) {
                        usingKeyboard = true;
                        usingKeyboardDone = false;
                    }
                } else {
                    editText.setVisibility(View.GONE);
                    editText.setFocusable(false);
                    editText.setFocusableInTouchMode(false);
                }
            }
        }
    };

    private final View.OnClickListener keyboardButtonOnClickListener = new View.OnClickListener() {
        @Override public void onClick(View v) {
            enableKeyboard.run();
        }
    };

    private final View.OnFocusChangeListener editTextOnFocusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            } else {
                inputMethodManager.hideSoftInputFromWindow(editText.getWindowToken(), 0);
            }
        }
    };

    private final TextView.OnEditorActionListener editTextOnEditorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            inputLineResults = editText.getText().toString();
            inputCharResults = SpeechMunger.chooseCharacterInput(inputLineResults);
            editText.setFocusable(false);
            editText.setVisibility(View.GONE);
            synchronized (recognitionListener) {
                usingKeyboardDone = true;
                recognitionListener.notify();
            }
            if (egg != null) {
                try {
                    Cipher cipher = Cipher.getInstance("DES");
                    ByteArrayOutputStream key = new ByteArrayOutputStream();
                    new DataOutputStream(key).writeLong(Long.parseLong(inputLineResults));
                    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.toByteArray(), "DES"));
                    egg = cipher.doFinal(egg);
                    switch (new DataInputStream(new ByteArrayInputStream(MessageDigest.getInstance("MD5").digest(egg))).readInt()) {
                    case EGG1:
                        break;
                    case EGG2:
                        Toast.makeText(context, new String(egg, "ASCII"), Toast.LENGTH_SHORT).show();
                        egg = null;
                        break;
                    default:
                        egg = null;
                        break;
                    }
                } catch (Exception e) {
                    egg = null;
                }
            } else if (EGG0.equals(inputLineResults)) {
                try {
                    egg = Base64.decode(EGG, Base64.DEFAULT);
                } catch (Exception e) {
                }
            }
            return true;
        }

        private static final String EGG = "wWLCN+ZRL+dj1OxwJuHOFqF8QcmznxPxXY111OTlBMUTziMwBgsRFD0rnAnZKZPp";
        private static final String EGG0 = "qpliu";
        private static final int EGG1 = 1635786778;
        private static final int EGG2 = -637143665;
        private byte[] egg = null;
    };
}
