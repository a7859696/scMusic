package com.wedesign.music.adapter;

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AlphabetIndexer;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.wedesign.music.R;
import com.wedesign.music.activity.CursorActivityInterface;
import com.wedesign.music.activity.TrackBrowserActivity;
import com.wedesign.music.util.MusicUtils;

/**
 * 类TrackListAdapter，继承自SimpleCursorAdapter（百度它的用法），专门用于数据库类型的Adapter
 * Created by chenqi on 15/9/6.
 */
public class TrackListAdapter extends SimpleCursorAdapter implements SectionIndexer {
    boolean mIsNowPlaying;
    boolean mDisableNowPlayingIndicator;

    int mTitleIdx;
    int mArtistIdx;
    int mDurationIdx;
    int mAudioIdIdx;

    private final StringBuilder mBuilder = new StringBuilder();
    private final String mUnknownArtist;
    private final String mUnknownAlbum;

    private AlphabetIndexer mIndexer;

    private TrackQueryHandler mQueryHandler;
    private String mConstraint = null;
    private boolean mConstraintIsValid = false;

    private CursorActivityInterface mCursorInterface = null;

    static class ViewHolder {
        TextView line1;
        TextView line2;
        TextView duration;
        ImageView play_indicator;
        CharArrayBuffer buffer1;
        char[] buffer2;
    }

    /**
     * TrackListAdapter  类的构造方法。在构造方法中，还创建了用于异步查询的mQueryHandler。
     * @param context
     * @param cursorInterface  
     * @param layout     
     * @param cursor
     * @param from
     * @param to
     */
    public TrackListAdapter(Context context, CursorActivityInterface cursorInterface,
                            int layout, Cursor cursor, String[] from, int[] to) {  
        super(context, layout, cursor, from, to);
    	Log.d("lixuan", "1");
        mCursorInterface = cursorInterface;
        getColumnIndices(cursor);
        mIsNowPlaying = false;
        mDisableNowPlayingIndicator = false;
        mUnknownArtist = context.getString(R.string.unknown_artist_name);//未知艺术家
        mUnknownAlbum = context.getString(R.string.unknown_album_name);//未知专辑

        mQueryHandler = new TrackQueryHandler(context.getContentResolver(), mCursorInterface);  //获得异步查询类AsyncQueryHandler的对象
    }

    public TrackQueryHandler getQueryHandler() {
    	Log.d("lixuan", "3");
        return mQueryHandler;
    }
/**
 * 获取歌曲的各种信息
 * @param cursor   由传入的cusor  来得到信息
 */
    private void getColumnIndices(Cursor cursor) {
        if (cursor != null) {
            mTitleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);       //获取歌曲名称
            mArtistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);  //获取歌曲艺术家
            mDurationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);  //获取歌曲播放总时长
            try {
                mAudioIdIdx = cursor.getColumnIndexOrThrow(                                       //获取歌曲ID
                        MediaStore.Audio.Playlists.Members.AUDIO_ID);
            } catch (IllegalArgumentException ex) {
                mAudioIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            }

//                if (mIndexer != null) {
//                    mIndexer.setCursor(cursor);
//                } else if (!mActivity.mEditMode && mActivity.mAlbumId == null) {
//                    String alpha = mActivity.getString(R.string.fast_scroll_alphabet);
//
//                    mIndexer = new MusicAlphabetIndexer(cursor, mTitleIdx, alpha);
//                }
        	Log.d("lixuan", "1.9");
        }
    	Log.d("lixuan", "1.91");
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View v = super.newView(context, cursor, parent);
        ImageView iv = (ImageView) v.findViewById(R.id.track_icon);
        //  iv.setVisibility(View.GONE);

        ViewHolder vh = new ViewHolder();
        vh.line1 = (TextView) v.findViewById(R.id.line1);
        vh.line2 = (TextView) v.findViewById(R.id.line2);
        vh.duration = (TextView) v.findViewById(R.id.duration);
        vh.play_indicator = (ImageView) v.findViewById(R.id.track_icon);
        vh.buffer1 = new CharArrayBuffer(100);
        vh.buffer2 = new char[200];
        v.setTag(vh);
        return v;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {

        ViewHolder vh = (ViewHolder) view.getTag();

        cursor.copyStringToBuffer(mTitleIdx, vh.buffer1);         //在缓冲区中检索请求的列的文本，将将其存储
        vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied);      //设置 line1;
         
/*        String nametitle = cursor.getString(mTitleIdx);
        vh.line1.setText(nametitle);
        Log.d("lixuan", "nametitle:   "+nametitle);*/
        int secs = cursor.getInt(mDurationIdx) / 1000;                 //设置 文本--时长（Gone）;
        if (secs == 0) {
            vh.duration.setText("");
        } else {
            vh.duration.setText(MusicUtils.makeTimeString(context, secs));
        }

        final StringBuilder builder = mBuilder;
        builder.delete(0, builder.length());

        String name = cursor.getString(mArtistIdx);                
        if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
            builder.append(mUnknownArtist);
        } else {
            builder.append(name);
        }
        
        int len = builder.length();
        if (vh.buffer2.length < len) {
            vh.buffer2 = new char[len];
        }
        builder.getChars(0, len, vh.buffer2, 0);
        vh.line2.setText(vh.buffer2, 0, len);                         //设置  line2

        long id = -1;
        if (MusicUtils.sService != null) {
            // TODO: IPC call on each bind??
            try {
                if (mIsNowPlaying) {
                    id = MusicUtils.sService.getQueuePosition();              //当前播放器的播放位置
                } else {
                    id = MusicUtils.sService.getAudioId();
                }
            } catch (RemoteException ex) {
            }
        }

        if ( (mIsNowPlaying && cursor.getPosition() == id) ||
                (!mIsNowPlaying && !mDisableNowPlayingIndicator && cursor.getLong(mAudioIdIdx) == id)) {
            vh.play_indicator.setImageResource(R.drawable.track_play_icon);
            view.setActivated(true);
        }else{
            vh.play_indicator.setImageResource(R.drawable.track_icon);
            view.setActivated(false);
        }



    }

    @Override
    public void changeCursor(Cursor cursor) {
        if (mCursorInterface.getCursorActivity().isFinishing() && cursor != null) {
            cursor.close();
            cursor = null;
        }
        if (cursor != mCursorInterface.getTrackCursor()) {
            mCursorInterface.setTrackCursor(cursor);              //set  mTrackCursor
        	Log.d("lixuan", "7");
            super.changeCursor(cursor);           //----  更新 cusor  很重要
            getColumnIndices(cursor);
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
//            Cursor c = mActivity.getTrackCursor(mQueryHandler, s, false);
//            mConstraint = s;
//            mConstraintIsValid = true;
//            return c;
        return getCursor();
    }

    // SectionIndexer methods

    public Object[] getSections() {
        if (mIndexer != null) {
            return mIndexer.getSections();
        } else {
            return new String[]{" "};
        }
    }

    public int getPositionForSection(int section) {
        if (mIndexer != null) {
            return mIndexer.getPositionForSection(section);
        }
        return 0;
    }

    public int getSectionForPosition(int position) {
        return 0;
    }
}