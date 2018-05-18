/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.music;

import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.view.KeyEvent;

/**
 * 
 */
public class MediaButtonIntentReceiver extends BroadcastReceiver {

    private static final int MSG_LONGPRESS_TIMEOUT = 1;
    private static final int LONG_PRESS_DELAY = 1000;

    private static long mLastClickTime = 0;
    private static boolean mDown = false;
    private static boolean mLaunched = false;

    /// M: AVRCP and Android Music AP supports the FF/REWIND @{
    private static final int ACTION_TIME = 500;
    /// @}

    /// M: tag for media button intent receiver
    private static final String TAG = "MediaButtonIntentReceiver";

    private static Handler sHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LONGPRESS_TIMEOUT:
                    if (!mLaunched) {
                        Context context = (Context)msg.obj;
                        Intent i = new Intent();
                        i.putExtra("autoshuffle", "true");
                        i.setClass(context, MusicBrowserActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(i);
                        mLaunched = true;
                    }
                    break;
            }
        }
    };
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String intentAction = intent.getAction();
        MusicLogUtils.d("MediaButtonIntentReceiver", "intentAction " + intentAction);
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intentAction)) {
            /// M: Only when Music service has been running, we need to send a start commond to stop playing. @
            if (isMusicServiceRunning(context)) {
                Intent i = new Intent(context, MediaPlaybackService.class);
                i.setAction(MediaPlaybackService.SERVICECMD);
                i.putExtra(MediaPlaybackService.CMDNAME, MediaPlaybackService.CMDPAUSE);
                //for multi user,when use BT play music,should start service match current user
                context.startServiceAsUser(i, UserHandle.CURRENT);
            }
            /// @}
        } else if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
            KeyEvent event = (KeyEvent)
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            
            if (event == null) {
                return;
            }

            int keycode = event.getKeyCode();
            int action = event.getAction();
            long eventtime = event.getEventTime();

            /// M: AVRCP and Android Music AP supports the FF/REWIND
            long deltaTime = eventtime - mLastClickTime;

            // single quick press: pause/resume. 
            // double press: next track
            // long press: start auto-shuffle mode.
            MusicLogUtils.d("MediaButtonIntentReceiver", "keycode:  " + keycode+"  action:  " + action+"  eventtime:  " + eventtime+" deltaTime: "+deltaTime);
            String command = null;
            switch (keycode) {
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    command = MediaPlaybackService.CMDSTOP;
                    break;
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    command = MediaPlaybackService.CMDTOGGLEPAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    command = MediaPlaybackService.CMDNEXT;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    command = MediaPlaybackService.CMDPREVIOUS;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    command = MediaPlaybackService.CMDPAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    command = MediaPlaybackService.CMDPLAY;
                    break;
                /// M: AVRCP and Android Music AP supports the FF/REWIND @{
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    command = MediaPlaybackService.CMDFORWARD;
                    break;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    command = MediaPlaybackService.CMDREWIND;
                    break;
                default:
                    break;
                /// @}
            }

            if (command != null) {
                if (action == KeyEvent.ACTION_DOWN) {
					MusicLogUtils.d("MediaButtonIntentReceiver", "mDown: " + mDown);
                    if (mDown) {
                        if ((MediaPlaybackService.CMDTOGGLEPAUSE.equals(command) ||
                                MediaPlaybackService.CMDPLAY.equals(command))
                                && mLastClickTime != 0 
                                && eventtime - mLastClickTime > LONG_PRESS_DELAY) {
                            sHandler.sendMessage(
                                    sHandler.obtainMessage(MSG_LONGPRESS_TIMEOUT, context));
                        }
                        /// M: AVRCP and Android Music AP supports the FF/REWIND @{
                        if ((MediaPlaybackService.CMDFORWARD.equals(command)
                                || MediaPlaybackService.CMDREWIND.equals(command))
                                && (mLastClickTime != 0)
                                && deltaTime > ACTION_TIME) {
                            sendToStartService(context, command, deltaTime);
                            mLastClickTime = eventtime;
                        }
                        /// @}
                    } else if (event.getRepeatCount() == 0) {
                        // only consider the first event in a sequence, not the repeat events,
                        // so that we don't trigger in cases where the first event went to
                        // a different app (e.g. when the user ends a phone call by
                        // long pressing the headset button)

                        // The service may or may not be running, but we need to send it
                        // a command.
                        MusicLogUtils.d("MediaButtonIntentReceiver", "CMDNEXT: eventtime - mLastClickTime: " + deltaTime);
                        Intent i = new Intent(context, MediaPlaybackService.class);
                        i.setAction(MediaPlaybackService.SERVICECMD);
                        if (keycode == KeyEvent.KEYCODE_HEADSETHOOK &&
                                eventtime - mLastClickTime < 300) {
                            i.putExtra(MediaPlaybackService.CMDNAME, MediaPlaybackService.CMDNEXT);
                            //for multi user,when use BT play music,should start service match current user
                            context.startServiceAsUser(i, UserHandle.CURRENT);
                            mLastClickTime = 0;
                        /// M: AVRCP and Android Music AP supports the FF/REWIND @{
                        } else if (MediaPlaybackService.CMDFORWARD.equals(command)
                                || MediaPlaybackService.CMDREWIND.equals(command)) {
                                    mLastClickTime = eventtime;
                        /// @}
                        //HQ_pangxuhui 20150408 add for bug :HQ01045563 begin
                        } else if(keycode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE &&
                                eventtime - mLastClickTime < 300){                            
                           i.putExtra(MediaPlaybackService.CMDNAME, MediaPlaybackService.CMDNEXT);
                            //for multi user,when use BT play music,should start service match current user
                            context.startServiceAsUser(i, UserHandle.CURRENT);
                            mLastClickTime = eventtime;
						//HQ_pangxuhui  end
                        }else {
                            i.putExtra(MediaPlaybackService.CMDNAME, command);
                            MusicLogUtils.d(TAG, command+ ",UserHandle.CURRENT: " + UserHandle.CURRENT);
                            //for multi user,when use BT play music,should start service match current user
                            context.startServiceAsUser(i, UserHandle.CURRENT);
                            mLastClickTime = eventtime;
                        }

                        mLaunched = false;
                        mDown = true; 
						mLastClickTime = eventtime;
                    }
                } else {
                    /// M: AVRCP and Android Music AP supports the FF/REWIND @{
                    if (MediaPlaybackService.CMDFORWARD.equals(command)
                            || MediaPlaybackService.CMDREWIND.equals(command)) {
                        sendToStartService(context, command, deltaTime);
                        //mLastClickTime = 0;
                        mLastClickTime = eventtime;
                        MusicLogUtils.d(TAG, " IF action == KeyEvent.ACTION_UP :mLastClickTime: " + mLastClickTime);
                    }
                    /// @}
                    sHandler.removeMessages(MSG_LONGPRESS_TIMEOUT);
                    mDown = false;
					MusicLogUtils.d(TAG, " action == KeyEvent.ACTION_UP :mLastClickTime: " + mLastClickTime);
                }
                if (isOrderedBroadcast()) {
                    abortBroadcast();
                }
            }
        }
    }

    /**
     * start service
     * @param context
     * @param command
     * @param deltaTime
     */
    public void sendToStartService(Context context, String command, long deltaTime) {
        Intent i = new Intent(context, MediaPlaybackService.class);
        i.setAction(MediaPlaybackService.SERVICECMD);
        i.putExtra(MediaPlaybackService.CMDNAME, command);
        i.putExtra(MediaPlaybackService.DELTATIME, deltaTime);
        MusicLogUtils.d(TAG, "sendToStartService,UserHandle.CURRENT: " + UserHandle.CURRENT);
        //for multi user,when use BT play music,should start service match current user
        context.startServiceAsUser(i, UserHandle.CURRENT);
    }

    /**
     * M: Check whether Music service is running.
     *
     * @param context The context
     * @return If running, return true, otherwise false.
     */
    private boolean isMusicServiceRunning(Context context) {
        boolean isServiceRuning = false;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final int maxServciesNum = 100;
        List<RunningServiceInfo> list = am.getRunningServices(maxServciesNum);
        for (RunningServiceInfo info : list) {
            if (MediaPlaybackService.class.getName().equals(info.service.getClassName())) {
                isServiceRuning = true;
                break;
            }
        }
        MusicLogUtils.d(TAG, "isMusicServiceRunning " + isServiceRuning + ", Runing service num is " + list.size());
        return isServiceRuning;
    }
}
