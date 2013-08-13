/**
 * File property dialog
 *
 * @Author wangxuguang
 * 
 * caozhe add this file, for displya gif image, 2011.3.28
 */

package com.android.gallery3d.util;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.ImageView;

import com.android.gallery3d.R;
import android.net.Uri;

import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.util.DisplayMetrics;
import android.content.res.Configuration;

public class ViewGifImage extends Activity
{
    public static final String TAG = "ViewGifImage";
    private String mFileDir;
    public static DisplayMetrics dm;
    ImageView gifView;

    static final int WIDTH = 320;
    static final int HEIGHT = 480;

    
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) 
    {   
        //android.util.Log.d(TAG, "=== onCreate() ===");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_gif_image);
        dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        //add end
        if(getIntent().getAction() != null 
            && getIntent().getAction().equals("hisense.view_gif_image")){
            // i am called by gallery3D or other apps who want to show a gif image
            Uri gifUri = getIntent().getData();
            showGifPicture(gifUri);

            return;
        }

        mFileDir = getIntent().getStringExtra("file_dir");
        Uri gifUri = getIntent().getData();


         showGifPicture(gifUri);
        

    }

    @Override
    public void onResume() 
    {   
        super.onResume();
    }

    @Override
    protected void onStop() 
    {
        super.onStop();
        finish();
    }
    
    @Override
	protected void onDestroy() {
		if (gifView != null){
            if (gifView instanceof GIFView)
            {
                ((GIFView)gifView).freeMemory();
                 gifView = null;
            }
       }
		super.onDestroy();
	}

    private void showGifPicture(Uri gifUri){
        gifView = new GIFView(this);
        ((LinearLayout)findViewById(R.id.image_absoluteLayout)).addView(gifView, new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        if(!((GIFView)gifView).setDrawable(gifUri))
        	finish();         
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    	getWindowManager().getDefaultDisplay().getMetrics(dm);
        super.onConfigurationChanged(newConfig);
    }
   

}
