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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.story_details);
        setStory((Story) getIntent().getSerializableExtra(Incant.STORY));
    }

    private void setStory(final Story story) {
        ((TextView) findViewById(R.id.name)).setText(story.getName(this));
        ((TextView) findViewById(R.id.author)).setText(story.getAuthor(this));
        ((TextView) findViewById(R.id.headline)).setText(story.getHeadline(this));
        ((TextView) findViewById(R.id.description)).setText(story.getDescription(this));
        if (!story.isDownloaded(this)) {
            findViewById(R.id.play).setVisibility(View.GONE);
            findViewById(R.id.cover).setVisibility(View.GONE);
            findViewById(R.id.progressbar).setVisibility(View.GONE);
            ((Button) findViewById(R.id.downloaddelete)).setText(R.string.download);
            findViewById(R.id.savecontainer).setVisibility(View.GONE);
        } else {
            findViewById(R.id.play).setVisibility(View.VISIBLE);
            findViewById(R.id.play).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent intent = new Intent(StoryDetails.this, Play.class);
                    intent.putExtra(Incant.STORY, story);
                    startActivity(intent);
                }
            });
            if (story.getCoverImageFile(this).exists()) {
                findViewById(R.id.cover).setVisibility(View.VISIBLE);
                ((ImageView) findViewById(R.id.cover)).setImageBitmap(story.getCoverImageBitmap(this));
            } else {
                findViewById(R.id.cover).setVisibility(View.GONE);
            }
            findViewById(R.id.progressbar).setVisibility(View.GONE);
            ((Button) findViewById(R.id.downloaddelete)).setText(R.string.delete);
            if (!story.getSaveFile(this).exists()) {
                findViewById(R.id.savecontainer).setVisibility(View.GONE);
            } else {
                findViewById(R.id.savecontainer).setVisibility(View.VISIBLE);
            }
        }            
    }
}
