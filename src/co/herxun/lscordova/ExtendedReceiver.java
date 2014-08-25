package co.herxun.lscordova;

import org.json.JSONException;
import org.json.JSONObject;

import co.herxun.lscordova.LightspeedPushPlugin.PushPayload;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

// This is the receiver extends Lightspeed default receiver PushBroadcastReceiver
// We can override "onReceive" method to implement our customized behavior.  
public class ExtendedReceiver extends BroadcastReceiver {
	private final String LOG = "ExtendedReceiver";

	private final String ARRIVAL_ACT = "co.herxun.lscordova.ArrivalAct";

	private Bundle mBundle = null;

	@Override
	// As receiving notification, we'll show a dialog with title and alert message
	public void onReceive(Context context, Intent intent) {

		// If current activity is null, we won't invoke dialog.
		mBundle = intent.getExtras();
		if (mBundle == null)
			return;

		if (!mBundle.containsKey(PushPayload.PAYLOAD)) {
			return;
		}

		LightspeedPushPlugin.PushPayload instance = LightspeedPushPlugin.PushPayload.getInstance(mBundle);
		if (instance == null) {
			if (LightspeedPushPlugin.checkStaticStatus(LightspeedPushPlugin.PushManager.RecvMgr.sRecvPushCB)) {
				LightspeedPushPlugin.PushManager.RecvMgr.sRecvPushCB
						.error(LightspeedPushPlugin.PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_NOTIFICATION_PAYLOAD);
			}
			return;
		}

		// App開著時，丟Payload到JS
		if (LightspeedPushPlugin.checkStaticStatus(LightspeedPushPlugin.PushManager.RecvMgr.sRecvPushCB)) {
			LightspeedPushPlugin.PushManager.RecvMgr.sRecvPushCB.success(instance.mAlert);
//			showNoti(context, instance);
		} else { // App關閉，秀出Notification
					// Default behavior of Lightspeed PushBroadcastReceiver
			showNoti(context, instance);
		}
	}

	public String getArrivalActivity(Context ct) {

		String arrivalName = "";
		ComponentName cn = new ComponentName(ct, ExtendedReceiver.class);
		try {
			ActivityInfo info;
			info = ct.getPackageManager().getReceiverInfo(cn, PackageManager.GET_META_DATA);

			if( info.metaData.containsKey(ARRIVAL_ACT)){
				arrivalName = info.metaData.getString(ARRIVAL_ACT);
			}
			
			Log.i(LOG, "Arrival name = " + arrivalName);
			
			// TODO 提供開啓第三方Application？
			if (arrivalName.contains(ct.getPackageName())) {
				return arrivalName;
			} else {
				return (ct.getPackageName() + arrivalName);
			}

		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		return arrivalName;
	}

	@SuppressLint("NewApi")
	public void showNoti(Context ct, PushPayload instance) {

		NotificationManager mNotificationManager = (NotificationManager) ct.getSystemService(Context.NOTIFICATION_SERVICE);

		Intent notificationIntent = new Intent();
		notificationIntent.setComponent(new ComponentName(ct.getPackageName(), getArrivalActivity(ct)));
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtras(mBundle);
		// notificationIntent.putExtra("pushBundle", extras);

		PendingIntent contentIntent = PendingIntent.getActivity(ct, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ct).setDefaults(Notification.DEFAULT_ALL)
				.setSmallIcon(ct.getApplicationInfo().icon).setWhen(System.currentTimeMillis()).setContentTitle(instance.mTitle)
				.setTicker(instance.mAlert).setContentIntent(contentIntent).setContentText(instance.mAlert).setAutoCancel(true);

		if (instance.mSound == null) {

		} else if (instance.mSound.equals("default")) {
			mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
		}

		if (instance.mbVibrate) {
			long[] vib = new long[] { 100, 100, 100, 100 };
			mBuilder.setVibrate(vib);
		}

		Notification noti = mBuilder.build();
		mNotificationManager.notify((String) ct.getPackageManager().getApplicationLabel(ct.getApplicationInfo()), 0, noti);
	}
}
