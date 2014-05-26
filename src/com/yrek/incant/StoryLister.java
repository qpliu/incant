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
    private String ifarchiveScrapeURL;
    private String ifarchiveDownloadURL;
    private long ifarchiveCacheTimeout;
    private String[] ifdbScrapeURLs;
    private long ifdbCacheTimeout;

    StoryLister(Context context) {
        this.context = context;
        this.scrapers = new Scraper[] {
            new IFDBScraper(context),
            new IFArchiveScraper(context),
        };
        ifarchiveScrapeURL = context.getString(R.string.ifarchive_scrape_url);
        ifarchiveDownloadURL = context.getString(R.string.ifarchive_download_url);
        ifarchiveCacheTimeout = (long) context.getResources().getInteger(R.integer.ifarchive_cache_timeout);
        ifdbScrapeURLs = context.getResources().getStringArray(R.array.ifdb_scrape_urls);
        ifdbCacheTimeout = (long) context.getResources().getInteger(R.integer.ifdb_cache_timeout);
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
        String name = story.getName();
        if (name == null || name.indexOf('/') >= 0 || name.equals(".") || name.equals("..")) {
            return;
        }
        for (Story s : stories) {
            if (name.equals(s.getName())) {
                return;
            }
        }
        stories.add(story);
    }

    private void addDownloaded(ArrayList<Story> stories) throws IOException {
        for (File file : Story.getRootDir(context).listFiles()) {
            File storyFile = Story.getStoryFile(context, file.getName());
            if (!storyFile.exists()) {
                continue;
            }
            File saveFile = Story.getSaveFile(context, file.getName());
            String description;
            if (!saveFile.exists()) {
                if (storyFile.lastModified() + 86400000L > System.currentTimeMillis()) {
                    description = context.getString(R.string.downloaded_recently, storyFile.lastModified());
                } else {
                    description = context.getString(R.string.downloaded_at, storyFile.lastModified());
                }
            } else {
                if (saveFile.lastModified() + 86400000L > System.currentTimeMillis()) {
                    description = context.getString(R.string.saved_recently, saveFile.lastModified());
                } else {
                    description = context.getString(R.string.saved_at, saveFile.lastModified());
                }
            }
            addStory(new Story(file.getName(), description, null), stories);
        }
    }

    private void addInitial(ArrayList<Story> stories) throws IOException {
        String[] initial = context.getResources().getStringArray(R.array.initial_story_list);
        for (int i = 0; i + 3 < initial.length; i += 4) {
            addStory(new Story(initial[i], initial[i+1], new URL(initial[i+2]), initial[i+3].length() > 0 ? initial[i+3] : null), stories);
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
                    String description = in.readUTF();
                    String url = in.readUTF();
                    String zipFile = in.readUTF();
                    addStory(new Story(name, description, new URL(url), zipFile.length() == 0 ? null : zipFile), stories);
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

        void writeStory(DataOutputStream out, String name, String description, String url, String zipFile) throws IOException {
            out.writeUTF(name);
            out.writeUTF(description == null ? "" : description);
            out.writeUTF(url);
            out.writeUTF(zipFile == null ? "" : zipFile);
        }
    }

    private class IFDBScraper extends Scraper {
        private final String[] scrapeURLs;
        private final String storyURL;

        IFDBScraper(Context context) {
            super(context, "ifdb", context.getResources().getInteger(R.integer.ifdb_cache_timeout));
            this.scrapeURLs = context.getResources().getStringArray(R.array.ifdb_scrape_urls);
            this.storyURL = context.getString(R.string.ifdb_story_url);
        }

        private final Pattern listPattern = Pattern.compile("\\<a href=\"(viewgame\\?id=[a-zA-Z0-9]+)\"\\>");
        private final Pattern htmlPattern = Pattern.compile("\\<html\\>");
        private final Pattern endHtmlPattern = Pattern.compile("\\</html\\>");
        private final Pattern downloadDivPattern = Pattern.compile("\\<div class=\"downloadfloat\"\\>");
        private final Pattern downloadItemPattern = Pattern.compile("\\<div class='downloaditem' id='[^']+'>");
        private final Pattern linkPattern = Pattern.compile("\\<a href=\"(http://[^\"]+\\.(z3|Z3|z5|Z5|z8|Z8|zblorb|ZBLORB|zip|ZIP))\"\\>");
        private final Pattern zipFilePattern = Pattern.compile("class=\"zip-contents-arrow\"\\>Contains \\<b\\>([^<>]+)\\</b\\>");
        private final Pattern titlePattern = Pattern.compile("\\<h1\\>(.+)\\</h1\\>");
        private final Pattern aboutTheStoryPattern = Pattern.compile("\\<h3\\>About the Story\\</h3\\>");

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
            PageScraper storyScraper = new PageScraper() {
                boolean hasDownload;
                boolean atDownloadItem;
                String url;
                String zipFile;
                String name;
                boolean atAboutTheStory;
                String description;

                void reset() {
                    hasDownload = false;
                    atDownloadItem = false;
                    url = null;
                    zipFile = null;
                    name = null;
                    atAboutTheStory = false;
                    description = null;
                }

                boolean validFile(String filename) {
                    return filename != null && (filename.endsWith(".z3") || filename.endsWith(".z5") || filename.endsWith(".z8") || filename.endsWith(".zblorb") || filename.endsWith(".Z3") || filename.endsWith(".Z5") || filename.endsWith(".Z8") || filename.endsWith(".ZBLORB"));
                }

                boolean validURL() {
                    if (validFile(url)) {
                        return true;
                    }
                    return url != null && (url.endsWith(".zip") || url.endsWith(".ZIP")) && validFile(zipFile);
                }

                @Override public void scrape(String line) throws IOException {
                    if (htmlPattern.matcher(line).find()) {
                        reset();
                    } else if (endHtmlPattern.matcher(line).find()) {
                        Log.d(TAG,"IFDBScraper:name="+name+",description="+description+",url="+url+",zipFile="+zipFile);
                        hasDownload = false;
                        if (validURL()) {
                            writeStory(out, name, description, url, validFile(url) ? null : zipFile);
                        }
                        reset();
                    } else if (downloadDivPattern.matcher(line).find()) {
                        hasDownload = true;
                    } else if (!hasDownload) {
                        return;
                    } else if (atDownloadItem) {
                        if (!validURL()) {
                            Matcher m = linkPattern.matcher(line);
                            if (m.find()) {
                                url = m.group(1);
                                zipFile = null;
                                m = zipFilePattern.matcher(line);
                                if (m.find()) {
                                    zipFile = m.group(1);
                                }
                            }
                        }
                        atDownloadItem = downloadItemPattern.matcher(line).find();
                    } else if (downloadItemPattern.matcher(line).find()) {
                        atDownloadItem = true;
                    } else if (name == null) {
                        Matcher m = titlePattern.matcher(line);
                        if (m.find()) {
                            name = m.group(1);
                        }
                    } else if (atAboutTheStory) {
                        description = line.trim();
                        atAboutTheStory = false;
                    } else if (description == null && aboutTheStoryPattern.matcher(line).find()) {
                        atAboutTheStory = true;
                    }
                }
            };
            for (String storyID : storyIDs) {
                scrapeURL(storyURL + storyID, storyScraper);
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

        private final Pattern pattern = Pattern.compile("\\<li.*class=\"Date\"\\>\\[([^]]+)\\].*\\.\\.(/if-archive/games/zcode/[^\"]+)\"\\>if-archive/games/zcode/([^/]+)\\.(z3|z5|z8|zblorb)\\</a\\>");

        @Override
        void scrape(final DataOutputStream out) throws IOException {
            scrapeURL(scrapeURL,  new PageScraper() {
                @Override public void scrape(String line) throws IOException {
                    Matcher m = pattern.matcher(line);
                    while (m.find()) {
                        writeStory(out, m.group(3), m.group(1), downloadURL + m.group(2), "");
                    }
                }
            });
        }
    }
}
