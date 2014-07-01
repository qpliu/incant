package com.yrek.incant;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Vector;

import com.yrek.ifstd.blorb.Blorb;
import com.yrek.ifstd.glk.GlkByteArray;
import com.yrek.ifstd.glk.GlkDispatch;
import com.yrek.ifstd.glk.GlkEvent;
import com.yrek.ifstd.glk.GlkStream;
import com.yrek.ifstd.glk.GlkWindow;
import com.yrek.ifstd.glk.GlkWindowArrangement;
import com.yrek.ifstd.glk.GlkWindowSize;
import com.yrek.ifstd.glulx.Glulx;
import com.yrek.incant.glk.GlkMain;

import com.zaxsoft.zmachine.Dimension;
import com.zaxsoft.zmachine.Point;
import com.zaxsoft.zmachine.ZCPU;
import com.zaxsoft.zmachine.ZUserInterface;

class ZCodeStory implements GlkMain {
    private static final long serialVersionUID = 0L;
    private static final String TAG = ZCodeStory.class.getSimpleName();
    final Story story;
    final String name;
    int textForegroundColor;
    int textBackgroundColor;
    int textInputColor;
    transient Thread thread = null;
    transient ZCPU zcpu = null;
    transient GlkDispatch glk = null;
    transient File zcodeFile = null;
    transient File saveFile = null;
    transient boolean suspendRequested = false;

