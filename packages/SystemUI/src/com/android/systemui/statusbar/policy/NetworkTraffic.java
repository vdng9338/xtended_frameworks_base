/*
 * Copyright (C) 2019 ion-OS
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

package com.android.systemui.statusbar.policy;

import java.text.DecimalFormat;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;

/*
*
* Seeing how an Integer object in java requires at least 16 Bytes, it seemed awfully wasteful
* to only use it for a single boolean. 32-bits is plenty of room for what we need it to do.
*
*/
public class NetworkTraffic extends TextView {

    private static final int INTERVAL = 1500; //ms
    private static final int BOTH = 0;
    private static final int UP = 1;
    private static final int DOWN = 2;
    private static final int COMBINED = 3;
    private static final int DYNAMIC = 4;
    private static final int KB = 1024;
    private static final int MB = KB * KB;
    private static final int GB = MB * KB;
    private static final String symbol = "B/s";

    private static DecimalFormat decimalFormat = new DecimalFormat("##0.#");
    static {
        decimalFormat.setMaximumIntegerDigits(3);
        decimalFormat.setMaximumFractionDigits(1);
    }

    private boolean mIsEnabled;
    protected boolean mAttached;
    private long totalRxBytes;
    private long totalTxBytes;
    private long lastUpdateTime;
    private int txtSize;
    private int txtImgPadding;
    private int mTrafficType;
    private boolean mShowArrow;
    private int mTrafficLayout;
    private int mAutoHideThreshold;
    private int mNetTrafSize;
    private int mTintColor;
    private boolean mTrafficVisible = false;
    private boolean iBytes;
    private boolean oBytes;

    private boolean mTrafficInHeaderView;

    private Handler mTrafficHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            long timeDelta = SystemClock.elapsedRealtime() - lastUpdateTime;

            if (timeDelta < INTERVAL * .95) {
                if (msg.what != 1) {
                    // we just updated the view, nothing further to do
                    return;
                }
                if (timeDelta < 1) {
                    // Can't div by 0 so make sure the value displayed is minimal
                    timeDelta = Long.MAX_VALUE;
                }
            }
            lastUpdateTime = SystemClock.elapsedRealtime();

            // Calculate the data rate from the change in total bytes and time
            long newTotalRxBytes = TrafficStats.getTotalRxBytes();
            long newTotalTxBytes = TrafficStats.getTotalTxBytes();
            long rxData = newTotalRxBytes - totalRxBytes;
            long txData = newTotalTxBytes - totalTxBytes;

            iBytes = (rxData <= (mAutoHideThreshold * 1024));
            oBytes = (txData <= (mAutoHideThreshold * 1024));

            if (shouldHide(rxData, txData, timeDelta)) {
                setText("");
                mTrafficVisible = false;
            } else {
                CharSequence output;
                if (mTrafficType == UP){
                    output = formatOutput(timeDelta, txData, symbol);
                } else if (mTrafficType == DOWN){
                    output = formatOutput(timeDelta, rxData, symbol);
                } else if (mTrafficType == BOTH) {
                    output = formatOutput(timeDelta, txData, symbol) + "\n" + formatOutput(timeDelta, rxData, symbol);
                } else if (mTrafficType == DYNAMIC) {
                    if (txData > rxData) {
                        output = formatOutput(timeDelta, txData, symbol);
                        if (!oBytes) {
                            oBytes = false;
                            iBytes = true;
                        } else {
                            oBytes = true;
                            iBytes = true;
                        }
                    } else {
                        output = formatOutput(timeDelta, rxData, symbol);
                        if (!iBytes) {
                            iBytes = false;
                            oBytes = true;
                        } else {
                            iBytes = true;
                            oBytes = true;
                        }
                    }
                } else {
                    output = formatOutput(timeDelta, rxData + txData, symbol);
                    if (txData > rxData) {
                        if (!oBytes) {
                            oBytes = false;
                            iBytes = true;
                        } else {
                            oBytes = true;
                            iBytes = true;
                        }
                    } else {
                        if (!iBytes) {
                            iBytes = false;
                            oBytes = true;
                        } else {
                            iBytes = true;
                            oBytes = true;
                        }
                    }
                }
                // Update view if there's anything new to show
                if (output != getText()) {
                    setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                    if (mTrafficLayout == 1) {
                        setMaxLines(2);
                        setLineSpacing(0.75f, 0.75f);
                    }
                    setText(output);
                }
                mTrafficVisible = true;
            }
            updateVisibility();
            updateTextSize();
            if (mShowArrow)
                updateTrafficDrawable();

