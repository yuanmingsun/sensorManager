package com.sunyuanming.myapplication;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.util.List;

public  class MainActivity extends Activity {
    private final String TAG="AudioActivity";
    private PowerManager.WakeLock mWakeLock;
    private KeyguardManager mKeyguardManager = null;
    private KeyguardManager.KeyguardLock mKeyguardLock = null;
    public boolean isDownHome;
    private SensorManager mSensorManager;
    private SensorEventListener mSensorEventListener;
    private Sensor mProximitySensor;
    private PowerManager mPowerManager;

    private float defaultScreenBrightness;//默认的屏幕亮度
    private boolean isProximity;//当前是否贴近手机  默认不是  如果是 则不分发事件
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        openKeyguardLock();
        preventTouch();
        super.onCreate(savedInstanceState);
    }
    //开启键盘锁以及亮屏  保证手机在锁屏熄屏情况下 可以唤醒
    private void openKeyguardLock() {
        //获取电源管理器 开启键盘锁 以及点亮屏幕 之后可以释放锁
        // 因为已经在onCreate中通过 getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 做过屏幕常亮处理
        PowerManager.WakeLock wakeLock = mPowerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK, "wakeLock");
        if (!wakeLock.isHeld()) wakeLock.acquire();
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        // 初始化键盘锁，可以锁定或解开键盘锁
        mKeyguardLock = mKeyguardManager.newKeyguardLock("");
        // 禁用显示键盘锁定
        mKeyguardLock.disableKeyguard();
        wakeLock.release();
    }
    //防触摸处理
    private void preventTouch() {
        //版本在api21以上  并且当前设备支持 接近屏幕熄灭的电源管理器  该管理器可以在靠近手机时关闭屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mPowerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            mWakeLock = mPowerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "CALL_ACTIVITY" + "#"
                    + getClass().getName());
            if (!mWakeLock.isHeld())
                mWakeLock.acquire();
        } else {
            //如果在api21以下或者不支持接近屏幕熄灭的电源管理器   通过接近传感器来实现将手机屏幕亮度变低 远离是恢复
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
             final WindowManager.LayoutParams attributes = getWindow().getAttributes();
            defaultScreenBrightness = attributes.screenBrightness;
            mSensorEventListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                        float[] values = event.values;
                        if (values.length > 0) {
                            if (values[0] == 0.0) {// 贴近手机
                                Log.i(TAG, "贴近手机");
                                attributes.screenBrightness = 0;
                                isProximity = true;
                            } else {// 远离手机
                                Log.i(TAG, "远离手机");
                                attributes.screenBrightness = defaultScreenBrightness;
                                isProximity = false;
                            }
                            getWindow().setAttributes(attributes);
                        }
                    }
                }
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            };
            List<Sensor> sensorList = mSensorManager.getSensorList(Sensor.TYPE_PROXIMITY);
            if (sensorList.size() > 0)
                mProximitySensor = sensorList.get(0);
        }
    }

    // 如果手机支持 靠近手机屏幕关闭的电源管理器 屏幕熄灭不会触发点击事件 则isProximity默认是false
    //否则贴近了手机 则不分发事件
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return isProximity || super.dispatchTouchEvent(ev);
    }
    /**
     * 重新上锁 释放电源管理器
     */
    private void releaseWakeLock() {
        try {
            if (mKeyguardLock != null) {
                mKeyguardLock.reenableKeyguard();
                mKeyguardLock = null;
            }
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSensorEventListener != null)
            mSensorManager.registerListener(mSensorEventListener, mProximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (mSensorEventListener != null)
            mSensorManager.unregisterListener(mSensorEventListener);
    }
    @Override
    protected void onDestroy() {
        releaseWakeLock();
        super.onDestroy();
    }
}