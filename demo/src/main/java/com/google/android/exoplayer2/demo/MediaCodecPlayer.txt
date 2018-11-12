package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * Created by Young on 17/3/23.
 */

public class MediaCodecPlayer extends Activity implements View.OnClickListener{

    private EditText edit;
    private Button play;
    private Button startChooser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_main);

        initView();
    }

    private void initView(){
        edit = (EditText)findViewById(R.id.media_url);
        edit.setText("rtsp://192.168.20.146:554/1");
        play = (Button) findViewById(R.id.play);
        startChooser = (Button) findViewById(R.id.chooser);
        play.setOnClickListener(this);
        startChooser.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent intent = null;
        switch (v.getId()){
            case R.id.play:
                String url = edit.getText().toString();
                if(url != null && !url.equals("")){
                    intent = new Intent(this,PlayerActivity.class);
                    intent.setData(Uri.parse(url));
                    intent.setAction(PlayerActivity.ACTION_VIEW);
                }else{
                    return;
                }
                break;
            case R.id.chooser:
                intent = new Intent(this,SampleChooserActivity.class);
                break;
        }
        if(intent != null){
            startActivity(intent);
        }
    }
}
