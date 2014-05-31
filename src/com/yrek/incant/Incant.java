package com.yrek.incant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.HashSet;

public class Incant extends Activity {
    private static final String TAG = Incant.class.getSimpleName();

    private StoryLister storyLister;
    private ListView storyList;
    private StoryListAdapter storyListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        storyLister = new StoryLister(this);
        storyList = (ListView) findViewById(R.id.storylist);
        storyListAdapter = new StoryListAdapter();

        storyList.setAdapter(storyListAdapter);
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
                download.setVisibility(View.VISIBLE);
                download.setText(R.string.scrape);
                download.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        download.setVisibility(View.GONE);
                        progressBar.setVisibility(View.VISIBLE);
                        new Thread() {
                            @Override public void run() {
                                try {
                                    storyLister.scrape();
                                } catch (Exception e) {
                                    Log.wtf(TAG,e);
                                }
                                storyList.post(refreshStoryList);
                            }
                        }.start();
                    }
                });
            } else {
                info.setVisibility(View.VISIBLE);
                ((TextView) convertView.findViewById(R.id.name)).setText(story.getName());
                ((TextView) convertView.findViewById(R.id.description)).setText(story.getDescription());
                if (story.isDownloaded(Incant.this)) {
                    download.setVisibility(View.GONE);
                    play.setVisibility(View.VISIBLE);
                    delete.setVisibility(View.VISIBLE);
                    play.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            Log.d(TAG, "play:"+story.getName());
                            Intent intent = new Intent(Incant.this, Play.class);
                            intent.putExtra(Play.STORY, story);
                            startActivity(intent);
                        }
                    });
                    delete.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            story.delete(Incant.this);
                            refreshStoryList();
                        }
                    });
                    if (story.getCoverImageFile(Incant.this).exists()) {
                        cover.setVisibility(View.VISIBLE);
                        cover.setImageBitmap(BitmapFactory.decodeFile(story.getCoverImageFile(Incant.this).getPath()));
                    } else {
                        cover.setVisibility(View.GONE);
                    }
                } else {
                    play.setVisibility(View.GONE);
                    delete.setVisibility(View.GONE);
                    cover.setVisibility(View.GONE);
                    synchronized (downloading) {
                        if (downloading.contains(story.getName())) {
                            download.setVisibility(View.GONE);
                            progressBar.setVisibility(View.VISIBLE);
                        } else {
                            download.setVisibility(View.VISIBLE);
                            download.setText(R.string.download);
                            download.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    download.setVisibility(View.GONE);
                                    progressBar.setVisibility(View.VISIBLE);
                                    new Thread() {
                                        @Override public void run() {
                                            synchronized (downloading) {
                                                downloading.add(story.getName());
                                            }
                                            try {
                                                story.download(Incant.this);
                                            } catch (Exception e) {
                                                Log.wtf(TAG,e);
                                            }
                                            synchronized (downloading) {
                                                downloading.remove(story.getName());
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
