package com.wedesign.music.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.wedesign.music.MediaPlaybackService;
import com.wedesign.music.R;
import com.wedesign.music.util.LogUtil;
import com.wedesign.music.util.MusicUtils;

import java.io.File;

public class LibraryBrowserActivity extends Activity implements View.OnClickListener {

    private static final String TAG = LibraryBrowserActivity.class.getName();
    private String[] paths;
    private boolean[] storageState = new boolean[10];
    private Button tvUsb;
    private Button tvSd;
    private Button tvDisk;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library_browser);
        MusicUtils.updateButtonBar(this, R.id.libray_tab);
        getMachineVolumePath();
        mContext = this;
        initView();

        IntentFilter storageFilter = new IntentFilter();
        storageFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        storageFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        storageFilter.addDataScheme("file");
        registerReceiver(mStorageReceiver, storageFilter);

    }

    private void initView() {

        tvUsb = (Button) findViewById(R.id.library_usb);
        tvSd = (Button) findViewById(R.id.library_sd);
        tvDisk = (Button) findViewById(R.id.library_disk);

        tvUsb.setOnClickListener(this);
        tvSd.setOnClickListener(this);
        tvDisk.setOnClickListener(this);

        judgeHighlight();

    }

    private void judgeHighlight() {
        int nStorageSelect = MusicUtils.getIntPref(this, "storage_select", 0);
        setUSBButton(false);
        setSDButton(false);
        setDiskButton(false);

        switch (nStorageSelect) {
            case 0:
                setDiskButton(true);
                break;

            case 1:
                setSDButton(true);
                break;

            case 2:
                setUSBButton(true);
                break;

        }
    }

    private void setSDButton(boolean isHighlight) {
        if (storageState.length >= 2 && storageState[1]) {
            tvSd.setEnabled(true);
            if (isHighlight) {
                tvSd.setBackgroundResource(R.drawable.music_library_sd_highlight_select);
                return;
            }
            tvSd.setBackgroundResource(R.drawable.music_library_sd_select);
            return;
        }
        tvSd.setBackgroundResource(R.drawable.library_sd_disable);
        tvSd.setEnabled(false);
    }


    private void setUSBButton(boolean isHighlight) {
        if (storageState.length >= 3 && storageState[2]) {
            tvUsb.setEnabled(true);
            if (isHighlight) {
                tvUsb.setBackgroundResource(R.drawable.music_library_usb_highlight_select);
                return;
            }
            tvUsb.setBackgroundResource(R.drawable.music_library_usb_select);
            return;
        }
        tvUsb.setBackgroundResource(R.drawable.library_usb_disable);
        tvUsb.setEnabled(false);
    }

    private void setDiskButton(boolean isHighlight) {
        if (storageState[0]) {
            tvDisk.setEnabled(true);
            if (isHighlight) {
                tvDisk.setBackgroundResource(R.drawable.music_library_disk_highlight_select);
                return;
            }
            tvDisk.setBackgroundResource(R.drawable.music_library_disk_select);
            return;
        }
        tvDisk.setBackgroundResource(R.drawable.library_hdd_disable);
        tvDisk.setEnabled(false);
    }


    /**
     * @Title getMachineVolumePath
     * @Throws
     */
    private void getMachineVolumePath() {
        StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        try {
            paths = (String[]) sm.getClass().getMethod("getVolumePaths")
                    .invoke(sm);

            if (paths.length == 4) {
                paths[2] = paths[3];
            }

            for (int i = 0; i < paths.length; i++) {
                storageState[i] = IsPathMounts(paths[i]);
                LogUtil.i(TAG,"getMachineVolumePath "+i+" path="+paths[i]+" state ="+storageState[i]);
            }

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private boolean IsPathMounts(String strPath) {
        String filenameTemp = strPath + "/tmp" + ".txt";
        File dir = new File(strPath);
        if (!dir.exists()) {
            return false;
        }

        File file = new File(filenameTemp);
        if (!file.exists()) {
            try {
                //在指定的文件夹中创建文件
                file.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (file.exists()) {
            file.delete();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onClick(View v) {

        int nIndex = 0;
        switch (v.getId()) {
            case R.id.library_disk:
                nIndex = 0;
                break;
            case R.id.library_sd:
                nIndex = 1;
                break;
            case R.id.library_usb:
                nIndex = 2;
                break;
        }
        MusicUtils.setIntPref(this, "storage_select", nIndex);
        MusicUtils.setStringPref(this, "storage_path", paths[nIndex]);
        MusicUtils.setStringPref(this, "last_mAlbumId", "");
        MusicUtils.setStringPref(this, "last_mArtistId", "");
        judgeHighlight();
        MusicUtils.updateButtonBar(this, R.id.song_tab);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mStorageReceiver);
        super.onDestroy();
    }

    /*
       * This listener gets called when the media scanner starts up or finishes, and
       * when the sd card is unmounted.
       */
    private BroadcastReceiver mStorageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_MEDIA_EJECT.equals(action) ||
                    Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
                getMachineVolumePath();
                judgeHighlight();
                MusicUtils.setStringPref(mContext, "last_mAlbumId", "");
                MusicUtils.setStringPref(mContext, "last_mArtistId", "");
            }

        }
    };
}