            // Post delayed message to refresh in ~1000ms
            totalRxBytes = newTotalRxBytes;
            totalTxBytes = newTotalTxBytes;
            clearHandlerCallbacks();
            mTrafficHandler.postDelayed(mRunnable, INTERVAL);
        }

        private CharSequence formatOutput(long timeDelta, long data, String symbol) {
            long speed = (long)(data / (timeDelta / 1000F));
            if (mTrafficLayout == 0 || mTrafficType == BOTH) {
                if (speed < KB) {
                    return decimalFormat.format(speed) + symbol;
                } else if (speed < MB) {
                    return decimalFormat.format(speed / (float)KB) + "Ki" + symbol;
                } else if (speed < GB) {
                    return decimalFormat.format(speed / (float)MB) + "Mi" + symbol;
                }
                return decimalFormat.format(speed / (float)GB) + "Gi" + symbol;
            } else {
                return formatDecimal(speed);
            }
        }

        private CharSequence formatDecimal(long speed) {
            DecimalFormat mDecimalFormat;
            String mUnit;
            String formatSpeed;
            SpannableString spanUnitString;
            SpannableString spanSpeedString;

            if (speed >= GB) {
                mUnit = "Gi";
                mDecimalFormat = new DecimalFormat("0.00");
                formatSpeed =  mDecimalFormat.format(speed / (float)GB);
            } else if (speed >= 100 * MB) {
                mDecimalFormat = new DecimalFormat("000");
                mUnit = "Mi";
                formatSpeed =  mDecimalFormat.format(speed / (float)MB);
            } else if (speed >= 10 * MB) {
                mDecimalFormat = new DecimalFormat("00.0");
                mUnit = "Mi";
                formatSpeed =  mDecimalFormat.format(speed / (float)MB);
            } else if (speed >= MB) {
                mDecimalFormat = new DecimalFormat("0.00");
                mUnit = "Mi";
                formatSpeed =  mDecimalFormat.format(speed / (float)MB);
            } else if (speed >= 100 * KB) {
                mDecimalFormat = new DecimalFormat("000");
                mUnit = "Ki";
                formatSpeed =  mDecimalFormat.format(speed / (float)KB);
            } else if (speed >= 10 * KB) {
                mDecimalFormat = new DecimalFormat("00.0");
                mUnit = "Ki";
                formatSpeed =  mDecimalFormat.format(speed / (float)KB);
            } else {
                mDecimalFormat = new DecimalFormat("0.00");
                mUnit = "Ki";
                formatSpeed = mDecimalFormat.format(speed / (float)KB);
            }

            spanSpeedString = new SpannableString(formatSpeed);
            spanSpeedString.setSpan(new RelativeSizeSpan(0.75f), 0, (formatSpeed).length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            spanUnitString = new SpannableString(mUnit + symbol);
            spanUnitString.setSpan(new RelativeSizeSpan(0.70f), 0, (mUnit + symbol).length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            return TextUtils.concat(spanSpeedString, "\n", spanUnitString);
        }

        private boolean shouldHide(long rxData, long txData, long timeDelta) {
            long speedTxKB = (long)(txData / (timeDelta / 1000f)) / KB;
            long speedRxKB = (long)(rxData / (timeDelta / 1000f)) / KB;
           if (mTrafficType == UP) {
             return !getConnectAvailable() || speedTxKB < mAutoHideThreshold;
           } else if (mTrafficType == DOWN) {
             return !getConnectAvailable() || speedRxKB < mAutoHideThreshold;
           } else {
             return !getConnectAvailable() ||
                    (speedRxKB < mAutoHideThreshold &&
                    speedTxKB < mAutoHideThreshold);
           }
        }
    };

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mTrafficHandler.sendEmptyMessage(0);
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_STATE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_TYPE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_LAYOUT), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_ARROW), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_FONT_SIZE), false,
                    this, UserHandle.USER_ALL);
        }

        /*
         *  @hide
         */
        @Override
        public void onChange(boolean selfChange) {
            setMode();
            updateSettings();
        }
    }

    /*
     *  @hide
     */
    public NetworkTraffic(Context context) {
        this(context, null);
    }

    /*
     *  @hide
     */
    public NetworkTraffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     *  @hide
     */
    public NetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
        txtSize = (mTrafficType == BOTH)
                    ? resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size)
                    : mNetTrafSize;
        txtImgPadding = resources.getDimensionPixelSize(R.dimen.net_traffic_txt_img_padding);
        mTintColor = resources.getColor(android.R.color.white);
        Handler mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        setMode();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearHandlerCallbacks();
        if (mAttached) {
            mContext.unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                updateSettings();
            }
        }
    };

    private boolean getConnectAvailable() {
        ConnectivityManager connManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = (connManager != null) ? connManager.getActiveNetworkInfo() : null;
        return network != null;
    }

    private void updateSettings() {
        final ContentResolver resolver = getContext().getContentResolver();
        mTrafficInHeaderView = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION, 0,
                UserHandle.USER_CURRENT) == 1;
        updateVisibility();
        updateTextSize();
        if (mIsEnabled) {
            if (mAttached) {
                totalRxBytes = TrafficStats.getTotalRxBytes();
                lastUpdateTime = SystemClock.elapsedRealtime();
                mTrafficHandler.sendEmptyMessage(1);
            }
            updateTrafficDrawable();
            return;
        } else {
            clearHandlerCallbacks();
        }
    }

    private void setMode() {
        ContentResolver resolver = mContext.getContentResolver();
        mIsEnabled = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_STATE, 0,
                UserHandle.USER_CURRENT) == 1;
        mTrafficType = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_TYPE, 0,
                UserHandle.USER_CURRENT);
        mTrafficLayout = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_LAYOUT, 0,
                UserHandle.USER_CURRENT);
        mAutoHideThreshold = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 0,
                UserHandle.USER_CURRENT);
        mShowArrow = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_ARROW, 1,
	        UserHandle.USER_CURRENT) == 1;
        mTrafficInHeaderView = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION, 0,
                UserHandle.USER_CURRENT) == 1;
        mNetTrafSize = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_FONT_SIZE, 24,
                UserHandle.USER_CURRENT);
    }

    private void clearHandlerCallbacks() {
        mTrafficHandler.removeCallbacks(mRunnable);
        mTrafficHandler.removeMessages(0);
        mTrafficHandler.removeMessages(1);
    }

    private void updateTrafficDrawable() {
        int intTrafficDrawable;
        if (mIsEnabled && mShowArrow) {
            if (mTrafficType == UP) {
                if (oBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                } else {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
                }
            } else if (mTrafficType == DOWN) {
                if (iBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                } else {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
                }
            } else if (mTrafficType == DYNAMIC || mTrafficType == COMBINED) {
                if (iBytes && !oBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
                } else if (!iBytes && oBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
                } else {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                }
            } else {
                if (!iBytes && !oBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_updown;
                } else if (!oBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
                } else if (!iBytes) {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
                } else {
                    intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                }
            }
        } else {
            intTrafficDrawable = 0;
        }
        if (intTrafficDrawable != 0 && mShowArrow) {
            Drawable d = getContext().getDrawable(intTrafficDrawable);
            d.setColorFilter(mTintColor, Mode.MULTIPLY);
            setCompoundDrawablePadding(txtImgPadding);
            setCompoundDrawablesWithIntrinsicBounds(null, null, d, null);
        } else {
            setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
        setTextColor(mTintColor);
    }

    private void updateTextSize() {
        if (mTrafficLayout == 0 || mTrafficType == BOTH) {
            if (mTrafficType == BOTH) {
                txtSize = getResources().getDimensionPixelSize(R.dimen.net_traffic_multi_text_size);
            } else {
                txtSize = mNetTrafSize;
            }
            setLineSpacing(1f, 1f);
        } else {
            txtSize = getResources().getDimensionPixelSize(R.dimen.net_traffic_single_text_size_x);
            setMaxLines(2);
            setLineSpacing(0.75f, 0.75f);
        }
        setTextSize(TypedValue.COMPLEX_UNIT_PX, (float)txtSize);
    }

    private void updateVisibility() {
        if (mIsEnabled && mTrafficVisible && mTrafficInHeaderView) {
            setVisibility(View.VISIBLE);
        } else {
            setText("");
            setVisibility(View.GONE);
        }
    }

    public void onDensityOrFontScaleChanged() {
        final Resources resources = getResources();
        txtSize = (mTrafficType == BOTH)
                    ? resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size)
                    : mNetTrafSize;
        txtImgPadding = resources.getDimensionPixelSize(R.dimen.net_traffic_txt_img_padding);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, (float)txtSize);
        setCompoundDrawablePadding(txtImgPadding);
        updateTextSize();
    }
}

