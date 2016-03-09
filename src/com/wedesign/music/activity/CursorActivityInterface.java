/**
  * Generated by smali2java 1.0.0.558
  * Copyright (C) 2013 Hensence.com
  */

package com.wedesign.music.activity;

import android.content.Context;
import android.app.Activity;
import com.wedesign.music.adapter.TrackQueryHandler;

import android.database.Cursor;

public interface CursorActivityInterface {
    
    public abstract Context getContext();
    
    
    public abstract Activity getCursorActivity();
    
    
    public abstract Cursor getTrackCursor();
    
    
    public abstract Cursor getTrackCursor(TrackQueryHandler p1, String filter, boolean async);
    
    
    public abstract void init(Cursor cursor, boolean p2);
    
    
    public abstract void setTrackCursor(Cursor p1);
    
}