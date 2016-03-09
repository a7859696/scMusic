package com.wedesign.music.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.wedesign.music.IMediaPlaybackService;
import com.wedesign.music.MediaPlaybackService;
import com.wedesign.music.R;
import com.wedesign.music.util.LogUtil;
import com.wedesign.music.util.MusicUtils;

/**
 * Created by chenqi on 15/9/5.
 */
public class TrackPlayingView extends LinearLayout implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {
    private static final String TAG = TrackPlayingView.class.getName();
    private Context mContext;
    private LayoutInflater mInflater;
    private Button mRepeat;
    private Button mShuffle;
    private ImageView mArtistImage;
    private SeekBar mSeekbar;
    private TextView mTitle;
    private TextView mArtist;
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mTracksInTotal;
    private RepeatingImageButton mPrevious;
    private Button mPause;
    private RepeatingImageButton mNext;
    private IMediaPlaybackService mService;
    private long mPosOverride = -1;
    private long mDuration;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private boolean mSeeking = false;
    private boolean mFromTouch = false;

    private static final int REFRESH = 1;
    private static final int QUIT = 2;
    private static final int GET_ALBUM_ART = 3;
    private static final int ALBUM_ART_DECODED = 4;
    private boolean mPaused = false;
    private Worker mAlbumArtWorker;
    private AlbumArtHandler mAlbumArtHandler;


    public TrackPlayingView(Context context) {
        super(context);
    }

    public TrackPlayingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        //

        mContext = context;
        mInflater = LayoutInflater.from(mContext);

        View view = mInflater.inflate(R.layout.track_player, null);                          //自定义ViewGrop 都用到的  inflate
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( 
                LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT);
        addView(view, params);                                                                              //把View加到  ViewGroup 中。

