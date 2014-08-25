package co.herxun.lscordova;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import co.herxun.lscordova.LightspeedPushPlugin.PushConstants.ParameterJsonKey.Push_Init;
import co.herxun.lscordova.LightspeedPushPlugin.PushConstants.ParameterJsonKey.Push_Schedule;
import co.herxun.lscordova.LightspeedPushPlugin.PushConstants.ParameterJsonKey.Push_Schedule_Resend;
import co.herxun.lscordova.LightspeedPushPlugin.PushConstants.ParameterJsonKey.Register;
import co.herxun.lscordova.LightspeedPushPlugin.PushManager.RecvMgr;

import com.arrownock.exception.ArrownockException;
import com.arrownock.push.AnPush;
import com.arrownock.push.AnPushCallbackAdapter;
import com.arrownock.push.AnPushStatus;

/**
 * @version 0.01
 * 
 * Lightspeed push Cordova Plugin.
 * 
 * Below document describes the steps you need to do for register Lightspeed Push channels.<br>
 * <br>
 * 1. With the action string {@link ActionSet#INITIALIZE_PUSH}, initialize a push instance.<br>
 * 2. If you're using built-in Lightspeed push, a Lightspeed server status monitor is necessary.<br>
 * With action string {@link ActionSet#STATUS_MONITOR} you could implement a callback function to monitor the status.<br>
 * 3. Register corresponding channels with action string {@link ActionSet#REGISTER}<br>
 * 4. Implement a callback function as receiving push with action string {@link ActionSet#RECV_PUSH}<br>
 * 5. ( Optional ) You could implement other feature of Push with the rest action string. For example, {@link ActionSet#SET_MUTE} set the device
 * receiving push without system alarm.<br>
 * <br>
 * <br>
 * For more detail information, please refer to the {@link ActionSet} to see what you can do with this plug-in.
 * 
 * @author Herxun
 */
public class LightspeedPushPlugin extends CordovaPlugin {
	private final static String LOG = "LightspeedPushPlugin";
	/**
	 * WebView from JavaScript
	 */
	static CordovaWebView sWebView;
	static Bundle sIntentBundle;

	/**
	 * Is current page on foreground.
	 */
	static Boolean sIsForeground = true;
	
//	private Bundle mBundle;

	private Context getApplicationContext() {
		return cordova.getActivity().getApplicationContext();
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		sWebView = this.webView;
		JSONObject jObj = args.getJSONObject(0);

		if( sIntentBundle == null ){
			sIntentBundle = cordova.getActivity().getIntent().getExtras();
		}
		// 初始化Push
		if (action.equals(PushConstants.ActionSet.INITIALIZE_PUSH)) {
			PushManager.initPush(getApplicationContext(), callbackContext, jObj);
		} // 註冊、反註冊頻道
		else if (action.equals(PushConstants.ActionSet.REGISTER)) {
			try {
				JSONArray array = jObj.getJSONArray(PushConstants.ParameterJsonKey.Register.CHANNEL);
				PushManager.RegisterManager.registerChannel(getApplicationContext(), callbackContext, array, true);
			} catch (ArrownockException e) {
				e.printStackTrace();
			}
		} else if (action.equals(PushConstants.ActionSet.UNREGISTER)) {
			try {
				JSONArray array = jObj.getJSONArray(PushConstants.ParameterJsonKey.Register.CHANNEL);
				PushManager.RegisterManager.registerChannel(getApplicationContext(), callbackContext, array, false);
			} catch (ArrownockException e) {
				e.printStackTrace();
			}
		} // 收到Push的Callback
		else if (action.equals(PushConstants.ActionSet.RECV_PUSH)) {
			PushManager.RecvMgr.handleRecv(callbackContext);
		} // 狀態改變Callback
		else if (action.equals(PushConstants.ActionSet.STATUS_MONITOR)) {
			PushManager.StatusMonitor.sStatusMonitor = callbackContext;
		} // 靜音模式
		else if (action.equals(PushConstants.ActionSet.SET_MUTE)) {
			PushManager.MuteManager.setMute(callbackContext);
		} // 靜音模式特定時間
		else if (action.equals(PushConstants.ActionSet.SET_SCHEDULE_MUTE)) {
			PushManager.MuteManager.setMute(callbackContext, jObj);
		} // 取消靜音模式
		else if( action.equals(PushConstants.ActionSet.CLEAR_MUTE)){
			PushManager.MuteManager.clearMute(callbackContext);
		} // 特定時間下，停收Push模式
		else if (action.equals(PushConstants.ActionSet.SET_SILENT_PERIOD)) {
			PushManager.SilentManager.setSilent(callbackContext, jObj);
		} // 取消停收Push模式
		else if(action.equals(PushConstants.ActionSet.CLEAR_SILENT_PERIOD)) {
			PushManager.SilentManager.clearSilent(callbackContext);
		}
		else{
			return false;
		}

		return true;
	}

