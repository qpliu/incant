package com.yrek.incant.glk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import com.yrek.ifstd.blorb.Blorb;
import com.yrek.ifstd.glk.GlkByteArray;
import com.yrek.ifstd.glk.GlkIntArray;
import com.yrek.ifstd.glk.GlkStream;
import com.yrek.ifstd.glk.GlkStreamResult;
import com.yrek.ifstd.glk.UnicodeString;

class StreamResource extends Stream {
    private static final long serialVersionUID = 0L;
    final byte[] contents;
    final boolean unicode;
    int readCount = 0;
    int index = 0;

    StreamResource(byte[] contents, boolean unicode, int rock) {
        super(rock);
        this.contents = contents;
        this.unicode = unicode;
    }

    static StreamResource open(int resourceId, Blorb blorb, boolean unicode, int rock) throws IOException {
        if (blorb == null) {
            return null;
        }
        Blorb.Resource resource = null;
        for (Blorb.Resource res : blorb.resources()) {
            if (res.getNumber() == resourceId) {
                resource = res;
                break;
            }
        }
        if (resource == null || resource.getChunk() == null) {
            return null;
        }
        byte[] contents;
        if (resource.getChunk().getId() != Blorb.FORM) {
            contents = resource.getChunk().getContents();
        } else {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(resource.getChunk().getId());
            out.writeInt(resource.getChunk().getLength());
            resource.getChunk().write(out);
            contents = bytes.toByteArray();
        }
        return new StreamResource(contents, unicode, rock);
    }
    
    @Override
    public GlkStreamResult close() throws IOException {
        destroy();
        return new GlkStreamResult(readCount, 0);
    }

    @Override
    public void putChar(int ch) throws IOException {
    }

    @Override
    public void putString(CharSequence string) throws IOException {
    }

    @Override
    public void putBuffer(GlkByteArray buffer) throws IOException {
    }

    @Override
    public void putCharUni(int ch) throws IOException {
    }

    @Override
    public void putStringUni(UnicodeString string) throws IOException {
    }

    @Override
    public void putBufferUni(GlkIntArray buffer) throws IOException {
    }

    @Override
    public void setStyle(int style) {
    }

    @Override
    public void setHyperlink(int linkVal) {
    }

    @Override
    public int getChar() throws IOException {
        if (unicode) {
            if (index + 3 >= contents.length) {
                return -1;
            }
            readCount++;
            index += 4;
            return contents[index-1]&255;
        } else {
            if (index >= contents.length) {
                return -1;
            }
            readCount++;
            index++;
            return contents[index-1]&255;
        }
    }

    @Override
    public int getLine(GlkByteArray buffer) throws IOException {
        for (int i = 0; i < buffer.getArrayLength() - 1; i++) {
            int ch = getChar();
            if (ch == -1 || ch == 10) {
                buffer.setByteElementAt(i, 0);
                return i;
            }
            buffer.setByteElementAt(i, ch);
        }
        int i = buffer.getArrayLength() - 1;
        buffer.setByteElementAt(i, 0);
        return i;
    }

    @Override
    public int getBuffer(GlkByteArray buffer) throws IOException {
        for (int i = 0; i < buffer.getArrayLength() - 1; i++) {
            int ch = getChar();
            if (ch == -1) {
                buffer.setByteElementAt(i, 0);
                return i;
            }
            buffer.setByteElementAt(i, ch);
        }
        int i = buffer.getArrayLength() - 1;
        buffer.setByteElementAt(i, 0);
        return i;
    }

    @Override
    public int getCharUni() throws IOException {
        if (unicode) {
            if (index + 3 >= contents.length) {
                return -1;
            }
            readCount++;
            index += 4;
            return ((contents[index-4]&255)<<24) | ((contents[index-3]&255)<<16) | ((contents[index-2]&255)<<8) | (contents[index-1]&255);
        } else {
            if (index >= contents.length) {
                return -1;
            }
            readCount++;
            index++;
            return contents[index-1]&255;
        }
    }

    @Override
    public int getLineUni(GlkIntArray buffer) throws IOException {
        for (int i = 0; i < buffer.getArrayLength() - 1; i++) {
            int ch = getCharUni();
            if (ch == -1 || ch == 10) {
                buffer.setIntElementAt(i, 0);
                return i;
            }
            buffer.setIntElementAt(i, ch);
        }
        int i = buffer.getArrayLength() - 1;
        buffer.setIntElementAt(i, 0);
        return i;
    }

    @Override
    public int getBufferUni(GlkIntArray buffer) throws IOException {
        for (int i = 0; i < buffer.getArrayLength() - 1; i++) {
            int ch = getCharUni();
            if (ch == -1) {
                buffer.setIntElementAt(i, 0);
                return i;
            }
            buffer.setIntElementAt(i, ch);
        }
        int i = buffer.getArrayLength() - 1;
        buffer.setIntElementAt(i, 0);
        return i;
    }

    @Override
    public void setPosition(int position, int seekMode) throws IOException {
        switch (seekMode) {
        case GlkStream.SeekModeStart:
            index = position;
            break;
        case GlkStream.SeekModeCurrent:
            index += position;
            break;
        case GlkStream.SeekModeEnd:
            index = contents.length + position;
            break;
        default:
            throw new IllegalArgumentException();
        }
    }

    @Override
    public int getPosition() throws IOException {
        return index;
    }

    @Override
    public DataOutput getDataOutput() {
        return null;
    }

    @Override
    public DataInput getDataInput() {
        return new DataInputStream(new ByteArrayInputStream(contents));
    }
}
