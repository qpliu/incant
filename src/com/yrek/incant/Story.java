package com.yrek.incant;

import android.content.Context;
import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.zip.ZipFile;

class Story implements Serializable {
    private static final long serializableVersionID = 0L;
    private static final String TAG = Story.class.getSimpleName();

    private final String name;
    private final String description;
    private final URL url;
    private final String zipEntry;

    Story(String name, String description, URL url) {
        this(name, description, url, null);
    }

    Story(String name, String description, URL url, String zipEntry) {
        this.name = name;
        this.description = description;
        this.url = url;
        this.zipEntry = zipEntry;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static File getRootDir(Context context) {
        return context.getDir("story", Context.MODE_PRIVATE);
    }

    public static File getStoryDir(Context context, String name) {
        return new File(getRootDir(context), name);
    }

    public static File getStoryFile(Context context, String name) {
        return new File(getStoryDir(context, name), "story");
    }

    public static File getSaveFile(Context context, String name) {
        return new File(getStoryDir(context, name), "save");
    }

    public static File getCoverImageFile(Context context, String name) {
        return new File(getStoryDir(context, name), "cover");
    }

    public static File getMetadataFile(Context context, String name) {
        return new File(getStoryDir(context, name), "metadata");
    }

    public File getDir(Context context) {
        return getStoryDir(context, name);
    }

    public File getFile(Context context) {
        return getStoryFile(context, name);
    }

    public File getFile(Context context, String file) {
        return new File(getDir(context), file);
    }

    public File getSaveFile(Context context) {
        return getSaveFile(context, name);
    }

    public File getCoverImageFile(Context context) {
        return getCoverImageFile(context, name);
    }

    public File getMetadataFile(Context context) {
        return getMetadataFile(context, name);
    }

    public boolean isDownloaded(Context context) {
        return getFile(context).exists();
    }

    public void download(Context context) throws IOException {
        getDir(context).mkdir();
        File tmpFile = File.createTempFile("tmp","tmp",getDir(context));
        try {
            if (zipEntry == null) {
                downloadTo(context, tmpFile);
            } else {
                File zipFile = File.createTempFile("zip","tmp",getDir(context));
                ZipFile zf = null;
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    downloadTo(context, zipFile);
                    zf = new ZipFile(zipFile);
                    in = zf.getInputStream(zf.getEntry(zipEntry));
                    out = new FileOutputStream(tmpFile);
                    byte[] buffer = new byte[8192];
                    for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
                        out.write(buffer, 0, n);
                    }
                } finally {
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    if (zf != null) {
                        zf.close();
                    }
                    zipFile.delete();
                }
            }
            Blorb blorb = null;
            try {
                blorb = new Blorb(tmpFile);
            } catch (IOException e) {
            }
            if (blorb == null) {
                tmpFile.renameTo(getFile(context));
            } else {
                try {
                    //... use IFmd or Fspc for cover image
                    for (Blorb.Chunk chunk : blorb.chunks()) {
                        switch (chunk.getId()) {
                        case Blorb.IFmd:
                            writeBlorbChunk(context, chunk, getMetadataFile(context));
                            break;
                        default:
                        }
                    }
                    for (Blorb.Resource res : blorb.resources()) {
                        Blorb.Chunk chunk = res.getChunk();
                        if (chunk == null) {
                            continue;
                        }
                        switch (res.getUsage()) {
                        case Blorb.Exec:
                            if (chunk.getId() == Blorb.ZCOD) {
                                writeBlorbChunk(context, chunk, getFile(context));
                            }
                            break;
                        case Blorb.Pict:
                            if (chunk.getId() == Blorb.PNG || chunk.getId() == Blorb.JPEG) {
                                writeBlorbChunk(context, chunk, getCoverImageFile(context));
                            }
                            break;
                        default:
                        }
                    }
                } finally {
                    blorb.close();
                }
            }
        } finally {
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
        }
    }

    protected void downloadTo(Context context, File file) throws IOException {
        getDir(context).mkdir();
        File tmpFile = File.createTempFile("tmp","tmp",getDir(context));
        InputStream in = null;
        try {
            in = url.openStream();
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(tmpFile);
                byte[] buffer = new byte[8192];
                for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
                    out.write(buffer, 0, n);
                }
            } finally {
                if (out != null) {
                    out.close();
                }
            }
            tmpFile.renameTo(file);
        } finally {
            if (in != null) {
                in.close();
            }
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
        }
    }

    protected void writeBlorbChunk(Context context, Blorb.Chunk chunk, File file) throws IOException {
        getDir(context).mkdir();
        File tmpFile = File.createTempFile("tmp","tmp",getDir(context));
        try {
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(tmpFile);
                chunk.read(out);
            } finally {
                if (out != null) {
                    out.close();
                }
            }
            tmpFile.renameTo(file);
        } finally {
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
        }
    }

    public void delete(Context context) {
        File dir = getDir(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        dir.delete();
    }

    public String chooseInput(List<String> options) {
        String input = options.get(0);
        if ("south east".equals(input)) {
            return "southeast";
        } else if ("go south east".equals(input)) {
            return "go southeast";
        } else if (input.startsWith("where the ") || input.startsWith("where a ") || input.startsWith("where an ")) {
            return "wear" + input.substring(5);
        }
        return input;
    }

    public char chooseCharacterInput(List<String> options) {
        String input = options.get(0);
        if ("space".equals(input)) {
            return ' ';
        } else if ("enter".equals(input)) {
            return '\n';
        }
        return input.charAt(0);
    }

    public String translateOutput(String output) {
        if (output == null || ">".equals(output)) {
            return null;
        }
        if (output.endsWith("\n>")) {
            return output.substring(0, output.length() - 2);
        }
        return output;
    }
}
