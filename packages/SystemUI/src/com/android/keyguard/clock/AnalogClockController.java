/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.keyguard.clock;

import android.app.WallpaperManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextClock;

import androidx.core.graphics.ColorUtils;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import java.util.TimeZone;

/**
 * Controller for Stretch clock that can appear on lock screen and AOD.
 */
public class AnalogClockController implements ClockPlugin {

    /**
     * Resources used to get title and thumbnail.
     */
    private final Resources mResources;

    /**
     * LayoutInflater used to inflate custom clock views.
     */
    private final LayoutInflater mLayoutInflater;

    /**
     * Extracts accent color from wallpaper.
     */
    private final SysuiColorExtractor mColorExtractor;

    /**
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    /**
     * Custom clock shown on AOD screen and behind stack scroller on lock.
     */
    private ClockLayout mBigClockView;
    private ImageClock mAnalogClock;
    private TextClock mLockClock;

    private int mHourColor;
    private int mMinuteColor;
    private int mBackgroundColor;

    /**
     * Create a BubbleClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public AnalogClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor) {
        mResources = res;
        mLayoutInflater = inflater;
        mColorExtractor = colorExtractor;
    }

    private void createViews() {
        mBigClockView = (ClockLayout) mLayoutInflater.inflate(R.layout.analog_clock, null);
        mAnalogClock = mBigClockView.findViewById(R.id.analog_clock);
        mHourColor = mResources.getColor(R.color.analog_clock_hour_color);
        mMinuteColor = mResources.getColor(R.color.analog_clock_minute_color);
        mBackgroundColor = mResources.getColor(R.color.analog_clock_bg_color);
        mAnalogClock.setClockColors(mHourColor, mMinuteColor);
    }

    @Override
    public void onDestroyView() {
        mBigClockView = null;
        mAnalogClock = null;
    }

    @Override
    public String getName() {
        return "analog";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.clock_title_analog);
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.analog_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {
        View previewClock = mLayoutInflater.inflate(R.layout.analog_clock_preview, null);
        ImageClock analogClock = previewClock.findViewById(R.id.analog_clock);
        analogClock.setClockColors(Color.WHITE, Color.WHITE);
        analogClock.setBackgroundResource(R.drawable.analog_clock_background_dark);
        analogClock.onTimeChanged();
        TextClock textDate = previewClock.findViewById(R.id.date);
        textDate.setTextColor(Color.WHITE);
        return mRenderer.createPreview(previewClock, width, height);
    }

    @Override
    public View getView() {
        return null;
    }

    @Override
    public View getBigClockView() {
        if (mBigClockView == null) {
            createViews();
        }
        return mBigClockView;
    }

    @Override
    public int getPreferredY(int totalHeight) {
        return totalHeight / 2;
    }

    @Override
    public void setTextColor(int color) {
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {
    }

    @Override
    public void onTimeTick() {
        mAnalogClock.onTimeChanged();
        mBigClockView.onTimeChanged();
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        int hourColor = ColorUtils.blendARGB(mHourColor, Color.WHITE, darkAmount);
        int minuteColor = ColorUtils.blendARGB(mMinuteColor, Color.WHITE, darkAmount);
        if (darkAmount == 1f) {
            mAnalogClock.setBackgroundResource(R.drawable.analog_clock_background_dark);
        } else {
            mAnalogClock.setBackgroundResource(R.drawable.analog_clock_background);
        }

        mAnalogClock.setClockColors(hourColor, minuteColor);
        mBigClockView.setDarkAmount(darkAmount);
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {
        mAnalogClock.onTimeZoneChanged(timeZone);
    }

    @Override
    public boolean shouldShowStatusArea() {
        return true;
    }

    @Override
    public void setTypeface(Typeface tf) {
    }
}
