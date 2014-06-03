package com.yrek.incant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import com.zaxsoft.zmachine.Dimension;
import com.zaxsoft.zmachine.Point;
import com.zaxsoft.zmachine.ZCPU;
import com.zaxsoft.zmachine.ZUserInterface;

public class Play extends Activity {
    private static final String TAG = Play.class.getSimpleName();

    private Story story;
    private ZCPU zcpu;
    private TextView textView;
    private Button keyboardButton;
    private Button skipButton;
    private EditText editText;
    private InputMethodManager inputMethodManager;
    private Thread zthread;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private SpannableStringBuilder screenBuffer;
    private int screenWidth;
    private int screenHeight;
    private int textColor;
    private int backgroundColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.play);
        story = (Story) getIntent().getSerializableExtra(Incant.STORY);
        ((TextView) findViewById(R.id.name)).setText(story.getName(Play.this));
        textView = (TextView) findViewById(R.id.text);
        keyboardButton = (Button) findViewById(R.id.keyboard);
        skipButton = (Button) findViewById(R.id.skip);
        editText = (EditText) findViewById(R.id.edit);
        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        screenBuffer = new SpannableStringBuilder();
        screenWidth = 0;
        screenHeight = 0;
        findViewById(R.id.onexone).addOnLayoutChangeListener(textMeasurer);
        findViewById(R.id.twoxtwo).addOnLayoutChangeListener(textMeasurer);
        textView.addOnLayoutChangeListener(textMeasurer);
        skipButton.setOnClickListener(skipButtonOnClickListener);
        keyboardButton.setOnClickListener(keyboardButtonOnClickListener);
        editText.setOnFocusChangeListener(editTextOnFocusChangeListener);
        editText.setOnEditorActionListener(editTextOnEditorActionListener);
        textColor = getResources().getColor(R.color.text);
        backgroundColor = getResources().getColor(R.color.background);
    }

    @Override
    protected void onStart() {
        super.onStart();
        zcpu = new ZCPU(zui);
        recognizerReady = true;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(recognitionListener);
        utteranceQueued = true;
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                synchronized(utteranceProgressListener) {
                    utteranceQueued = false;
                    utteranceProgressListener.notify();
                }
            }
        });
        textToSpeech.setOnUtteranceProgressListener(utteranceProgressListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (zthread != null) {
            zthread.interrupt();
        }
        speechRecognizer.destroy();
        speechRecognizer = null;
        textToSpeech.shutdown();
        textToSpeech = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        keyboardButton.setVisibility(View.GONE);
        skipButton.setVisibility(View.GONE);
        if (zthread == null) {
            zthread = new Thread("ZCPU") {
                @Override public void run() {
                    try {
                        synchronized (screenBuffer) {
                            while (screenWidth == 0) {
                                screenBuffer.wait();
                            }
                        }
                        Log.d(TAG,"ZCPU:screenWidth="+screenWidth+",screenHeight="+screenHeight);
                        zcpu.initialize(story.getFile(Play.this).getPath());
                        zcpu.run();
                    } catch (InterruptedException e) {
                    } catch (ZQuitException e) {
                    }
                    zthread = null;
                    textView.post(new Runnable() {
                        @Override public void run() {
                            finish();
                        }
                    });
                }
            };
            zthread.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        screenBuffer.clear();
        screenWidth = 0;
        screenHeight = 0;
    }

    private final View.OnLayoutChangeListener textMeasurer = new View.OnLayoutChangeListener() {
        private int w = 0, h = 0, w1 = 0, h1 = 0, w2 = 0, h2 = 0;

        @Override public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (screenWidth != 0) {
                return;
            }
            switch (v.getId()) {
            case R.id.text: w = right - left; h = bottom - top; break;
            case R.id.onexone: w1 = right - left; h1 = bottom - top; break;
            case R.id.twoxtwo: w2 = right - left; h2 = bottom - top; break;
            default:
            }
            Log.d(TAG,"onLayoutChange:w,h="+w+","+h+",w1,h1="+w1+","+h1+",w2,h2="+w2+","+h2);
            if (w != 0 && w1 != 0 && w2 != 0) {
                int charw = w2 - w1, charh = h2 - h1;
                int xmargin = w1 - charw, ymargin = h1 - charh;
                synchronized (screenBuffer) {
                    screenWidth = (w - xmargin) / charw;
                    screenHeight = (h - ymargin) / charh;
                    screenBuffer.notify();
                }
            }
        }
    };

    private class ZQuitException extends RuntimeException {}

    private Bundle recognitionResults;
    private boolean recognizerReady = true;
    private boolean usingKeyboard = false;
    private boolean usingKeyboardDone = false;
    private String inputLineResults;
    private char inputCharResults;

    private void recognizeSpeech() throws InterruptedException {
        synchronized (recognitionListener) {
            while (!recognizerReady) {
                recognitionListener.wait();
            }
            for (;;) {
                recognizerReady = false;
                recognitionResults = null;
                textView.post(startRecognizing);
                while (!recognizerReady) {
                    recognitionListener.wait();
                    if (usingKeyboard && usingKeyboardDone) {
                        return;
                    }
                }
                if (recognitionResults != null) {
                    inputLineResults = story.chooseInput(recognitionResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                    inputCharResults = story.chooseCharacterInput(recognitionResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                    if (usingKeyboard) {
                        final String text = inputLineResults;
                        textView.post(new Runnable() {
                            @Override public void run() {
                                editText.getEditableText().append(text);
                            }
                        });
                    } else {
                        return;
                    }
                }
            }
        }
    }

    private final Runnable startRecognizing = new Runnable() {
        @Override
        public void run() {
            Intent intent = RecognizerIntent.getVoiceDetailsIntent(Play.this);
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

    private final View.OnClickListener keyboardButtonOnClickListener = new View.OnClickListener() {
        @Override public void onClick(View v) {
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
            if (inputLineResults.length() > 0) {
                inputCharResults = inputLineResults.charAt(0);
            } else {
                inputCharResults = '\n';
            }
            textView.requestFocus();
            editText.setFocusable(false);
            editText.setVisibility(View.GONE);
            synchronized (recognitionListener) {
                usingKeyboardDone = true;
                recognitionListener.notify();
            }
            return true;
        }
    };

    private boolean speechCanceled = false;
    private boolean utteranceQueued = false;

    private final UtteranceProgressListener utteranceProgressListener = new UtteranceProgressListener() {
        @Override
        public void onDone(String utteranceId) {
            synchronized(this) {
                utteranceQueued = false;
                this.notify();
            }
        }

        @Override
        public void onError(String utteranceId) {
            synchronized(this) {
                utteranceQueued = false;
                this.notify();
            }
        }

        @Override
        public void onStart(String utteranceId) {
        }
    };

    private void say(String s) throws InterruptedException {
        synchronized (utteranceProgressListener) {
            if (speechCanceled) {
                return;
            }
            while (utteranceQueued) {
                utteranceProgressListener.wait();
            }
            utteranceQueued = true;
            HashMap<String,String> params = new HashMap<String,String>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, s);
            textToSpeech.speak(s, TextToSpeech.QUEUE_ADD, params);
            while (utteranceQueued) {
                utteranceProgressListener.wait();
            }
        }
    }

    private final View.OnClickListener skipButtonOnClickListener = new View.OnClickListener() {
        @Override public void onClick(View v) {
            synchronized (utteranceProgressListener) {
                if (!utteranceQueued) {
                    return;
                }
                speechCanceled = true;
                textToSpeech.stop();
            }
            skipButton.setVisibility(View.GONE);
        }
    };

    private final ZUserInterface zui = new ZUserInterface() {
        private int screenSplit = 0;
        private int currentWindow = 0;
        private int[] x = new int[] { 0, 0 };
        private int[] y = new int[] { 0, 0 };
        private int[] ymin = new int[] { 0, 0 };
        private int[] ymax = new int[] { 0, 0 };
        private int scrollCount;
        private StringBuilder blankLine = new StringBuilder();
        private int textStyle = 0;

        private void refreshScreen() {
            for (Object o : screenBuffer.getSpans(0, screenBuffer.length(), Object.class)) {
                if (screenBuffer.getSpanStart(o) >= screenBuffer.getSpanEnd(o)) {
                    screenBuffer.removeSpan(o);
                }
            }
            textView.setText(screenBuffer);
        }

        private final Runnable prepareForInput = new Runnable() {
            @Override
            public void run() {
                skipButton.setVisibility(View.GONE);
                keyboardButton.setVisibility(View.VISIBLE);
                refreshScreen();
            }
        };

        private final Runnable prepareForOutput = new Runnable() {
            @Override
            public void run() {
                keyboardButton.setVisibility(View.GONE);
                synchronized (utteranceProgressListener) {
                    if (!speechCanceled) {
                        skipButton.setVisibility(View.VISIBLE);
                        refreshScreen();
                    }
                }
            }
        };

        private final Runnable inputReceived = new Runnable() {
            @Override
            public void run() {
                keyboardButton.setVisibility(View.GONE);
                skipButton.setVisibility(View.GONE);
                refreshScreen();
            }
        };

        private void print(char c) {
            int windowHeight = ymax[currentWindow] - ymin[currentWindow];
            if (y[currentWindow] >= windowHeight) {
                scroll();
                y[currentWindow] = windowHeight - 1;
            }
            if (c == '\n' || x[currentWindow] >= screenWidth) {
                x[currentWindow] = 0;
                y[currentWindow]++;
            }
            if (y[currentWindow] >= windowHeight) {
                scroll();
                y[currentWindow] = windowHeight - 1;
            }
            if (c != '\n') {
                screenBuffer.replace((y[currentWindow]+ymin[currentWindow])*(screenWidth+1)+x[currentWindow],(y[currentWindow]+ymin[currentWindow])*(screenWidth+1)+x[currentWindow]+1,String.valueOf(c));
                x[currentWindow]++;
            }
        }

        private void print(String s) {
            scrollCount = 0;
            int startX = x[currentWindow];
            int startY = y[currentWindow];
            if (currentWindow != 0) {
                for (int i = 0; i < s.length(); i++) {
                    print(s.charAt(i));
                }
            } else {
                int i = 0;
                while (i < s.length()) {
                    int wordStart = i;
                    while (wordStart < s.length() && s.charAt(wordStart) == ' ') {
                        wordStart++;
                    }
                    int wordEnd = wordStart;
                    while (wordEnd < s.length() && s.charAt(wordEnd) != ' ' && s.charAt(wordEnd) != '\n') {
                        wordEnd++;
                    }
                    if (x[0] + (wordEnd - i) >= screenWidth && x[0] > 0 && wordEnd > wordStart) {
                        print('\n');
                        i = wordStart;
                    } else if (i < wordEnd) {
                        while (i < wordEnd) {
                            print(s.charAt(i));
                            i++;
                        }
                    } else {
                        print(s.charAt(i));
                        i++;
                    }
                }
            }
            startY -= scrollCount;
            if (startY < 0) {
                startY = 0;
                startX = 0;
            }
            int start = startX + (startY+ymin[currentWindow])*(screenWidth+1);
            int end = x[currentWindow] + (y[currentWindow]+ymin[currentWindow])*(screenWidth+1);
            for (Object o : screenBuffer.getSpans(start, end, Object.class)) {
                if (screenBuffer.getSpanStart(o) >= start && screenBuffer.getSpanEnd(o) <= end) {
                    screenBuffer.removeSpan(o);
                }
            }
            if ((textStyle & 1) != 0) {
                screenBuffer.setSpan(new ForegroundColorSpan(backgroundColor), start, end, 0);
                screenBuffer.setSpan(new BackgroundColorSpan(textColor), start, end, 0);
            }
            switch (textStyle & 134) {
            case 2:
                screenBuffer.setSpan(new StyleSpan(Typeface.BOLD), start, end, 0);
                break;
            case 4:
                screenBuffer.setSpan(new StyleSpan(Typeface.ITALIC), start, end, 0);
                break;
            case 6:
                screenBuffer.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end, 0);
                break;
            case 128:
                screenBuffer.setSpan(new TextAppearanceSpan(Play.this, R.style.inputtext), start, end, 0);
            default:
            }
        }

        private void scroll() {
            scrollCount++;
            if (currentWindow == 0) {
                screenBuffer.delete(screenSplit*(screenWidth+1), (screenSplit+1)*(screenWidth+1)).append(blankLine);
            } else if (screenSplit > 0) {
                screenBuffer.insert(screenSplit*(screenWidth+1), blankLine).delete(0, screenWidth+1);
            }
        }

        @Override
        public void fatal(String errmsg) {
            Log.wtf(TAG,"fatal:errmsg="+errmsg);
            throw new ZQuitException();
        }

        @Override
        public void initialize(int ver) {
            Log.d(TAG,"initialize:ver="+ver);
            if (ver <= 3) {
                screenSplit = 1;
                ymin[0] = 1; ymax[0] = screenHeight;
                ymin[1] = 0; ymax[1] = 1;
            } else {
                screenSplit = 0;
                ymin[0] = 0; ymax[0] = screenHeight;
                ymin[1] = 0; ymax[1] = 0;
            }
            currentWindow = 0;
            x[0] = 0; x[1] = 0; y[0] = 0; y[1] = 0;
            blankLine.setLength(0);
            for (int i = 0; i < screenWidth; i++) {
                blankLine.append(' ');
            }
            blankLine.append('\n');
            screenBuffer.clear();
            for (int i = 0; i < screenHeight; i++) {
                screenBuffer.append(blankLine);
            }
            textStyle = 0;
        }

        @Override
        public void setTerminatingCharacters(Vector chars) {
        }

        @Override
        public boolean hasStatusLine() {
            return true;
        }

        @Override
        public boolean hasUpperWindow() {
            return false;
        }

        @Override
        public boolean defaultFontProportional() {
            return false;
        }

        @Override
        public boolean hasColors() {
            return false;
        }

        @Override
        public boolean hasBoldface() {
            return true;
        }

        @Override
        public boolean hasItalic() {
            return true;
        }

        @Override
        public boolean hasFixedWidth() {
            return true;
        }

        @Override
        public boolean hasTimedInput() {
            return false;
        }

        @Override
        public Dimension getScreenCharacters() {
            return new Dimension(screenWidth, screenHeight);
        }

        @Override
        public Dimension getScreenUnits() {
            return new Dimension(screenWidth, screenHeight);
        }

        @Override
        public Dimension getFontSize() {
            return new Dimension(1, 1);
        }

        @Override
        public Dimension getWindowSize(int window) {
            return new Dimension(screenWidth, ymax[currentWindow] - ymin[currentWindow]);
        }

        @Override
        public int getDefaultForeground() {
            return 1;
        }

        @Override
        public int getDefaultBackground() {
            return 0;
        }

        @Override
        public Point getCursorPosition() {
            return new Point(x[currentWindow]+1, y[currentWindow]+1);
        }

        @Override
        public void showStatusBar(String s,int a,int b,boolean flag) {
            Log.d(TAG,"showStatusBar:"+s+","+a+","+b);
            screenBuffer.replace(0, screenWidth, blankLine, 0, screenWidth);
            int w = Math.min(s.length(), screenWidth);
            screenBuffer.replace(0, w, s, 0, w);
            String numbers;
            if (flag) {
                numbers = String.format(" %2d:%02d", a, b);
            } else {
                numbers = String.format(" %d/%d", a, b);
            }
            if (numbers.length() < screenWidth) {
                screenBuffer.replace(screenWidth - numbers.length(), screenWidth, numbers);
            }
            for (Object o : screenBuffer.getSpans(0, screenWidth, Object.class)) {
                screenBuffer.removeSpan(o);
            }
            screenBuffer.setSpan(new ForegroundColorSpan(backgroundColor), 0, screenWidth, 0);
            screenBuffer.setSpan(new BackgroundColorSpan(textColor), 0, screenWidth, 0);
        }

        @Override
        public void splitScreen(int lines) {
            int oldSplit = screenSplit;
            Log.d(TAG,"splitScreen:lines="+lines);
            screenSplit = lines;
            x[1] = 0; y[1] = 0;
            ymin[0] = lines; ymax[0] = screenHeight;
            ymin[1] = 0; ymax[1] = lines;
            y[0] = Math.max(0, Math.min(y[0] + oldSplit - screenSplit, screenHeight - screenSplit));
        }

        @Override
        public void setCurrentWindow(int window) {
            Log.d(TAG,"setCurrentWindow:window="+window);
            currentWindow = window;
        }

        @Override
        public void setCursorPosition(int newx,int newy) {
            Log.d(TAG,"setCursorPosition:x="+newx+",y="+newy);
            int windowHeight = ymax[currentWindow] - ymin[currentWindow];
            if (newx < 32768) {
                newx = newx - 1;
            } else {
                newx = screenWidth + newx - 65536;
            }
            if (newy < 32768) {
                newy = newy - 1;
            } else {
                newy = windowHeight + newy - 65536;
            }
            x[currentWindow] = Math.min(Math.max(0, newx), screenWidth);
            y[currentWindow] = Math.min(Math.max(0, newy), windowHeight);
        }

        @Override
        public void setColor(int fg,int bg) {
        }

        @Override
        public void setTextStyle(int style) {
            Log.d(TAG,"setTextStyle:style="+style);
            // 0: roman
            // 1: reverse
            // 2: bold
            // 4: italic
            // 8: fixed
            textStyle = style;
        }

        @Override
        public void setFont(int font) {
        }

        @Override
        public int readLine(StringBuffer sb,int time) {
            Log.d(TAG,"readLine");
            if (Thread.currentThread().isInterrupted()) {
                throw new ZQuitException();
            }
            textView.post(prepareForInput);
            usingKeyboard = false;
            usingKeyboardDone = false;
            speechCanceled = false;
            try {
                recognizeSpeech();
            } catch (InterruptedException e) {
                throw new ZQuitException();
            }
            Log.d(TAG,"readLine:"+inputLineResults);
            int saveCurrentWindow = currentWindow;
            int saveTextStyle = textStyle;
            currentWindow = 0;
            textStyle = 128;
            print(inputLineResults);
            print('\n');
            textStyle = saveTextStyle;
            currentWindow = saveCurrentWindow;
            textView.post(inputReceived);
            sb.append(inputLineResults);
            return 10;
        }

        @Override
        public int readChar(int time) {
            Log.d(TAG,"readChar");
            if (Thread.currentThread().isInterrupted()) {
                throw new ZQuitException();
            }
            textView.post(prepareForInput);
            usingKeyboard = false;
            usingKeyboardDone = false;
            speechCanceled = false;
            try {
                recognizeSpeech();
            } catch (InterruptedException e) {
                throw new ZQuitException();
            }
            Log.d(TAG,"readChar:"+inputCharResults);
            textView.post(inputReceived);
            return (int) inputCharResults;
        }

        @Override
        public void showString(String s) {
            Log.d(TAG,"showString:s="+s);
            if (Thread.currentThread().isInterrupted()) {
                throw new ZQuitException();
            }
            print(s);
            if (currentWindow == 0) {
                s = story.translateOutput(s);
                if (s == null) {
                    return;
                }
                textView.post(prepareForOutput);
                try {
                    say(s);
                } catch (InterruptedException e) {
                    throw new ZQuitException();
                }
            }
        }

        @Override
        public void scrollWindow(int lines) {
            Log.d(TAG,"scrollWindow:lines="+lines);
            for (int i = 0; i < lines; i++) {
                scroll();
            }
        }

        @Override
        public void eraseLine(int s) {
            Log.d(TAG,"eraseLine:s="+s);
            int start;
            if (currentWindow == 0) {
                start = (s+screenSplit)*(screenWidth+1);
            } else {
                start = s*(screenWidth+1);
            }
            screenBuffer.replace(start, start+screenWidth, blankLine, 0, screenWidth);
            for (Object o : screenBuffer.getSpans(start, start+screenWidth, Object.class)) {
                screenBuffer.removeSpan(o);
            }
        }

        @Override
        public void eraseWindow(int window) {
            Log.d(TAG,"eraseWindow:window="+window);
            int start;
            int end;
            if (window == 0) {
                start = screenSplit*(screenWidth+1);
                end = start;
                for (int i = screenSplit; i < screenHeight; i++) {
                    screenBuffer.replace(i*(screenWidth+1), i*(screenWidth+1)+screenWidth, blankLine, 0, screenWidth);
                    end = i*(screenWidth+1)+screenWidth;
                }
            } else {
                start = 0;
                end = start;
                for (int i = 0; i < screenSplit; i++) {
                    screenBuffer.replace(i*(screenWidth+1), i*(screenWidth+1)+screenWidth, blankLine, 0, screenWidth);
                    end = i*(screenWidth+1)+screenWidth;
                }
            }
            for (Object o : screenBuffer.getSpans(start, end, Object.class)) {
                screenBuffer.removeSpan(o);
            }
        }

        @Override
        public String getFilename(String title,String suggested,boolean saveFlag) {
            return story.getSaveFile(Play.this).getPath();
        }

        @Override
        public void quit() {
            throw new ZQuitException();
        }

        @Override
        public void restart() {
        }
    };
}