        initViews();
    }

    private void initViews() {
        mRepeat = (Button) findViewById(R.id.repeat);
        mRepeat.setOnClickListener(this);
        mShuffle = (Button) findViewById(R.id.shuffle);
        mShuffle.setOnClickListener(this);
        mArtistImage = (ImageView) findViewById(R.id.artwork_image);
        mSeekbar = (SeekBar) findViewById(R.id.progress);
        mSeekbar.setOnSeekBarChangeListener(this);
        mSeekbar.setMax(1000);
        mTitle = (TextView) findViewById(R.id.track_title);
        mArtist = (TextView) findViewById(R.id.track_artist);
        mCurrentTime = (TextView) findViewById(R.id.currenttime);
        mTotalTime = (TextView) findViewById(R.id.totaltime);
        mTracksInTotal = (TextView) findViewById(R.id.track_in_total);
        mPrevious = (RepeatingImageButton) findViewById(R.id.prev);
        mPrevious.setOnClickListener(this);
        mPrevious.setRepeatListener(mRewListener, 260);             //设置监听
        mPause = (Button) findViewById(R.id.pause);
        mPause.setOnClickListener(this);
        mNext = (RepeatingImageButton) findViewById(R.id.next);
        mNext.setOnClickListener(this);
        mNext.setRepeatListener(mFfwdListener, 260);                  //设置监听

    }

    private void scanBackward(int repcnt, long delta) {
        if (mService == null) return;
        try {
            if (repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10;
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos - delta;
                if (newpos < 0) {
                    // move to previous track
                    mService.prev();
                    long duration = mService.duration();
                    mStartSeekPos += duration;
                    newpos += duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0) {
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }

    private void scanForward(int repcnt, long delta) {
        if (mService == null) return;
        try {
            if (repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10;
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos + delta;
                long duration = mService.duration();
                if (newpos >= duration) {
                    // move to next track
                    mService.next();
                    mStartSeekPos -= duration; // is OK to go negative
                    newpos -= duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0) {
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }

    private RepeatingImageButton.RepeatListener mRewListener =
            new RepeatingImageButton.RepeatListener() {
                public void onRepeat(View v, long howlong, int repcnt) {
                    scanBackward(repcnt, howlong);
                }
            };

    private RepeatingImageButton.RepeatListener mFfwdListener =
            new RepeatingImageButton.RepeatListener() {
                public void onRepeat(View v, long howlong, int repcnt) {
                    scanForward(repcnt, howlong);
                }
            };

    public TrackPlayingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // initViews();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            mPaused = false;
            updateTrackInfo();
            queueNextRefresh(1);
            setRepeatButtonImage();
            setShuffleButtonImage();
            updateTrackInTotal();
            return;
        }
        mPaused = true;
    }

    public void setIMediaPlaybackService(IMediaPlaybackService service) {   
        mService = service;      //在TrackBrowserActivity中，将IMediaPlaybackService 的service  传过来。使得
    }                                        // 在这个自定义的ViewGroup能通过service  来控制


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.repeat: {             //设置 循环播放  模式按钮
                setRepeatMode();
            }
            break;
            case R.id.shuffle: {              //设置随机播放
                setShuffleMode();
            }
            break;
            case R.id.prev: {                //上一曲
                doPrev();
            }
            break;
            case R.id.pause: {             //暂停
                doPauseResume();
            }
            break;
            case R.id.next: {              //下一曲
                doNext();
            }
            break;
        }
    }

    /**
     * 下一曲
     */
    private void doNext() {
        if (mService == null) return;
        try {
            mService.next();
        } catch (RemoteException ex) {
        }

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);       
        f.addAction(MediaPlaybackService.META_CHANGED);
        mContext.getApplicationContext().registerReceiver(mStatusListener, new IntentFilter(f));             //注册这两个最主要的   广播！！。
        mPaused = false;
        mAlbumArtWorker = new Worker("album art worker");
        mAlbumArtHandler = new AlbumArtHandler(mAlbumArtWorker.getLooper());
        long next = refreshNow();
        queueNextRefresh(next);
    }
 //onAttachedToWindow- -- - onDetachedFromWindow   是一对组合
    protected void onDetachedFromWindow() {
        mPaused = true;
        mHandler.removeMessages(REFRESH);

        mContext.getApplicationContext().unregisterReceiver(mStatusListener);
        mAlbumArtWorker.quit();
        super.onDetachedFromWindow();
    }



    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LogUtil.i(TAG,"mStatusListener action = "+action);
            if (action.equals(MediaPlaybackService.META_CHANGED)) {
                // redraw the artist/title info and
                // set new max for progress bar
                updateTrackInfo();
                setPauseButtonImage();
                queueNextRefresh(REFRESH);
                updateTrackInTotal();
                setRepeatButtonImage();
                setShuffleButtonImage();
            } else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                setRepeatButtonImage();
                setShuffleButtonImage();
                setPauseButtonImage();
                updateTrackInTotal();
            }
        }
    };

    private void queueNextRefresh(long delay) {
        if (!mPaused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ALBUM_ART_DECODED:
                    mArtistImage.setImageBitmap((Bitmap)msg.obj);
                    mArtistImage.getDrawable().setDither(true);
                    break;

                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;


                default:
                    break;
            }
        }
    };

    /**
     * 上一曲
     */
    private void doPrev() {
        if (mService == null) return;
        try {

            mService.prev();
//            if (mService.position() < 2000) {
//                mService.prev();
//            } else
//            {
//                mService.seek(0);
//                mService.play();
//            }
        } catch (RemoteException ex) {
        }
    }

    /**
     * 暂停
     */
    private void doPauseResume() {
        try {
            if (mService != null) {
                if (mService.isPlaying()) {
                    mService.pause();
                } else {
                    mService.play();
                }
                refreshNow();
                setPauseButtonImage();
            }
        } catch (RemoteException ex) {
        }
    }

    private void setPauseButtonImage() {
        try {
            if (mService != null && mService.isPlaying()) {
                mPause.setBackgroundResource(R.drawable.play_control_pause);
            } else {
                mPause.setBackgroundResource(R.drawable.play_control_playing);
            }
        } catch (RemoteException ex) {
        }
    }

    private long refreshNow() {
        if (mService == null)
            return 500;
        try {
            long pos = mPosOverride < 0 ? mService.position() : mPosOverride;       //pos是毫秒，获得  当前的播放时间
            if ((pos >= 0) && (mDuration > 0)) {
                mCurrentTime.setText(MusicUtils.makeTimeString(mContext, pos / 1000));       //转化为秒传入进去
                int progress = (int) (1000 * pos / mDuration);
                mSeekbar.setProgress(progress);

                if (mService.isPlaying()) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                    return 500;
                }
            } else {
                mCurrentTime.setText("--:--");
                mSeekbar.setProgress(1000);
            }
            // calculate the number of milliseconds until the next full second, so
            // the counter can be updated at just the right time
            long remaining = 1000 - (pos % 1000);

            // approximate how often we would need to refresh the slider to
            // move it smoothly
            int width = mSeekbar.getWidth();
            if (width == 0) width = 320;
            long smoothrefreshtime = mDuration / width;

            if (smoothrefreshtime > remaining) return remaining;
            if (smoothrefreshtime < 20) return 20;
            return smoothrefreshtime;
        } catch (RemoteException ex) {
        }
        return 500;
    }

    private void updateTrackInTotal(){
        if (mService == null) {
            return;
        }

        try {
            int total = mService.getQueue().length;
            int cur = mService.getQueuePosition()+1;
            mTracksInTotal.setText(cur+"/"+total);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    private void updateTrackInfo() {
    	Log.d("lixuan", "TrackPlayingView--- updateTrackInfo");
        if (mService == null) {
            return;
        }
        try {
            String path = mService.getPath();
            if (path == null) {
                return;
            }

            long songid = mService.getAudioId();
            if (songid < 0 && path.toLowerCase().startsWith("http://")) {
                // Once we can get album art and meta data from MediaPlayer, we
                // can show that info again when streaming.
                mArtist.setVisibility(View.INVISIBLE);
                mTitle.setText(path);
                mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
                mAlbumArtHandler.obtainMessage(GET_ALBUM_ART, new AlbumSongIdWrapper(-1, -1)).sendToTarget();
            } else {
                mArtist.setVisibility(View.VISIBLE);
                String artistName = mService.getArtistName();
                if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
                    artistName = mContext.getResources().getString(R.string.unknown_artist_name);
                }
                mArtist.setText(artistName);
                String albumName = mService.getAlbumName();
                long albumid = mService.getAlbumId();
                if (MediaStore.UNKNOWN_STRING.equals(albumName)) {
                    albumName = mContext.getResources().getString(R.string.unknown_album_name);
                    albumid = -1;
                }
                mTitle.setText(mService.getTrackName());
                mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
                mAlbumArtHandler.obtainMessage(GET_ALBUM_ART, new AlbumSongIdWrapper(albumid, songid)).sendToTarget();
                mArtistImage.setVisibility(View.VISIBLE);
            }
            mDuration = mService.duration();     //MediaPlayer取得音视频文件总时长
            mTotalTime.setText(MusicUtils.makeTimeString(mContext, mDuration / 1000));
        } catch (RemoteException ex) {

        }
    }


    /**
     * 设置   随机播放
     */
    private void setShuffleMode() {
        if (mService == null) {
            return;
        }
        try {
            int shuffle = mService.getShuffleMode();
            if (shuffle == MediaPlaybackService.SHUFFLE_NONE) {
                mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NORMAL);
                if (mService.getRepeatMode() == MediaPlaybackService.REPEAT_CURRENT) {
                    mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                    setRepeatButtonImage();
                }
            } else if (shuffle == MediaPlaybackService.SHUFFLE_NORMAL ||
                    shuffle == MediaPlaybackService.SHUFFLE_AUTO) {
                mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
            } else {
                LogUtil.i(TAG, "Invalid shuffle mode: " + shuffle);
            }
            setShuffleButtonImage();
        } catch (RemoteException ex) {
        }
    }

    /**
     * 设置 随机播放 按钮的对应状态下的北京图片
     */
    private void setShuffleButtonImage() {
        if (mService == null) return;
        try {
            switch (mService.getShuffleMode()) {
                case MediaPlaybackService.SHUFFLE_NONE:
                    mShuffle.setBackgroundResource(R.drawable.shuffle_off_selector);
                    break;
                case MediaPlaybackService.SHUFFLE_AUTO:
                    mShuffle.setBackgroundResource(R.drawable.shuffle_on_selector);
                    break;
                default:
                    mShuffle.setBackgroundResource(R.drawable.shuffle_on_selector);
                    break;
            }
        } catch (RemoteException ex) {
        }
    }

    /**
     * 设置循环按钮相对应的背景图片
     */
    private void setRepeatButtonImage() {
        if (mService == null) return;
        try {
            switch (mService.getRepeatMode()) {
                case MediaPlaybackService.REPEAT_ALL:
                    mRepeat.setBackgroundResource(R.drawable.repeat_on_selector);
                    break;
                case MediaPlaybackService.REPEAT_CURRENT:
                    mRepeat.setBackgroundResource(R.drawable.repeat_one_selector);
                    break;
                default:
                    mRepeat.setBackgroundResource(R.drawable.repeat_off_selector);
                    break;
            }
        } catch (RemoteException ex) {
        }
    }
   
    /**
     * 设置播放模式：单曲循环和循环所有
     */
    private void setRepeatMode() {
        if (mService == null) {                    //确保  service（Aidl 的进程） 是在运行的
            return;
        }
        try {
            int mode = mService.getRepeatMode();                      //得到  模式
            if (mode == MediaPlaybackService.REPEAT_NONE) {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
            } else if (mode == MediaPlaybackService.REPEAT_ALL) {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_CURRENT);
                if (mService.getShuffleMode() != MediaPlaybackService.SHUFFLE_NONE) {
                    mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                    setShuffleButtonImage();
                }
            } else {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_NONE);
            }
            setRepeatButtonImage();
        } catch (RemoteException ex) {
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {          //SeekBar数值的改变
        if (!fromUser || (mService == null)) return;
        long now = SystemClock.elapsedRealtime();
        Log.d("lixuan", "onProgressChanged    :"+now);
        if ((now - mLastSeekEventTime) > 250) {
            mLastSeekEventTime = now;
            mPosOverride = mDuration * progress / 1000;
            try {
                mService.seek(mPosOverride);
            } catch (RemoteException ex) {
            }

            // trackball event, allow progress updates
            if (!mFromTouch) {
                refreshNow();
                mPosOverride = -1;
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {                     //SeekBar开始拖动
        mPosOverride = -1;
        mFromTouch = false;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {                  //SeekBar 停止拖动
        mLastSeekEventTime = 0;
        mFromTouch = true;
    }

    public class AlbumArtHandler extends Handler {
        private long mAlbumId = -1;

        public AlbumArtHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            long albumid = ((AlbumSongIdWrapper) msg.obj).albumid;
            long songid = ((AlbumSongIdWrapper) msg.obj).songid;
            if (msg.what == GET_ALBUM_ART && (mAlbumId != albumid || albumid < 0)) {
                // while decoding the new image, show the default album art
                Message numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, null);
                mHandler.removeMessages(ALBUM_ART_DECODED);
                mHandler.sendMessageDelayed(numsg, 300);
                // Don't allow default artwork here, because we want to fall back to song-specific
                // album art if we can't find anything for the album.
                Bitmap bm = MusicUtils.getArtwork(mContext, songid, albumid, false);
                if (bm == null) {
                    bm = MusicUtils.getArtwork(mContext, songid, -1);
                    albumid = -1;
                }
                if (bm != null) {
                    numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, bm);
                    mHandler.removeMessages(ALBUM_ART_DECODED);
                    mHandler.sendMessage(numsg);
                }
                mAlbumId = albumid;
            }
        }
    }

    private static class Worker implements Runnable {
        private final Object mLock = new Object();
        private Looper mLooper;

        /**
         * Creates a worker thread with the given name. The thread
         * then runs a {@link android.os.Looper}.
         *
         * @param name A name for the new thread
         */
        Worker(String name) {
            Thread t = new Thread(null, this, name);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
            synchronized (mLock) {
                while (mLooper == null) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }

        public Looper getLooper() {
            return mLooper;
        }

        public void run() {
            synchronized (mLock) {
                Looper.prepare();
                mLooper = Looper.myLooper();
                mLock.notifyAll();
            }
            Looper.loop();
        }

        public void quit() {
            mLooper.quit();
        }
    }

    private static class AlbumSongIdWrapper {
        public long albumid;
        public long songid;

        AlbumSongIdWrapper(long aid, long sid) {
            albumid = aid;
            songid = sid;
        }
    }

}
