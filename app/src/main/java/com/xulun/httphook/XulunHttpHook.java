package com.xulun.httphook;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import okhttp3.Request;

public class XulunHttpHook implements IXposedHookLoadPackage {
    private static final String TAG = "XulunHttpHook";
    private static final String package_name = "co.runner.app";
    private static final byte[] lock = new byte[1];
    private Gson gson = new GsonBuilder()
            //.setPrettyPrinting()
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return false;
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return clazz.getName().contains("Method");
                }
            })
            .create();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log(TAG + " Loaded app: " + lpparam.packageName);
        Log.i(TAG, "haha " + lpparam.packageName);
        hookOriginNewCall(lpparam);
    }

    private void hookOriginNewCall(final XC_LoadPackage.LoadPackageParam lp) {
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final ClassLoader cl = ((Context) param.args[0]).getClassLoader();

                Class<?> aClass = cl.loadClass("okhttp3.OkHttpClient");
                Class<?> requestClass = cl.loadClass("okhttp3.Request");
                findAndHookMethod(aClass, "newCall", requestClass, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);

                        try {
                            String coreInfo = "";
                            String str = toJson(param.args[0]);
                            //okhttp3.Request cannot be cast to okhttp3.Request
                            try {
                                Request request = gson.fromJson(str, Request.class);
                                //set result 修改请求参数
                                coreInfo += request.method() + " " + request.url() + " ----- ";
                            } catch (Exception e) {

                            }
                            Log.i(TAG, "request json string " + lp.packageName + " " + coreInfo + str);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Log.i(TAG, "OkHttpClient afterHookedMethod package name = " + lp.packageName);
                    }
                });
            }
        });
    }

    private void findHideDex(final OnFindDexListener listener) {
        XposedBridge.hookAllMethods(ContextWrapper.class, "attachBaseContext", new XC_MethodHook() {

            public void beforeHookedMethod(MethodHookParam param) {
                ClassLoader classLoader = ((Context) param.args[0]).getClassLoader();
                if (classLoader == null) return;
                if (listener != null) listener.onFind(classLoader);
            }
        });
        XposedBridge.hookAllConstructors(ClassLoader.class, new XC_MethodHook() {
            public void beforeHookedMethod(MethodHookParam param) {
                ClassLoader classLoader = (ClassLoader) param.args[0];
                if (classLoader == null) return;
                if (listener != null) listener.onFind(classLoader);
            }
        });
    }


    private String toJson(Object object) {
        synchronized (lock) {
            String r = "to json error";
            try {
                r = gson.toJson(object);
            } catch (Exception e) {
                r = "to json error: " + e.getMessage();
            }
            return r;
        }
    }

    private <T> T fromJson(String string, Class<T> classOfT) {
        synchronized (lock) {
            return gson.fromJson(string, classOfT);
        }
    }

    public interface OnFindDexListener {
        void onFind(ClassLoader classLoader);
    }
}
