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

import com.yrek.ifstd.blorb.Blorb;
import com.yrek.incant.glk.SpeechMunger;

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

    public URL getDownloadURL(Context context) {
        return downloadURL;
    }

    public String getZipEntry(Context context) {
        return zipEntry;
    }

    public static File getRootDir(Context context) {
        return context.getDir("story", Context.MODE_PRIVATE);
    }

    public static File getStoryDir(Context context, String name) {
        return new File(getRootDir(context), name);
    }

    public static File getZcodeFile(Context context, String name) {
        return new File(getStoryDir(context, name), "zcode");
    }

    public static File getGlulxFile(Context context, String name) {
        return new File(getStoryDir(context, name), "glulx");
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

    public static File getBlorbFile(Context context, String name) {
        return new File(getStoryDir(context, name), "blorb");
    }

    public static boolean isDownloaded(Context context, String name) {
        return getZcodeFile(context, name).exists() || getGlulxFile(context, name).exists();
    }

    public File getDir(Context context) {
        return getStoryDir(context, name);
    }

    public File getFile(Context context, String file) {
        return new File(getDir(context), file);
    }

    public File getStoryFile(Context context) {
        return isZcode(context) ? getZcodeFile(context) : getGlulxFile(context);
    }

    public File getZcodeFile(Context context) {
        return getZcodeFile(context, name);
    }

    public File getGlulxFile(Context context) {
        return getGlulxFile(context, name);
    }

    public File getSaveFile(Context context) {
        return getSaveFile(context, name);
    }

    public File getCoverImageFile(Context context) {
        return getCoverImageFile(context, name);
    }

    public Bitmap getCoverImageBitmap(Context context) {
        File file = getCoverImageFile(context);
        return file.exists() ? BitmapFactory.decodeFile(file.getPath()) : null;
    }

    public File getMetadataFile(Context context) {
        return getMetadataFile(context, name);
    }

    public File getBlorbFile(Context context) {
        return getBlorbFile(context, name);
    }

    public boolean isDownloaded(Context context) {
        return getZcodeFile(context).exists() || getGlulxFile(context).exists();
    }

    public boolean isZcode(Context context) {
        return getZcodeFile(context).exists();
    }

    public boolean isGlulx(Context context) {
        return getGlulxFile(context).exists();
    }

    public boolean download(Context context) throws IOException {
        return download(context, null);
    }

    protected boolean download(Context context, InputStream inputStream) throws IOException {
        boolean downloaded = false;
        getDir(context).mkdir();
        File tmpFile = File.createTempFile("tmp","tmp",getDir(context));
        try {
            int magic;
            if (inputStream == null) {
                magic = downloadTo(context, downloadURL, tmpFile);
            } else {
                magic = downloadTo(context, inputStream, tmpFile);
            }
            if (magic == 0x504b0304 && zipEntry != null) {
                File tmpEntry = File.createTempFile("tmp","tmp",getDir(context));
                try {
                    magic = unzipTo(context, tmpFile, tmpEntry);
                    tmpEntry.renameTo(tmpFile);
                } finally {
                    if (tmpEntry.exists()) {
                        tmpEntry.delete();
                    }
                }
            }
            if (magic == 0x476c756c) {
                tmpFile.renameTo(getGlulxFile(context));
                downloaded = true;
            } else if ((magic >> 24) >= 3 && (magic >> 24) <= 8) {
                tmpFile.renameTo(getZcodeFile(context));
                downloaded = true;
            } else if (magic == 0x464f524d) {
                tmpFile.renameTo(getBlorbFile(context));
                Blorb blorb = null;
                try {
                    blorb = Blorb.from(getBlorbFile(context));
                    int coverImage = -1;
                    for (Blorb.Chunk chunk : blorb.chunks()) {
                        switch (chunk.getId()) {
                        case Blorb.IFmd:
                            writeBlorbChunk(context, chunk, getMetadataFile(context));
                            break;
                        case Blorb.Fspc:
                            coverImage = new DataInputStream(new ByteArrayInputStream(chunk.getContents())).readInt();
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
                                writeBlorbChunk(context, chunk, getZcodeFile(context));
                                downloaded = true;
                            } else if (chunk.getId() == Blorb.GLUL) {
                                writeBlorbChunk(context, chunk, getGlulxFile(context));
                                downloaded = true;
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
                    if (blorb != null) {
                        blorb.close();
                    }
                }
            }
        } finally {
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
            if (!downloaded) {
                delete(context);
            }
        }
        if (downloaded) {
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
        return downloaded;
    }

    protected int unzipTo(Context context, File zipFile, File file) throws IOException {
        InputStream in = null;
        try {
            ZipFile zf = new ZipFile(zipFile);
            in = zf.getInputStream(zf.getEntry(zipEntry));
            FileOutputStream out = null;
            int magic = 0;
            try {
                out = new FileOutputStream(file);
                for (int i = 0; i < 4; i++) {
                    int b = in.read();
                    if (b < 0) {
                        break;
                    } else {
                        out.write(b);
                        magic |= (b&255) << (24 - 8*i);
                    }
                }
                byte[] buffer = new byte[8192];
                for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
                    out.write(buffer, 0, n);
                }
            } finally {
                if (out != null) {
                    out.close();
                }
            }
            return magic;
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    protected int downloadTo(Context context, URL url, File file) throws IOException {
        InputStream in = null;
        try {
            in = url.openStream();
            return downloadTo(context, in, file);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    protected int downloadTo(Context context, InputStream in, File file) throws IOException {
        File tmpFile = File.createTempFile("tmp","tmp",getDir(context));
        try {
            int magic = 0;
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(tmpFile);
                for (int i = 0; i < 4; i++) {
                    int b = in.read();
                    if (b < 0) {
                        break;
                    } else {
                        out.write(b);
                        magic |= (b&255) << (24 - 8*i);
                    }
                }
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
            return magic;
        } finally {
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
                chunk.write(out);
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
            } else if ("ifindex/story/glulx/coverpicture".equals(path)) {
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
}
