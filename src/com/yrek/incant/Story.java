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

    public File getDir(Context context) {
        return new File(getRootDir(context), name);
    }

    public File getFile(Context context) {
        return getFile(context, "story");
    }

    public File getFile(Context context, String file) {
        return new File(getDir(context), file);
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
            DataInputStream in = null;
            try {
                in = new DataInputStream(new FileInputStream(tmpFile));
                int type = in.readInt();
                int count = in.readInt();
                int type2 = in.readInt();
                if (type != 0x464f524d /*FORM*/ || type2 != 0x49465253 /*IFRS*/) {
                    tmpFile.renameTo(getFile(context));
                } else {
                    for (;;) {
                        type = in.readInt();
                        count = in.readInt();
                        if (type != 0x5a434f44 /*ZCOD*/) {
                            in.skip(count);
                        } else {
                            File tmp2File = File.createTempFile("tmp","tmp",getDir(context));
                            FileOutputStream out = null;
                            try {
                                out = new FileOutputStream(tmp2File);
                                byte[] buffer = new byte[8192];
                                int total = 0;
                                for (int n = in.read(buffer); n >= 0 && total < count; n = in.read(buffer)) {
                                    out.write(buffer, 0, Math.min(n, count - total));
                                    total += n;
                                }
                                tmp2File.renameTo(getFile(context));
                            } finally {
                                if (out != null) {
                                    out.close();
                                }
                                if (tmp2File.exists()) {
                                    tmp2File.delete();
                                }
                            }
                            break;
                        }
                    }
                }
            } finally {
                if (in != null) {
                    in.close();
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
        return options.get(0);
    }

    public char chooseCharacterInput(List<String> options) {
        if ("space".equals(options.get(0))) {
            return ' ';
        }
        return options.get(0).charAt(0);
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
