package com.wedesign.music.util;


import android.util.Log;

/**
 * 日志管理类
 * 
 * @author chenqi
 * 
 */
final public class LogUtil {

	/**
	 * 日志开关
	 */
	public static final boolean LOG_OPEN_DEBUG = true;

	/**
	 * 日志类型开关，必须 LOG_OPEN_DEBUG = true的时候才能启作用
	 */
	private static boolean logOpeni = true;
	private static boolean logOpend = true;
	private static boolean logOpenw = true;
	private static boolean logOpene = true;

	/**
	 * 日志目录
	 */
	private static final String AUTHOR = "[qi]--";

	public static void d(String tag, String message) {
		if (message != null && message != null) {
			if (LOG_OPEN_DEBUG && logOpend) {
				Log.d(tag, AUTHOR + message);
			}
		}

	}

	public static void i(String tag, String message) {
		if (message != null && message != null) {
			if (LOG_OPEN_DEBUG && logOpeni) {
				Log.i(tag, AUTHOR + message);
			}
		}

	}

	public static void w(String tag, String message) {
		if (message != null && message != null) {
			if (LOG_OPEN_DEBUG && logOpenw) {
				Log.w(tag, AUTHOR + message);
			}
		}

	}

	public static void e(String tag, String message) {
		if (message != null && message != null) {
			if (LOG_OPEN_DEBUG && logOpene) {
				Log.e(tag, AUTHOR + message);
			}
		}

	}
}
