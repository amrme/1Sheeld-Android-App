package com.integreight.onesheeld;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.MapBuilder;
import com.integreight.firmatabluetooth.ArduinoLibraryVersionChangeHandler;
import com.integreight.firmatabluetooth.FirmwareVersionQueryHandler;
import com.integreight.onesheeld.appFragments.SheeldsList;
import com.integreight.onesheeld.popup.ArduinoConnectivityPopup;
import com.integreight.onesheeld.popup.ArduinoConnectivityPopup.onConnectedToBluetooth;
import com.integreight.onesheeld.popup.FirmwareUpdatingPopup;
import com.integreight.onesheeld.popup.ValidationPopup;
import com.integreight.onesheeld.services.OneSheeldService;
import com.integreight.onesheeld.utils.Log;
import com.integreight.onesheeld.utils.customviews.AppSlidingLeftMenu;
import com.integreight.onesheeld.utils.customviews.MultiDirectionSlidingDrawer;
import com.parse.ParseAnalytics;

public class MainActivity extends FragmentActivity {
	// private final String TAG = "MainActivity";
	// private boolean isBoundService = false;
	public AppSlidingLeftMenu appSlidingMenu;
	public boolean isForground = false;
	private onConnectedToBluetooth onConnectToBlueTooth = null;
	public static String currentShieldTag = null;
	public static MainActivity thisInstance;

	public OneSheeldApplication getThisApplication() {
		return (OneSheeldApplication) getApplication();
	}

	@Override
	public void onCreate(Bundle arg0) {
		super.onCreate(arg0);
		initCrashlyticsAndUncaughtThreadHandler();
		ParseAnalytics.trackAppOpened(getIntent());
		// requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.one_sheeld_main);
		initLooperThread();
		// set the Behind View
		// setBehindContentView(R.layout.menu_frame);
		replaceCurrentFragment(R.id.appTransitionsContainer,
				SheeldsList.getInstance(), "base", true, false);
		resetSlidingMenu();
		if (getThisApplication().getAppFirmata() != null) {
			getThisApplication().getAppFirmata()
					.addFirmwareVersionQueryHandler(versionChangingHandler);
			getThisApplication().getAppFirmata()
					.addArduinoLibraryVersionQueryHandler(
							arduinoLibraryVersionHandler);
		}
		// ValidationPopup popub = new ValidationPopup(MainActivity.this,
		// "Firmware Upgrading", "There's a new version for your 1Sheeld",
		// new ValidationPopup.ValidationAction("Now",
		// new View.OnClickListener() {
		//
		// @Override
		// public void onClick(View v) {
		// new FirmwareUpdatingPopup(MainActivity.this)
		// .show();
		// }
		// }, true), new ValidationPopup.ValidationAction(
		// "Not Now", new View.OnClickListener() {
		//
		// @Override
		// public void onClick(View v) {
		// // TODO Auto-generated method stub
		//
		// }
		// }, true));
		// popub.show();
		if (getThisApplication().getShowTutAgain()
				&& getThisApplication().getTutShownTimes() < 6)
			startActivity(new Intent(this, Tutorial.class));
	}

	public Thread looperThread;
	public Handler backgroundThreadHandler;
	private Looper backgroundHandlerLooper;

	private void stopLooperThread() {
		if (looperThread != null && looperThread.isAlive()) {
			looperThread.interrupt();
			backgroundHandlerLooper.quit();
			looperThread = null;
		}
	}

