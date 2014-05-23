package com.yrek.incant;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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
    private TextView statusView;
    private TextView mainView;
    private Thread zthread;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private int screenWidth = 40;
    private int screenHeight = 25;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.play);
        story = (Story) getIntent().getSerializableExtra(STORY);
        ((TextView) findViewById(R.id.name)).setText(story.getName());
        statusView = (TextView) findViewById(R.id.status);
        mainView = (TextView) findViewById(R.id.main);
    }

    @Override
    protected void onStart() {
        super.onStart();
        zcpu = new ZCPU(zui);
        zcpu.initialize(story.getFile(this).getPath());
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
                        zcpu.run();
                    } catch (ZQuitException e) {
                    }
                    zthread = null;
                    statusView.post(new Runnable() {
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
    }

    private class ZQuitException extends RuntimeException {}

    private class Window {
        private char[][] buffer;
        private int x;
        private int y;
        private TextView textView;

        Window(int width, int height, TextView textView) {
            this.textView = textView;
            buffer = new char[height][width];
            reset();
        }

        void updateView() {
            StringBuffer sb = new StringBuffer();
            for (char[] line : buffer) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            final String text = sb.toString();
            textView.post(new Runnable() {
                @Override public void run() {
                    textView.setVisibility(View.VISIBLE);
                    textView.setText(text);
                }
            });
        }

        void scroll(int n) {
            for (int y = n; y < buffer.length; y++) {
                for (int x = 0; x < buffer[y].length; x++) {
                    buffer[y-n][x] = buffer[y][x];
                }
            }
            for (int y = buffer.length - n; y < buffer.length; y++) {
                Arrays.fill(buffer[y], ' ');
            }
        }

        void show(String s) {
            for (int i = 0; i < s.length(); i++) {
                show(s.charAt(i));
            }
        }

        void show(char c) {
            if (y >= buffer.length) {
                scroll(1);
                y = buffer.length - 1;
            }
            if (c == '\n' || x >= buffer[y].length) {
                x = 0;
                y++;
            }
            if (y >= buffer.length) {
                scroll(1);
                y = buffer.length - 1;
            }
            if (c != '\n') {
                buffer[y][x] = c;
                x++;
            }
        }

        Dimension getSize() {
            return new Dimension(buffer[0].length, buffer.length);
        }

        void hide() {
            textView.post(new Runnable() {
                @Override public void run() {
                    textView.setVisibility(View.GONE);
                }
            });
        }

        void reset() {
            x = 0;
            y = 0;
            for (char[] line : buffer) {
                Arrays.fill(line, ' ');
            }
        }
    }

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
                statusView.post(startRecognizing);
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
            Log.d(TAG,"onReadyForSpeech");
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
        private ArrayList<Window> windows = new ArrayList<Window>();
        private int currentWindow = 0;

        @Override
        public void fatal(String errmsg) {
            Log.wtf(TAG,"fatal:errmsg="+errmsg);
            throw new ZQuitException();
        }

        @Override
        public void initialize(int ver) {
            Log.d(TAG,"initialize:ver="+ver);
            windows.add(new Window(screenWidth, screenHeight, mainView));
            if (ver <= 3) {
                windows.add(new Window(screenWidth, 1, statusView));
            }
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
            return false;
        }

        @Override
        public boolean hasItalic() {
            return false;
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
            return windows.get(window).getSize();
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
            Window w = windows.get(currentWindow);
            return new Point(w.x, w.y);
        }

        @Override
        public void showStatusBar(String s,int a,int b,boolean flag) {
            Log.d(TAG,"showStatusBar:"+s+","+a+","+b);
            Window statusBar = windows.get(1);
            statusBar.reset();
            int w = statusBar.buffer[0].length;
            if (s.length() > w) {
                s = s.substring(0, w);
            }
            statusBar.show(s);
            String numbers;
            if (flag) {
                numbers = String.format(" %2d:%02d", a, b);
            } else {
                numbers = String.format(" %d/%d", a, b);
            }
            if (numbers.length() < w) {
                statusBar.x = w - numbers.length();
                statusBar.show(numbers);
            }
        }

        @Override
        public void splitScreen(int lines) {
            Log.d(TAG,"splitScreen:lines="+lines);
            if (windows.size() < 2) {
                windows.add(new Window(screenWidth, lines, statusView));
            } else {
                windows.set(1,new Window(screenWidth, lines, statusView));
            }
        }
    
        @Override
        public void setCurrentWindow(int window) {
            Log.d(TAG,"setCurrentWindow:window="+window);
            currentWindow = window;
        }

        @Override
        public void setCursorPosition(int x,int y) {
            Log.d(TAG,"setCursorPosition:x="+x+",y="+y);
            Window w = windows.get(currentWindow);
            if (x < 32768) {
                w.x = x - 1;
            } else {
                w.x = w.buffer[0].length + x - 65536;
            }
            w.x = Math.min(Math.max(0, w.x), w.buffer[0].length);
            w.y = Math.min(Math.max(0, y - 1), w.buffer.length);
        }

        @Override
        public void setColor(int fg,int bg) {
        }

        @Override
        public void setTextStyle(int style) {
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
            for (Window w : windows) {
                w.updateView();
            }
            try {
                recognizeSpeech();
            } catch (InterruptedException e) {
                throw new ZQuitException();
            }
            String r = story.chooseInput(recognitionResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
            Log.d(TAG,"readLine:"+r);
            windows.get(0).show(r);
            windows.get(0).show('\n');
            sb.append(r);
            return 10;
        }

        @Override
        public int readChar(int time) {
            Log.d(TAG,"readChar");
            if (Thread.currentThread().isInterrupted()) {
                throw new ZQuitException();
            }
            for (Window w : windows) {
                w.updateView();
            }
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
            windows.get(currentWindow).show(s);
            if (currentWindow == 0) {
                s = story.translateOutput(s);
                if (s == null) {
                    return;
                }
                for (Window w : windows) {
                    w.updateView();
                }
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
            windows.get(currentWindow).scroll(lines);
        }

        @Override
        public void eraseLine(int s) {
            Log.d(TAG,"eraseLine:s="+s);
            Window w = windows.get(currentWindow);
            Arrays.fill(w.buffer[s], ' ');
        }

        @Override
        public void eraseWindow(int window) {
            Log.d(TAG,"eraseWindow:window="+window);
            if (window == 0) {
                windows.get(0).reset();
            } else if (windows.size() > window) {
                windows.get(window).hide();
                windows.remove(window);
            }
        }

        @Override
        public String getFilename(String title,String suggested,boolean saveFlag) {
            return story.getFile(Play.this, "save").getPath();
        }

        @Override
        public void quit() {
            throw new ZQuitException();
        }

        @Override
        public void restart() {
            for (Window w : windows) {
                w.reset();
            }
            statusView.post(new Runnable() {
                @Override public void run() {
                    statusView.setText("");
                    mainView.setText("");
                }
            });
        }
    };
}
