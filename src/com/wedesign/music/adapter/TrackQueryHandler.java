package com.wedesign.music.adapter;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.wedesign.music.activity.CursorActivityInterface;
import com.wedesign.music.util.MusicUtils;

/**
 * Handler的异步查询方式。。。 （开发艺术书上也有讲到它的用法）
 * Created by chenqi on 15/9/6.
 */
public class TrackQueryHandler extends AsyncQueryHandler {
    private CursorActivityInterface mCursorInterface;

    class QueryArgs {
        public Uri uri;
        public String[] projection;
        public String selection;
        public String[] selectionArgs;
        public String orderBy;
    }

    public TrackQueryHandler(ContentResolver cr, CursorActivityInterface cursorInterface) {
        super(cr);
        mCursorInterface = cursorInterface;
    	Log.d("lixuan", "2");
    }

    public Cursor doQuery(Uri uri, String[] projection,
                          String selection, String[] selectionArgs,
                          String orderBy, boolean async) {
        if (async) {
        	Log.d("lixuan", "4");
            // Get 100 results first, which is enough to allow the user to start scrolling,
            // while still being very fast.
            Uri limituri = uri.buildUpon().appendQueryParameter("limit", "100").build();
            QueryArgs args = new QueryArgs();
            args.uri = uri;
            args.projection = projection;
            args.selection = selection;
            args.selectionArgs = selectionArgs;
            args.orderBy = orderBy;
        	Log.d("lixuan", "查询的limituri:  "+limituri.toString());
            startQuery(0, args, limituri, projection, selection, selectionArgs, orderBy);
            return null;
        }
    	Log.d("lixuan", "4.1");
        return MusicUtils.query(mCursorInterface.getContext(),
                uri, projection, selection, selectionArgs, orderBy);
    }

    @Override
    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        //Log.i("@@@", "query complete: " + cursor.getCount() + "   " + mActivity);
    	Log.d("lixuan", "5");
        mCursorInterface.init(cursor, cookie != null);
        if (token == 0 && cookie != null && cursor != null && cursor.getCount() >= 100) {
            QueryArgs args = (QueryArgs) cookie;
        	Log.d("lixuan", "5.1");
            startQuery(1, null, args.uri, args.projection, args.selection,
                    args.selectionArgs, args.orderBy);
        }
    }
}
