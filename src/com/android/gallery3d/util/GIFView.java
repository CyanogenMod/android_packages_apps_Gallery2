package com.android.gallery3d.util;

import java.io.InputStream;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.net.Uri;
import android.content.res.AssetManager;
import java.io.FileNotFoundException;
import java.io.IOException;
import android.database.Cursor;
import android.widget.ImageView;
import java.io.FileInputStream;

import com.android.gallery3d.R;

import android.content.ContentResolver;
import android.widget.Toast;

public class GIFView extends ImageView implements GifAction{

    private static final String TAG = "GIFView";
	
    private GifDecoder gifDecoder = null;

    private Bitmap currentImage = null;
	
    private static boolean isRun = false;
	
    private static boolean pause = true;

    private int W;
    
    private int H;
	
    private DrawThread drawThread = null;

    Uri mUri;
    private Context mContext;
	
    public GIFView(Context context) {
        super(context);
        mContext=context;
   
    }

    public boolean setDrawable(Uri uri){
        if (null == uri){
            return false;
        }
        isRun = true;
        pause = false;
        mUri = uri;
        int mSize = 0;
        ContentResolver cr = mContext.getContentResolver();
        InputStream input = null;
        try {
            input = cr.openInputStream(uri);
            
            if (input instanceof FileInputStream) {
                FileInputStream f = (FileInputStream) input;
                mSize = (int) f.getChannel().size();
            } else {
                while (-1 != input.read()) {
                    mSize++;
                }
            }

        } catch (IOException e) {
            
        } finally {
           
        }
        //wss , return if file is invalid
        if(mSize == 0){
        	return false;
        }

        if(mSize > 1024*1024){  //gif must be smaller than 1MB
            if (null != input) {
                try {
                    input.close();
                } catch (IOException e) {
                }
            }
            Toast.makeText(mContext, R.string.gif_image_too_large, Toast.LENGTH_LONG).show();
            return false;
        }
        
		setGifDecoderImage(input);
        
        
		
        android.content.ContentResolver resolver = mContext.getContentResolver();
        Cursor c = resolver.query(uri, new String[]{"_data"}, null, null, null);
		
//        if ( c != null && 1 == c.getCount()){
//            c.moveToFirst();
//            
//            AssetManager am = mContext.getAssets();
//            try{
//            	System.out.println(">>>>>>>>>1 "+c.getString(0));
//                setGifDecoderImage(am.open(c.getString(0), AssetManager.ACCESS_RANDOM));
//            }catch(FileNotFoundException e){
//                Log.v(TAG, "e:" + e);
//            }catch(IOException e){
//                Log.v(TAG, "e:" + e);
//            }finally{
//                c.close();
//            }
//            
//            return true;
//        }
//        else{
//        	AssetManager am1 = mContext.getAssets();                           
//            try {
//            	System.out.println(">>>>>>>>2 "+mUri.getPath());
//    			setGifDecoderImage(am1.open(mUri.getPath(), AssetManager.ACCESS_UNKNOWN));
//    		} catch (IOException e1) {
//    			e1.printStackTrace();
//    		}
//    		return true;
//        }
        return true;
    }
    
    private void setGifDecoderImage(InputStream is){
    	 if(gifDecoder != null){
            gifDecoder.free();
            gifDecoder= null;
    	 }
    	 gifDecoder = new GifDecoder(is,this);
    	 gifDecoder.start();
    }
    
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //wangmiao add
        W = ViewGifImage.dm.widthPixels;//480;
        H = ViewGifImage.dm.heightPixels;//800;
        //Log.w(TAG,"the width is "+W +"the hight is "+H);
        if(gifDecoder == null){
            return;
        }
            
