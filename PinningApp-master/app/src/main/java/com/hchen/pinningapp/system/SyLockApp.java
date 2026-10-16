/*
 * This file is part of PinningApp.

 * PinningApp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2024 PinningApp Contributions
 */
package com.hchen.pinningapp.system;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.hchen.pinningapp.hook.Hook;
import com.hchen.pinningapp.utils.Device;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class SyLockApp extends Hook {
    private int taskId;
    private boolean isObserver = false;
    boolean isLock = false;

    @Override
    public void init() {
        findAndHookMethod("com.android.server.wm.ActivityTaskManagerService",
                "onSystemReady",
                new HookAction() {
                    @Override
                    protected void after(XC_MethodHook.MethodHookParam param) {
                        try {
                            Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                            if (context == null) {
                                context = findContext(FlAG_ONLY_ANDROID);
                                if (context == null) {
                                    logE(tag, "onSystemReady context is null!!");
                                    return;
                                }
                            }
                            if (!isObserver) {
                                Context finalContext = context;
                                ContentObserver contentObserver = new ContentObserver(new Handler(finalContext.getMainLooper())) {
                                    @Override
                                    public void onChange(boolean selfChange, @Nullable Uri uri, int flags) {
                                        isLock = getLockApp(finalContext) != -1;
                                        if (isLock) {
                                            taskId = getLockApp(finalContext);
                                            callMethod(param.thisObject, "startSystemLockTaskMode", taskId);
                                        } else {
                                            callMethod(param.thisObject, "stopSystemLockTaskMode");
                                        }
                                    }
                                };
                                context.getContentResolver().registerContentObserver(
                                        Settings.Global.getUriFor("key_lock_app"),
                                        false, contentObserver);
                                isObserver = true;
                            }
                        } catch (Throwable e) {
                            logE(tag, "E: " + e);
                        }
                    }
                }
        );

        // Hook shouldLockKeyguard 的所有重载，确保能拦截到实际被调用的那个
        hookAllMethods("com.android.server.wm.LockTaskController", "shouldLockKeyguard",
                new HookAction() {
                    @Override
                    protected void before(XC_MethodHook.MethodHookParam param) {
                        try {
                            Context mContext = (Context) getObjectField(param.thisObject, "mContext");
                            if (mContext == null) {
                                mContext = findContext(FlAG_ONLY_ANDROID);
                                if (mContext == null) {
                                    logE(tag, "shouldLockKeyguard context is null!!");
                                    return;
                                }
                            }
                            int enterLock = getEnterLock(mContext);
                            logI(tag, "shouldLockKeyguard intercepted, args=" + param.args.length
                                    + " enterLock=" + enterLock);
                            // -e 控制进入固定时是否锁屏; 1=锁屏, 0=不锁屏
                            param.setResult(enterLock == 1);
                        } catch (Throwable e) {
                            logE(tag, "shouldLockKeyguard hook E: " + e);
                        }
                    }
                }
        );

        if (Device.isPad()) {
            findAndHookMethod("com.android.server.wm.MiuiCvwGestureController$GesturePointerEventListener",
                    "onPointerEvent", MotionEvent.class,
                    new HookAction() {
                        @Override
                        protected void before(XC_MethodHook.MethodHookParam param) {
                            if (isLock) {
                                param.setResult(null);
                            }
                        }
                    }
            );
        }
    }

    public int getLockApp(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), "key_lock_app");

        } catch (Settings.SettingNotFoundException e) {
            logE(tag, "getLockApp E: " + e);
        }
        return -1;
    }

    /** 新增：进入固定后是否锁屏（pm pinning -e 控制） */
    public int getEnterLock(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), "lock_app_enter_lock");
        } catch (Settings.SettingNotFoundException e) {
            logE(tag, "getEnterLock E: " + e);
        }
        return 0; // 默认不锁屏
    }

    /** 原有：退出固定时是否锁屏（pm pinning -l 控制） */
    public int getExitLock(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), "exit_lock_app_screen");
        } catch (Settings.SettingNotFoundException e) {
            logE(tag, "getExitLock E: " + e);
        }
        return 0;
    }

}
