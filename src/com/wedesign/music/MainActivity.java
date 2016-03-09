package com.wedesign.music;

import android.app.Activity;
import android.os.Bundle;

import com.wedesign.music.util.MusicUtils;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int activeTab = MusicUtils.getIntPref(this, "activetab", R.id.song_tab);
        if (activeTab ==  R.id.libray_tab){
            activeTab = R.id.song_tab;
        }
        if (activeTab != R.id.artist_tab
                && activeTab != R.id.libray_tab
                && activeTab != R.id.song_tab
                && activeTab != R.id.album_tab){
            activeTab = R.id.song_tab;
        }
        MusicUtils.activateTab(this, activeTab);             //启动Activtity
    }

}
