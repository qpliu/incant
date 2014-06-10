package com.yrek.incant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class StoryDetails extends Activity {
    private static final String TAG = StoryDetails.class.getSimpleName();

    private Story story;
    private TextAppearanceSpan titleStyle;
    private TextAppearanceSpan authorStyle;
    private TextAppearanceSpan headlineStyle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.story_details);
        story = (Story) getIntent().getSerializableExtra(Incant.STORY);
        titleStyle = new TextAppearanceSpan(this, R.style.story_details_title);
        authorStyle = new TextAppearanceSpan(this, R.style.story_details_author);
        headlineStyle = new TextAppearanceSpan(this, R.style.story_details_headline);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setView.run();
    }

    private final Runnable setView = new Runnable() {
        private Thread downloadingObserver = null;

        private void setDownloadingObserver() {
            synchronized (Incant.downloading) {
                if (downloadingObserver == null) {
                    downloadingObserver = new Thread() {
                        @Override
                        public void run() {
                            synchronized (Incant.downloading) {
                                try {
                                    Incant.downloading.wait();
                                } catch (Exception e) {
                                    Log.wtf(TAG,e);
                                }
                                downloadingObserver = null;
                                findViewById(R.id.progressbar).post(setView);
                            }
                        }
                    };
                    downloadingObserver.start();
                }
            }
        }

        @Override
        public void run() {
            final String storyName = story.getName(StoryDetails.this);
            ((TextView) findViewById(R.id.name)).setText(makeName());
            ((TextView) findViewById(R.id.author)).setText(makeAuthor());
            ((TextView) findViewById(R.id.description)).setText(story.getDescription(StoryDetails.this));
            if (!story.isDownloaded(StoryDetails.this)) {
                findViewById(R.id.play_container).setVisibility(View.GONE);
                findViewById(R.id.cover).setVisibility(View.GONE);
                findViewById(R.id.progressbar).setVisibility(View.INVISIBLE);
                ((Button) findViewById(R.id.download_delete)).setText(R.string.download);
                findViewById(R.id.download_delete).setVisibility(View.VISIBLE);
                findViewById(R.id.download_delete).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(final View v) {
                        v.setVisibility(View.INVISIBLE);
                        findViewById(R.id.progressbar).setVisibility(View.VISIBLE);
                        synchronized (Incant.downloading) {
                            Incant.downloading.add(storyName);
                            setDownloadingObserver();
                        }
                        new Thread() {
                            @Override public void run() {
                                try {
                                    story.download(StoryDetails.this);
                                } catch (Exception e) {
                                    Log.wtf(TAG,e);
                                }
                                synchronized (Incant.downloading) {
                                    Incant.downloading.remove(storyName);
                                    Incant.downloading.notifyAll();
                                }
                                v.post(setView);
                            }
                        }.start();
                    }
                });
                String downloadText = story.getDownloadURL(StoryDetails.this).toString();
                String zipEntry = story.getZipEntry(StoryDetails.this);
                if (zipEntry != null) {
                    downloadText = downloadText + "[" + zipEntry + "]";
                }
                ((TextView) findViewById(R.id.download_delete_text)).setText(downloadText);
                findViewById(R.id.download_text).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.download_text)).setText(downloadText);
                findViewById(R.id.save_container).setVisibility(View.GONE);
                synchronized (Incant.downloading) {
                    if (Incant.downloading.contains(storyName)) {
                        findViewById(R.id.download_delete).setVisibility(View.GONE);
                        findViewById(R.id.progressbar).setVisibility(View.VISIBLE);
                        setDownloadingObserver();
                    }
                }
            } else {
                findViewById(R.id.play_container).setVisibility(View.VISIBLE);
                findViewById(R.id.play).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Intent intent = new Intent(StoryDetails.this, Play.class);
                        intent.putExtra(Incant.STORY, story);
                        startActivity(intent);
                    }
                });
                if (story.getCoverImageFile(StoryDetails.this).exists()) {
                    findViewById(R.id.cover).setVisibility(View.VISIBLE);
                    ((ImageView) findViewById(R.id.cover)).setImageBitmap(story.getCoverImageBitmap(StoryDetails.this));
                } else {
                    findViewById(R.id.cover).setVisibility(View.GONE);
                }
                findViewById(R.id.progressbar).setVisibility(View.INVISIBLE);
                ((Button) findViewById(R.id.download_delete)).setText(R.string.delete_story);
                findViewById(R.id.download_delete).setVisibility(View.VISIBLE);
                findViewById(R.id.download_delete).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        story.delete(StoryDetails.this);
                        finish();
                    }
                });
                ((TextView) findViewById(R.id.download_delete_text)).setText(Incant.getTimeString(StoryDetails.this, R.string.downloaded_recently, R.string.downloaded_at, story.getFile(StoryDetails.this).lastModified()));
                if (!story.getSaveFile(StoryDetails.this).exists()) {
                    findViewById(R.id.save_container).setVisibility(View.GONE);
                } else {
                    findViewById(R.id.save_container).setVisibility(View.VISIBLE);
                    findViewById(R.id.delete_save).setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            story.getSaveFile(StoryDetails.this).delete();
                            setView.run();
                        }
                    });
                    ((TextView) findViewById(R.id.save_text)).setText(Incant.getTimeString(StoryDetails.this, R.string.saved_recently, R.string.saved_at, story.getSaveFile(StoryDetails.this).lastModified()));
                }
                findViewById(R.id.download_text).setVisibility(View.GONE);
            }
        }
    };

    private SpannableStringBuilder makeName() {
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

    private SpannableStringBuilder makeAuthor() {
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
}
