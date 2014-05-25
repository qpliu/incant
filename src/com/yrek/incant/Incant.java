package com.yrek.incant;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

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
        refreshStoryList(new Runnable() {
            @Override public void run() {
                storyList.post(refreshStoryList);
            }
        });
    }

    private final Runnable refreshStoryList = new Runnable() {
        @Override
        public void run() {
            refreshStoryList(null);
        }
    };

    private void refreshStoryList(Runnable onListUpdated) {
        storyListAdapter.setNotifyOnChange(false);
        storyListAdapter.clear();
        try {
            storyListAdapter.addAll(storyLister.getStories(onListUpdated));
        } catch (Exception e) {
            Log.wtf(TAG,e);
        }
        storyListAdapter.notifyDataSetChanged();
    }

    private class StoryListAdapter extends ArrayAdapter<Story> {
        StoryListAdapter() {
            super(Incant.this, R.layout.story);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Story story = getItem(position);
            if (convertView == null) {
                convertView = Incant.this.getLayoutInflater().inflate(R.layout.story, parent, false);
            }
            ((TextView) convertView.findViewById(R.id.name)).setText(story.getName());
            ((TextView) convertView.findViewById(R.id.description)).setText(story.getDescription());
            final Button download = (Button) convertView.findViewById(R.id.download);
            final Button play = (Button) convertView.findViewById(R.id.play);
            final Button delete = (Button) convertView.findViewById(R.id.delete);
            final ProgressBar progressBar = (ProgressBar) convertView.findViewById(R.id.progressbar);
            if (story.isDownloaded(Incant.this)) {
                download.setVisibility(View.GONE);
                play.setVisibility(View.VISIBLE);
                delete.setVisibility(View.VISIBLE);
            } else {
                download.setVisibility(View.VISIBLE);
                play.setVisibility(View.GONE);
                delete.setVisibility(View.GONE);
            }
            progressBar.setVisibility(View.GONE);
            download.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    download.setVisibility(View.GONE);
                    progressBar.setVisibility(View.VISIBLE);
                    new Thread() {
                        @Override public void run() {
                            try {
                                story.download(Incant.this);
                            } catch (Exception e) {
                                Log.wtf(TAG,e);
                            }
                            storyList.post(refreshStoryList);
                        }
                    }.start();
                }
            });
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
                    refreshStoryList(null);
                }
            });
            return convertView;
        }
    }
}
