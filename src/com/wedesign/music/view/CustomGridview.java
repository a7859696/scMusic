package com.wedesign.music.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.GridView;

import com.wedesign.music.R;

/**
 * Created by chenqi on 15/9/9.
 */
public class CustomGridview extends GridView {
    private Bitmap background;
    Drawable mInterlayer = this.getResources().getDrawable(R.drawable.custom_gridview_bg);//书架图片
    Rect mMyDrawRect = new Rect();//书架的矩形位置

    public CustomGridview(Context context, AttributeSet attrs) {
        super(context, attrs);
        background = BitmapFactory.decodeResource(getResources(),
                R.drawable.custom_gridview_bg);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
//        int count = getChildCount();
//        int top = count > 0 ? getChildAt(0).getTop() : 0;
//        int backgroundWidth = background.getWidth();
//        int backgroundHeight = 400-background.getHeight()+2;
//        int width = getWidth();
//        int height = getHeight();
//
//        for (int y = top; y < height; y += backgroundHeight) {
//            for (int x = 0; x < width; x += backgroundWidth) {
//                canvas.drawBitmap(background, x, y, null);
//            }
//        }
        int count = getChildCount();
        if (count > 0) {     //当有内容时
            View v = getChildAt(0);//获取屏幕的第一个可见的View


            if (v != null) {
                int gridview_height = this.getHeight();                           //整个GirdView的高度
                int interlayerHeight = mInterlayer.getIntrinsicHeight();   //获取图片的高度
                int blockGapHeight = v.getHeight();                                   //子View的高度         
                mMyDrawRect.left = 0;
                mMyDrawRect.right = getWidth();                       
                int initPos = v.getTop()+blockGapHeight-interlayerHeight;
//Log.d("lixuan", "各种高度----gridview_height:"+gridview_height    +"interlayerHeight:"+interlayerHeight+"       blockGapHeight:"+blockGapHeight+"    v.getTop():"+ v.getTop());
//Log.d("lixuan","mMyDrawRect.top: "+mMyDrawRect.top+"        mMyDrawRect.right:"+mMyDrawRect.right);
                for (int i = initPos; i <= gridview_height; i += blockGapHeight) {
                	
                    mMyDrawRect.top = i;
                    mMyDrawRect.bottom = mMyDrawRect.top + interlayerHeight;
                    mInterlayer.setBounds(mMyDrawRect);
                    mInterlayer.draw(canvas);//画书架图片
                }
            }
        }
        super.dispatchDraw(canvas);
    }

}
