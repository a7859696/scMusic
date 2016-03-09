package com.wedesign.music.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.wedesign.music.R;

/**
 * Created by chenqi on 15/9/5.
 */
public class MediaPlaybackAlbumView extends ImageView {
    private Drawable mAlbum;
    private int drawableTop = 0;
    private int drawableBottom = 191;
    private int drawableLeft = 0;
    private int drawableRight = 191;


    public MediaPlaybackAlbumView(Context context) {
        super(context);
    }

    public MediaPlaybackAlbumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAlbum = getResources().getDrawable(R.drawable.music_artist_default);
    }

    public MediaPlaybackAlbumView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAlbumDrawable(Drawable mAlbum) {
        this.mAlbum = mAlbum;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mAlbum.setBounds(drawableLeft, drawableTop, drawableRight, drawableBottom);
        mAlbum.draw(canvas);
    }
}
