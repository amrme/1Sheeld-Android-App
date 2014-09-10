package com.integreight.onesheeld.shields.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterStream;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.integreight.firmatabluetooth.ShieldFrame;
import com.integreight.onesheeld.enums.UIShield;
import com.integreight.onesheeld.shields.ControllerParent;
import com.integreight.onesheeld.shields.controller.utils.ImageUtils;
import com.integreight.onesheeld.shields.controller.utils.SocialUtils;
import com.integreight.onesheeld.shields.controller.utils.TwitterAuthorization;
import com.integreight.onesheeld.shields.controller.utils.TwitterDialog;
import com.integreight.onesheeld.shields.controller.utils.TwitterDialogListner;
import com.integreight.onesheeld.utils.AlertDialogManager;
import com.integreight.onesheeld.utils.ConnectionDetector;
import com.integreight.onesheeld.utils.Log;

public class TwitterShield extends ControllerParent<TwitterShield> {
	private TwitterEventHandler eventHandler;
	private String lastTweet;
	private static final byte UPDATE_STATUS_METHOD_ID = (byte) 0x01;
	private static final byte UPDATE_SEND_MESSAGE_METHOD_ID = (byte) 0x02;
	private static final byte UPLOAD_PHOTO_METHOD_ID = (byte) 0x03;
	TwitterStream twitterStream;

	public String getUsername() {
		return mSharedPreferences.getString(PREF_KEY_TWITTER_USERNAME, "");
	}

	// Preference Constants
	// static String PREFERENCE_NAME = "twitter_oauth";
	static final String PREF_KEY_OAUTH_TOKEN = "oauth_token";
	static final String PREF_KEY_OAUTH_SECRET = "oauth_token_secret";
	static final String PREF_KEY_TWITTER_LOGIN = "isTwitterLogedIn";
	static final String PREF_KEY_TWITTER_USERNAME = "TwitterUsername";

	final String PREFS_NAME = "pref";
	TwitterFactory factory;
	Twitter twitter;
	RequestToken requestToken;
	private final String CALLBACKURL = "oob";
	String authUrl;

	// Shared Preferences
	private static SharedPreferences mSharedPreferences;

	// Alert Dialog Manager
	AlertDialogManager alert = new AlertDialogManager();

	public String getLastTweet() {
		return lastTweet;
	}

	public TwitterShield() {
		super();
		// startListeningOnAKeyword("1sheeld");
	}

	@Override
	public ControllerParent<TwitterShield> setTag(String tag) {
		mSharedPreferences = activity.getApplicationContext()
				.getSharedPreferences("com.integreight.onesheeld",
						Context.MODE_PRIVATE);
		return super.setTag(tag);
	}

	public TwitterShield(Activity activity, String tag) {
		super(activity, tag);
		mSharedPreferences = activity.getApplicationContext()
				.getSharedPreferences("com.integreight.onesheeld",
						Context.MODE_PRIVATE);
	}

	// @Override
	// public void onUartReceive(byte[] data) {
	// if (data.length < 2)
	// return;
	// byte command = data[0];
	// byte methodId = data[1];
	// int n = data.length - 2;
	// byte[] newArray = new byte[n];
	// System.arraycopy(data, 2, newArray, 0, n);
	// if (command == UPDATE_STATUS_METHOD_ID) {
	// String tweet = new String(newArray);
	// if (isTwitterLoggedInAlready())
	// if (methodId == UPDATE_STATUS_METHOD_ID) {
	// if (!tweet.equals(lastTweet)) {
	// tweet(tweet);
	// eventHandler.onRecieveTweet(tweet);
	// } else
	// eventHandler
	// .onTwitterError("You have posted this tweet before!");
	// lastTweet = tweet;
	// }
	//
	// }
	// super.onUartReceive(data);
	// }

	public void setTwitterEventHandler(TwitterEventHandler eventHandler) {
		this.eventHandler = eventHandler;
	}