        if(currentImage == null){
            currentImage = gifDecoder.getImage();
        }
        if(currentImage == null){
            setImageURI(mUri);  // if can not play this gif, we just try to show it as jpg by parsing mUri, bug: T81-4307
            return;
        }
        setImageURI(null);
        int saveCount = canvas.getSaveCount();
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        //canvas.drawBitmap(currentImage, (W - currentImage.getWidth()) / 2, (H - currentImage.getHeight())/2, null);
        Rect sRect = null;        
        Rect dRect = null;
        
        int imageHeight = currentImage.getHeight();
        int imageWidth = currentImage.getWidth();

        //int newHeight = H/2;
        int newHeight = H;        
        int newWidth = W;
        
        if (newWidth < imageWidth)
        {
            if (newHeight < imageHeight)
            {
                //h big, w big;                            
                //Log.w(TAG," h big, w big");
                if (imageHeight*W > imageWidth*H)
                {
                   //too height                
                   //newHeight = H/2;
                   newWidth = (imageWidth * newHeight)/imageHeight;    
                   //Log.w(TAG," h too big = "+ newHeight+" w big = "+newWidth);                
                }
                else
                {
                    //newWidth = W;
                    newHeight = (imageHeight * newWidth)/imageWidth;   
                    //Log.w(TAG," h big = "+ newHeight+" w too big = "+newWidth);
                }                
                
                //sRect = new Rect(0, 0, currentImage.getWidth(), currentImage.getHeight());                
                dRect = new Rect((W - newWidth) / 2, 0, (W + newWidth) / 2, newHeight);
            }
            else
            {
                //h small, w big;
                newHeight = (imageHeight * newWidth)/imageWidth;
                dRect = new Rect(0, 0, newWidth, newHeight);
            }
            canvas.drawBitmap(currentImage, sRect, dRect, null);
            
        }
        else if (newHeight < imageHeight)
        {
            //h big, w small;        
            newWidth = (imageWidth * newHeight)/imageHeight;
            dRect = new Rect((W - newWidth) / 2, 0, 
                (W + newWidth) / 2, newHeight);    
            canvas.drawBitmap(currentImage, sRect, dRect, null);                
        }
        else
        {
            //h small, w small;
            canvas.drawBitmap(currentImage, (W - imageWidth) / 2, (H - imageHeight) / 2, null);
        }
        
        canvas.restoreToCount(saveCount);
    }
 
    public void parseOk(boolean parseStatus,int frameIndex){
        if(parseStatus){
            if(gifDecoder != null){
                if(frameIndex == -1){
                    if(gifDecoder.getFrameCount() > 1){  
                        if(drawThread == null){
                            drawThread = new DrawThread();
                        } else{
                            drawThread = null;
                            drawThread = new DrawThread();
                        }
                        drawThread.start();
                    }
                }
            }
        }else{
            Log.e("gif","parse error");
        }
    }

    private Handler redrawHandler = new Handler(){
    	public void handleMessage(Message msg) {
           invalidate();
    	}
    };
    
    private class DrawThread extends Thread{
        public void run(){
            if(gifDecoder == null){
                return;
            }
			
            while(isRun){
                if(pause == false){
                    if(!isShown()){
                        isRun = false;
                        pause = true;
                        break;
                    }
                    GifFrame frame = gifDecoder.next();
                    currentImage = frame.image;
                    long sp = frame.delay;
                    if(sp == 0) sp = 200;  //wangmiao add merge from T92
                    if(redrawHandler != null){
                        Message msg = redrawHandler.obtainMessage();
                        redrawHandler.sendMessage(msg);
                        try{
                            Thread.sleep(sp);
                        } catch(InterruptedException e){}
                    }else{
                        break;
                    }
                } else{
                    break;
                }
            }
            isRun = true;
            pause = false;
        }
    }
    public void freeMemory()
    {
        isRun = false;
        pause = true;
        if (drawThread != null)
        {
            //drawThread.isStop = true;
            drawThread = null;
        }
        if (gifDecoder != null)
        {   
            Log.w(TAG," free");
            gifDecoder.free();
            gifDecoder = null;
        }
    }
}



