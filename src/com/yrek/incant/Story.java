package com.yrek.incant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.Xml;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.zip.ZipFile;

import org.xmlpull.v1.XmlSerializer;

class Story implements Serializable {
    private static final long serializableVersionID = 0L;
    private static final String TAG = Story.class.getSimpleName();

    private final String name;
    private final String author;
    private final String headline;
    private final String description;
    private final URL downloadURL;
    private final String zipEntry;
    private final URL imageURL;
    private transient Metadata metadata;
    private transient Bitmap coverImageBitmap;

    Story(String name, String author, String headline, String description, URL downloadURL, String zipEntry, URL imageURL) {
        this.name = name;
        this.author = author;
        this.headline = headline;
        this.description = description;
        this.downloadURL = downloadURL;
        this.zipEntry = zipEntry;
        this.imageURL = imageURL;
    }

    public String getName(Context context) {
        return name;
    }

    private Metadata getMetadata(Context context) {
        if (metadata != null || !getMetadataFile(context).exists()) {
            return metadata;
        }
        Metadata m = new Metadata();
        try {
            new XMLScraper(m).scrape(getMetadataFile(context));
            metadata = m;
        } catch (Exception e) {
            Log.wtf(TAG,e);
        }
        return metadata;
    }

    public String getAuthor(Context context) {
        Metadata m = getMetadata(context);
        return m != null ? m.author : author;
    }

    public String getHeadline(Context context) {
        Metadata m = getMetadata(context);
        return m != null ? m.headline : headline;
    }

    public String getDescription(Context context) {
        Metadata m = getMetadata(context);
        return m != null ? m.description : description;
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

    public Bitmap getCoverImageBitmap(Context context) {
        if (coverImageBitmap == null) {
            File file = getCoverImageFile(context);
            if (file.exists()) {
                coverImageBitmap = BitmapFactory.decodeFile(file.getPath());
            }
        }
        return coverImageBitmap;
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
                downloadTo(context, downloadURL, tmpFile);
            } else {
                File zipFile = File.createTempFile("zip","tmp",getDir(context));
                ZipFile zf = null;
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    downloadTo(context, downloadURL, zipFile);
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
                    int coverImage = -1;
                    for (Blorb.Chunk chunk : blorb.chunks()) {
                        switch (chunk.getId()) {
                        case Blorb.IFmd:
                            writeBlorbChunk(context, chunk, getMetadataFile(context));
                            break;
                        case Blorb.Fspc:
                            coverImage = new DataInputStream(new ByteArrayInputStream(chunk.read())).readInt();
                            break;
                        default:
                        }
                    }
                    Metadata md = getMetadata(context);
                    if (md != null && md.coverpicture != null) {
                        coverImage = Integer.parseInt(md.coverpicture);
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
                            if (res.getNumber() == coverImage && (chunk.getId() == Blorb.PNG || chunk.getId() == Blorb.JPEG)) {
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
        try {
            if (imageURL != null && !getCoverImageFile(context).exists()) {
                downloadTo(context, imageURL, getCoverImageFile(context));
            }
        } catch (Exception e) {
            Log.wtf(TAG,e);
        }
        try {
            if (!getMetadataFile(context).exists()) {
                writeMetadata(context, getMetadataFile(context));
            }
        } catch (Exception e) {
            Log.wtf(TAG,e);
        }
    }

    protected void downloadTo(Context context, URL url, File file) throws IOException {
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

    private class Metadata implements XMLScraper.Handler {
        String author;
        String headline;
        String description;
        String coverpicture;

        @Override
        public void startDocument() {
        }

        @Override
        public void endDocument() {
        }

        @Override
        public void element(String path, String value) {
            if ("ifindex/story/bibliographic/author".equals(path)) {
                author = value;
            } else if ("ifindex/story/bibliographic/headline".equals(path)) {
                headline = value;
            } else if ("ifindex/story/bibliographic/description".equals(path)) {
                description = value;
            } else if ("ifindex/story/zcode/coverpicture".equals(path)) {
                coverpicture = value;
            }
        }
    }

    protected void writeMetadata(Context context, File file) throws IOException {
        getDir(context).mkdir();
        File tmpFile = File.createTempFile("tmp","tmp",getDir(context));
        try {
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(tmpFile);
                XmlSerializer xmlSerializer = Xml.newSerializer();
                xmlSerializer.setOutput(out, "UTF-8");
                xmlSerializer.startDocument("UTF-8", null);
                xmlSerializer.startTag("http://babel.ifarchive.org/protocol/iFiction/", "ifindex");
                xmlSerializer.startTag(null, "story");
                xmlSerializer.startTag(null, "bibliographic");
                if (author != null) {
                    xmlSerializer.startTag(null, "author");
                    xmlSerializer.text(author);
                    xmlSerializer.endTag(null, "author");
                }
                if (headline != null) {
                    xmlSerializer.startTag(null, "headline");
                    xmlSerializer.text(headline);
                    xmlSerializer.endTag(null, "headline");
                }
                if (description != null) {
                    xmlSerializer.startTag(null, "description");
                    xmlSerializer.text(description);
                    xmlSerializer.endTag(null, "description");
                }
                xmlSerializer.endTag(null, "bibliographic");
                xmlSerializer.endTag(null, "story");
                xmlSerializer.endTag("http://babel.ifarchive.org/protocol/iFiction/", "ifindex");
                xmlSerializer.endDocument();
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
