package com.yrek.incant;

import android.app.Activity;
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
import android.util.Log;
import android.view.View;
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
    static final String STORY = "STORY";

    private Story story;
    private ZCPU zcpu;
    private TextView textView;
    private Thread zthread;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private SpannableStringBuilder screenBuffer;
    private int screenWidth;
    private int screenHeight;
    private int inputtextColor;
    private int textColor;
    private int backgroundColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.play);
        story = (Story) getIntent().getSerializableExtra(STORY);
        ((TextView) findViewById(R.id.name)).setText(story.getName());
        textView = (TextView) findViewById(R.id.text);
        screenBuffer = new SpannableStringBuilder();
        screenWidth = 0;
        screenHeight = 0;
        textView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                synchronized (screenBuffer) {
                    screenWidth = (right - left) / 18; //... figure this out
                    screenHeight = (bottom - top) / textView.getLineHeight();
                    screenBuffer.notify();
                }
            }
        });
        inputtextColor = getResources().getColor(R.color.inputtext);
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
                Log.d(TAG,"TextToSpeech.onInit:status="+status);
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

    private class ZQuitException extends RuntimeException {}

    private Bundle recognitionResults;
    private boolean recognizerReady = true;

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
                }
                if (recognitionResults != null) {
                    return;
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

        private final Runnable refreshScreen = new Runnable() {
            @Override
            public void run() {
                for (Object o : screenBuffer.getSpans(0, screenBuffer.length(), Object.class)) {
                    if (screenBuffer.getSpanStart(o) >= screenBuffer.getSpanEnd(o)) {
                        screenBuffer.removeSpan(o);
                    }
                }
                textView.setText(screenBuffer);
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
            for (int i = 0; i < s.length(); i++) {
                print(s.charAt(i));
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
                screenBuffer.setSpan(new ForegroundColorSpan(inputtextColor), start, end, 0);
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
            for (int i = 0; i < lines; i++) {
                screenBuffer.replace(i*(screenWidth+1), i*(screenWidth+1)+screenWidth, blankLine, 0, screenWidth);
            }
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
            textView.post(refreshScreen);
            try {
                recognizeSpeech();
            } catch (InterruptedException e) {
                throw new ZQuitException();
            }
            String r = story.chooseInput(recognitionResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
            Log.d(TAG,"readLine:"+r);
            int saveCurrentWindow = currentWindow;
            int saveTextStyle = textStyle;
            currentWindow = 0;
            textStyle = 128;
            print(r);
            print('\n');
            textStyle = saveTextStyle;
            currentWindow = saveCurrentWindow;
            sb.append(r);
            return 10;
        }

        @Override
        public int readChar(int time) {
            Log.d(TAG,"readChar");
            if (Thread.currentThread().isInterrupted()) {
                throw new ZQuitException();
            }
            textView.post(refreshScreen);
            try {
                recognizeSpeech();
            } catch (InterruptedException e) {
                throw new ZQuitException();
            }
            char c = story.chooseCharacterInput(recognitionResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
            Log.d(TAG,"readChar:"+c);
            return  (int) c;
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
                textView.post(refreshScreen);
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
            if (currentWindow == 0) {
                screenBuffer.replace((s+screenSplit)*(screenWidth+1), (s+screenSplit)*(screenWidth+1)+screenWidth, blankLine, 0, screenWidth);
            } else {
                screenBuffer.replace(s*(screenWidth+1), s*(screenWidth+1)+screenWidth, blankLine, 0, screenWidth);
            }
        }

        @Override
        public void eraseWindow(int window) {
            Log.d(TAG,"eraseWindow:window="+window);
            if (window == 0) {
                for (int i = screenSplit; i < screenHeight; i++) {
                    screenBuffer.replace(i*(screenWidth+1), i*(screenWidth+1)+screenWidth, blankLine, 0, screenWidth);
                }
            } else {
                for (int i = 0; i < screenSplit; i++) {
                    screenBuffer.replace(i*(screenWidth+1), i*(screenWidth+1)+screenWidth, blankLine, 0, screenWidth);
                }
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