	/**
	 * 所有和Push相關的子class、method，都會置放在這裡。
	 * 
	 * @author Herxun
	 */
	static class PushManager {
		static AnPush sAnPush;
		static Boolean sbIsGCM = false;
		static CallbackContext sCBPushInit;

		/**
		 * 初始化AnPush
		 * 
		 * @param ct
		 * @param cb
		 * @param obj
		 */
		static void initPush(Context ct, CallbackContext cb, JSONObject obj) {
			if (cb != null)
				sCBPushInit = cb;

			if (sAnPush == null) {
				try {
					sAnPush = AnPush.getInstance(ct);
					if (obj != null) {
						try {
							sAnPush.setSecureConnection(true);

							sbIsGCM = obj.getBoolean(PushConstants.ParameterJsonKey.Push_Init.GCM_PUSH);

							String appKey = obj.getString(PushConstants.ParameterJsonKey.Push_Init.APP_KEY);
							sAnPush.setAppKey(appKey);
							
							sAnPush.setCallback(new MyPushCallback());
							sCBPushInit.success();

						} catch (JSONException e) {
							sCBPushInit.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_ARGUMENT);
							e.printStackTrace();
						}
					}
					else{
						sCBPushInit.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_ARGUMENT);
					}

				} catch (ArrownockException e) {
					e.printStackTrace();
					if (e.getErrorCode() == ArrownockException.PUSH_INVALID_APP_KEY) {
						sCBPushInit.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_APPKEY);
					} else {
						sCBPushInit.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_INITIALIZE_PUSH);
					}
				}
			}
		}

		static class SilentManager {
			static CallbackContext sCBSilentPeriod;
			static CallbackContext sCBClearSilent;

			static void setSilent(CallbackContext cb, JSONObject obj) {
				sCBSilentPeriod = cb;
				try {
					int startHour = obj.getInt(PushConstants.ParameterJsonKey.Push_Schedule_Resend.HOUR);
					int startMinute = obj.getInt(PushConstants.ParameterJsonKey.Push_Schedule_Resend.MIN);
					int duration = obj.getInt(PushConstants.ParameterJsonKey.Push_Schedule_Resend.DURATION);
					Boolean resend = obj.getBoolean(PushConstants.ParameterJsonKey.Push_Schedule_Resend.RESEND);
					
					if (resend == null) {
						resend = false;
					}
					try {
						sAnPush.setSilentPeriod(startHour, startMinute, duration, resend);
					} catch (ArrownockException e) {
						sCBSilentPeriod.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_SILENT);
						e.printStackTrace();
					}
				} catch (JSONException e) {
					sCBSilentPeriod.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_ARGUMENT);
					e.printStackTrace();
				}
			}
			
			static void clearSilent(CallbackContext cb){
				sCBClearSilent = cb;
				try {
					sAnPush.clearSilentPeriod();
				} catch (ArrownockException e) {
					sCBClearSilent.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_CLEAR_SILENT);
					e.printStackTrace();
				}
			}

		}

		static class MuteManager {
			static CallbackContext sCBMute;
			static CallbackContext sCBClearMute;
			static CallbackContext sCBScheduleMute;

			static void clearMute(CallbackContext cb){
				sCBClearMute = cb;
				try {
					sAnPush.clearMute();
				} catch (ArrownockException e) {
					sCBClearMute.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_CLEAR_MUTE);
					e.printStackTrace();
				}
			}
			
			static void setMute(CallbackContext cb) {
				sCBMute = cb;
				try {
					sAnPush.setMute();
				} catch (ArrownockException e) {
					sCBMute.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_MUTE);
					e.printStackTrace();
				}
			}

			static void setMute(CallbackContext cb, JSONObject obj) {
				sCBScheduleMute = cb;
				try {
					int startHour = obj.getInt(PushConstants.ParameterJsonKey.Push_Schedule.HOUR);
					int startMinute = obj.getInt(PushConstants.ParameterJsonKey.Push_Schedule.MIN);
					int duration = obj.getInt(PushConstants.ParameterJsonKey.Push_Schedule.DURATION);
					try {
						sAnPush.setScheduledMute(startHour, startMinute, duration);
					} catch (ArrownockException e) {
						sCBScheduleMute.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_MUTE);
						e.printStackTrace();
					}
				} catch (JSONException e) {
					sCBScheduleMute.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_ARGUMENT);
					e.printStackTrace();
				}

			}
		}

		static class MyPushCallback extends AnPushCallbackAdapter {

			@Override
			public void register(boolean err, String arg1, ArrownockException arg2) {
				super.register(err, arg1, arg2);
				if (err) {
					Log.i(LOG,"err = " + arg2.getErrorCode() + " , " + arg2.getMessage());
					if (checkStaticStatus(RegisterManager.sRegPushCB)) {
						RegisterManager.sRegPushCB.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_REGISTER_CHANNELS);
					}
					return;
				}

				try {
					if (sbIsGCM == false)
						sAnPush.enable();

					if (checkStaticStatus(RegisterManager.sRegPushCB)) {
						RegisterManager.sRegPushCB.success();
					}
				} catch (ArrownockException e) {
					e.printStackTrace();
					if (checkStaticStatus(RegisterManager.sRegPushCB)) {
						RegisterManager.sRegPushCB.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_REGISTER_CHANNELS);
					}
				}
			};

			@Override
			public void clearMute(boolean err, ArrownockException arg1) {
				super.clearMute(err, arg1);
				if( err ){
					if( checkStaticStatus(MuteManager.sCBClearMute)){
						MuteManager.sCBClearMute.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_CLEAR_MUTE);
					}
				}
				else{
					MuteManager.sCBClearMute.success();
				}
			}
			
			@Override
			public void clearSilentPeriod(boolean err, ArrownockException arg1) {
				super.clearSilentPeriod(err, arg1);
				if( err ){
					if( checkStaticStatus(SilentManager.sCBClearSilent)){
						SilentManager.sCBClearSilent.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_CLEAR_SILENT);
					}
				}
				else{
					SilentManager.sCBClearSilent.success();
				}
			}
			
			@Override
			public void setMute(boolean err, ArrownockException arg1) {
				super.setMute(err, arg1);
				if (err) {
					if (checkStaticStatus(MuteManager.sCBMute)) {
						MuteManager.sCBMute.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_MUTE);
					}
				} else {
					MuteManager.sCBMute.success();
				}
			}

			@Override
			public void setScheduledMute(boolean err, ArrownockException arg1) {
				super.setScheduledMute(err, arg1);
				if (err) {
					if (checkStaticStatus(MuteManager.sCBScheduleMute)) {
						MuteManager.sCBScheduleMute.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_MUTE);
					}
				} else {
					MuteManager.sCBScheduleMute.success();
				}
			}

			@Override
			public void setSilentPeriod(boolean err, ArrownockException arg1) {
				super.setSilentPeriod(err, arg1);
				if(err){
					if( checkStaticStatus(SilentManager.sCBSilentPeriod)){
						SilentManager.sCBSilentPeriod.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_SILENT);
					}
					else{
						SilentManager.sCBSilentPeriod.success();
					}
				}
			}

			@Override
			public void statusChanged(AnPushStatus status, ArrownockException arg1) {
				super.statusChanged(status, arg1);
				if( StatusMonitor.sStatusMonitor != null ){
					if (status == AnPushStatus.DISABLE) {
						StatusMonitor.sStatusMonitor.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_STATUS_DISABLE);
					} else {
						StatusMonitor.sStatusMonitor.success();
					}
				}
			}

			@Override
			public void unregister(boolean err, ArrownockException arg1) {
				super.unregister(err, arg1);
				if (err) {
					if (checkStaticStatus(RegisterManager.sUnRegPushCB)) {
						RegisterManager.sUnRegPushCB.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_UNREGISTER_CHANNELS);
					}
				} else {
					if (checkStaticStatus(RegisterManager.sUnRegPushCB)) {
						RegisterManager.sUnRegPushCB.success();
					}
				}
			}
		}

		static class StatusMonitor {
			static CallbackContext sStatusMonitor;
		}

		static class RegisterManager {

			static CallbackContext sRegPushCB;
			static CallbackContext sUnRegPushCB;

			static void registerChannel(Context ct, CallbackContext cb, JSONArray args, Boolean isReg) throws ArrownockException {

				List<String> channel = new ArrayList<String>();

				try {
					for (int i = 0; i < args.length(); i++) {
						channel.add(args.getString(i));
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}

				if (isReg) {
					RegisterManager.sRegPushCB = cb;
					sAnPush.register(channel);
				} else {
					RegisterManager.sUnRegPushCB = cb;
					sAnPush.unregister(channel);
				}
			}
		}

		static class RecvMgr {
			static CallbackContext sRecvPushCB;

			static void handleRecv(CallbackContext cb) {
				sRecvPushCB = cb;
				
				if ( sIntentBundle != null && sIntentBundle.containsKey(PushPayload.PAYLOAD)) {
					PushPayload instance = PushPayload.getInstance(sIntentBundle);
					if( instance == null ){
						sRecvPushCB.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_NOTIFICATION_PAYLOAD);
					}
					else{
						sRecvPushCB.success(instance.mAlert);
						sIntentBundle.remove(PushPayload.PAYLOAD);
					}
				}
			}
		}
	}

	static Boolean checkStaticStatus(CallbackContext cb) {
		if (sIsForeground != null && sIsForeground == true && sWebView != null && cb != null) {
			return true;
		} else
			return false;
	}

	@Override
	public void onResume(boolean multitasking) {
		super.onResume(multitasking);
		Log.i(LOG,"onResume");
		sIsForeground = true;
	}

	@Override
	public void onPause(boolean multitasking) {
		super.onPause(multitasking);
		Log.i(LOG,"onPause");
		sIsForeground = false;
		sIntentBundle.remove(PushPayload.PAYLOAD);
	}

	@Override
	public void onDestroy() {
		sWebView = null;
		super.onDestroy();
	}
	
	@Override
	public void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		Log.i(LOG,"onNewIntent invoked");
		
		Bundle bundle = intent.getExtras();
		PushPayload instance = PushPayload.getInstance(bundle);
		if( instance != null ){
			RecvMgr.sRecvPushCB.success(instance.mAlert);
		}
		else{
			RecvMgr.sRecvPushCB.error(PushConstants.ErrorMessage.LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_NOTIFICATION_PAYLOAD);
		}
	}

	/**
	 * 將Bundle裡面的東西，轉成Json字串
	 * @param b
	 * @return
	 * @throws JSONException
	 */