    ZCodeStory(Story story, String name) {
        this.story = story;
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void init(Context context, GlkDispatch glk, Serializable suspendState) {
        this.glk = glk;
        zcodeFile = story.getZcodeFile(context);
        saveFile = story.getSaveFile(context);
        textForegroundColor = context.getResources().getColor(R.color.text);
        textBackgroundColor = context.getResources().getColor(R.color.background);
        textInputColor = context.getResources().getColor(R.color.input_text);
        //... restore from suspendState unimplemented
        zcpu = new ZCPU(new ZUI());
    }

    @Override
    public void start(final Runnable waitForInit) {
        thread = new Thread("glk") {
            @Override public void run() {
                try {
                    waitForInit.run();
                    zcpu.initialize(zcodeFile.getPath());
                    glk.glk.main(zcpu);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (ZQuitException e) {
                }
                thread = null;
            }
        };
        thread.start();
    }

    @Override
    public void requestSuspend() {
        suspendRequested = true;
        //... restore from suspendState unimplemented
    }

    @Override
    public Serializable suspend() {
        if (thread != null) {
            thread.interrupt();
        }
        //... restore from suspendState unimplemented
        return null;
    }

    @Override
    public boolean finished() {
        return thread == null;
    }

    @Override
    public Blorb getBlorb(Context context) {
        File blorbFile = story.getBlorbFile(context);
        if (blorbFile.exists()) {
            try {
                return Blorb.from(blorbFile);
            } catch (IOException e) {
                Log.wtf(TAG,e);
            }
        }
        return null;
    }

    @Override
    public File getSaveFile(Context context) {
        return story.getSaveFile(context);
    }

    @Override
    public File getDir(Context context) {
        return story.getDir(context);
    }


    @Override
    public int getGlkLayout() {
        return R.layout.glk;
    }

    @Override
    public int getFrameLayout() {
        return R.id.frame;
    }

    @Override
    public int getNextButton() {
        return R.id.next;
    }

    @Override
    public int getKeyboardButton() {
        return R.id.keyboard;
    }

    @Override
    public int getEditText() {
        return R.id.edit;
    }

    @Override
    public int getSkipButton() {
        return R.id.skip;
    }

    @Override
    public int getOneByOneMeasurer() {
        return R.id.onexone;
    }

    @Override
    public int getTwoByTwoMeasurer() {
        return R.id.twoxtwo;
    }

    @Override
    public int getProgressBar() {
        return R.id.progress_bar;
    }


    @Override
    public int getTextBufferStyle(int style) {
        switch (style) {
        case GlkStream.StyleEmphasized:
            return R.style.zcode_italic;
        case GlkStream.StylePreformatted:
            return R.style.zcode_preformatted;
        case GlkStream.StyleHeader:
            return R.style.zcode_bold;
        case GlkStream.StyleSubheader:
            return R.style.zcode_bolditalic;
        case GlkStream.StyleAlert:
            return R.style.zcode_normal;
        case GlkStream.StyleNote:
            return R.style.zcode_preformatted;
        case GlkStream.StyleBlockQuote:
            return R.style.zcode_italic;
        case GlkStream.StyleUser1:
            return R.style.zcode_bold;
        case GlkStream.StyleUser2:
            return R.style.zcode_bolditalic;
        case GlkStream.StyleInput:
            return R.style.glk_input;
        default:
            return R.style.glk_normal;
        }
    }

    @Override
    public int getTextGridStyle(int style) {
        switch (style) {
        case GlkStream.StyleEmphasized:
            return R.style.zcode_grid_italic;
        case GlkStream.StylePreformatted:
            return R.style.zcode_grid_normal;
        case GlkStream.StyleHeader:
            return R.style.zcode_grid_bold;
        case GlkStream.StyleSubheader:
            return R.style.zcode_grid_bolditalic;
        case GlkStream.StyleAlert:
        case GlkStream.StyleNote:
            return R.style.zcode_grid_normal;
        case GlkStream.StyleBlockQuote:
            return R.style.zcode_grid_italic;
        case GlkStream.StyleUser1:
            return R.style.zcode_grid_bold;
        case GlkStream.StyleUser2:
            return R.style.zcode_grid_bolditalic;
        default:
            return R.style.glk_grid_normal;
        }
    }

    @Override
    public Integer getStyleForegroundColor(int style) {
        switch (style) {
        case GlkStream.StyleAlert:
        case GlkStream.StyleNote:
        case GlkStream.StyleBlockQuote:
        case GlkStream.StyleUser1:
        case GlkStream.StyleUser2:
            return textBackgroundColor;
        case GlkStream.StyleInput:
            return textInputColor;
        default:
            return textForegroundColor;
        }
    }

    @Override
    public Integer getStyleBackgroundColor(int style) {
        switch (style) {
        case GlkStream.StyleAlert:
        case GlkStream.StyleNote:
        case GlkStream.StyleBlockQuote:
        case GlkStream.StyleUser1:
        case GlkStream.StyleUser2:
            return textForegroundColor;
        default:
            return textBackgroundColor;
        }
    }

    private class ZQuitException extends RuntimeException {}

    private class ZUI implements ZUserInterface {
        private GlkWindow textBuffer;
        private GlkWindow statusWindow;
        private int zversion;
        private GlkWindow currentWindow;

        @Override
        public void fatal(String errmsg) {
            Log.wtf(TAG,"fatal:errmsg="+errmsg);
            throw new ZQuitException();
        }

        @Override
        public void initialize(int ver) {
            Log.d(TAG,"initialize:ver="+ver);
            zversion = ver;
            textBuffer = glk.glk.windowOpen(null, 0, 0, GlkWindow.TypeTextBuffer, 0);
            currentWindow = textBuffer;
            statusWindow = glk.glk.windowOpen(textBuffer, GlkWindowArrangement.MethodAbove | GlkWindowArrangement.MethodFixed, 0, GlkWindow.TypeTextGrid, 0);
            if (ver <= 3) {
                statusWindow.getParent().setArrangement(GlkWindowArrangement.MethodAbove | GlkWindowArrangement.MethodFixed, 1, statusWindow);
                statusWindow.getStream().setStyle(GlkStream.StyleAlert);
            }
            glk.glk.requestTimerEvents(1);
            try {
                glk.glk.select();
            } catch (IOException e) {
                Log.wtf(TAG,e);
                throw new RuntimeException(e);
            }
            glk.glk.requestTimerEvents(0);
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
            return true;
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
            GlkWindowSize size = textBuffer.getSize();
            GlkWindowSize statusSize = statusWindow.getSize();
            return new Dimension(size.width, size.height + statusSize.height);
        }

        @Override
        public Dimension getScreenUnits() {
            GlkWindowSize size = textBuffer.getSize();
            GlkWindowSize statusSize = statusWindow.getSize();
            return new Dimension(size.width, size.height + statusSize.height);
        }

        @Override
        public Dimension getFontSize() {
            return new Dimension(1, 1);
        }

        @Override
        public Dimension getWindowSize(int window) {
            GlkWindowSize size;
            if (window == 0) {
                size = textBuffer.getSize();
            } else {
                size = statusWindow.getSize();
            }
            return new Dimension(size.width, size.height);
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
            return new Point(currentWindow.getCursorX()+1, currentWindow.getCursorY()+1);
        }

        @Override
        public void showStatusBar(String s,int a,int b,boolean flag) {
            Log.d(TAG,"showStatusBar:"+s+","+a+","+b);
            GlkWindowSize size = statusWindow.getSize();
            StringBuilder sb = new StringBuilder();
            sb.append(s);
            String numbers;
            if (flag) {
                numbers = String.format(" %2d:%02d", a, b);
            } else {
                numbers = String.format(" %d/%d", a, b);
            }
            while (sb.length() + numbers.length() < size.width) {
                sb.append(' ');
            }
            if (numbers.length() > size.width) {
                sb.setLength(size.width);
            } else if (sb.length() > size.width - numbers.length()) {
                sb.setLength(size.width - numbers.length());
                sb.append(numbers);
            } else {
                sb.append(numbers);
            }
            try {
                statusWindow.clear();
                statusWindow.getStream().setStyle(GlkStream.StyleAlert);
                statusWindow.getStream().putString(sb);
            } catch (IOException e) {
                Log.wtf(TAG,e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void splitScreen(int lines) {
            textBuffer.getParent().setArrangement(GlkWindowArrangement.MethodAbove | GlkWindowArrangement.MethodFixed, lines, statusWindow);
        }

        @Override
        public void setCurrentWindow(int window) {
            if (window == 0) {
                currentWindow = textBuffer;
            } else {
                currentWindow = statusWindow;
            }
        }

        @Override
        public void setCursorPosition(int newx,int newy) {
            Log.d(TAG,"setCursorPosition:x="+newx+",y="+newy);
            GlkWindowSize size = currentWindow.getSize();
            if (newx < 32768) {
                newx = newx - 1;
            } else {
                newx = size.width + newx - 65536;
            }
            if (newy < 32768) {
                newy = newy - 1;
            } else {
                newy = size.height + newy - 65536;
            }
            try {
                currentWindow.moveCursor(newx, newy);
            } catch (IOException e) {
                Log.wtf(TAG,e);
                throw new RuntimeException(e);
            }
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
            switch (style) {
            case 0: style = GlkStream.StyleNormal; break;
            case 1: style = GlkStream.StyleAlert; break;
            case 2: style = GlkStream.StyleHeader; break;
            case 3: style = GlkStream.StyleUser1; break;
            case 4: style = GlkStream.StyleEmphasized; break;
            case 5: style = GlkStream.StyleBlockQuote; break;
            case 6: style = GlkStream.StyleSubheader; break;
            case 7: style = GlkStream.StyleUser2; break;
            case 8: case 10: case 12: case 14:
                style = GlkStream.StylePreformatted;
                break;
            case 9: case 11: case 13: case 15:
                style = GlkStream.StyleNote;
                break;
            default:
                style = GlkStream.StyleNormal;
                break;
            }
            textBuffer.getStream().setStyle(style);
            statusWindow.getStream().setStyle(style);
        }

        @Override
        public void setFont(int font) {
        }

        private final char[] lineEventBuffer = new char[256];
        private final GlkByteArray lineEventByteArray = new GlkByteArray() {
            @Override
            public int getByteElement() {
                return lineEventBuffer[0];
            }

            @Override
            public void setByteElement(int element) {
                lineEventBuffer[0] = (char) (element&255);
            }

            @Override
            public int getByteElementAt(int index) {
                return lineEventBuffer[index]&255;
            }

            @Override
            public void setByteElementAt(int index, int element) {
                lineEventBuffer[index] = (char) (element&255);
            }

            @Override
            public int getReadArrayIndex() {
                return 0;
            }

            @Override
            public int setReadArrayIndex(int index) {
                return 0;
            }

            @Override
            public int getWriteArrayIndex() {
                return 0;
            }

            @Override
            public int setWriteArrayIndex(int index) {
                return 0;
            }

            @Override
            public int getArrayLength() {
                return lineEventBuffer.length;
            }

            @Override
            public void setArrayLength(int length) {
            }
        };

        @Override
        public int readLine(StringBuffer sb,int time) {
            Log.d(TAG,"readLine");
            textBuffer.requestLineEvent(lineEventByteArray, lineEventByteArray.getArrayLength());
            try {
                for (;;) {
                    GlkEvent event = glk.glk.select();
                    if (suspendRequested) {
                        throw new ZQuitException();
                    }
                    if (event.type == GlkEvent.TypeLineInput) {
                        sb.append(lineEventBuffer, 0, event.val1);
                        return 10;
                    }
                }
            } catch (IOException e) {
                Log.wtf(TAG,e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public int readChar(int time) {
            Log.d(TAG,"readChar");
            textBuffer.requestCharEvent();
            try {
                for (;;) {
                    GlkEvent event = glk.glk.select();
                    if (suspendRequested) {
                        throw new ZQuitException();
                    }
                    if (event.type == GlkEvent.TypeCharInput) {
                        return event.val1;
                    }
                }
            } catch (IOException e) {
                Log.wtf(TAG,e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void showString(String s) {
            Log.d(TAG,"showString:s="+s);
            try {
                currentWindow.getStream().putString(s);
            } catch (IOException e) {
                Log.wtf(TAG,e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void scrollWindow(int lines) {
            Log.d(TAG,"scrollWindow:lines="+lines);
            try {
                for (int i = 0; i < lines; i++) {
                    currentWindow.getStream().putChar(10);
                }
            } catch (IOException e) {
                Log.wtf(TAG,e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void eraseLine(int s) {
            Log.d(TAG,"eraseLine:s="+s);
            try {
                GlkWindowSize size = currentWindow.getSize();
                currentWindow.moveCursor(0, s-1);
                for (int i = 0; i < size.width; i++) {
                    currentWindow.getStream().putChar(' ');
                }
                currentWindow.moveCursor(0, s-1);
            } catch (IOException e) {
                Log.wtf(TAG,e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void eraseWindow(int window) {
            Log.d(TAG,"eraseWindow:window="+window);
            try {
                currentWindow.clear();
            } catch (IOException e) {
                Log.wtf(TAG,e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getFilename(String title,String suggested,boolean saveFlag) {
            return saveFile.getPath();
        }

        @Override
        public void quit() {
            glk.glk.exit();
        }

        @Override
        public void restart() {
        }
    }
}