	public void tweet(final String tweet) {
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setOAuthConsumerKey(activity.getThisApplication().socialKeys.twitter.id);
		cb.setOAuthConsumerSecret(activity.getThisApplication().socialKeys.twitter.secret);
		cb.setOAuthAccessToken(mSharedPreferences.getString(
				PREF_KEY_OAUTH_TOKEN, null));
		cb.setOAuthAccessTokenSecret(mSharedPreferences.getString(
				PREF_KEY_OAUTH_SECRET, null));
		factory = new TwitterFactory(cb.build());
		twitter = factory.getInstance();
		// twitter.setOAuthConsumer(
		// mSharedPreferences.getString(PREF_KEY_OAUTH_TOKEN, null),
		// mSharedPreferences.getString(PREF_KEY_OAUTH_SECRET, null));
		AccessToken accestoken = new AccessToken(mSharedPreferences.getString(
				PREF_KEY_OAUTH_TOKEN, null), mSharedPreferences.getString(
				PREF_KEY_OAUTH_SECRET, null));
		twitter.setOAuthAccessToken(accestoken);
		final StatusUpdate st = new StatusUpdate(tweet);
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					twitter.updateStatus(st);
					Toast.makeText(activity, "Tweet posted!", Toast.LENGTH_SHORT).show();
				} catch (TwitterException e) {
					Log.e("TAG",
							"TwitterShield::StatusUpdate::TwitterException", e);
					if (eventHandler != null)eventHandler.onTwitterError(e.getErrorMessage());
				}
				if (eventHandler != null)
					eventHandler.stopProgress();

			}
		}).start();
	}
	
	public void sendDirectMessage(String userHandle, final String msg) {
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setOAuthConsumerKey(activity.getThisApplication().socialKeys.twitter.id);
		cb.setOAuthConsumerSecret(activity.getThisApplication().socialKeys.twitter.secret);
		cb.setOAuthAccessToken(mSharedPreferences.getString(
				PREF_KEY_OAUTH_TOKEN, null));
		cb.setOAuthAccessTokenSecret(mSharedPreferences.getString(
				PREF_KEY_OAUTH_SECRET, null));
		factory = new TwitterFactory(cb.build());
		twitter = factory.getInstance();
		AccessToken accestoken = new AccessToken(mSharedPreferences.getString(
				PREF_KEY_OAUTH_TOKEN, null), mSharedPreferences.getString(
				PREF_KEY_OAUTH_SECRET, null));
		twitter.setOAuthAccessToken(accestoken);
		if(!userHandle.startsWith("@"))userHandle="@"+userHandle;
		final String properUserHandle=userHandle;
		final Handler handler=new Handler(Looper.getMainLooper());
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					twitter.sendDirectMessage(properUserHandle, msg);
					handler.post(new Runnable() {
						
						@Override
						public void run() {
							// TODO Auto-generated method stub
							Toast.makeText(activity, "Message sent to "+properUserHandle+"!", Toast.LENGTH_SHORT).show();
							
						}
					});
				} catch (TwitterException e) {
					Log.e("TAG",
							"TwitterShield::StatusUpdate::TwitterException", e);
					if (eventHandler != null)eventHandler.onTwitterError(e.getErrorMessage());
				}
				if (eventHandler != null)
					eventHandler.stopProgress();

			}
		}).start();
	}

	public void login() {
		factory = new TwitterFactory();
		twitter = new TwitterFactory().getInstance();
		twitter.setOAuthConsumer(
				activity.getThisApplication().socialKeys.twitter.id,
				activity.getThisApplication().socialKeys.twitter.secret);
		if (mSharedPreferences.getString(PREF_KEY_OAUTH_TOKEN, null) != null
				&& mSharedPreferences.getString(PREF_KEY_OAUTH_SECRET, null) != null) {
			AccessToken accestoken = new AccessToken(
					mSharedPreferences.getString(PREF_KEY_OAUTH_TOKEN, null),
					mSharedPreferences.getString(PREF_KEY_OAUTH_SECRET, null));
			twitter.setOAuthAccessToken(accestoken);
		} else {
			new AsyncTask<Void, Void, Void>() {
				ProgressDialog prog;

				@Override
				protected void onPreExecute() {
					prog = new ProgressDialog(activity);
					prog.setMessage("Please, Wait!");
					prog.setCancelable(false);
					prog.show();
					super.onPreExecute();
				};

				@Override
				protected Void doInBackground(Void... params) {
					try {
						requestToken = twitter
								.getOAuthRequestToken(CALLBACKURL);
						authUrl = requestToken.getAuthenticationURL();
					} catch (TwitterException e) {
						Log.e("TAG",
								"TwitterShield::requestToken::TwitterException",
								e);
						if (eventHandler != null)eventHandler.onTwitterError(e.getErrorMessage());
					}
					return null;
				}

				@Override
				protected void onPostExecute(Void result) {

					final TwitterDialogListner listener = new TwitterDialogListner() {
						@Override
						public void onComplete() {
							SharedPreferences.Editor editor = mSharedPreferences
									.edit();
							editor.putString(PREF_KEY_OAUTH_TOKEN,
									TwitterAuthorization.FETCHED_ACCESS_TOKEN);
							editor.putString(PREF_KEY_OAUTH_SECRET,
									TwitterAuthorization.FETCHED_SECRET_TOKEN);
							editor.putString(PREF_KEY_TWITTER_USERNAME,
									TwitterAuthorization.TWITTER_USER_NAME);
							editor.putBoolean(PREF_KEY_TWITTER_LOGIN, true);
							if (eventHandler != null)
								eventHandler
										.onTwitterLoggedIn(TwitterAuthorization.TWITTER_USER_NAME);
							// Commit the edits!
							editor.commit();

						}

						@Override
						public void onError(String error) {
							if (error != null && !error.isEmpty())
								Toast.makeText(
										getApplication()
												.getApplicationContext(),
										error, Toast.LENGTH_SHORT).show();
						}

						@Override
						public void onCancel() {
							Toast.makeText(
									getApplication().getApplicationContext(),
									"Twitter Login Canceled",
									Toast.LENGTH_SHORT).show();
						}
					};
					new Handler().post(new Runnable() {

						@Override
						public void run() {
							TwitterDialog mDialog = new TwitterDialog(
									getActivity(), authUrl, twitter,
									requestToken, listener);
							mDialog.show();
							if (prog != null) {
								prog.dismiss();
								prog.cancel();
							}
						}
					});

					super.onPostExecute(result);
				}

			}.execute(null, null);

		}
	}

	public static interface TwitterEventHandler {
		void onRecieveTweet(String tweet);
		void onImageUploaded(String tweet);
		void onDirectMessageSent(String userHandle, String msg);

		void onTwitterLoggedIn(String userName);

		void onTwitterError(String error);

		void startProgress();

		void stopProgress();
	}

	public boolean isTwitterLoggedInAlready() {
		// return twitter login status from Shared Preferences
		return mSharedPreferences.getBoolean(PREF_KEY_TWITTER_LOGIN, false);
	}

	public void logoutFromTwitter() {
		Editor e = mSharedPreferences.edit();
		e.remove(PREF_KEY_OAUTH_TOKEN);
		e.remove(PREF_KEY_OAUTH_SECRET);
		e.remove(PREF_KEY_TWITTER_LOGIN);
		e.remove(PREF_KEY_TWITTER_USERNAME);
		e.commit();
	}

	@Override
	public void onNewShieldFrameReceived(ShieldFrame frame) {
		if (frame.getShieldId() == UIShield.TWITTER_SHIELD.getId()) {
			
			if (isTwitterLoggedInAlready())

				if (ConnectionDetector.isConnectingToInternet(getApplication()
						.getApplicationContext())) {
					if (eventHandler != null)
						eventHandler.startProgress();
					if (frame.getFunctionId() == UPDATE_STATUS_METHOD_ID) {
						lastTweet = frame.getArgumentAsString(0);
						tweet(lastTweet);
						if (eventHandler != null)
							eventHandler.onRecieveTweet(lastTweet);
					} else if (frame.getFunctionId() == UPLOAD_PHOTO_METHOD_ID) {
						lastTweet = frame.getArgumentAsString(0);
						byte sourceFolderId=frame.getArgument(1)[0];
						String imgPath=null;
						if(sourceFolderId==SocialUtils.FROM_ONESHEELD_FOLDER)
							imgPath=SocialUtils.getLastCapturedImagePathFromOneSheeldFolder(activity);
						else if (sourceFolderId==SocialUtils.FROM_CAMERA_FOLDER)
							imgPath=SocialUtils.getLastCapturedImagePathFromCameraFolder(activity);
						if(imgPath!=null){
						uploadPhoto(
								imgPath,
								lastTweet);
						if (eventHandler != null)
							eventHandler.onImageUploaded(lastTweet);
						}
					}
					else if(frame.getFunctionId()==UPDATE_SEND_MESSAGE_METHOD_ID){
						String userHandle=frame.getArgumentAsString(0);
						String msg=frame.getArgumentAsString(1);
						sendDirectMessage(userHandle, msg);
						if (eventHandler != null)
							eventHandler.onDirectMessageSent(userHandle, msg);
					}
					
				} else
					Toast.makeText(
							getApplication().getApplicationContext(),
							"Please check your Internet connection and try again.",
							Toast.LENGTH_SHORT).show();
		}

	}

	public void uploadPhoto(String filePath, String msg) {
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setOAuthConsumerKey(activity.getThisApplication().socialKeys.twitter.id);
		cb.setOAuthConsumerSecret(activity.getThisApplication().socialKeys.twitter.secret);
		cb.setOAuthAccessToken(mSharedPreferences.getString(
				PREF_KEY_OAUTH_TOKEN, null));
		cb.setOAuthAccessTokenSecret(mSharedPreferences.getString(
				PREF_KEY_OAUTH_SECRET, null));
		factory = new TwitterFactory(cb.build());
		twitter = factory.getInstance();
		AccessToken accestoken = new AccessToken(mSharedPreferences.getString(
				PREF_KEY_OAUTH_TOKEN, null), mSharedPreferences.getString(
				PREF_KEY_OAUTH_SECRET, null));
		twitter.setOAuthAccessToken(accestoken);
		final Handler handler=new Handler(Looper.getMainLooper());
		new AsyncTask<String, Void, Status>() {

			@Override
			protected twitter4j.Status doInBackground(String... params) {
				if (twitter != null)
					try {
						StatusUpdate status = new StatusUpdate(params[1]);
						File f = new File(params[0]);
						if (f.exists()) {
							int rotate = ImageUtils.getCameraPhotoOrientation(params[0]);
							Bitmap scaledBitmap  = ImageUtils.decodeFile(new File(params[0]), 1024);
							Matrix matrix = new Matrix();
							matrix.postRotate(rotate);
							Bitmap bitmap=Bitmap.createBitmap(scaledBitmap , 0, 0, scaledBitmap .getWidth(), scaledBitmap .getHeight(), matrix, true);
							ByteArrayOutputStream bos = new ByteArrayOutputStream(); 
							bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
							byte[] bitmapdata = bos.toByteArray();
							ByteArrayInputStream bs = new ByteArrayInputStream(bitmapdata);
							status.setMedia("Image",bs);
							handler.post(new Runnable() {
								
								@Override
								public void run() {
									// TODO Auto-generated method stub
									Toast.makeText(activity, "Uploading the image!", Toast.LENGTH_SHORT).show();
								}
							});
							
							return twitter.updateStatus(status);
						} else if (eventHandler != null) {
							System.err.println("File Not Found  " + params[0]);
							eventHandler.onTwitterError("File Not Found   "
									+ params[0]);
							eventHandler.stopProgress();
						}

					} catch (TwitterException e) {
						if (eventHandler != null) {
							eventHandler.stopProgress();
							eventHandler.onTwitterError(e.getErrorMessage());
						}
						e.printStackTrace();
					}
				return null;
			}

			@Override
			protected void onPostExecute(twitter4j.Status result) {
				if (eventHandler != null)
					eventHandler.stopProgress();
				if (result != null)
					Toast.makeText(activity, "Image uploaded and tweet posted!", Toast.LENGTH_LONG).show();
				super.onPostExecute(result);
			}
		}.execute(filePath, msg);

	}

	// private void startListeningOnAKeyword(String keyword) {
	// ConfigurationBuilder cb = new ConfigurationBuilder();
	// cb.setOAuthConsumerKey(TwitterAuthorization.CONSUMER_KEY);
	// cb.setOAuthConsumerSecret(TwitterAuthorization.CONSUMER_SECRET);
	// cb.setOAuthAccessToken("1608329586-6oJK4QJOPHT5fCwo7ckc9wZGEuaT1p6ZIQ5RBVH");
	// cb.setOAuthAccessTokenSecret("aRq7RsAp132UwHtO6Kr7ottM3f9i5wb58xTIGLZcElhoH");
	//
	// twitterStream = new TwitterStreamFactory(cb.build()).getInstance();
	// // twitterStream.s
	// twitterStream.clearListeners();
	// twitterStream.cleanUp();
	// FilterQuery query = new FilterQuery();
	// String[] keywords = new String[] { keyword };
	// query.track(keywords);
	//
	// StatusListener listener = new StatusListener() {
	// public void onScrubGeo(long arg0, long arg1) {
	// // TODO Auto-generated method stub
	//
	// }
	//
	// @Override
	// public void onStallWarning(StallWarning arg0) {
	// // TODO Auto-generated method stub
	//
	// }
	//
	// @Override
	// public void onStatus(twitter4j.Status arg0) {
	// // TODO Auto-generated method stub
	// Log.d("tweet", arg0.getText());
	// ShieldFrame frame = new ShieldFrame(
	// UIShield.TWITTER_SHIELD.getId(), (byte) 0x01);
	// frame.addStringArgument(arg0.getUser().getName());
	// frame.addStringArgument(arg0.getText());
	// activity.getThisApplication().getAppFirmata()
	// .sendShieldFrame(frame);
	// }
	//
	// @Override
	// public void onException(Exception arg0) {
	// // TODO Auto-generated method stub
	//
	// }
	//
	// @Override
	// public void onDeletionNotice(StatusDeletionNotice arg0) {
	// // TODO Auto-generated method stub
	//
	// }
	//
	// @Override
	// public void onTrackLimitationNotice(int arg0) {
	// // TODO Auto-generated method stub
	//
	// }
	// };
	// twitterStream.addListener(listener);
	// // sample() method internally creates a thread which manipulates
	// // TwitterStream and calls these adequate listener methods continuously.
	// twitterStream.filter(query);
	// // twitterStream.sample();
	// }
	//
	// private void stopListeningOnAKeyword() {
	// if (twitterStream == null)
	// return;
	// twitterStream.clearListeners();
	// twitterStream.cleanUp();
	// twitterStream = null;
	// }

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		// stopListeningOnAKeyword();
	}

}
