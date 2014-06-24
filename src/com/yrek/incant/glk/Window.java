package com.yrek.incant.glk;

import java.io.Serializable;

import com.yrek.ifstd.glk.GlkWindow;

abstract class Window extends GlkWindow implements Serializable {
    Window(int rock) {
        super(null, rock);
    }
}
