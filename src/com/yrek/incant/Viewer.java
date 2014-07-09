package com.yrek.incant;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.InputStream;

public class Viewer extends Activity {
    private static final String TAG = Viewer.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        Log.d(TAG,"intent="+intent);
        if (!intent.getAction().equals(Intent.ACTION_VIEW)) {
            finish();
            return;
        }
        String title = null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(intent.getData(), new String[] { "title" }, null, null, null);
            if (cursor.getCount() >= 1) {
                cursor.moveToFirst();
                title = cursor.getString(0);
            }
        } finally {
            cursor.close();
        }
        if (title == null) {
            finish();
            return;
        } else {
            int index = title.lastIndexOf('/');
            if (index > 0) {
                title = title.substring(index+1);
            }
            index = title.indexOf('.');
            if (index > 0) {
                title = title.substring(0, index);
            }
        }
        Log.d(TAG,"title="+title);
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(intent.getData());
            Story story = new Story(title, null, null, null, null, null, null);
            if (story.download(this, in)) {
                intent = new Intent(this, StoryDetails.class);
                intent.putExtra(Incant.STORY, story);
                startActivity(intent);
                return;
            }
        } catch (Exception e) {
            Log.wtf(TAG,e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    Log.wtf(TAG,e);
                }
            }
        }
        Toast.makeText(this, getString(R.string.download_invalid, title), Toast.LENGTH_SHORT).show();
        finish();
    }
}
