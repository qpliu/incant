package com.yrek.incant.glk;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.yrek.ifstd.glk.GlkByteArray;
import com.yrek.ifstd.glk.GlkFile;
import com.yrek.ifstd.glk.GlkIntArray;
import com.yrek.ifstd.glk.GlkStream;
import com.yrek.ifstd.glk.GlkStreamResult;
import com.yrek.ifstd.glk.UnicodeString;

class StreamFile extends Stream {
    private static final long serialVersionUID = 0L;
    transient RandomAccessFile file;
    final boolean unicode;
    int readCount = 0;
    int writeCount = 0;

    StreamFile(FileRef fileRef, int mode, boolean unicode, int rock) throws IOException {
        super(rock);
        this.file = new RandomAccessFile(fileRef.file, mode == GlkFile.ModeRead ? "r" : "rw");
        switch (mode) {
        case GlkFile.ModeWrite:
            this.file.setLength(0L);
            break;
        case GlkFile.ModeRead:
        case GlkFile.ModeReadWrite:
            this.file.seek(0L);
            break;
        case GlkFile.ModeWriteAppend:
            this.file.seek(this.file.length());
            break;
        default:
            throw new IllegalArgumentException();
        }
        this.unicode = unicode;
    }
    
    @Override
    public GlkStreamResult close() throws IOException {
        file.close();
        destroy();
        return new GlkStreamResult(readCount, writeCount);
    }

    @Override
    public void putChar(int ch) throws IOException {
        writeCount++;
        if (unicode) {
            file.writeInt(ch & 255);
        } else {
            file.write(ch);
        }
    }

    @Override
    public void putString(CharSequence string) throws IOException {
        writeCount += string.length();
        if (unicode) {
            for (int i = 0; i < string.length(); i++) {
                file.writeInt(string.charAt(i) & 255);
            }
        } else {
            file.writeBytes(string.toString());
        }
    }

    @Override
    public void putBuffer(GlkByteArray buffer) throws IOException {
        writeCount += buffer.getArrayLength();
        if (unicode) {
            for (int i = 0; i < buffer.getArrayLength(); i++) {
                file.writeInt(buffer.getByteElementAt(i) & 255);
            }
        } else {
            for (int i = 0; i < buffer.getArrayLength(); i++) {
                file.write(buffer.getByteElementAt(i));
            }
        }
    }

    @Override
    public void putCharUni(int ch) throws IOException {
        writeCount++;
        if (unicode) {
            file.writeInt(ch);
        } else {
            file.write(ch);
        }
    }

    @Override
    public void putStringUni(UnicodeString string) throws IOException {
        writeCount += string.codePointCount();
        if (unicode) {
            for (int i = 0; i < string.codePointCount(); i++) {
                file.writeInt(string.codePointAt(i));
            }
        } else {
            for (int i = 0; i < string.codePointCount(); i++) {
                file.write(string.codePointAt(i));
            }
        }
    }

    @Override
    public void putBufferUni(GlkIntArray buffer) throws IOException {
        writeCount += buffer.getArrayLength();
        if (unicode) {
            for (int i = 0; i < buffer.getArrayLength(); i++) {
                file.writeInt(buffer.getIntElementAt(i));
            }
        } else {
            for (int i = 0; i < buffer.getArrayLength(); i++) {
                file.write(buffer.getIntElementAt(i));
            }
        }
    }

    @Override
    public void setStyle(int style) {
    }

    @Override
    public int getChar() throws IOException {
        int ch = -1;
        try {
            if (unicode) {
                ch = file.readInt() & 255;
            } else {
                ch = file.readUnsignedByte();
            }
            readCount++;
        } catch (EOFException e) {
        }
        return ch;
    }

    @Override
    public int getLine(GlkByteArray buffer) throws IOException {
        int index = 0;
        while (index < buffer.getArrayLength() - 1) {
            int ch;
            try {
                if (unicode) {
                    ch = file.readInt() & 255;
                } else {
                    ch = file.readUnsignedByte();
                }
            } catch (EOFException e) {
                break;
            }
            readCount++;
            buffer.setByteElementAt(index, ch);
            index++;
            if (ch == 10) {
                break;
            }
        }
        buffer.setByteElementAt(index, 0);
        return index;
    }

    @Override
    public int getBuffer(GlkByteArray buffer) throws IOException {
        int index = 0;
        while (index < buffer.getArrayLength() - 1) {
            int ch;
            try {
                if (unicode) {
                    ch = file.readInt() & 255;
                } else {
                    ch = file.readUnsignedByte();
                }
            } catch (EOFException e) {
                break;
            }
            readCount++;
            buffer.setByteElementAt(index, ch);
            index++;
        }
        buffer.setByteElementAt(index, 0);
        return index;
    }

    @Override
    public int getCharUni() throws IOException {
        int ch = -1;
        try {
            if (unicode) {
                ch = file.readInt();
            } else {
                ch = file.readUnsignedByte();
            }
            readCount++;
        } catch (EOFException e) {
        }
        return ch;
    }

    @Override
    public int getLineUni(GlkIntArray buffer) throws IOException {
        int index = 0;
        while (index < buffer.getArrayLength() - 1) {
            int ch;
            try {
                if (unicode) {
                    ch = file.readInt();
                } else {
                    ch = file.readUnsignedByte();
                }
            } catch (EOFException e) {
                break;
            }
            readCount++;
            buffer.setIntElementAt(index, ch);
            index++;
            if (ch == 10) {
                break;
            }
        }
        buffer.setIntElementAt(index, 0);
        return index;
    }

    @Override
    public int getBufferUni(GlkIntArray buffer) throws IOException {
        int index = 0;
        while (index < buffer.getArrayLength() - 1) {
            int ch;
            try {
                if (unicode) {
                    ch = file.readInt();
                } else {
                    ch = file.readUnsignedByte();
                }
            } catch (EOFException e) {
                break;
            }
            readCount++;
            buffer.setIntElementAt(index, ch);
            index++;
        }
        buffer.setIntElementAt(index, 0);
        return index;
    }

    @Override
    public void setPosition(int position, int seekMode) throws IOException {
        switch (seekMode) {
        case GlkStream.SeekModeStart:
            file.seek((long) position);
            break;
        case GlkStream.SeekModeCurrent:
            file.seek(file.getFilePointer() + position);
            break;
        case GlkStream.SeekModeEnd:
            file.seek(file.length() + position);
            break;
        default:
            throw new IllegalArgumentException();
        }
    }

    @Override
    public int getPosition() throws IOException {
        return (int) file.getFilePointer();
    }

    @Override
    public DataOutput getDataOutput() {
        return file;
    }

    @Override
    public DataInput getDataInput() {
        return file;
    }
}
