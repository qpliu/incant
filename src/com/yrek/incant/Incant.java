package com.yrek.incant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.HashSet;

public class Incant extends Activity {
    private static final String TAG = Incant.class.getSimpleName();
    static final String STORY = "STORY";

    private StoryLister storyLister;
    private ListView storyList;
    private StoryListAdapter storyListAdapter;

    private TextAppearanceSpan titleStyle;
    private TextAppearanceSpan authorStyle;
    private TextAppearanceSpan headlineStyle;
    private TextAppearanceSpan descriptionStyle;
    private TextAppearanceSpan saveTimeStyle;
    private TextAppearanceSpan downloadTimeStyle;

    private Handler handler;
    private HandlerThread handlerThread;
    private LruCache<String,Bitmap> coverImageCache;

    static HashSet<String> downloading = new HashSet<String>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        storyLister = new StoryLister(this);
        storyList = (ListView) findViewById(R.id.storylist);
        storyListAdapter = new StoryListAdapter();

        storyList.setAdapter(storyListAdapter);

        titleStyle = new TextAppearanceSpan(this, R.style.story_title);
        authorStyle = new TextAppearanceSpan(this, R.style.story_author);
        headlineStyle = new TextAppearanceSpan(this, R.style.story_headline);
        descriptionStyle = new TextAppearanceSpan(this, R.style.story_description);
        saveTimeStyle = new TextAppearanceSpan(this, R.style.story_save_time);
        downloadTimeStyle = new TextAppearanceSpan(this, R.style.story_download_time);

        coverImageCache = new LruCache<String,Bitmap>(30);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handlerThread = new HandlerThread("Incant.HandlerThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    protected void onStop() {
        super.onStop();
        handlerThread.quit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStoryList();
    }

    private final Runnable refreshStoryList = new Runnable() {
        @Override
        public void run() {
            refreshStoryList();
        }
    };

    private void refreshStoryList() {
        storyListAdapter.setNotifyOnChange(false);
        storyListAdapter.clear();
        try {
            storyListAdapter.addAll(storyLister.getStories());
        } catch (Exception e) {
            Log.wtf(TAG,e);
        }
        storyListAdapter.add(null);
        storyListAdapter.notifyDataSetChanged();
    }

