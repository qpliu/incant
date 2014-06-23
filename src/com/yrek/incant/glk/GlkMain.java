package com.yrek.incant.glk;

import java.io.Serializable;

import com.yrek.ifstd.glk.GlkDispatch;

public interface GlkMain extends Serializable {
    public String name();
    public void init(GlkDispatch glk, Serializable suspendState);
    public void start();
    public void requestSuspend();
    public Serializable suspend();
}
