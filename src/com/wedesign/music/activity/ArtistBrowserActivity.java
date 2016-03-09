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
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.AlphabetIndexer;
import android.widget.GridView;
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
import com.wedesign.music.view.CustomGridview;
import com.wedesign.music.view.TrackPlayingView;

public class ArtistBrowserActivity extends Activity implements ServiceConnection, AdapterView.OnItemClickListener, CursorActivityInterface {

    private String[] mCursorCols;
    private MusicUtils.ServiceToken mToken;
    private Cursor mArtistCursor;
    private Cursor mTrackCursor;
    private ArtistListAdapter mAdapter;
    private TrackPlayingView track_play;
    private CustomGridview artistGridView;
    private ListView tracksList;
    private Context mContext;
    private Resources mResources;
    private Drawable mMaskDrawable;
    public  String mArtistId = "";
    private String mSortOrder;
    private TrackListAdapter mTrackAdapter;

    private View mGridListContainer;
    private View mListContainer;
    private View mTrackPlayingView;
    private boolean isShowPlaying = false;
    private TranslateAnimation mPlayInAnimation;
    private TranslateAnimation mPlayOutAnimation;
    private TranslateAnimation mArtistInAnimation;
    private TranslateAnimation mArtistOutAnimation;
    private IMediaPlaybackService mService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;

        setContentView(R.layout.activity_artist_browser);
        MusicUtils.updateButtonBar(this, R.id.artist_tab);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mArtistId = MusicUtils.getStringPref(this, "last_mArtistId", "");

        initView();
        initAnimation();

