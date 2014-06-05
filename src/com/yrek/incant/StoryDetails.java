package com.yrek.incant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class StoryDetails extends Activity {
    private static final String TAG = StoryDetails.class.getSimpleName();

    private Story story;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.story_details);
        story = (Story) getIntent().getSerializableExtra(Incant.STORY);
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
            ((TextView) findViewById(R.id.name)).setText(story.getName(StoryDetails.this));
            ((TextView) findViewById(R.id.author)).setText(story.getAuthor(StoryDetails.this));
            ((TextView) findViewById(R.id.headline)).setText(story.getHeadline(StoryDetails.this));
            ((TextView) findViewById(R.id.description)).setText(story.getDescription(StoryDetails.this));
            if (!story.isDownloaded(StoryDetails.this)) {
                findViewById(R.id.play).setVisibility(View.GONE);
                findViewById(R.id.cover).setVisibility(View.GONE);
                findViewById(R.id.progressbar).setVisibility(View.GONE);
                ((Button) findViewById(R.id.downloaddelete)).setText(R.string.download);
                findViewById(R.id.downloaddelete).setVisibility(View.VISIBLE);
                findViewById(R.id.downloaddelete).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(final View v) {
                        v.setVisibility(View.GONE);
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
                ((TextView) findViewById(R.id.downloaddeletetext)).setText(downloadText);
                findViewById(R.id.savecontainer).setVisibility(View.GONE);
                synchronized (Incant.downloading) {
                    if (Incant.downloading.contains(storyName)) {
                        findViewById(R.id.downloaddelete).setVisibility(View.GONE);
                        findViewById(R.id.progressbar).setVisibility(View.VISIBLE);
                        setDownloadingObserver();
                    }
                }
            } else {
                findViewById(R.id.play).setVisibility(View.VISIBLE);
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
                findViewById(R.id.progressbar).setVisibility(View.GONE);
                ((Button) findViewById(R.id.downloaddelete)).setText(R.string.delete_story);
                findViewById(R.id.downloaddelete).setVisibility(View.VISIBLE);
                findViewById(R.id.downloaddelete).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        story.delete(StoryDetails.this);
                        finish();
                    }
                });
                ((TextView) findViewById(R.id.downloaddeletetext)).setText(Incant.getTimeString(StoryDetails.this, R.string.downloaded_recently, R.string.downloaded_at, story.getFile(StoryDetails.this).lastModified()));
                if (!story.getSaveFile(StoryDetails.this).exists()) {
                    findViewById(R.id.savecontainer).setVisibility(View.GONE);
                } else {
                    findViewById(R.id.savecontainer).setVisibility(View.VISIBLE);
                    findViewById(R.id.deletesave).setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            story.getSaveFile(StoryDetails.this).delete();
                            setView.run();
                        }
                    });
                    ((TextView) findViewById(R.id.savetext)).setText(Incant.getTimeString(StoryDetails.this, R.string.saved_recently, R.string.saved_at, story.getSaveFile(StoryDetails.this).lastModified()));
                }
            }
        }
    };
}
