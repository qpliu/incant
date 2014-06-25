package com.yrek.incant.glk;

import android.view.View;

import java.io.Serializable;

import com.yrek.ifstd.glk.GlkEvent;
import com.yrek.ifstd.glk.GlkWindow;

abstract class Window extends GlkWindow implements Serializable {
    transient View view = null;
    WindowPair parent;

    Window(int rock) {
        super(rock);
    }

    abstract View createView();
    abstract boolean hasPendingEvent();
    abstract GlkEvent getEvent(long timeout, boolean polling) throws InterruptedException;

    // Run in IO thread.  Recursively descend window tree.
    // Return whether there is pending asynchronous output.
    // If there is asynchronous output pending, post continueOutput
    // to the IO thread once the asynchronous output has completed.
    // Examples of asynchronous output:
    // - text to speech
    // - waiting for the user to hit the next button (when output
    //   will scroll out of view (if text to speech is being skipped))
    abstract boolean updatePendingOutput(Runnable continueOutput, boolean doSpeech);

    View getView() {
        if (view == null) {
            view = createView();
        }
        return view;
    }

    static Window open(Window split, int method, int size, int winType, int rock) {
        Window newWindow = null;
        switch (winType) {
        case GlkWindow.TypeBlank:
            throw new RuntimeException("unimplemented");
        case GlkWindow.TypeTextBuffer:
            newWindow = new WindowTextBuffer(rock);
            break;
        case GlkWindow.TypeTextGrid:
            throw new RuntimeException("unimplemented");
        case GlkWindow.TypeGraphics:
            throw new RuntimeException("unimplemented");
        default:
            return null;
        }
        if (split != null) {
            WindowPair oldParent = split.parent;
            WindowPair newParent = new WindowPair(method, size, split, newWindow);
            if (oldParent != null) {
                oldParent.replaceChild(split, newParent);
            }
        }
        return newWindow;
    }

    @Override
    public GlkWindow getParent() {
        return parent;
    }

    @Override
    public GlkWindow getSibling() {
        if (parent == null) {
            return null;
        }
        if (parent.child1 == this) {
            return parent.child2;
        } else {
            assert parent.child2 == this;
            return parent.child1;
        }
    }
}