	public void initLooperThread() {
		stopLooperThread();
		looperThread = new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				Looper.prepare();
				backgroundHandlerLooper = Looper.myLooper();
				backgroundThreadHandler = new Handler();
				Looper.loop();
			}
		});
		looperThread.start();
	}

	private void initCrashlyticsAndUncaughtThreadHandler() {
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

			@Override
			public void uncaughtException(Thread arg0, final Throwable arg1) {
				arg1.printStackTrace();
				ArduinoConnectivityPopup.isOpened = false;
				// stopService(new Intent(getApplicationContext(),
				// OneSheeldService.class));
				moveTaskToBack(true);
				if (((OneSheeldApplication) getApplication()).getAppFirmata() != null) {
					while (!((OneSheeldApplication) getApplication())
							.getAppFirmata().close())
						;
				}
				stopService();
				new Thread(new Runnable() {

					@Override
					public void run() {
						// tryToSendNotificationsToAdmins(arg1);
						Intent in = new Intent(getIntent());
						PendingIntent intent = PendingIntent
								.getActivity(getBaseContext(), 0, in,
										getIntent().getFlags());

						AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
						mgr.set(AlarmManager.RTC,
								System.currentTimeMillis() + 1000, intent);
						getThisApplication().setTutShownTimes(
								getThisApplication().getTutShownTimes() + 1);
						System.exit(0);
					}
				}).start();
			}
		});
		Crashlytics.start(this);
	}

	public void tryToSendNotificationsToAdmins(Throwable arg1) {
		try {
			// Get Exception Details
			StackTraceElement[] stackTraceElement = arg1.getStackTrace();

			String fileName = "";
			String methodName = "";
			String className = arg1.getClass().getSimpleName();
			int lineNumber = 0;

			String packageName = MainActivity.this.getApplicationInfo().packageName;
			for (int i = 0; i < stackTraceElement.length; i++) {
				if (stackTraceElement[i].getClassName().startsWith(packageName)) {
					fileName = stackTraceElement[i].getFileName();
					methodName = stackTraceElement[i].getMethodName();
					lineNumber = stackTraceElement[i].getLineNumber();
					break;
				}
			}

			String url = "http://api.instapush.im/v1/post";
			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(url);
			httppost.addHeader("Content-Type", "application/json");
			httppost.addHeader("x-instapush-appid", "53627f8ca4c48a291a1de9af");
			httppost.addHeader("x-instapush-appsecret",
					"93df7ea24ebf96a9f13f5b5bbb9c7eb9");
			JSONObject instaPushObject = new JSONObject();
			instaPushObject.put("event", "app_crash");
			JSONObject instaPushTrackers = new JSONObject();
			instaPushTrackers.put("exception_name", className);
			instaPushTrackers.put("class_name", fileName);
			instaPushTrackers.put("method_name", methodName);
			instaPushTrackers.put("line_number", lineNumber);
			instaPushObject.put("trackers", instaPushTrackers);
			StringEntity entity = new StringEntity(instaPushObject.toString());
			httppost.setEntity(entity);
			// Execute the request
			HttpResponse response;

			response = httpclient.execute(httppost);
			HttpEntity entity1 = response.getEntity();
			if (entity1 != null) {
				InputStream instream = entity1.getContent();
				String result = convertStreamToString(instream);
				Log.d("InstaPush", result);
				instream.close();
			}

		} catch (Exception e) {
			Log.e("TAG", "Exception", e);
		}
	}

	private static String convertStreamToString(InputStream is) {
		/*
		 * To convert the InputStream to String we use the
		 * BufferedReader.readLine() method. We iterate until the BufferedReader
		 * return null which means there's no more data to read. Each line will
		 * appended to a StringBuilder and returned as String.
		 */
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
		} catch (IOException e) {
			Log.e("TAG", "Exception", e);
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				Log.e("TAG", "Exception", e);
			}
		}
		return sb.toString();
	}

	Handler versionHandling = new Handler();
	FirmwareVersionQueryHandler versionChangingHandler = new FirmwareVersionQueryHandler() {
		ValidationPopup popub;

		@Override
		public void onVersionReceived(final int minorVersion,
				final int majorVersion) {
			versionHandling.post(new Runnable() {

				@Override
				public void run() {
					Log.d("Onesheeld", minorVersion + "     " + majorVersion);
					if (getThisApplication().getMinorVersion() != -1
							&& getThisApplication().getMajorVersion() != -1) {
						if (majorVersion == getThisApplication()
								.getMajorVersion()
								&& minorVersion != getThisApplication()
										.getMinorVersion()) {
							String msg = "";
							try {
								JSONObject obj = new JSONObject(
										((OneSheeldApplication) getApplication())
												.getVersionWebResult());
								try {
									msg += obj.getString("name") + "\n";
								} catch (Exception e) {
									// TODO: handle exception
								}
								try {
									msg += obj.getString("description") + "\n";
								} catch (Exception e) {
									// TODO: handle exception
								}
								try {
									msg += "Release Date: "
											+ obj.getString("date");
								} catch (Exception e) {
									// TODO: handle exception
								}
							} catch (JSONException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							popub = new ValidationPopup(MainActivity.this,
									"Optional Firmware Upgrade", msg,
									new ValidationPopup.ValidationAction("Now",
											new View.OnClickListener() {

												@Override
												public void onClick(View v) {
													new FirmwareUpdatingPopup(
															MainActivity.this,
															false).show();
												}
											}, true),
									new ValidationPopup.ValidationAction(
											"Not Now",
											new View.OnClickListener() {

												@Override
												public void onClick(View v) {
													// TODO Auto-generated
													// method
													// stub

												}
											}, true));
							if (!isFinishing())
								popub.show();
						} else if (majorVersion != getThisApplication()
								.getMajorVersion()) {
							String msg = "";
							try {
								JSONObject obj = new JSONObject(
										((OneSheeldApplication) getApplication())
												.getVersionWebResult());
								try {
									msg += obj.getString("name") + "\n";
								} catch (Exception e) {
									// TODO: handle exception
								}
								try {
									msg += obj.getString("description") + "\n";
								} catch (Exception e) {
									// TODO: handle exception
								}
								try {
									msg += "Release Date: "
											+ obj.getString("date");
								} catch (Exception e) {
									// TODO: handle exception
								}
							} catch (JSONException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							popub = new ValidationPopup(MainActivity.this,
									"Required Firmware Upgrade", msg,
									new ValidationPopup.ValidationAction(
											"Start",
											new View.OnClickListener() {

												@Override
												public void onClick(View v) {
													FirmwareUpdatingPopup fup = new FirmwareUpdatingPopup(
															MainActivity.this,
															false);
													fup.setCancelable(false);
													fup.show();
												}
											}, true));
							if (!isFinishing())
								popub.show();
						}
					}
				}
			});

		}
	};
	ArduinoLibraryVersionChangeHandler arduinoLibraryVersionHandler = new ArduinoLibraryVersionChangeHandler() {
		ValidationPopup popub;

		@Override
		public void onArduinoLibraryVersionChange(final int version) {
			versionHandling.post(new Runnable() {

				@Override
				public void run() {
					if (version < OneSheeldApplication.ARDUINO_LIBRARY_VERSION) {
						popub = new ValidationPopup(
								MainActivity.this,
								"Arduino Library Update",
								"There's a new version of 1Sheeld's Arduino library available on our website!",
								new ValidationPopup.ValidationAction("OK",
										new View.OnClickListener() {

											@Override
											public void onClick(View v) {
												popub.dismiss();
											}
										}, true));
						if (!isFinishing())
							popub.show();
					}
				}
			});
		}
	};

	@Override
	protected void onResume() {
		super.onResume();
	}

	private BackOnconnectionLostHandler backOnConnectionLostHandler;

	public BackOnconnectionLostHandler getOnConnectionLostHandler() {
		if (backOnConnectionLostHandler == null) {
			backOnConnectionLostHandler = new BackOnconnectionLostHandler() {

				@Override
				public void handleMessage(Message msg) {
					if (connectionLost) {
						runOnUiThread(new Runnable() {
							public void run() {
								if (!ArduinoConnectivityPopup.isOpened
										&& !isFinishing())
									new ArduinoConnectivityPopup(
											MainActivity.this).show();
							}
						});
						if (getSupportFragmentManager()
								.getBackStackEntryCount() > 1) {
							getSupportFragmentManager().beginTransaction()
									.setCustomAnimations(0, 0, 0, 0)
									.commitAllowingStateLoss();
							getSupportFragmentManager().popBackStack();// ("operations",FragmentManager.POP_BACK_STACK_INCLUSIVE);
							getSupportFragmentManager()
									.executePendingTransactions();
						}
					}
					connectionLost = false;
					super.handleMessage(msg);
				}
			};
		}
		return backOnConnectionLostHandler;
	}

	public static class BackOnconnectionLostHandler extends Handler {
		public boolean canInvokeOnCloseConnection = true,
				connectionLost = false;
	}

	@Override
	public void onBackPressed() {
		resetSlidingMenu();
		MultiDirectionSlidingDrawer pinsView = (MultiDirectionSlidingDrawer) findViewById(R.id.pinsViewSlidingView);
		MultiDirectionSlidingDrawer settingsView = (MultiDirectionSlidingDrawer) findViewById(R.id.settingsSlidingView);
		if ((pinsView == null || (pinsView != null && !pinsView.isOpened()))
				&& (settingsView == null || (settingsView != null && !settingsView
						.isOpened()))) {
			if (appSlidingMenu.isOpen()) {
				appSlidingMenu.closePane();
			} else {
				if (getSupportFragmentManager().getBackStackEntryCount() > 1) {
					findViewById(R.id.getAvailableDevices).setOnClickListener(
							new View.OnClickListener() {

								@Override
								public void onClick(View v) {
								}
							});
					getSupportFragmentManager().popBackStack();// ("operations",FragmentManager.POP_BACK_STACK_INCLUSIVE);
					getSupportFragmentManager().executePendingTransactions();
				} else
					moveTaskToBack(true);
			}
		} else {
			if (pinsView.isOpened())
				pinsView.animateOpen();
			else if (settingsView.isOpened())
				settingsView.animateOpen();
		}
	}

	public void replaceCurrentFragment(int container, Fragment targetFragment,
			String tag, boolean addToBackStack, boolean animate) {
		// String backStateName = tag;
		// String fragmentTag = tag;
		if (!isFinishing()) {
			FragmentManager manager = getSupportFragmentManager();
			boolean fragmentPopped = manager.popBackStackImmediate(tag, 0);

			if (!fragmentPopped && manager.findFragmentByTag(tag) == null) { // fragment
				// not
				// in
				// back
				// stack,
				// create
				// it.
				FragmentTransaction ft = manager.beginTransaction();
				if (animate)
					ft.setCustomAnimations(R.anim.slide_out_right,
							R.anim.slide_in_left, R.anim.slide_out_left,
							R.anim.slide_in_right);
				ft.replace(container, targetFragment, tag);
				if (addToBackStack) {
					ft.addToBackStack(tag);
				}
				ft.commit();
			}
		}
	}

	public void stopService() {
		this.stopService(new Intent(this, OneSheeldService.class));
	}

	@Override
	protected void onDestroy() {
		// isBoundService = OneSheeldService.isBound;
		// if (isMyServiceRunning())
		getThisApplication().getGaTracker().send(
				MapBuilder.createEvent("App lifecycle",
						"Finished the app manually", "", 0L).build());
		ArduinoConnectivityPopup.isOpened = false;
		stopService();
		stopLooperThread();
		// isBoundService = false;
		super.onDestroy();
	}

	//
	// private boolean isMyServiceRunning() {
	// ActivityManager manager = (ActivityManager)
	// getSystemService(Context.ACTIVITY_SERVICE);
	// for (RunningServiceInfo service : manager
	// .getRunningServices(Integer.MAX_VALUE)) {
	// if (OneSheeldApplication.class.getName().equals(
	// service.service.getClassName())) {
	// return true;
	// }
	// }
	// return false;
	// }
	public void openMenu() {
		resetSlidingMenu();
		appSlidingMenu.openPane();
	}

	public void closeMenu() {
		resetSlidingMenu();
		appSlidingMenu.closePane();
	}

	public void enableMenu() {
		resetSlidingMenu();
		appSlidingMenu.setCanSlide(true);
	}

	public void disableMenu() {
		resetSlidingMenu();
		appSlidingMenu.closePane();
		appSlidingMenu.setCanSlide(false);
	}

	private void resetSlidingMenu() {
		if (appSlidingMenu == null) {
			appSlidingMenu = (AppSlidingLeftMenu) findViewById(R.id.sliding_pane_layout);
		}
	}

	public void setOnConnectToBluetooth(onConnectedToBluetooth listener) {
		this.onConnectToBlueTooth = listener;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case SheeldsList.REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			// if (resultCode == Activity.RESULT_OK) {
			// ((MainActivity) getActivity())
			// .setSupportProgressBarIndeterminateVisibility(true);
			// // connectDevice(data);
			// }
			break;
		case SheeldsList.REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode != Activity.RESULT_OK) {
				Toast.makeText(this, R.string.bt_not_enabled_leaving,
						Toast.LENGTH_SHORT).show();
				// ArduinoConnectivityPopup.isOpened = false;
				// finish();
			} else {
				if (onConnectToBlueTooth != null
						&& ArduinoConnectivityPopup.isOpened)
					onConnectToBlueTooth.onConnect();
			}
			break;
		default:
			break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onResumeFragments() {
		thisInstance = this;
		isForground = true;
		Crashlytics.setString("isBackground", "No");
		new Thread(new Runnable() {

			@Override
			public void run() {
				ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
				List<RunningAppProcessInfo> appProcesses = activityManager
						.getRunningAppProcesses();
				String apps = "";
				for (int i = 0; i < appProcesses.size(); i++) {
					Log.d("Executed app", "Application executed : "
							+ appProcesses.get(i).processName + "\t\t ID: "
							+ appProcesses.get(i).pid + "");
					apps += appProcesses.get(i).processName + "\n";
				}
				Crashlytics.setString("Running apps", apps);
			}
		}).start();
		super.onResumeFragments();
	}

	long pausingTime = 0;

	@Override
	protected void onPause() {
		isForground = false;
		pausingTime = System.currentTimeMillis();
		float hours = TimeUnit.MILLISECONDS.toSeconds(System
				.currentTimeMillis() - pausingTime);
		float minutes = TimeUnit.MILLISECONDS.toMinutes(System
				.currentTimeMillis() - pausingTime);
		float seconds = TimeUnit.MILLISECONDS.toHours(System
				.currentTimeMillis() - pausingTime);
		Crashlytics.setString("isBackground", "since " + hours + " hours - "
				+ minutes + " minutes - " + seconds + " seocnds");
		new Thread(new Runnable() {

			@Override
			public void run() {
				ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
				List<RunningAppProcessInfo> appProcesses = activityManager
						.getRunningAppProcesses();
				String apps = "";
				for (int i = 0; i < appProcesses.size(); i++) {
					Log.d("Executed app", "Application executed : "
							+ appProcesses.get(i).processName + "\t\t ID: "
							+ appProcesses.get(i).pid + "");
					apps += appProcesses.get(i).processName + "  ||||||  ";
				}
				Crashlytics.setString("Running apps", apps);
			}
		}).start();
		super.onPause();
	}

	@Override
	protected void onStart() {
		EasyTracker.getInstance(this).activityStart(this);
		super.onStart();
	}

	@Override
	protected void onStop() {
		EasyTracker.getInstance(this).activityStop(this);
		super.onStop();
	}

	public void showToast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
	}

	public void hideSoftKeyboard() {
		if (getCurrentFocus() != null) {
			InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
			inputMethodManager.hideSoftInputFromWindow(getCurrentFocus()
					.getWindowToken(), 0);
		}
	}

}
