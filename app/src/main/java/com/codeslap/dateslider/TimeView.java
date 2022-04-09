/*
 * Copyright (C) 2011 Daniel Berndt - Codeus Ltd  -  DateSlider
 * 
 * This interface represents Views that are put onto the ScrollLayout
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

package com.codeslap.dateslider;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rareventure.gps2.reviewer.EnterFromDateToToDateActivity;
import com.rareventure.gps2.reviewer.EnterFromDateToToDateActivity.TimeObject;

import java.util.Calendar;

/**
 * This interface represents the views that will settle the ScrollLayout. Each element has to deal
 * with its label via the setText method and needs to contain the start and end time of the element.
 * Moreover this interface contains three implementations one simple TextView, A two-row
 * LinearLayout and a LinearLayout which colors Sundays red.
 */
public interface TimeView {

    float TEXT_SIZE_DP = 20;
	float MINI_TEXT_SIZE_DP = 8;

	void setVals(TimeObject to);

    void setVals(TimeView other);

    String getTimeText();

    long getStartTime();

    long getEndTime();

    /**
     * This is a simple implementation of a TimeView which realised through a TextView.
     */
    class TimeTextView extends androidx.appcompat.widget.AppCompatTextView implements TimeView {
        private long endTime, startTime;

        /**
         * constructor
         *
         * @param context      used to create the view
         * @param isCenterView true if the element is the centered view in the ScrollLayout
         * @param textSize     text size in dps
         */
        public TimeTextView(Context context, boolean isCenterView, float textSize) {
            super(context);
            setupView(isCenterView, textSize);
        }

        /**
         * this method should be overwritten by inheriting classes to define its own look and feel
         *
         * @param isCenterView true if the element is in the center of the scrollLayout
         * @param textSize     textSize in dps
         */
        void setupView(boolean isCenterView, float textSize) {
            setGravity(Gravity.CENTER);
            setTextSize(textSize);
            if (isCenterView) {
                setTypeface(Typeface.DEFAULT_BOLD);
                setTextColor(0xFF333333);
            } else {
                setTextColor(0xFF666666);
            }
        }

        public void setVals(EnterFromDateToToDateActivity.TimeObject to) {
            setText(to.text);
            this.startTime = to.startTime;
            this.endTime = to.endTime;
        }

        public void setVals(TimeView other) {
            setText(other.getTimeText());
            startTime = other.getStartTime();
            endTime = other.getEndTime();
        }

        public long getStartTime() {
            return this.startTime;
        }

        public long getEndTime() {
            return this.endTime;
        }

        public String getTimeText() {
            return getText().toString();
        }
    }

    /**
     * This is a more complex implementation of the TimeView consisting of a LinearLayout with
     * two TimeViews.
     */
    class TimeLayoutView extends LinearLayout implements TimeView {
        long endTime;
        long startTime;
        String text;
        boolean isCenter = false;
        TextView topView;
        TextView bottomView;

        /**
         * constructor
         *
         * @param context        used to create the view
         * @param isCenterView   true if the element is the centered view in the ScrollLayout
         * @param topTextSize    text size of the top TextView in dps
         * @param bottomTextSize text size of the bottom TextView in dps
         * @param lineHeight     LineHeight of the top TextView
         */
        public TimeLayoutView(Context context, boolean isCenterView, float topTextSize, float bottomTextSize, float lineHeight) {
            super(context);
            setupView(context, isCenterView, topTextSize, bottomTextSize, lineHeight);
            System.out.println("setupView(context, " + isCenterView + ", " + topTextSize + ", " + bottomTextSize + ", " + lineHeight + ");"+this);
        }

        /**
         * Setting up the top TextView and bottom TextVew
         *
         * @param context        used to create the view
         * @param isCenterView   true if the element is the centered view in the ScrollLayout
         * @param topTextSize    text size of the top TextView in dps
         * @param bottomTextSize text size of the bottom TextView in dps
         * @param lineHeight     LineHeight of the top TextView
         */
        void setupView(Context context, boolean isCenterView, float topTextSize, float bottomTextSize, float lineHeight) {
            setOrientation(VERTICAL);
            topView = new TextView(context);
            topView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            topView.setTextSize(topTextSize);
            bottomView = new TextView(context);
            bottomView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            bottomView.setTextSize(bottomTextSize);
            topView.setLineSpacing(0, lineHeight);
            if (isCenterView) {
                isCenter = true;
                topView.setTypeface(Typeface.DEFAULT_BOLD);
                topView.setTextColor(0xFF333333);
                bottomView.setTypeface(Typeface.DEFAULT_BOLD);
                bottomView.setTextColor(0xFF444444);
                topView.setPadding(0, 5 - (int) (topTextSize / 15.0), 0, 0);
            } else {
                topView.setPadding(0, 5, 0, 0);
                topView.setTextColor(0xFF666666);
                bottomView.setTextColor(0xFF666666);
            }
            addView(topView);
            addView(bottomView);

        }

        public void setVals(TimeObject to) {
            text = to.text.toString();
            setText();
            this.startTime = to.startTime;
            this.endTime = to.endTime;
        }

        public void setVals(TimeView other) {
            text = other.getTimeText().toString();
            setText();
            startTime = other.getStartTime();
            endTime = other.getEndTime();
        }

        /**
         * sets the TextView texts by splitting the text into two
         */
        void setText() {
            String[] splitTime = text.split(" ");
            topView.setText(splitTime[0]);
            bottomView.setText(splitTime[1]);
        }

        public String getTimeText() {
            return text;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }

    }

    /**
     * More complex implementation of the TimeView which is based on the TimeLayoutView.
     * Sundays are colored red in here.
     */
    class DayTimeLayoutView extends TimeLayoutView {

        boolean isSunday = false;

        /**
         * Constructor
         *
         * @param context      used to create the view
         * @param isCenterView true if the element is the centered view in the ScrollLayout
         */
        public DayTimeLayoutView(Context context, boolean isCenterView, float topTextSize,
                                 float bottomTextSize, float lineHeight) {
            super(context, isCenterView, topTextSize, bottomTextSize, lineHeight);
        }


        public void setVals(TimeObject to) {
            super.setVals(to);
            // TODO: make it timeZone dependent!
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(to.endTime);
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && !isSunday) {
                isSunday = true;
                colorMeSunday();
            } else if (isSunday && c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                isSunday = false;
                colorMeWorkday();
            }
        }

        /**
         * this method is called when the current View takes a Sunday as time unit
         */
        void colorMeSunday() {
            if (isCenter) {
                bottomView.setTextColor(0xFF773333);
                topView.setTextColor(0xFF553333);
            } else {
                bottomView.setTextColor(0xFF442222);
                topView.setTextColor(0xFF553333);
            }
        }


        /**
         * this method is called when the current View takes no Sunday as time unit
         */
        void colorMeWorkday() {
            if (isCenter) {
                topView.setTextColor(0xFF333333);
                bottomView.setTextColor(0xFF444444);
            } else {
                topView.setTextColor(0xFF666666);
                bottomView.setTextColor(0xFF666666);
            }
        }

        public void setVals(TimeView other) {
            super.setVals(other);
            DayTimeLayoutView otherDay = (DayTimeLayoutView) other;
            if (otherDay.isSunday && !isSunday) {
                isSunday = true;
                colorMeSunday();
            } else if (isSunday && !otherDay.isSunday) {
                isSunday = false;
                colorMeWorkday();
            }
        }

    }
}
