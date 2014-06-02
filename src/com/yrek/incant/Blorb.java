package com.yrek.incant;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.Iterator;

public class Blorb implements Closeable {
    public static final int FORM = 0x464f524d;
    public static final int IFRS = 0x49465253;
    public static final int RIdx = 0x52496478;
    public static final int Pict = 0x50696374;
    public static final int Snd  = 0x536e5420;
    public static final int Data = 0x44617461;
    public static final int Exec = 0x45786563;
    public static final int PNG  = 0x504e4720;
    public static final int JPEG = 0x4a504547;
    public static final int ZCOD = 0x5a434f44;
    public static final int IFmd = 0x49466d64;
    public static final int Fspc = 0x46737063;

    private final RandomAccessFile file;
    private final TreeMap<Long,Chunk> chunks = new TreeMap<Long,Chunk>();
    private final LinkedList<Resource> resources = new LinkedList<Resource>();

    public Blorb(File file) throws IOException {
        this.file = new RandomAccessFile(file, "r");
        try {
            init();
        } catch (IOException e) {
            this.file.close();
            throw e;
        }
    }

    private void init() throws IOException {
        if (file.readInt() != FORM) {
            throw new IOException("Invalid file");
        }
        long eof = 8 + (0xffffffffL & file.readInt());
        if (file.readInt() != IFRS) {
            throw new IOException("Invalid file");
        }
        if (eof > 12) {
            for (;;) {
                Chunk chunk = new Chunk();
                chunks.put(chunk.start, chunk);
                if (chunk.start + 8 + chunk.length >= eof - 8) {
                    break;
                }
            }
        }
        for (Chunk chunk : chunks.values()) {
            if (chunk.id == RIdx) {
                file.seek(chunk.start + 8);
                int count = file.readInt();
                for (int i = 0; i < count; i++) {
                    resources.add(new Resource());
                }
            }
        }
    }

    public Iterable<Chunk> chunks() {
        return chunks.values();
    }

    public Iterable<Resource> resources() {
        return resources;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    public class Chunk {
        final int id;
        final int length;
        final long start;

        Chunk() throws IOException {
            start = file.getFilePointer();
            id = file.readInt();
            length = file.readInt();
            file.seek(start + 8 + length);
        }

        public int getId() {
            return id;
        }

        public int getLength() {
            return length;
        }

        public byte[] read() throws IOException {
            byte[] contents = new byte[length];
            file.seek(start + 8);
            file.readFully(contents);
            return contents;
        }

        public void read(OutputStream out) throws IOException {
            byte[] buffer = new byte[8192];
            file.seek(start + 8);
            int total = 0;
            while (total < length) {
                int count = file.read(buffer, 0, Math.min(buffer.length, length - total));
                if (count < 0) {
                    break;
                }
                out.write(buffer, 0, count);
                total += count;
            }
        }
    }

    public class Resource {
        final int usage;
        final int number;
        final long start;

        Resource() throws IOException {
            usage = file.readInt();
            number = file.readInt();
            start = (long) file.readInt();
        }

        public int getUsage() {
            return usage;
        }

        public int getNumber() {
            return number;
        }

        public Chunk getChunk() {
            return chunks.get(start);
        }
    }
}