    private SpannableStringBuilder makeName(Story story) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append(story.getName(this));
        sb.setSpan(titleStyle, start, sb.length(), 0);
        String headline = story.getHeadline(this);
        if (headline != null) {
            sb.append(' ');
            start = sb.length();
            sb.append(headline);
            sb.setSpan(headlineStyle, start, sb.length(), 0);
        }
        return sb;
    }

    private SpannableStringBuilder makeAuthor(Story story) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String author = story.getAuthor(this);
        int start = sb.length();
        if (author == null) {
            sb.append("author unknown");
        } else {
            sb.append("by ");
            sb.append(author);
        }
        sb.setSpan(authorStyle, start, sb.length(), 0);
        return sb;
    }

    private SpannableStringBuilder makeDescription(Story story) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String description = story.getDescription(this);
        File saveFile = story.getSaveFile(this);
        File storyFile = story.getStoryFile(this);
        int start = sb.length();
        if (saveFile.exists()) {
            sb.append(getTimeString(this, R.string.saved_recently, R.string.saved_at, saveFile.lastModified()));
            sb.setSpan(saveTimeStyle, start, sb.length(), 0);
        } else if (description != null) {
            sb.append(description);
            sb.setSpan(descriptionStyle, start, sb.length(), 0);
        } else if (storyFile.exists()) {
            sb.append(getTimeString(this, R.string.downloaded_recently, R.string.downloaded_at, storyFile.lastModified()));
            sb.setSpan(downloadTimeStyle, start, sb.length(), 0);
        }
        return sb;
    }

    private class StoryListAdapter extends ArrayAdapter<Story> {
        private Thread downloadingObserver = null;

        StoryListAdapter() {
            super(Incant.this, R.layout.story);
        }

        private void setDownloadingObserver() {
            synchronized (downloading) {
                if (downloadingObserver == null && !downloading.isEmpty()) {
                    downloadingObserver = new Thread() {
                        @Override
                        public void run() {
                            synchronized (downloading) {
                                try {
                                    downloading.wait();
                                } catch (Exception e) {
                                    Log.wtf(TAG,e);
                                }
                                downloadingObserver = null;
                                storyList.post(refreshStoryList);
                            }
                        }
                    };
                    downloadingObserver.start();
                }
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Story story = getItem(position);
            final String storyName = story == null ? null : story.getName(Incant.this);
            if (convertView == null) {
                convertView = Incant.this.getLayoutInflater().inflate(R.layout.story, parent, false);
            }
            final TextView download = (TextView) convertView.findViewById(R.id.download);
            final TextView play = (TextView) convertView.findViewById(R.id.play);
            final ProgressBar progressBar = (ProgressBar) convertView.findViewById(R.id.progressbar);
            final ImageView cover = (ImageView) convertView.findViewById(R.id.cover);
            View info = convertView.findViewById(R.id.info);
            cover.setTag(null);
            progressBar.setVisibility(View.GONE);
            if (story == null) {
                info.setVisibility(View.GONE);
                play.setVisibility(View.GONE);
                cover.setVisibility(View.GONE);
                convertView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) {
                        startActivity(new Intent(Incant.this, StoryDownload.class));
                        return true;
                    }
                });
                synchronized (downloading) {
                    if (downloading.contains("")) {
                        download.setVisibility(View.GONE);
                        progressBar.setVisibility(View.VISIBLE);
                        convertView.setOnClickListener(null);
                    } else {
                        download.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                        download.setText(R.string.scrape);
                        convertView.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                download.setVisibility(View.GONE);
                                progressBar.setVisibility(View.VISIBLE);
                                synchronized (downloading) {
                                    downloading.add("");
                                    setDownloadingObserver();
                                }
                                new Thread() {
                                    @Override public void run() {
                                        try {
                                            storyLister.scrape();
                                        } catch (Exception e) {
                                            Log.wtf(TAG,e);
                                        }
                                        synchronized (downloading) {
                                            downloading.remove("");
                                            downloading.notifyAll();
                                        }
                                    }
                                }.start();
                            }
                        });
                    }
                }
            } else {
                info.setVisibility(View.VISIBLE);
                ((TextView) convertView.findViewById(R.id.name)).setText(makeName(story));
                ((TextView) convertView.findViewById(R.id.author)).setText(makeAuthor(story));
                ((TextView) convertView.findViewById(R.id.description)).setText(makeDescription(story));
                convertView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) {
                        Intent intent = new Intent(Incant.this, StoryDetails.class);
                        intent.putExtra(STORY, story);
                        startActivity(intent);
                        return true;
                    }
                });
                if (story.isDownloaded(Incant.this)) {
                    download.setVisibility(View.GONE);
                    convertView.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            if (story.isZcode(Incant.this)) {
                                Intent intent = new Intent(Incant.this, Play.class);
                                intent.putExtra(STORY, story);
                                startActivity(intent);
                            } else {
                                Toast.makeText(Incant.this, "Glulx not implemented", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    play.setVisibility(View.VISIBLE);
                    cover.setVisibility(View.GONE);
                    if (story.getCoverImageFile(Incant.this).exists()) {
                        cover.setTag(story);
                        handler.post(new Runnable() {
                            @Override public void run() {
                                Bitmap image = coverImageCache.get(storyName);
                                if (image == null) {
                                    image = story.getCoverImageBitmap(Incant.this);
                                    coverImageCache.put(storyName, image);
                                }
                                final Bitmap bitmap = image;
                                cover.post(new Runnable() {
                                    @Override public void run() {
                                        if (story == cover.getTag()) {
                                            play.setVisibility(View.GONE);
                                            cover.setVisibility(View.VISIBLE);
                                            cover.setImageBitmap(bitmap);
                                            cover.setTag(null);
                                        }
                                    }
                                });
                            }
                        });
                    }
                } else {
                    play.setVisibility(View.GONE);
                    cover.setVisibility(View.GONE);
                    synchronized (downloading) {
                        if (downloading.contains(storyName)) {
                            download.setVisibility(View.GONE);
                            progressBar.setVisibility(View.VISIBLE);
                            convertView.setOnClickListener(null);
                        } else {
                            download.setVisibility(View.VISIBLE);
                            download.setText(R.string.download);
                            convertView.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(final View v) {
                                    download.setVisibility(View.GONE);
                                    progressBar.setVisibility(View.VISIBLE);
                                    synchronized (downloading) {
                                        downloading.add(storyName);
                                        setDownloadingObserver();
                                    }
                                    new Thread() {
                                        @Override public void run() {
                                            String error = null;
                                            try {
                                                if (!story.download(Incant.this)) {
                                                    error = Incant.this.getString(R.string.download_invalid, story.getName(Incant.this));
                                                }
                                            } catch (Exception e) {
                                                Log.wtf(TAG,e);
                                                error = Incant.this.getString(R.string.download_failed, story.getName(Incant.this));
                                            }
                                            synchronized (downloading) {
                                                downloading.remove(storyName);
                                                downloading.notifyAll();
                                            }
                                            if (error != null) {
                                                final String msg = error;
                                                v.post(new Runnable() {
                                                    @Override public void run() {
                                                        Toast.makeText(Incant.this, msg, Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            }
                                        }
                                    }.start();
                                }
                            });
                        }
                    }
                }
            }
            setDownloadingObserver();
            return convertView;
        }
    }

    public static String getTimeString(Context context, int recentStringId, int stringId, long time) {
        if (time + 86400000L > System.currentTimeMillis()) {
            return context.getString(recentStringId, time);
        } else {
            return context.getString(stringId, time);
        }
    }
}
