package com.yrek.incant;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class StoryLister {
    private static final String TAG = StoryLister.class.getSimpleName();

    private static final long IFARCHIVE_CACHE_TIMEOUT = 1000L*3600L*24L*7L;

    public List<Story> getStories(Context context, Runnable onUpdateStories) throws IOException {
        ArrayList<Story> stories = new ArrayList<Story>();
        addDownloaded(context, stories);
        addInitial(context, stories);
        addIfArchive(context, onUpdateStories, stories);
        return stories;
    }

    private void addStory(Story story, ArrayList<Story> stories) {
        for (Story s : stories) {
            if (s.getName().equals(story.getName())) {
                return;
            }
        }
        stories.add(story);
    }

    private void addDownloaded(Context context, ArrayList<Story> stories) throws IOException {
        for (File file : Story.getRootDir(context).listFiles()) {
            Story story = new Story(file.getName(), "", null);
            if (story.isDownloaded(context)) {
                addStory(story, stories);
            }
        }
    }

    private void addInitial(Context context, ArrayList<Story> stories) throws IOException {
        String[] initial = context.getResources().getStringArray(R.array.initial_story_list);
        for (int i = 0; i + 3 < initial.length; i += 4) {
            addStory(new Story(initial[i], initial[i+1], new URL(initial[i+2]), initial[i+3].length() > 0 ? initial[i+3] : null), stories);
        }
    }

    private void addIfArchive(final Context context, final Runnable onUpdateStories, ArrayList<Story> stories) throws IOException {
        final File ifarchiveCache = new File(context.getDir("cache", Context.MODE_PRIVATE), "ifarchive");
        DataInputStream in = null;
        try {
            in = new DataInputStream(new FileInputStream(ifarchiveCache));
            for (;;) {
                addStory(new Story(in.readUTF(), in.readUTF(), new URL("http://www.ifarchive.org"+in.readUTF())), stories);
            }
        } catch (FileNotFoundException e) {
        } catch (EOFException e) {
        } finally {
            if (in != null) {
                in.close();
            }
        }
        if (ifarchiveCache.lastModified() + IFARCHIVE_CACHE_TIMEOUT < System.currentTimeMillis()) {
            new Thread() {
                @Override public void run() {
                    File tmpFile = null;
                    try {
                        tmpFile = File.createTempFile("tmp","tmp",context.getDir("cache", Context.MODE_PRIVATE));
                        FileOutputStream out = null;
                        try {
                            out = new FileOutputStream(tmpFile);
                            readIFArchive(context, new DataOutputStream(out));
                        } finally {
                            if (out != null) {
                                out.close();
                            }
                        }
                        tmpFile.renameTo(ifarchiveCache);
                        if (onUpdateStories != null) {
                            onUpdateStories.run();
                        }
                    } catch (Exception e) {
                        Log.wtf(TAG,e);
                    } finally {
                        if (tmpFile != null) {
                            tmpFile.delete();
                        }
                    }
                }
            }.start();
        }
    }

    private void readIFArchive(Context context, DataOutputStream out) throws IOException {
        Pattern pattern = Pattern.compile("\\<li.*class=\"Date\"\\>\\[([^]]+)\\].*\\.\\.(/if-archive/games/zcode/[^\"]+)\"\\>if-archive/games/zcode/([^/]+)\\.(z3|z5|z8|zblorb)\\</a\\>");
        InputStream in = null;
        try {
            in = new URL("http://www.ifarchive.org/indexes/date_3.html").openStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    out.writeUTF(m.group(3));
                    out.writeUTF(m.group(1));
                    out.writeUTF(m.group(2));
                }
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
