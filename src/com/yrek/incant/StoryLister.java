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
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class StoryLister {
    private static final String TAG = StoryLister.class.getSimpleName();
    private static final String CACHE = "cache";
    private static final String IFARCHIVE = "ifarchive";
    private static final String IFDB = "ifdb";

    private Context context;
    private Scraper[] scrapers;

    StoryLister(Context context) {
        this.context = context;
        this.scrapers = new Scraper[] {
            new IFDBScraper(context),
            new IFArchiveScraper(context),
        };
    }

    public List<Story> getStories() throws IOException {
        ArrayList<Story> stories = new ArrayList<Story>();
        addDownloaded(stories);
        addInitial(stories);
        for (Scraper scraper : scrapers) {
            scraper.addStories(stories);
        }
        return stories;
    }

    public void scrape() throws IOException {
        for (Scraper scraper : scrapers) {
            scraper.scrape();
        }
    }

    private void addStory(Story story, ArrayList<Story> stories) {
        String name = story.getName(context);
        if (name == null || name.indexOf('/') >= 0 || name.equals(".") || name.equals("..")) {
            return;
        }
        for (Story s : stories) {
            if (name.equals(s.getName(context))) {
                return;
            }
        }
        stories.add(story);
    }

    private void addDownloaded(ArrayList<Story> stories) throws IOException {
        for (File file : Story.getRootDir(context).listFiles()) {
            if (!Story.isDownloaded(context, file.getName())) {
                continue;
            }
            addStory(new Story(file.getName(), null, null, null, null, null, null), stories);
        }
    }

    private void addInitial(ArrayList<Story> stories) throws IOException {
        String[] initial = context.getResources().getStringArray(R.array.initial_story_list);
        for (int i = 0; i + 6 < initial.length; i += 7) {
            addStory(new Story(initial[i], initial[i+1], initial[i+2], initial[i+3], new URL(initial[i+4]), initial[i+5].length() > 0 ? initial[i+5] : null, initial[i+6].length() > 0 ? new URL(initial[i+6]) : null), stories);
        }
    }

    private interface PageScraper {
        public void scrape(String line) throws IOException;
    }

    private abstract class Scraper {
        private final File cacheDir;
        private final File cacheFile;
        private final long cacheTimeout;

        Scraper(Context context, String name, int cacheTimeout) {
            this.cacheDir = context.getDir("cache", Context.MODE_PRIVATE);
            this.cacheFile = new File(cacheDir, name);
            this.cacheTimeout = (long) cacheTimeout;
        }

        public void addStories(ArrayList<Story> stories) throws IOException {
            DataInputStream in = null;
            try {
                in = new DataInputStream(new FileInputStream(cacheFile));
                for (;;) {
                    String name = in.readUTF();
                    String author = in.readUTF();
                    String url = in.readUTF();
                    String zipFile = in.readUTF();
                    addStory(new Story(name, author.length() == 0 ? null : author, null, null, new URL(url), zipFile.length() == 0 ? null : zipFile, null), stories);
                }
            } catch (FileNotFoundException e) {
            } catch (EOFException e) {
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        }

        public void scrape() throws IOException {
            if (cacheFile.lastModified() + cacheTimeout > System.currentTimeMillis()) {
                return;
            }
            File tmpFile = null;
            try {
                tmpFile = File.createTempFile("tmp", "tmp", cacheDir);
                FileOutputStream out = null;
                try {
                    out = new FileOutputStream(tmpFile);
                    scrape(new DataOutputStream(out));
                } finally {
                    if (out != null) {
                        out.close();
                    }
                }
                tmpFile.renameTo(cacheFile);
            } finally {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
            }
        }

        abstract void scrape(DataOutputStream out) throws IOException;

        void scrapeURL(String url, PageScraper pageScraper) throws IOException {
            InputStream in = null;
            try {
                in = new URL(url).openStream();
                BufferedReader r = new BufferedReader(new InputStreamReader(in));
                for (String line = r.readLine(); line != null; line = r.readLine()) {
                    pageScraper.scrape(line);
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        }

        void writeStory(DataOutputStream out, String name, String author, String url, String zipFile) throws IOException {
            out.writeUTF(name);
            out.writeUTF(author == null ? "" : author);
            out.writeUTF(url);
            out.writeUTF(zipFile == null ? "" : zipFile);
        }
    }

    private class IFDBScraper extends Scraper {
        private final String[] scrapeURLs;
        private final String downloadInfoURL;

        IFDBScraper(Context context) {
            super(context, "ifdb", context.getResources().getInteger(R.integer.ifdb_cache_timeout));
            this.scrapeURLs = context.getResources().getStringArray(R.array.ifdb_scrape_urls);
            this.downloadInfoURL = context.getString(R.string.ifdb_download_info_url);
        }

        private final Pattern listPattern = Pattern.compile("\\<a href=\"viewgame\\?id=([a-zA-Z0-9]+)\"\\>");

        @Override
        void scrape(final DataOutputStream out) throws IOException {
            final HashSet<String> storyIDs = new HashSet<String>();
            PageScraper listScraper = new PageScraper() {
                @Override public void scrape(String line) throws IOException {
                    Matcher m = listPattern.matcher(line);
                    while (m.find()) {
                        storyIDs.add(m.group(1));
                    }
                }
            };
            for (String scrapeURL : scrapeURLs) {
                scrapeURL(scrapeURL, listScraper);
            }
            Log.d(TAG,"IFDBScraper:storyIDs="+storyIDs);
            try {
                XMLScraper xmlScraper = new XMLScraper(new XMLScraper.Handler() {
                    String name;
                    String author;
                    String url;
                    String extraURL;
                    String zipFile;
                    String format;
                    @Override public void startDocument() {
                        name = null;
                        author = null;
                        url = null;
                        extraURL = null;
                        zipFile = null;
                        format = null;
                    }
                    @Override public void endDocument() {
                        Log.d(TAG,"name="+name+",author="+author+",url="+url+",extraURL="+extraURL+",zipFile="+zipFile+",format="+format);
                        try {
                            if (name == null) {
                            } else if (url != null && url.matches(".*\\.(z[1-8]|zblorb|ulx|blb|gblorb)")) {
                                writeStory(out, name, author, url, null);
                            } else if (zipFile != null && zipFile.matches(".*\\.(z[1-8]|zblorb|ulx|blb|gblorb)")) {
                                writeStory(out, name, author, extraURL, zipFile);
                            }
                        } catch (Exception e) {
                            Log.wtf(TAG,e);
                        }
                    }
                    @Override public void element(String path, String value) {
                        if (value.trim().length() == 0) {
                        } else if ("autoinstall/title".equals(path)) {
                            name = value;
                        } else if ("autoinstall/author".equals(path)) {
                            author = value;
                        } else if ("autoinstall/download/game/href".equals(path)) {
                            url = value;
                        } else if ("autoinstall/download/game/format/id".equals(path)) {
                            format = value;
                        } else if ("autoinstall/download/game/compression/primaryfile".equals(path)) {
                            if (url != null && url.matches(".*\\.(zip|ZIP)") && value.matches(".*\\.(z[1-8]|zblorb|ulx|blb|gblorb)")) {
                                extraURL = url;
                                zipFile = value;
                            }
                        } else if ("autoinstall/download/extra/href".equals(path)) {
                            if (url == null && value.matches(".*\\.(z[1-8]|zblorb|ulx|blb|gblorb)")) {
                                url = value;
                            } else if (zipFile == null) {
                                extraURL = value;
                            }
                        } else if ("autoinstall/download/extra/compression/primaryfile".equals(path)) {
                            if (extraURL != null && extraURL.matches(".*\\.(zip|ZIP)") && value.matches(".*\\.(z[1-8]|zblorb|ulx|blb|gblorb)")) {
                                zipFile = value;
                            }
                        }
                    }
                });
                for (String storyID : storyIDs) {
                    try {
                        xmlScraper.scrape(downloadInfoURL+storyID);
                    } catch (Exception e) {
                        Log.wtf(TAG,e);
                    }
                }
            } catch (Exception e) {
                Log.wtf(TAG,e);
            }
        }
    }

    private class IFArchiveScraper extends Scraper {
        private final String scrapeURL;
        private final String downloadURL;

        IFArchiveScraper(Context context) {
            super(context, "ifarchive", context.getResources().getInteger(R.integer.ifarchive_cache_timeout));
            this.scrapeURL = context.getString(R.string.ifarchive_scrape_url);
            this.downloadURL = context.getString(R.string.ifarchive_download_url);
        }

        private final Pattern pattern = Pattern.compile("\\<li.*class=\"Date\"\\>\\[([^]]+)\\].*\\.\\.(/if-archive/games/(zcode|glulx)/[^\"]+)\"\\>if-archive/games/(zcode|glulx)/([^/]+)\\.(z[1-8]|zblorb|ulx|blb|gblorb)\\</a\\>");

        @Override
        void scrape(final DataOutputStream out) throws IOException {
            scrapeURL(scrapeURL,  new PageScraper() {
                @Override public void scrape(String line) throws IOException {
                    Matcher m = pattern.matcher(line);
                    while (m.find()) {
                        writeStory(out, m.group(5), "", downloadURL + m.group(2), "");
                    }
                }
            });
        }
    }
}