//	static JSONObject convertBundleToJSON(Bundle b) throws JSONException {
//		JSONObject obj = new JSONObject();
//		// 取得所有key String
//		Iterator<String> it = b.keySet().iterator();
//		// 將key 與 value ，以Json Pair的方式存進Object中
//		while (it.hasNext()) {
//			String key = it.next();
//			Object value = b.get(key);
//			obj.put(key, value);
//		}
//
//		return obj;
//	}

	/**
	 * There are three (String)constant set under this field.<br>
	 * 1. {@link ActionSet}: All available action string is described detail here.<br>
	 * 2. {@link ErrorMessage}: Possible error messages.<br>
	 * 3. {@link ParameterJsonKey}: The corresponding parameter of JS method exec().<br>
	 * 
	 * @author Herxun
	 */
	public static class PushConstants {
		/**
		 * Available action strings for PushPlugin used in JavaScript.<br>
		 * For detail information of how to register channel on Lightspeed, please refer to the explanation of {@link LightspeedPushPlugin}
		 * 
		 * @author Herxun
		 */
		public static class ActionSet {

			/**
			 * This is the very first step in every application to initialize a push instance . <br>
			 * <br>
			 * 
			 * String Value: {@value #INITIALIZE_PUSH}<br>
			 * <br>
			 * # Param: Refer to {@link Push_Init}<br>
			 * # Success: {@link CallbackContext#success()}<br>
			 * # Error: {@link CallbackContext#error(String)} with error message {@link ErrorMessage}
			 * 
			 */
			public static final String INITIALIZE_PUSH = "initializePush";

			/**
			 * Register specific channels on Lightspeed server. <br>
			 * <br>
			 * 
			 * String Value: {@value REGISTER}<br>
			 * <br>
			 * # Param: Refer to {@link Register}<br>
			 * # Success: {@link CallbackContext#success()}<br>
			 * # Error: {@link CallbackContext#error(String)} with error message {@link ErrorMessage}
			 */
			public static final String REGISTER = "registerChannels";

			/**
			 * Unregister specific channels on Lightspeed server.<br>
			 * <br>
			 * 
			 * String Value: {@value #UNREGISTER}<br>
			 * <br>
			 * # Param: Refer to {@link Register}<br>
			 * # Success: {@link CallbackContext#success()}<br>
			 * # Error: {@link CallbackContext#error(String)} with error message {@link ErrorMessage}
			 */
			public static final String UNREGISTER = "unregisterChannels";

			/**
			 * Designate callback method as receiving push. As there's push message left in the queue, this would also handle the push message.<br>
			 * <br>
			 * 
			 * String Value: {@value #RECV_PUSH}<br>
			 * <br>
			 * # Success: {@link CallbackContext#success()}<br>
			 * # Error: {@link CallbackContext#error(String)} with error message {@link ErrorMessage}
			 */
			public static final String RECV_PUSH = "monitorReceivedRemoteNotification";

			/**
			 * This is for push by Lightspeed only ( GCM doesn't need this callback ) <br>
			 * Designate a callback method to monitor the connection state to Lightspeed push service.<br>
			 * <br>
			 * 
			 * String Value: {@value #STATUS_MONITOR}<br>
			 * <br>
			 * # Connected: invoke {@link CallbackContext#success()} <br>
			 * # Disconnected: invoke {@link CallbackContext#error(String)} with error message {@link ErrorMessage}
			 */
			public static final String STATUS_MONITOR = "statusMonitor";

			/**
			 * Receiving push without system alarm.<br>
			 * <br>
			 * 
			 * String Value: {@value #SET_MUTE}<br>
			 * <br>
			 * # Success: {@link CallbackContext#success()}<br>
			 * # Error: {@link CallbackContext#error(String)} with error message {@link ErrorMessage}
			 */
			public static final String SET_MUTE = "setMute";

			/**
			 * In a specific time period (of everyday) , receive push without any system alarm .<br>
			 * <br>
			 * 
			 * String Value: {@value #SET_SCHEDULE_MUTE}<br>
			 * <br>
			 * # Param: Refer to {@link Push_Schedule}<br>
			 * # Success: {@link CallbackContext#success()}<br>
			 * # Error: {@link CallbackContext#error(String)} with error message {@link ErrorMessage}
			 */
			public static final String SET_SCHEDULE_MUTE = "setMuteSchedule";

			/**
			 * Cancel all mute setting.<br>
			 * <br>
			 * 
			 * String Value: {@value #CLEAR_MUTE}<br>
			 * <br>
			 * # Success: {@link CallbackContext#success()}<br>
			 * # Error: {@link CallbackContext#error(String)} with error message {@link ErrorMessage}
			 */
			public static final String CLEAR_MUTE = "clearMute";

			/**
			 * Designate a specific period of time (of everyday) not to receive any push. And set whether the push will be resent afterward.<br>
			 * <br>
			 * 
			 * String Value: {@value #SET_SILENT_PERIOD}<br>
			 * <br>
			 * # Param: Refer to {@link Push_Schedule_Resend}<br>
			 * # Success: {@link CallbackContext#success()}<br>
			 * # Error: {@link CallbackContext#error(String)} with error message {@link ErrorMessage}
			 */
			public static final String SET_SILENT_PERIOD = "setSlientSchedule";

			/**
			 * Cancel all silent period setting.<br>
			 * <br>
			 * 
			 * String Value: {@value #CLEAR_SILENT_PERIOD}<br>
			 * <br>
			 * # Success: {@link CallbackContext#success()}<br>
			 * # Error: {@link CallbackContext#error(String)} with error message {@link ErrorMessage}
			 */
			public static final String CLEAR_SILENT_PERIOD = "clearSilent";
		}

		/**
		 * All error message list.
		 * 
		 * @author Herxun {@link ArrownockException}
		 * 
		 */
		public static class ErrorMessage {

			/**
			 * App key is invalid. <br>
			 * String Value: {@value #LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_APPKEY}
			 */
			public static final String LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_APPKEY = "LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_APPKEY";

			/**
			 * Initialize Push Instance failed. <br>
			 * String Value: {@value #LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_INITIALIZE_PUSH}
			 */
			public static final String LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_INITIALIZE_PUSH = "LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_REGISTER_REMOTE_NOTIFICATION";

			/**
			 * Register channels failed. <br>
			 * String Value: {@value #LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_REGISTER_CHANNELS}
			 */
			public static final String LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_REGISTER_CHANNELS = "LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_REGISTER_CHANNELS";
			/**
			 * Unregister channels failed. <br>
			 * String Value: {@value #LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_UNREGISTER_CHANNELS}
			 */
			public static final String LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_UNREGISTER_CHANNELS = "LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_UNREGISTER_CHANNELS";
			/**
			 * Payload format incorrect. <br>
			 * String Value: {@value #LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_NOTIFICATION_PAYLOAD}
			 */
			public static final String LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_NOTIFICATION_PAYLOAD = "LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_NOTIFICATION_PAYLOAD";
			/**
			 * Set mute failed. <br>
			 * String Value: {@value #LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_MUTE}
			 */
			public static final String LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_MUTE = "LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_MUTE";
			/**
			 * Clear mute setting failed. <br>
			 * String Value: {@value #LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_CLEAR_MUTE}
			 */
			public static final String LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_CLEAR_MUTE = "LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_CLEAR_MUTE";
			/**
			 * Set silent mode failed. <br>
			 * String Value: {@value #LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_SILENT}
			 */
			public static final String LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_SILENT = "LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_SET_SILENT";
			/**
			 * Clear silent mode failed. <br>
			 * String Value: {@value #LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_CLEAR_SILENT}
			 */
			public static final String LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_CLEAR_SILENT = "LSPUSH_ANDROID_PLUGIN_ERROR_FAILED_TO_CLEAR_SILENT";

			/**
			 * Parameter format is wrong. <br>
			 * String Value: {@value #LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_ARGUMENT}
			 */
			public static final String LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_ARGUMENT = "LSPUSH_ANDROID_PLUGIN_ERROR_INVALID_ARGUMENT";
			
			/**
			 * Lightspeed built-in push service is disabled. <br>
			 * String Value: {@value #LSPUSH_ANDROID_PLUGIN_ERROR_STATUS_DISABLE}
			 */
			public static final String LSPUSH_ANDROID_PLUGIN_ERROR_STATUS_DISABLE = "LSPUSH_ANDROID_PLUGIN_ERROR_STATUS_DISABLE";
		}

		/**
		 * (IMPORTANT) Put a JSONObject at the first index of the JSONArray argument as the argument of JS exec() method.<br>
		 * Put corresponding key-value pair into the JSONObject for Java usage.
		 * 
		 * @author Herxun
		 * 
		 */
		public static class ParameterJsonKey {

			/**
			 * Register/Un-register method parameters.
			 * 
			 * @author Herxun
			 */
			public static class Register {
				/**
				 * Specify the channels want to register with.<br>
				 * <br>
				 * Key[String]: {@value #CHANNEL} <br>
				 * Value[JSONArray]: Put channel list into a JSONArray
				 */
				public static final String CHANNEL = "channels";
			}

			/**
			 * Specified a push scheduled time period.
			 * 
			 * @author Herxun
			 */
			public static class Push_Schedule {
				/**
				 * The start hour of every day.<br>
				 * Key[String]: {@value #HOUR}<br>
				 * Value[Integer]: 0-23
				 */
				public static final String HOUR = "hour";
				/**
				 * The start min of every day.<br>
				 * Key[String]: {@value #MIN}<br>
				 * Value[Integer]: 0-59
				 */
				public static final String MIN = "min";
				/**
				 * Duration time in minutes.<br>
				 * Key[String]: {@value #DURATION}<br>
				 * Value[Integer]: 0-1440
				 */
				public static final String DURATION = "duration";
			}

			/**
			 * Specified a push scheduled time period with resend setting {@link ActionSet#SET_SILENT_PERIOD}.
			 * 
			 * @author Herxun
			 */
			public static class Push_Schedule_Resend {

				/**
				 * The start hour of every day.<br>
				 * Key[String]: {@value #HOUR}<br>
				 * Value[Integer]: 0-23
				 */
				public static final String HOUR = "hour";
				/**
				 * The start min of every day.<br>
				 * Key[String]: {@value #MIN}<br>
				 * Value[Integer]: 0-59
				 */
				public static final String MIN = "min";
				/**
				 * Duration time in minutes.<br>
				 * Key[String]: {@value #DURATION}<br>
				 * Value[Integer]: 0-1440
				 */
				public static final String DURATION = "duration";

				/**
				 * Resend push or not.<br>
				 * Key[String]: {@value #RESEND}<br>
				 * Value[Boolean]:<br>
				 * - true: Server resent the push after the designate period. <br>
				 * - false: Don't re-send at all
				 */
				public static final String RESEND = "resend";
			}

			/**
			 * Necessary parameter to initialize a push service.
			 * 
			 * @author Herxun
			 */
			public static class Push_Init {
				/**
				 * Specify whether it is a GCM push. <br>
				 * <br>
				 * Key[String]: {@value #GCM_PUSH}<br>
				 * Value[Boolean]:<br>
				 * - true: GCM Push.<br>
				 * - false: Lightspeed built-in push.
				 */
				public static final String GCM_PUSH = "gcmPush";
				/**
				 * Designate push application key. <br>
				 * Key[String]: {@value #APP_KEY}<br>
				 * Value[String]: Your App Key
				 */
				public static final String APP_KEY = "appKey";
			}
		}
	}
	
	static class PushPayload{
		static final String PAYLOAD = "payload";
		static final String ALERT = "alert";
		static final String SOUND = "sound";
		static final String TITLE = "title";
		static final String VIBRATE = "vibrate";
		
		String mTitle = "";
		String mAlert = "";
		String mSound = "";
		Boolean mbVibrate = false;
		
		static PushPayload getInstance(Bundle bundle){
			PushPayload instance = null;
			
			if( bundle != null && bundle.containsKey(PAYLOAD)){
				String payload = bundle.getString(PAYLOAD);
				instance = new PushPayload();
				try {
					JSONObject json = new JSONObject(payload);
					JSONObject androidPayload = json.getJSONObject("android");
//					Log.i(LOG, "Payload content = " + androidPayload.toString(1));

					if( androidPayload.has(ALERT))
						instance.mAlert = (String) androidPayload.get(ALERT);
					
					if( androidPayload.has(SOUND))
						instance.mSound = (String) androidPayload.get(SOUND);
					
					if( androidPayload.has(TITLE))
						instance.mTitle = (String) androidPayload.get(TITLE);
					
					if( androidPayload.has(VIBRATE))
						instance.mbVibrate = androidPayload.getBoolean(VIBRATE);

				} catch (JSONException e1) {
					e1.printStackTrace();
					instance = null;
				}
			}
			
			return instance;
		}
	}
}
