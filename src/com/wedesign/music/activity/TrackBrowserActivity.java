package com.wedesign.music.activity;

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AlphabetIndexer;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.wedesign.music.IMediaPlaybackService;
import com.wedesign.music.MediaPlaybackService;
import com.wedesign.music.R;
import com.wedesign.music.adapter.TrackListAdapter;
import com.wedesign.music.adapter.TrackQueryHandler;
import com.wedesign.music.util.MusicUtils;
import com.wedesign.music.view.TrackPlayingView;

public class TrackBrowserActivity extends Activity implements ServiceConnection,CursorActivityInterface, AdapterView.OnItemClickListener {

    private TrackPlayingView track_play;
    private ListView tracksList;
    public Cursor mTrackCursor;
    private String mSortOrder;
    private String mGenre;
    private String[] mCursorCols;
    private String[] mPlaylistMemberCols;
    private String mPlaylist;
    private TrackListAdapter mAdapter;
    private String mAlbumId;
    private String mArtistId;
    private MusicUtils.ServiceToken mToken;
    private Context mContext;
    private IMediaPlaybackService mService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("lixuan", "TrackBrowserActivity----onCreate");
        mContext = this;
        setContentView(R.layout.activity_track_browser);        //布局由3个layout组成。一个是左边的GroupButton,一个是listview,一个是隐藏的
        MusicUtils.updateButtonBar(this, R.id.song_tab);        

        setVolumeControlStream(AudioManager.STREAM_MUSIC); //设置该Activity中音量控制键控制的音频流，

        initView();

