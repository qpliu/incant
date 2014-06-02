package com.yrek.incant;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.HashSet;

public class Incant extends Activity {
    private static final String TAG = Incant.class.getSimpleName();

    private StoryLister storyLister;
    private ListView storyList;
    private StoryListAdapter storyListAdapter;

    private TextAppearanceSpan titleStyle;
    private TextAppearanceSpan authorStyle;
    private TextAppearanceSpan headlineStyle;
    private TextAppearanceSpan descriptionStyle;
    private TextAppearanceSpan saveTimeStyle;
    private TextAppearanceSpan downloadTimeStyle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        storyLister = new StoryLister(this);
        storyList = (ListView) findViewById(R.id.storylist);
        storyListAdapter = new StoryListAdapter();

        storyList.setAdapter(storyListAdapter);

        titleStyle = new TextAppearanceSpan(this, R.style.storytitle);
        authorStyle = new TextAppearanceSpan(this, R.style.storyauthor);
        headlineStyle = new TextAppearanceSpan(this, R.style.storyheadline);
        descriptionStyle = new TextAppearanceSpan(this, R.style.storydescription);
        saveTimeStyle = new TextAppearanceSpan(this, R.style.storysavetime);
        downloadTimeStyle = new TextAppearanceSpan(this, R.style.storydownloadtime);
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
        String author = story.getAuthor(this);
        if (author != null) {
            sb.append(' ');
            start = sb.length();
            sb.append(author);
            sb.setSpan(authorStyle, start, sb.length(), 0);
        }
        String headline = story.getHeadline(this);
        if (headline != null) {
            sb.append(' ');
            start = sb.length();
            sb.append(headline);
            sb.setSpan(headlineStyle, start, sb.length(), 0);
        }
        return sb;
    }

    private SpannableStringBuilder makeDescription(Story story) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String description = story.getDescription(this);
        File saveFile = story.getSaveFile(this);
        File storyFile = story.getFile(this);
        int start = sb.length();
        if (saveFile.exists()) {
            if (saveFile.lastModified() + 86400000L > System.currentTimeMillis()) {
                sb.append(getString(R.string.saved_recently, saveFile.lastModified()));
            } else {
                sb.append(getString(R.string.saved_at, saveFile.lastModified()));
            }
            sb.setSpan(saveTimeStyle, start, sb.length(), 0);
        } else if (description != null) {
            sb.append(description);
            sb.setSpan(descriptionStyle, start, sb.length(), 0);
        } else if (storyFile.exists()) {
            if (storyFile.lastModified() + 86400000L > System.currentTimeMillis()) {
                sb.append(getString(R.string.downloaded_recently, storyFile.lastModified()));
            } else {
                sb.append(getString(R.string.downloaded_at, storyFile.lastModified()));
            }
            sb.setSpan(downloadTimeStyle, start, sb.length(), 0);
        }
        return sb;
    }

    private class StoryListAdapter extends ArrayAdapter<Story> {
        private HashSet<String> downloading = new HashSet<String>();

        StoryListAdapter() {
            super(Incant.this, R.layout.story);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Story story = getItem(position);
            if (convertView == null) {
                convertView = Incant.this.getLayoutInflater().inflate(R.layout.story, parent, false);
            }
            final Button download = (Button) convertView.findViewById(R.id.download);
            final Button play = (Button) convertView.findViewById(R.id.play);
            final Button delete = (Button) convertView.findViewById(R.id.delete);
            final ProgressBar progressBar = (ProgressBar) convertView.findViewById(R.id.progressbar);
            final ImageView cover = (ImageView) convertView.findViewById(R.id.cover);
            View info = convertView.findViewById(R.id.info);
            progressBar.setVisibility(View.GONE);
            if (story == null) {
                info.setVisibility(View.GONE);
                download.setVisibility(View.VISIBLE);
                play.setVisibility(View.GONE);
                delete.setVisibility(View.GONE);
                play.setVisibility(View.GONE);
                delete.setVisibility(View.GONE);
                cover.setVisibility(View.GONE);
                synchronized (downloading) {
                    if (downloading.contains("")) {
                        download.setVisibility(View.GONE);
                        progressBar.setVisibility(View.VISIBLE);
                    } else {
                        download.setVisibility(View.VISIBLE);
                        download.setText(R.string.scrape);
                        download.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                download.setVisibility(View.GONE);
                                progressBar.setVisibility(View.VISIBLE);
                                synchronized (downloading) {
                                    downloading.add("");
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
                                        }
                                        storyList.post(refreshStoryList);
                                    }
                                }.start();
                            }
                        });
                    }
                }
            } else {
                info.setVisibility(View.VISIBLE);
                ((TextView) convertView.findViewById(R.id.name)).setText(makeName(story));
                ((TextView) convertView.findViewById(R.id.description)).setText(makeDescription(story));
                if (story.isDownloaded(Incant.this)) {
                    download.setVisibility(View.GONE);
                    delete.setVisibility(View.VISIBLE);
                    View.OnClickListener clickPlay = new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            Log.d(TAG, "play:"+story.getName(Incant.this));
                            Intent intent = new Intent(Incant.this, Play.class);
                            intent.putExtra(Play.STORY, story);
                            startActivity(intent);
                        }
                    };
                    play.setOnClickListener(clickPlay);
                    cover.setOnClickListener(clickPlay);
                    delete.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            story.delete(Incant.this);
                            refreshStoryList();
                        }
                    });
                    if (story.getCoverImageFile(Incant.this).exists()) {
                        play.setVisibility(View.GONE);
                        cover.setVisibility(View.VISIBLE);
                        cover.setImageBitmap(story.getCoverImageBitmap(Incant.this));
                    } else {
                        play.setVisibility(View.VISIBLE);
                        cover.setVisibility(View.GONE);
                    }
                } else {
                    play.setVisibility(View.GONE);
                    delete.setVisibility(View.GONE);
                    cover.setVisibility(View.GONE);
                    synchronized (downloading) {
                        if (downloading.contains(story.getName(Incant.this))) {
                            download.setVisibility(View.GONE);
                            progressBar.setVisibility(View.VISIBLE);
                        } else {
                            download.setVisibility(View.VISIBLE);
                            download.setText(R.string.download);
                            download.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    download.setVisibility(View.GONE);
                                    progressBar.setVisibility(View.VISIBLE);
                                    synchronized (downloading) {
                                        downloading.add(story.getName(Incant.this));
                                    }
                                    new Thread() {
                                        @Override public void run() {
                                            try {
                                                story.download(Incant.this);
                                            } catch (Exception e) {
                                                Log.wtf(TAG,e);
                                            }
                                            synchronized (downloading) {
                                                downloading.remove(story.getName(Incant.this));
                                            }
                                            storyList.post(refreshStoryList);
                                        }
                                    }.start();
                                }
                            });
                        }
                    }
                }
            }
            return convertView;
        }
    }
}