        mCursorCols = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION
        };

        mAdapter = (ArtistListAdapter) getLastNonConfigurationInstance();

        if (mAdapter != null) {
            mAdapter.setActivity(this);
            artistGridView.setAdapter(mAdapter);
        }
        mToken = MusicUtils.bindToService(this, this);

    }

    private void initAnimation() {

        mArtistInAnimation = new TranslateAnimation(-409f, 0f, 0, 0);
        mArtistOutAnimation = new TranslateAnimation(0, -409f, 0, 0);
        mPlayInAnimation = new TranslateAnimation(409f, 0, 0, 0);
        mPlayOutAnimation = new TranslateAnimation(409f, 800f, 0, 0);

    }

    private void initView() {

        mGridListContainer = findViewById(R.id.grid_container);
        artistGridView = (CustomGridview) findViewById(R.id.list);
        
        mListContainer = findViewById(R.id.list_container);
        mTrackPlayingView = findViewById(R.id.track_play);
   
        track_play = (TrackPlayingView) findViewById(R.id.track_play);
        tracksList = (ListView) findViewById(R.id.tracks);

        mTrackAdapter = new TrackListAdapter(
                getApplication(), // need to use application context to avoid leaks
                this, R.layout.track_list_item,
                null, // cursor
                new String[]{},
                new int[]{});
        tracksList.setAdapter(mTrackAdapter);

        tracksList.setOnItemClickListener(new OnTrackItemClickListener());


        mResources = getResources();
        mMaskDrawable = mResources.getDrawable(R.drawable.mask_panel);

//        artistGridView.enableShowAp(true);
//        artistGridView.setMaskPanel(mMaskDrawable);
//        artistGridView.setPanelOffset(0);
        artistGridView.setOnItemClickListener(this);
        artistGridView.setSelector(new ColorDrawable(Color.TRANSPARENT));       //点击girdview的时候背景变

    }

    @Override
    protected void onDestroy() {

        MusicUtils.unbindFromService(mToken);


        // If we have an adapter and didn't send it off to another activity yet, we should
        // close its cursor, which we do by assigning a null cursor to it. Doing this
        // instead of closing the cursor directly keeps the framework from accessing
        // the closed cursor later.
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
        if (mTrackAdapter != null) {
            mTrackAdapter.changeCursor(null);
        }
        // Because we pass the adapter to the next activity, we need to make
        // sure it doesn't keep a reference to this activity. We can do this
        // by clearing its DatasetObservers, which setListAdapter(null) does.
        tracksList.setAdapter(null);
        artistGridView.setAdapter(null);

        mAdapter = null;
        mTrackAdapter = null;
        MusicUtils.setStringPref(this, "last_mArtistId", mArtistId);
        unregisterReceiverSafe(mScanListener);
        unregisterReceiverSafe(mPlayListener);
        super.onDestroy();
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

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
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
            //Log.i("@@@", "starting query");
            mAdapter = new ArtistListAdapter(
                    getApplication(), // need to use application context to avoid leaks
                    this, R.layout.track_list_item_grid,
                    null, // cursor
                    new String[]{},
                    new int[]{});
            artistGridView.setAdapter(mAdapter);
            //setTitle(R.string.working_songs);
            getArtistCursor(mAdapter.getQueryHandler(), null);
        } else {
            mArtistCursor = mAdapter.getCursor();
            if (mArtistCursor != null) {
                initArtist(mArtistCursor);
            } else {
                // setTitle(R.string.working_songs);
                getArtistCursor(mAdapter.getQueryHandler(), null);
            }
        }

        if (mArtistId.length() != 0){
            getTrackCursor(mTrackAdapter.getQueryHandler(), null, true);
        }

        mService = IMediaPlaybackService.Stub.asInterface(service);
        track_play.setIMediaPlaybackService(mService);
    }

    private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            mReScanHandler.sendEmptyMessage(0);
            if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                MusicUtils.clearAlbumArtCache();
            }

        }
    };

    private Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            if (mAdapter != null) {
                getArtistCursor(mAdapter.getQueryHandler(), null);
            }


            if (mTrackAdapter != null) {
                getTrackCursor(mTrackAdapter.getQueryHandler(), null, true);
            }
            // if the query results in a null cursor, onQueryComplete() will
            // call init(), which will post a delayed message to this handler
            // in order to try again.
        }
    };

    private void showArtistList(boolean animation) {
        mGridListContainer.setVisibility(View.VISIBLE);
        mListContainer.setVisibility(View.VISIBLE);
        mTrackPlayingView.setVisibility(View.GONE);
        if (animation) {
            mArtistInAnimation.setDuration(300);
            mGridListContainer.startAnimation(mArtistInAnimation);
            mListContainer.startAnimation(mArtistInAnimation);
        }
        isShowPlaying = false;
    }

    private void showPlayList(boolean animation) {
        mGridListContainer.setVisibility(View.GONE);
        mListContainer.setVisibility(View.VISIBLE);
        mTrackPlayingView.setVisibility(View.VISIBLE);
        if (animation) {
            mArtistOutAnimation.setDuration(300);
            mGridListContainer.startAnimation(mArtistOutAnimation);
            mPlayInAnimation.setDuration(300);
            mListContainer.startAnimation(mPlayInAnimation);
            mTrackPlayingView.startAnimation(mPlayInAnimation);
        }
        isShowPlaying = true;
    }


    @Override
    public void onBackPressed() {
        if (isShowPlaying) {
            showArtistList(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        finish();
    }

    public void initArtist(Cursor c) {

        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(c); // also sets mAlbumCursor

    }

//    private Cursor getArtistCursor(AsyncQueryHandler async, String filter) {
//        String[] cols = new String[]{
//                MediaStore.Audio.Artists._ID,
//                MediaStore.Audio.Artists.ARTIST,
//                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
//                MediaStore.Audio.Artists.NUMBER_OF_TRACKS
//        };
//
//
//        Cursor ret = null;
//        Uri uri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
//        if (!TextUtils.isEmpty(filter)) {
//            uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
//        }
//        if (async != null) {
//            async.startQuery(0, null,
//                    uri,
//                    cols, null, null, MediaStore.Audio.Artists.DEFAULT_SORT_ORDER);
//        } else {
//            ret = MusicUtils.query(this, uri,
//                    cols, null, null, MediaStore.Audio.Artists.DEFAULT_SORT_ORDER);
//        }
//        return ret;
//    }

    private Cursor getArtistCursor(AsyncQueryHandler async, String filter) {
        String[] cols = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ARTIST,
        };


        Cursor ret = null;

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");

        //路径过滤
        String strStoragePath = MusicUtils.getStringPref(this, "storage_path", "");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");

        if (strStoragePath != null && strStoragePath.length() != 0) {
            where.append(" AND " + MediaStore.Audio.Media.DATA + " LIKE '" + strStoragePath + "/%'");
        }
        //重复数据排序
        where.append(") GROUP BY (" + MediaStore.Audio.Media.ARTIST_ID);

        if (!TextUtils.isEmpty(filter)) {
            uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
        }


        if (async != null) {
            async.startQuery(0, null,
                    uri,
                    cols, where.toString(), null, MediaStore.Audio.Artists.DEFAULT_SORT_ORDER);
        } else {
            ret = MusicUtils.query(this, uri,
                    cols, where.toString(), null, MediaStore.Audio.Artists.DEFAULT_SORT_ORDER);
        }


        return ret;
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mArtistId = String.valueOf(id);
        if (mArtistCursor != null) {
            mArtistId = mArtistCursor.getString(mArtistCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID));
        }
        getTrackCursor(mTrackAdapter.getQueryHandler(), null, true);
        mAdapter.notifyDataSetChanged();
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

    @Override
    public Cursor getTrackCursor(TrackQueryHandler queryhandler, String filter,
                                 boolean async) {

        if (queryhandler == null) {
            throw new IllegalArgumentException();
        }

        Cursor ret = null;
        mSortOrder = MediaStore.Audio.Media.TITLE_KEY;
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");

        String strStoragePath = MusicUtils.getStringPref(this, "storage_path", "");

        //专辑
//        if (mAlbumId != null) {
//            where.append(" AND " + MediaStore.Audio.Media.ALBUM_ID + "=" + mAlbumId);
//            mSortOrder = MediaStore.Audio.Media.TRACK + ", " + mSortOrder;
//        }
        //艺术家
        if (mArtistId != null) {
            where.append(" AND " + MediaStore.Audio.Media.ARTIST_ID + "=" + mArtistId);
        }

        if (strStoragePath != null && strStoragePath.length() != 0) {
            where.append(" AND " + MediaStore.Audio.Media.DATA + " LIKE '" + strStoragePath + "/%'");
        }

        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
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
    public void init(Cursor newCursor, boolean p2) {

        if (mTrackAdapter == null) {
            return;
        }
        mTrackAdapter.changeCursor(newCursor); // also sets mTrackCursor

        if (mTrackCursor == null) {
//            MusicUtils.displayDatabaseError(this);
//            closeContextMenu();
//            mReScanHandler.sendEmptyMessageDelayed(0, 1000);
            return;
        }

    }

    @Override
    public void setTrackCursor(Cursor p1) {
        this.mTrackCursor = p1;
    }


    class ArtistListAdapter extends SimpleCursorAdapter implements SectionIndexer {

        //    private final Drawable mNowPlayingOverlay;
        private final BitmapDrawable mDefaultAlbumIcon;
        private int mAlbumIdx;
        private int mArtistIdx;
        private int mGroupArtistIdx;
        private final Resources mResources;
        private final StringBuilder mStringBuilder = new StringBuilder();
        private final String mUnknownAlbum;
        private final String mUnknownArtist;
        private final String mAlbumSongSeparator;
        private final Object[] mFormatArgs = new Object[1];
        private AlphabetIndexer mIndexer;
        private ArtistBrowserActivity mActivity;
        private AsyncQueryHandler mQueryHandler;
        private String mConstraint = null;
        private boolean mConstraintIsValid = false;

        class ViewHolder {
            TextView album_title;
            TextView artist_title;
            ImageView music_image_song;
        }

        class QueryHandler extends AsyncQueryHandler {
            QueryHandler(ContentResolver res) {
                super(res);
            }

            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                //Log.i("@@@", "query complete");
                mActivity.initArtist(cursor);
            }
        }

        ArtistListAdapter(Context context, ArtistBrowserActivity currentactivity,
                          int layout, Cursor cursor, String[] from, int[] to) {
            super(context, layout, cursor, from, to);

            mActivity = currentactivity;
            mQueryHandler = new QueryHandler(context.getContentResolver());

            mUnknownAlbum = context.getString(R.string.unknown_album_name);
            mUnknownArtist = context.getString(R.string.unknown_artist_name);
            mAlbumSongSeparator = context.getString(R.string.albumsongseparator);

            Resources r = context.getResources();
            // mNowPlayingOverlay = r.getDrawable(R.drawable.indicator_ic_mp_playing_list);

            Bitmap b = BitmapFactory.decodeResource(r, R.drawable.artwork_album_default);
            mDefaultAlbumIcon = new BitmapDrawable(context.getResources(), b);
            // no filter or dither, it's a lot faster and we can't tell the difference
            mDefaultAlbumIcon.setFilterBitmap(false);
            mDefaultAlbumIcon.setDither(false);
            getColumnIndices(cursor);
            mResources = context.getResources();
        }

        private void getColumnIndices(Cursor cursor) {
            if (cursor != null) {
                mArtistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID);
                mGroupArtistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);

                if (mIndexer != null) {
                    mIndexer.setCursor(cursor);
                } else {
//                    mIndexer = new MusicAlphabetIndexer(cursor, mAlbumIdx, mResources.getString(
//                            R.string.fast_scroll_alphabet));
                }
            }
        }

        public void setActivity(ArtistBrowserActivity newactivity) {
            mActivity = newactivity;
        }

        public AsyncQueryHandler getQueryHandler() {
            return mQueryHandler;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = super.newView(context, cursor, parent);
            ViewHolder vh = new ViewHolder();
            vh.album_title = (TextView) v.findViewById(R.id.album_title);
            vh.artist_title = (TextView) v.findViewById(R.id.artist_title);
            vh.music_image_song = (ImageView) v.findViewById(R.id.music_image_song);
            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {

            ViewHolder vh = (ViewHolder) view.getTag();

            String name = cursor.getString(mGroupArtistIdx);
            String displayname = name;
//            boolean unknown = name == null || name.equals(MediaStore.UNKNOWN_STRING);
//            if (unknown) {
//                displayname = mUnknownAlbum;
//            }
//            vh.album_title.setText(displayname);
            vh.album_title.setVisibility(View.GONE);

            displayname = name;
            if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                displayname = mUnknownArtist;
            }
            vh.artist_title.setText(displayname);

            long currentartistid = MusicUtils.getCurrentArtistId();
            long artistid = cursor.getLong(mArtistIdx);
            if (mArtistId.length() != 0 && Integer.parseInt(mArtistId)== artistid) {
                view.setActivated(true);
                return;
            }
            view.setActivated(false);

//            ImageView iv = vh.music_image_song;
//            // We don't actually need the path to the thumbnail file,
//            // we just use it to see if there is album art or not
//            String art = cursor.getString(mAlbumArtIndex);
//            long aid = cursor.getLong(0);
//            if (unknown || art == null || art.length() == 0) {
//                iv.setImageDrawable(mDefaultAlbumIcon);
//            } else {
//                Drawable d = MusicUtils.getCachedArtwork(context, aid, mDefaultAlbumIcon);
//                iv.setImageDrawable(d);
//            }

        }

        @Override
        public void changeCursor(Cursor cursor) {
            if (mActivity.isFinishing() && cursor != null) {
                cursor.close();
                cursor = null;
            }
            if (cursor != mActivity.mArtistCursor) {
                mActivity.mArtistCursor = cursor;
                getColumnIndices(cursor);
                super.changeCursor(cursor);
            }
        }

        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            String s = constraint.toString();
            if (mConstraintIsValid && (
                    (s == null && mConstraint == null) ||
                            (s != null && s.equals(mConstraint)))) {
                return getCursor();
            }
//            Cursor c = mActivity.getArtistCursor(null, s);
//            mConstraint = s;
//            mConstraintIsValid = true;
//            return c;
            return getCursor();
        }

        public Object[] getSections() {
            return mIndexer.getSections();
        }

        public int getPositionForSection(int section) {
            return mIndexer.getPositionForSection(section);
        }

        public int getSectionForPosition(int position) {
            return 0;
        }
    }

    private class OnTrackItemClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (isShowPlaying == false) {
                showPlayList(true);
            }
            MusicUtils.playAll(mContext, mTrackCursor, position);
            if (mTrackAdapter != null) {
                mTrackAdapter.notifyDataSetChanged();
            }

        }
    }

    private BroadcastReceiver mPlayListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MediaPlaybackService.PLAYSTATE_CHANGED.equals(action) ||
                    MediaPlaybackService.META_CHANGED.equals(action)) {
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            }

        }
    };
}