        mCursorCols = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION
        };
        mPlaylistMemberCols = new String[]{
                MediaStore.Audio.Playlists.Members._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Playlists.Members.PLAY_ORDER,
                MediaStore.Audio.Playlists.Members.AUDIO_ID,
                MediaStore.Audio.Media.IS_MUSIC
        };

        mAdapter = (TrackListAdapter) getLastNonConfigurationInstance();

        if (mAdapter != null) {
           // mAdapter.setActivity(this);
         	Log.d("lixuan", "mAdapter!=null");
            tracksList.setAdapter(mAdapter);
        }
        mToken = MusicUtils.bindToService(this, this);

    }

    private void initView() {
        track_play = (TrackPlayingView) findViewById(R.id.track_play);
        tracksList = (ListView) findViewById(R.id.tracks);
        tracksList.setOnItemClickListener(this);
        track_play.setVisibility(View.VISIBLE);


    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {                     //绑定完后进行下面的查询操作
    	Log.d("lixuan", "TrackBrowserActivity = = = =onServiceConnected ");
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        f.addDataScheme("file");
        registerReceiver(mScanListener, f);


        IntentFilter f1 = new IntentFilter();
        f1.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f1.addAction(MediaPlaybackService.META_CHANGED);
        registerReceiver(mPlayListener, f1);


        
        if (mAdapter == null) {
        	Log.d("lixuan", "TrackBrowserActivity----mAdapter==null");
            //Log.i("@@@", "starting query");
            mAdapter = new TrackListAdapter(                                                                  //创建Adpter的对象。
                    getApplication(), // need to use application context to avoid leaks
                    this, R.layout.track_list_item,
                    null, // cursor
                    new String[]{},
                    new int[]{});
            tracksList.setAdapter(mAdapter);
            //setTitle(R.string.working_songs);
            getTrackCursor(mAdapter.getQueryHandler(), null, true);
        } else {
            mTrackCursor = mAdapter.getCursor();                                            //  mTrackCursor  的获得
            // If mTrackCursor is null, this can be because it doesn't have
            // a cursor yet (because the initial query that sets its cursor
            // is still in progress), or because the query failed.
            // In order to not flash the error dialog at the user for the
            // first case, simply retry the query when the cursor is null.
            // Worst case, we end up doing the same query twice.
            if (mTrackCursor != null) {
                init(mTrackCursor, false);
            } else { 
                // setTitle(R.string.working_songs);
                getTrackCursor(mAdapter.getQueryHandler(), null, true);           
            }
        }

        mService = IMediaPlaybackService.Stub.asInterface(service);
        track_play.setIMediaPlaybackService(mService);             //把IBindler  传给TrackPlayingView
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        finish();
    }

    public void init(Cursor newCursor, boolean isLimited) {

        if (mAdapter == null) {
            return;
        }
    	Log.d("lixuan", "6");
        mAdapter.changeCursor(newCursor); // also sets mTrackCursor

        if (mTrackCursor == null) {
//            MusicUtils.displayDatabaseError(this);
//            closeContextMenu();
//            mReScanHandler.sendEmptyMessageDelayed(0, 1000);
            return;
        }

        //  MusicUtils.hideDatabaseError(this);
        // mUseLastListPos = MusicUtils.updateButtonBar(this, R.id.songtab);
        //   setTitle();

        // Restore previous position
//        if (mLastListPosCourse >= 0 && mUseLastListPos) {
//            ListView lv = getListView();
//            // this hack is needed because otherwise the position doesn't change
//            // for the 2nd (non-limited) cursor
//            lv.setAdapter(lv.getAdapter());
//            lv.setSelectionFromTop(mLastListPosCourse, mLastListPosFine);
//            if (!isLimited) {
//                mLastListPosCourse = -1;
//            }
//        }
//
//        // When showing the queue, position the selection on the currently playing track
//        // Otherwise, position the selection on the first matching artist, if any
//        IntentFilter f = new IntentFilter();
//        f.addAction(MediaPlaybackService.META_CHANGED);
//        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
//        if ("nowplaying".equals(mPlaylist)) {
//            try {
//                int cur = MusicUtils.sService.getQueuePosition();
//                setSelection(cur);
//                registerReceiver(mNowPlayingListener, new IntentFilter(f));
//                mNowPlayingListener.onReceive(this, new Intent(MediaPlaybackService.META_CHANGED));
//            } catch (RemoteException ex) {
//            }
//        } else {
//            String key = getIntent().getStringExtra("artist");
//            if (key != null) {
//                int keyidx = mTrackCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID);
//                mTrackCursor.moveToFirst();
//                while (! mTrackCursor.isAfterLast()) {
//                    String artist = mTrackCursor.getString(keyidx);
//                    if (artist.equals(key)) {
//                        setSelection(mTrackCursor.getPosition());
//                        break;
//                    }
//                    mTrackCursor.moveToNext();
//                }
//            }
//            registerReceiver(mTrackListListener, new IntentFilter(f));
//            mTrackListListener.onReceive(this, new Intent(MediaPlaybackService.META_CHANGED));
//        }
    }

    @Override
    public void setTrackCursor(Cursor cursor) {
        mTrackCursor = cursor;
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public Activity getCursorActivity() {
        return this;
    }

    @Override
    public Cursor getTrackCursor() {
        return mTrackCursor;
    }

    public Cursor getTrackCursor(TrackQueryHandler queryhandler, String filter,
                                 boolean async) {
    	Log.d("lixuan", "filter=:   "+filter+"     async:"+async);
        if (queryhandler == null) {
            throw new IllegalArgumentException();
        }

        Cursor ret = null;
        mSortOrder = MediaStore.Audio.Media.TITLE_KEY;
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");

        String strStoragePath = MusicUtils.getStringPref(this, "storage_path", "");
        Log.d("lixuan", "where==:   "+where.toString()+"     strStoragePath:"+strStoragePath);
        //专辑
        if (mAlbumId != null) {
            Log.d("lixuan", "mAlbumld:"+mAlbumId);
            where.append(" AND " + MediaStore.Audio.Media.ALBUM_ID + "=" + mAlbumId);
            mSortOrder = MediaStore.Audio.Media.TRACK + ", " + mSortOrder;
        }
        //艺术家
        if (mArtistId != null) {
        	   Log.d("lixuan", "mArtistId:"+mArtistId);
            where.append(" AND " + MediaStore.Audio.Media.ARTIST_ID + "=" + mArtistId);
        }

        if (strStoragePath != null && strStoragePath.length() != 0) {
            where.append(" AND " + MediaStore.Audio.Media.DATA + " LIKE '" + strStoragePath + "%'");
        }

        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        Log.d("lixuan", "where最终：    "+where.toString());
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;       //URI  取得所有歌曲的信息
        if (!TextUtils.isEmpty(filter)) {
            uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
        }
      
        
        ret = queryhandler.doQuery(uri,
                mCursorCols, where.toString(), null, mSortOrder, async);           

        // This special case is for the "nowplaying" cursor, which cannot be handled
        // asynchronously using AsyncQueryHandler, so we do some extra initialization here.
        if (ret != null && async) {
            init(ret, false);
            //  setTitle();
        }
        return ret;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MusicUtils.unbindFromService(mToken);


        // If we have an adapter and didn't send it off to another activity yet, we should
        // close its cursor, which we do by assigning a null cursor to it. Doing this
        // instead of closing the cursor directly keeps the framework from accessing
        // the closed cursor later.
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
        // Because we pass the adapter to the next activity, we need to make
        // sure it doesn't keep a reference to this activity. We can do this
        // by clearing its DatasetObservers, which setListAdapter(null) does.
        tracksList.setAdapter(null);
        mAdapter = null;
        unregisterReceiverSafe(mScanListener);
        unregisterReceiverSafe(mPlayListener);
    }

    /**
     * Unregister a receiver, but eat the exception that is thrown if the
     * receiver was never registered to begin with. This is a little easier
     * than keeping track of whether the receivers have actually been
     * registered by the time onDestroy() is called.
     */
    private void unregisterReceiverSafe(BroadcastReceiver receiver) {
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }

    /**
     * listview的Item  的click。  点击之后的效果是：播放歌曲
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    	Log.d("lixuan", "TrackBrowserActivity---onItemClick--position:  "+position);
        MusicUtils.playAll(this, mTrackCursor, position);
        mAdapter.notifyDataSetChanged();
    }


    /*
   * This listener gets called when the media scanner starts up or finishes, and
   * when the sd card is unmounted.
   */
    private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action) ||
                    Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
             Log.d("lixuan", "BroadcastReceiver-----mScanListener--S-F");
                mReScanHandler.sendEmptyMessage(0);
            }else if (MediaPlaybackService.PLAYSTATE_CHANGED.equals(action) ||
                    MediaPlaybackService.META_CHANGED.equals(action)){
            	Log.d("lixuan", "BroadcastReceiver-----mScanListener--C-C");
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            }

        }
    };

    private BroadcastReceiver mPlayListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
         if (MediaPlaybackService.PLAYSTATE_CHANGED.equals(action) ||
                    MediaPlaybackService.META_CHANGED.equals(action)){
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            }

        }
    };



    private Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getTrackCursor(mAdapter.getQueryHandler(), null, true);
            }
            // if the query results in a null cursor, onQueryComplete() will
            // call init(), which will post a delayed message to this handler
            // in order to try again.
        }
    };
}
