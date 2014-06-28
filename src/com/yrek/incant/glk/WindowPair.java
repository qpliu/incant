package com.yrek.incant.glk;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.io.IOException;

import com.yrek.ifstd.glk.GlkEvent;
import com.yrek.ifstd.glk.GlkStreamResult;
import com.yrek.ifstd.glk.GlkWindow;
import com.yrek.ifstd.glk.GlkWindowArrangement;
import com.yrek.ifstd.glk.GlkWindowSize;

class WindowPair extends Window {
    private static final long serialVersionUID = 0L;
    private static final String TAG = WindowPair.class.getSimpleName();
    int method;
    int size;
    Window child1;
    Window child2;
    Window key;
    private transient boolean updateView;

    WindowPair(int method, int size, Window child1, Window child2) {
        super(0);
        this.method = method;
        this.size = size;
        this.child1 = child1;
        this.child2 = child2;
        this.key = child2;
        updateView = true;
    }

    @Override
    View createView(Context context) {
        return new LinearLayout(context);
    }

    @Override
    void initActivity(GlkActivity activity) {
        super.initActivity(activity);
        child1.initActivity(activity);
        child2.initActivity(activity);
    }

    @Override
    boolean hasPendingEvent() {
        return child1.hasPendingEvent() || child2.hasPendingEvent();
    }

    @Override
    synchronized GlkEvent getEvent(long timeout, boolean polling) throws InterruptedException {
        if (child1.hasPendingEvent()) {
            return child1.getEvent(timeout, polling);
        } else if (child2.hasPendingEvent()) {
            return child2.getEvent(timeout, polling);
        } else {
            assert polling;
            return null;
        }
    }

    @Override
    synchronized boolean updatePendingOutput(Runnable continueOutput, boolean doSpeech) {
        if (updateView) {
            updateView = false;
            LinearLayout layout = (LinearLayout) getView();
            layout.removeViews(0, layout.getChildCount());
            switch (method & GlkWindowArrangement.MethodDirMask) {
            case GlkWindowArrangement.MethodLeft:
                Log.d(TAG,"pair:MethodLeft");
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.addView(child2.getView());
                layout.addView(child1.getView());
                break;
            case GlkWindowArrangement.MethodRight:
                Log.d(TAG,"pair:MethodRight");
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.addView(child1.getView());
                layout.addView(child2.getView());
                break;
            case GlkWindowArrangement.MethodAbove:
                Log.d(TAG,"pair:MethodAbove");
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.addView(child2.getView());
                layout.addView(child1.getView());
                break;
            case GlkWindowArrangement.MethodBelow:
                Log.d(TAG,"pair:MethodBelow");
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.addView(child1.getView());
                layout.addView(child2.getView());
                break;
            default:
                throw new AssertionError();
            }
            layout.setWeightSum(100f);
            switch (method & GlkWindowArrangement.MethodDivisionMask) {
            case GlkWindowArrangement.MethodProportional:
                Log.d(TAG,"pair:MethodProportional:size="+size);
                child2.getView().setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, (float) size));
                child1.getView().setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 100f - (float) size));
                break;
            case GlkWindowArrangement.MethodFixed:
                switch (layout.getOrientation()) {
                case LinearLayout.HORIZONTAL:
                    Log.d(TAG,"pair:MethodFixed,hor:size="+size+","+child2.getPixelWidth(size));
                    child2.getView().setLayoutParams(new LinearLayout.LayoutParams(child2.getPixelWidth(size), ViewGroup.LayoutParams.MATCH_PARENT, 0f));
                    child1.getView().setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 100f));
                    break;
                case LinearLayout.VERTICAL:
                    Log.d(TAG,"pair:MethodFixed,ver:size="+size+","+child2.getPixelHeight(size));
                    child2.getView().setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, child2.getPixelHeight(size), 0f));
                    child1.getView().setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 100f));
                    break;
                default:
                    throw new AssertionError();
                }
                break;
            default:
                throw new AssertionError();
            }
            switch (method & GlkWindowArrangement.MethodBorderMask) {
            case GlkWindowArrangement.MethodBorder:
                layout.setShowDividers(LinearLayout.SHOW_DIVIDER_BEGINNING | LinearLayout.SHOW_DIVIDER_MIDDLE | LinearLayout.SHOW_DIVIDER_END);
                break;
            case GlkWindowArrangement.MethodNoBorder:
                layout.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);
                break;
            default:
                throw new AssertionError();
            }
            post(continueOutput);
            return true;
        }
        return child1.updatePendingOutput(continueOutput, doSpeech) || child2.updatePendingOutput(continueOutput, doSpeech);
    }

    synchronized void replaceChild(Window oldChild, Window newChild) {
        if (oldChild == child1) {
            child1 = newChild;
        } else if (oldChild == child2) {
            child2 = newChild;
        } else {
            throw new IllegalArgumentException();
        }
        newChild.parent = this;
        updateView = true;
    }

    @Override
    public GlkStreamResult close() throws IOException {
        super.close();
        child1.close();
        child2.close();
        return new GlkStreamResult(0, 0);
    }

    @Override
    public GlkWindowSize getSize() {
        switch (method & GlkWindowArrangement.MethodDirMask) {
        case GlkWindowArrangement.MethodLeft:
        case GlkWindowArrangement.MethodRight:
            return new GlkWindowSize(2, 1);
        case GlkWindowArrangement.MethodAbove:
        case GlkWindowArrangement.MethodBelow:
            return new GlkWindowSize(1, 2);
        default:
            throw new AssertionError();
        }
    }

    @Override
    public synchronized void setArrangement(int method, int size, GlkWindow key) {
        this.method = method;
        this.size = size;
        this.key = (Window) key;
        updateView = true;
    }

    @Override
    public GlkWindowArrangement getArrangement() {
        return new GlkWindowArrangement(method, size, key);
    }

    @Override
    public int getType() {
        return GlkWindow.TypePair;
    }
}
