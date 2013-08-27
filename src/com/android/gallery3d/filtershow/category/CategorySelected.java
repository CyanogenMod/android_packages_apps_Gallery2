package com.android.gallery3d.filtershow.category;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CategorySelected extends View {
    Paint mPaint = new Paint();

    public CategorySelected(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void onDraw(Canvas canvas) {
        mPaint.reset();
        int margin = 20;
        mPaint.setStrokeWidth(margin);
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(Color.GRAY);
        canvas.drawCircle(getWidth()/2, getHeight()/2, getWidth()/2 - margin, mPaint);
    }

}
