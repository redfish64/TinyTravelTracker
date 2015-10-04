/** 
    Copyright 2015 Tim Engler, Rareventure LLC

    This file is part of Tiny Travel Tracker.

    Tiny Travel Tracker is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Tiny Travel Tracker is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Tiny Travel Tracker.  If not, see <http://www.gnu.org/licenses/>.

*/
package com.rareventure.gps2.reviewer.timeview;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Region.Op;
import android.graphics.Shader;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;

import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.database.TimeZoneTimeRow;
import com.rareventure.gps2.reviewer.map.OsmMapGpsTrailerReviewerMapActivity;

public class TimeView extends View {
	private static final String[] MONTHS = new String[] { "Jan", "Feb", "Mar",
			"Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

	private static final String[] DAYS_OF_WEEK = new String[] { "Sun", "Mon",
			"Tue", "Wed", "Thu", "Fri", "Sat" };

	private static final float Y_TO_SCREEN = 2f;

	private static final double MOVE_X_SCALE = 1;
	private static final double MOVE_Y_SCALE = 1;

	private static final float SELECTED_AREA_WIDTH_PERC = .70f;

	private static final float SELECTED_AREA_HEIGHT_DP = 40;

	public static final float YPOS_LINE_STEP = .2f;

	private static final int SELECTED_AREA_Y_POS_FROM_BOTTOM_DP = 25;

	private static final int MAX_LABEL_LINES = 4;

	private TextPaint textPaint;
	private FontMetricsInt textPaintFontMetrics;

	public static interface Listener {
		void notifyTimeViewChange();
		void notifyTimeViewReady();
	}

	private interface Strip {
		public abstract void processLabels(DrawerLabelProcessor lp,
				int startSec, int endSec, int selectedStartTime,
				int selectedEndTime, long nowMs, long nowMidnightMs);

		public abstract void processLines(DrawerLineProcessor lp, int startSec,
				int endSec);

		public abstract int roundTo(int timeSec);

		public abstract boolean isTimeAndDate();
	}

	public class CalendarStrip implements Strip {
		protected int calendarId;
		protected int step;
		private StaticLabels staticLabels = new StaticLabels();

		public CalendarStrip(int calendarId, int step) {
			super();
			this.calendarId = calendarId;
			this.step = step;
		}

		@Override
		public void processLabels(DrawerLabelProcessor lp, int startSec,
				int endSec, int selectedTimeStart, int selectedTimeEnd,
				long nowMs, long nowMidnightMs) {
			calendar.setTimeInMillis(startSec * 1000l);
			Util.clearCalendarValuesUnder(calendar, calendarId);
			// truncate the calendar id value to its step
			calendar.set(calendarId,
					calendar.get(calendarId) - calendar.get(calendarId) % step);

			int time = (int) (calendar.getTimeInMillis() / 1000);

			String[] dynamicLabels = new String[3];

			lp.process(time, TimeView.getDynamicLabels(dynamicLabels, calendar,
					calendarId));

			while (time < endSec) {
				stepCalendar();
				time = (int) (calendar.getTimeInMillis() / 1000);
				lp.process(time, TimeView.getDynamicLabels(dynamicLabels,
						calendar, calendarId));
			}

			getStaticLabels(staticLabels, calendar, calendarId,
					selectedTimeStart, selectedTimeEnd, nowMs, nowMidnightMs);

			lp.processStaticLabels(startSec, endSec, staticLabels);

		}

		@Override
		public void processLines(DrawerLineProcessor lp, int startSec,
				int endSec) {
			calendar.setTimeInMillis(startSec * 1000l);
			Util.clearCalendarValuesUnder(calendar, calendarId);
			// truncate the calendar id value to its step
			calendar.set(calendarId,
					calendar.get(calendarId) - calendar.get(calendarId) % step);

			int time = (int) (calendar.getTimeInMillis() / 1000);

			lp.process(time);

			while (time < endSec) {
				stepCalendar();
				time = (int) (calendar.getTimeInMillis() / 1000);
				lp.process(time);
			}
		}

		protected void stepCalendar() {
			calendar.add(calendarId, step);
		}

		public int roundTo(int timeSec) {
			calendar.setTimeInMillis(timeSec * 1000l);
			Util.clearCalendarValuesUnder(calendar, calendarId);
			int prevTime = (int) (calendar.getTimeInMillis() / 1000);
			stepCalendar();
			int nextTime = (int) (calendar.getTimeInMillis() / 1000);

			if (timeSec - prevTime < nextTime - timeSec)
				return prevTime;

			return nextTime;
		}

		@Override
		public boolean isTimeAndDate() {
			switch (calendarId) {
			case Calendar.HOUR:
			case Calendar.HOUR_OF_DAY:
			case Calendar.MINUTE:
			case Calendar.SECOND:
			case Calendar.MILLISECOND:
				return true;
			default:
				return false;
			}
		}
	}

	// like a calendar strip for days, but will skip 30th and 31st
	// and go right to the 1st
	public class DayOfMonthStrip extends CalendarStrip {

		public DayOfMonthStrip(Calendar calendar, int step) {
			super(Calendar.DAY_OF_MONTH, step);
		}

		@Override
		protected void stepCalendar() {
			calendar.add(Calendar.DAY_OF_MONTH, step);
			if (calendar.get(calendarId) >= 29) {
				calendar.add(Calendar.MONTH, 1);
				calendar.set(Calendar.DAY_OF_MONTH,
						calendar.getActualMinimum(Calendar.DAY_OF_MONTH));
			}
		}
	}

	public TimeViewOvalDrawer ovalDrawer;

	public class StripData {
		private static final float STRIP_LABEL_SCALE = 1.1f;
		float yPos;

		// this is the min x to time sec at the label of the strip
		float xToTimeSec;
		Strip strip;
		private double w;

		public StripData(float xToTimeSec, Strip strip) {
			super();
			init(xToTimeSec, strip);
		}

		public StripData(float xToTimeSec, int calendarId, int step) {
			init(xToTimeSec, new CalendarStrip(calendarId,
					step));
		}

		public StripData(String biggestText, int timePerStep, int calendarId,
				int step) {
			init(calcXToTimeSec(biggestText, timePerStep), new CalendarStrip(
				 calendarId, step));
		}

		private void init(float xToTimeSec, Strip strip) {
			this.xToTimeSec = xToTimeSec;
			this.strip = strip;
		}

		private float calcXToTimeSec(String biggestText, int timePerStep) {
			Rect bounds = new Rect();
			textPaint.getTextBounds(biggestText, 0, biggestText.length(),
					bounds);
			return (bounds.width() * STRIP_LABEL_SCALE + STRIP_LABEL_EXTRA_PAD)
					/ timePerStep;
		}

		public StripData(String biggestText, int timePerStep, Strip strip) {
			init(calcXToTimeSec(biggestText, timePerStep), strip);
		}

		public void calcGradient(int yPos, StripData sd2) {
			this.yPos = yPos;
			w = Math.log(sd2.xToTimeSec / this.xToTimeSec);
		}

	}

	private StripData[] stripData;

	/**
	 * The center of the selected area in the y direction (which is not the
	 * center of the the screen)
	 */
	double yPos;

	private OsmMapGpsTrailerReviewerMapActivity gtum;

	public TimeView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public TimeView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public TimeView(Context context) {
		super(context);
		init();
	}

	public void setActivity(OsmMapGpsTrailerReviewerMapActivity gtum) {
		this.gtum = gtum;
	}

	private RectF tempRect = new RectF();

	private float downX;
	private Listener listener;

	private float lastX;

	private long lastDownTime;

	private float lastY;

	private TimeZoneTimeRow currTimeZone;

	private TimeZone localTimeZone = Util.getCurrTimeZone();

	private int minTimeSec;

	private int maxTimeSec;

	private DrawerLabelProcessor drawerLabelProcessor = new DrawerLabelProcessor();
	private DrawerLineProcessor drawerLineProcessor = new DrawerLineProcessor();

	private Rect selectedAreaDim;

	public int selectedTimeStart;

	public int selectedTimeEnd;

	private int centerTimeSec;

	private int yPosSdIndex;

	private Rect bounds = new Rect();
	protected final float STRIP_LABEL_EXTRA_PAD = Util.convertSpToPixel(10,
			getContext());

	private Paint minMaxPaint;

	private long nowMidnightMs;

	/**
	 * This calendar is updated to the current timezone 
	 */
	private Calendar calendar;

	private void init() {
		
		calendar = Calendar.getInstance();
		Util.clearCalendarValuesUnder(calendar, Calendar.DATE);
		nowMidnightMs = calendar.getTimeInMillis();

		minMaxPaint = new Paint();
		minMaxPaint.setStrokeWidth(Util.convertDpToPixel(3, getContext()));
		minMaxPaint.setColor(Color.LTGRAY);

		textPaint = new TextPaint();
		textPaint.setTextSize(Util.convertSpToPixel(12, getContext()));
		textPaint.setColor(Color.WHITE);
		textPaint.setAntiAlias(true);
		textPaintFontMetrics = textPaint.getFontMetricsInt();

		ovalDrawer = new TimeViewOvalDrawer(getContext());

		stripData = new StripData[] {
				new StripData("2000", Util.SECONDS_IN_YEAR, Calendar.YEAR, 1),
				new StripData("2000", Util.SECONDS_IN_MONTH * 3,
						Calendar.MONTH, 3),
				new StripData("2000", Util.SECONDS_IN_MONTH, Calendar.MONTH, 1),
				new StripData("Aug 30", Util.SECONDS_IN_MONTH / 2,
						new DayOfMonthStrip(calendar, 15)),
				new StripData("Aug 30", Util.SECONDS_IN_MONTH / 4,
						new DayOfMonthStrip(calendar, 7)),
				new StripData("Aug 30", Util.SECONDS_IN_DAY * 2,
						Calendar.DAY_OF_MONTH, 2),
				new StripData("Aug 30", Util.SECONDS_IN_DAY,
						Calendar.DAY_OF_MONTH, 1),
				new StripData("12:00 AM", 3600 * 12, Calendar.HOUR_OF_DAY, 12),
				new StripData("12:00 AM", 3600 * 6, Calendar.HOUR_OF_DAY, 6),
				new StripData("12:00 AM", 3600 * 3, Calendar.HOUR_OF_DAY, 3),
				new StripData("12:00 AM", 3600, Calendar.HOUR_OF_DAY, 1),
				new StripData("12:00 AM", (3600 / 2), Calendar.MINUTE, 30),
				new StripData("12:00 AM", (3600 / 4), Calendar.MINUTE, 15),
				new StripData("12:00 AM", (3600 / 20), Calendar.MINUTE, 5),
				new StripData("12:00 AM", (3600 / 60), Calendar.MINUTE, 1) };

		//default ypos
		yPos = stripData[6].yPos;
		yPosSdIndex = getStripDataIndex(yPos);

		centerTimeSec = (int) (System.currentTimeMillis() / 1000);
		
		// setup stripdata
		for (int i = 0; i < stripData.length - 1; i++) {
			stripData[i].calcGradient(i, stripData[i + 1]);
		}

		stripData[stripData.length - 1].yPos = stripData.length - 1;
		
		//initialize the current time to a good value
		int now = (int) (System.currentTimeMillis()/1000l);
		
		Util.runWhenGetWidthWorks(this, new Runnable()
		 {

			@Override
			public void run() {
				setupSelectedAreaDim();
				listener.notifyTimeViewReady();
			}});

	}

	public static String[] getDynamicLabels(String[] out, Calendar calendar,
			int calendarId) {
		int value = calendar.get(calendarId);

		out[0] = out[1] = "";

		switch (calendarId) {
		case Calendar.YEAR:
			out[1] = String.valueOf(value);
			break;
		case Calendar.MONTH:
			out[1] = MONTHS[value - calendar.getActualMinimum(Calendar.MONTH)];
			break;
		case Calendar.DATE:
			out[0] = MONTHS[calendar.get(Calendar.MONTH)
					- calendar.getActualMinimum(Calendar.MONTH)]
					+ " "
					+ (value - calendar.getActualMinimum(Calendar.DATE) + 1);
			out[1] = DAYS_OF_WEEK[calendar.get(Calendar.DAY_OF_WEEK)
					- calendar.getActualMinimum(Calendar.DAY_OF_WEEK)];
			break;
		case Calendar.HOUR_OF_DAY:
			out[1] = (value % 12 == 0 ? "12:00" : (value % 12) + ":00 ")
					+ (value >= 12 ? "PM" : "AM");
			break;
		case Calendar.MINUTE:
			int hour = calendar.get(Calendar.HOUR_OF_DAY);

			out[1] = String.format("%02d:%02d %s", hour % 12 == 0 ? 12
					: hour % 12, value, (hour >= 12 ? " PM" : " AM"));
			break;
		default:
			throw new IllegalStateException("what is ?" + calendarId);
		}

		return out;

	}

	private static class StaticLabels {
		StringBuffer[] out1;
		StringBuffer[] out2;

		public StaticLabels() {
			out1 = new StringBuffer[3];
			out2 = new StringBuffer[3];

			for (int i = 0; i < 3; i++) {
				out1[i] = new StringBuffer();
				out2[i] = new StringBuffer();
			}
		}

		public void reset() {
			for (int i = 0; i < 3; i++) {
				out1[i].delete(0, out1[i].length());
				out2[i].delete(0, out2[i].length());
			}
		}
	}

	/**
	 * 
	 * @param out
	 *            an output array of length 3
	 * @param calendar
	 * @param calendarId
	 * @param endSec
	 * @param startSec
	 */
	public void getStaticLabels(StaticLabels out, Calendar calendar,
			int calendarId, int startSec, int endSec, long nowMs,
			long nowMidnightMs) {

		boolean atStart = false, atEnd = false;

		if (endSec > maxTimeSec) {
			endSec = maxTimeSec;
			atEnd = true;
		}

		if (startSec < minTimeSec) {
			startSec = minTimeSec;
			atStart = true;
		}
		
		//sometimes the bar can get completely out of range of the 
		//selected area. If this happens we want to make sure the
		// static labels show "now" rather than "now to 7 minutes ago (end)"
		if (startSec > maxTimeSec) {
			startSec = maxTimeSec;
		}

		if (endSec < minTimeSec) {
			endSec = minTimeSec;
		}

		out.reset();

		calendar.setTimeInMillis(startSec * 1000l);

		switch (calendarId) {
		case Calendar.MINUTE:
		case Calendar.HOUR_OF_DAY:

			int startDate = calendar.get(Calendar.DATE);

			out.out1[2].append(DAYS_OF_WEEK[calendar.get(Calendar.DAY_OF_WEEK)
					- calendar.getActualMinimum(Calendar.DAY_OF_WEEK)]);

			out.out1[1]
					.append(MONTHS[calendar.get(Calendar.MONTH)
							- calendar.getActualMinimum(Calendar.MONTH)])
					.append(" ")
					.append(calendar.get(Calendar.DATE)
							- calendar.getActualMinimum(Calendar.DATE) + 1);
			chooseFromNowDescription(out.out1[0], calendar, nowMs,
					nowMidnightMs);

			// update calendar to end sec here
			calendar.setTimeInMillis(endSec * 1000l);

			StringBuffer end = chooseFromNowDescription(null, calendar, nowMs,
					nowMidnightMs);

			addStartAndEnd(out.out1[0], end, atStart, atEnd);

			// since not all days are 24 hours long (dst, leap seconds, etc),
			// but we know they definately are under 48 hours long
			// we do this check as so
			if (endSec > startSec + Util.SECONDS_IN_DAY * 2
					|| calendar.get(Calendar.DATE) != startDate) {
				out.out1[1].append(" - ");

				out.out2[1]
						.append(MONTHS[calendar.get(Calendar.MONTH)
								- calendar.getActualMinimum(Calendar.MONTH)])
						.append(" ")
						.append(calendar.get(Calendar.DATE)
								- calendar.getActualMinimum(Calendar.DATE) + 1);
				out.out2[2].append(DAYS_OF_WEEK[calendar
						.get(Calendar.DAY_OF_WEEK)
						- calendar.getActualMinimum(Calendar.DAY_OF_WEEK)]);
			}

			break;
		case Calendar.DATE:
		case Calendar.MONTH:
		case Calendar.YEAR:
			chooseFromNowDescription(out.out1[0], calendar, nowMs,
					nowMidnightMs);

			calendar.setTimeInMillis(endSec * 1000l);

			end = chooseFromNowDescription(null, calendar, nowMs,
					nowMidnightMs);
			
			addStartAndEnd(out.out1[0], end, atStart, atEnd);

			break;
		default:
			throw new IllegalStateException("what is ?" + calendarId);
		}

		
		
		String tztStr = getTimeZoneStr();
		
		if(tztStr != null)
		{
			if (out.out2[1].length() > 0)
				out.out2[1].append(" ").append(getTimeZoneStr());
			else if (out.out1[1].length() > 0)
				out.out1[1].append(" ").append(getTimeZoneStr());
			else
				out.out1[1].append(getTimeZoneStr());
		}
		
	}

	private String getTimeZoneStr() {
		if(currTimeZone != null && currTimeZone.getTimeZone() != null) return currTimeZone.getTimeZone().getDisplayName();
		return null;
	}

	private void addStartAndEnd(StringBuffer sb, CharSequence end, boolean atStart, boolean atEnd) {
		// PERF: bla object creation, StringBuffer.equals doesn't work
		boolean endSameAsSb = sb.toString().equals(end.toString());
		
		if(atStart && !atEnd && !endSameAsSb)
		{
			sb.append(" (start) to ").append(end);
		}
		else {
			if(!endSameAsSb)
				sb.append(" to ").append(end);
			if(minTimeSec == maxTimeSec)
				sb.append(" (no data)");
			else if (atEnd && atStart) {
				sb.append(" (all data)");
			} else if (atEnd) {
				sb.append(" (end)");
			} else if (atStart) //in this case endSameAsSb is true (because we check above), so
				//we just add start to the end
			{
				sb.append(" (start)");
			}
		}
	}

	/**
	 * 
	 * @param out
	 * @param thenCalendar
	 * @param nowMs
	 * @param nowMidnightMs
	 *            today at midnight. We use this because if it's 5 AM, 11:59 PM
	 *            last night is yesterday
	 * @param atStart
	 *            true if at the end of recorded data
	 * @param atEnd
	 *            true if at the start of recorded data
	 * @return
	 */
	private static StringBuffer chooseFromNowDescription(StringBuffer out,
			Calendar thenCalendar, long nowMs, long nowMidnightMs) {
		if (out == null)
			out = new StringBuffer();

		int months = Util.calcDiff(thenCalendar, nowMidnightMs, Calendar.MONTH);

		if (months >= 3) {
			out.append( MONTHS[thenCalendar.get(Calendar.MONTH)
					- thenCalendar.getActualMinimum(Calendar.MONTH)]).append(" ").
					append(thenCalendar.get(Calendar.DATE)).append(", ").append(thenCalendar.get(Calendar.YEAR));
			return out;
		}

		if (months >= 2) {

			// TODO 3 internationalize
			out.append(months).append(" months ago");
			return out;
		}

		int days = Util.calcDiff(thenCalendar, nowMidnightMs, Calendar.DATE) + 1;
		int weeks = days / 7;

		if (weeks >= 2) {
			out.append(weeks).append(" weeks ago");
			return out;
		}

		if (days >= 2) {
			out.append(days).append(" days ago");
			return out;
		}

		int hours = Util.calcDiff(thenCalendar, nowMs, Calendar.HOUR_OF_DAY);

		int minutes = 0;

		if (hours >= 2) {
			out.append(hours).append(" hours ago");
			return out;
		} else if (hours == 1) {
			out.append("1 hour, ");
			minutes = -60;
		}

		minutes += Util.calcDiff(thenCalendar, nowMs, Calendar.MINUTE);

		if (minutes > 1) {
			out.append(minutes).append(" minutes ago");
			return out;
		} else if (minutes == 1) {
			out.append("1 minutes ago");
			return out;
		}

		if (hours == 1) {
			out.append("0 minutes ago");
		} else
			out.append("now");
		return out;
	}

	private static String getIth(int value) {
		String ith;
		int valueMod10 = value % 10;

		if (valueMod10 == 1)
			ith = "st";
		else if (valueMod10 == 2)
			ith = "nd";
		else if (valueMod10 == 3)
			ith = "rd";
		else
			ith = "th";

		return value + ith;
	}

	private Canvas canvas;

	private class DrawerLabelProcessor {
		private int sdIndex;

		public void init(int sdIndex) {
			this.sdIndex = sdIndex;
		}

		public void process(int timeSec, String[] dynamicLabels) {
			// draw labels between lines
			int screenX = convertTimeSecToX(timeSec, yPos, stripData[sdIndex],
					stripData[sdIndex]);
			int screenY = selectedAreaDim.top;
			chooseColor(textPaint, timeSec, timeSec);

			for (int i = 1; i >= 0; i--) {
				canvas.drawText(dynamicLabels[i], screenX, screenY, textPaint);
				screenY -= textPaint.getFontMetricsInt(textPaintFontMetrics);
			}
		}

		public void processStaticLabels(int startTimeSec, int endTimeSec,
				StaticLabels staticLabels) {

			// draw labels between lines
			int screenX = selectedAreaDim.left;
			int screenY = selectedAreaDim.top;
			textPaint.setColor(Color.WHITE);

			int secondColumnX = -1;

			for (int i = 2; i >= 0; i--) {
				screenY -= textPaint.getFontMetricsInt(textPaintFontMetrics);

				String text = staticLabels.out1[i].toString();
				canvas.drawText(text, screenX, screenY, textPaint);
				if (staticLabels.out2[i].length() != 0) {
					// the string manipulation bit is because getTextWidth will
					// ignore spaces at the end
					// PERF: hack creates a lot of objects, oh well
					secondColumnX = Math
							.max(getTextWidth(
									textPaint,
									text.substring(0, text.length() - 2) + " -",
									0, text.length())
									+ screenX, secondColumnX);
				}
			}

			screenY = selectedAreaDim.top;
			for (int i = 2; i >= 0; i--) {
				screenY -= textPaint.getFontMetricsInt(textPaintFontMetrics);
				if (staticLabels.out2[i].length() != 0)
					canvas.drawText(staticLabels.out2[i].toString(),
							secondColumnX, screenY, textPaint);
			}
		}
	}

	private class DrawerLineProcessor {
		StripData sd1;
		StripData sd2;

		public void process(int timeSec) {
			chooseColor(textPaint, timeSec, timeSec);
			drawLine(timeSec, textPaint);
		}

		public void drawLine(int timeSec, Paint paint) {
			int screenX = convertTimeSecToXForLabel(timeSec, sd1);
//			int screenX = convertTimeSecToX(timeSec, yPos, sd1, sd2);
			int screenY = convertYToScreenY(sd1.yPos);

			int endScreenX = convertTimeSecToXForLabel(timeSec, sd2);
			int endScreenY = convertYToScreenY(sd2.yPos);

			int lastScreenX;
			int lastScreenY;

			for (double y = sd1.yPos + YPOS_LINE_STEP; y < sd2.yPos; y += YPOS_LINE_STEP) {
				lastScreenX = screenX;
				lastScreenY = screenY;

				screenX = convertTimeSecToX(timeSec, y, sd1, sd2);
//				screenX = convertTimeSecToX(timeSec, yPos, sd1, sd2);
				screenY = convertYToScreenY(y);

				canvas.drawLine(lastScreenX, lastScreenY, screenX, screenY,
						paint);

			}

			canvas.drawLine(screenX, screenY, endScreenX, endScreenY, paint);
		}
	}



	private int getTextWidth(TextPaint textPaint, String text, int start,
			int end) {
		textPaint.getTextBounds(text, start, end, bounds);

		return bounds.width();
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		GTG.ccRwtm.registerReadingThread();
		try {
			// selectedTimeStart =
			// roundTo(convertXToTimeSec(selectedAreaDim.left, yPos),
			// stripData[yPosSdIndex+1]);
			// selectedTimeEnd =
			// roundTo(convertXToTimeSec(selectedAreaDim.right, yPos),
			// stripData[yPosSdIndex+1]);

			// double yStart = yPos - Y_TO_SCREEN/2;
			// double yEnd = yPos + Y_TO_SCREEN/2;

			this.canvas = canvas;

			drawSelectedArea(gtum.gpsTrailerOverlay.drawer.earliestOnScreenPointSec,
					gtum.gpsTrailerOverlay.drawer.latestOnScreenPointSec);

//			// if there is no data
//			if (minTimeSec == maxTimeSec) {
//				// report it
//
//				// co: we just want to draw nothing, because it may be that the
//				// cache is just destroyed,
//				// but there are gps points (like when we do a restore)
//				// zODO 3 internationalize
//				// String text = "No Data";
//				//
//				// canvas.drawText(text, (getWidth() - getTextWidth(textPaint,
//				// text, 0 , text.length()))/2, getHeight()/2, textPaint);
//				return;
//			}

			int screenMinTs = convertXToTimeSec(0, yPos,
					stripData[yPosSdIndex], stripData[yPosSdIndex + 1]);
			int screenMaxTs = convertXToTimeSec(getWidth(), yPos,
					stripData[yPosSdIndex], stripData[yPosSdIndex + 1]);

			drawerLabelProcessor.init(yPosSdIndex);

			long nowMs = System.currentTimeMillis();

			// TODO 4: doesn't work for DST days, oh well
			// if we've crossed the day line, we need to update midnight to the
			// next day
			if (nowMs - nowMidnightMs > Util.MS_PER_DAY)
				nowMidnightMs += Util.MS_PER_DAY;

			// draw the strip lines
			for (int i = 0; i < stripData.length; i++) {
				if (i != 0) {
					drawerLineProcessor.sd1 = stripData[i - 1];
					drawerLineProcessor.sd2 = stripData[i];

					int startTs = convertXToTimeSecForLabel(0, stripData[i - 1]);
					int endTs = convertXToTimeSecForLabel(getWidth(),
							stripData[i - 1]);

					// this draws the individual marker lines
					stripData[i - 1].strip.processLines(drawerLineProcessor,
					// we use the time from the previous level so we get lines
					// that are visible at
					// the start but cut off at the end
							startTs, endTs);

					// draw min/max lines
					drawerLineProcessor.drawLine(minTimeSec,
							minMaxPaint);
					drawerLineProcessor.drawLine(maxTimeSec,
							minMaxPaint);
				}

			}

			// draw the strip labels
			stripData[yPosSdIndex].strip.processLabels(drawerLabelProcessor,
					screenMinTs, screenMaxTs, selectedTimeStart,
					selectedTimeEnd, System.currentTimeMillis(), nowMidnightMs);

			this.canvas = null;

		} finally {
			GTG.ccRwtm.unregisterReadingThread();
		}
	}

	private void drawSelectedArea(int earliestOnScreenPointSec, 
			int latestOnScreenPointSec) {
		canvas.drawLine(selectedAreaDim.left, selectedAreaDim.top,
				selectedAreaDim.left, selectedAreaDim.bottom,
				ovalDrawer.selectedRegionPaint);
		canvas.drawLine(selectedAreaDim.right, selectedAreaDim.top,
				selectedAreaDim.right, selectedAreaDim.bottom,
				ovalDrawer.selectedRegionPaint);
		tempRect.left = ovalDrawer.selectedRegionPaint.getStrokeWidth() / 2
				+ selectedAreaDim.left;
		tempRect.top = 0;
		tempRect.right = selectedAreaDim.right
				- ovalDrawer.selectedRegionPaint.getStrokeWidth() / 2;
		tempRect.bottom = getHeight();

		canvas.clipRect(tempRect);

		int onScreenPointStartX = convertTimeSecToX(earliestOnScreenPointSec,
				yPos, stripData[yPosSdIndex], stripData[yPosSdIndex + 1]);
		int onScreenPointEndX = convertTimeSecToX(latestOnScreenPointSec, yPos,
				stripData[yPosSdIndex], stripData[yPosSdIndex + 1]);

		int selectedAreaCenterY = (selectedAreaDim.top + selectedAreaDim.bottom) / 2;

		// draw line to the left side of selector
		if (onScreenPointStartX - ovalDrawer.DISPLAYED_POINTS_BAR_RADIUS_PX > selectedAreaDim.left) {
			canvas.drawLine(selectedAreaDim.left, selectedAreaCenterY,
					onScreenPointStartX
							- ovalDrawer.DISPLAYED_POINTS_BAR_RADIUS_PX,
					selectedAreaCenterY, ovalDrawer.selectedRegionPaint);
		}

		// draw line to the right side of selector
		if (onScreenPointEndX + ovalDrawer.DISPLAYED_POINTS_BAR_RADIUS_PX < selectedAreaDim.right) {
			canvas.drawLine(onScreenPointEndX
					+ ovalDrawer.DISPLAYED_POINTS_BAR_RADIUS_PX,
					selectedAreaCenterY, selectedAreaDim.right,
					selectedAreaCenterY, ovalDrawer.selectedRegionPaint);
		}

		ovalDrawer.drawOval(canvas, selectedAreaDim, onScreenPointStartX, 
				onScreenPointEndX);

		tempRect.left = 0;
		tempRect.top = 0;
		tempRect.right = getWidth();
		tempRect.bottom = getHeight();

		canvas.clipRect(tempRect, Op.REPLACE);
	}

	private int getStripDataIndex(double currYPos) {
		int i;
		for (i = 0; i < stripData.length - 2; i++) {
			if (stripData[i + 1].yPos >= currYPos)
				break;
		}

		return i;
	}

	private void chooseColor(Paint paint, int timeSec, int timeSec2) {
		paint.setColor(timeSec2 >= selectedTimeStart
				&& timeSec < selectedTimeEnd
				&& timeSec2 >= minTimeSec
				&& timeSec < maxTimeSec ? Color.WHITE
				: Color.DKGRAY);
	}

	private int convertYToScreenY(double yPos) {
		return (int) ((yPos - this.yPos) / Y_TO_SCREEN * getHeight() + (selectedAreaDim.top + selectedAreaDim.bottom) / 2);
	}

	private double convertScreenYToY(float screenY) {
		return (screenY - (selectedAreaDim.top + selectedAreaDim.bottom) / 2)
				* Y_TO_SCREEN / getHeight() + this.yPos;
	}

	private int convertTimeSecToX(int timeSec, double yPos, StripData sd1,
			StripData sd2) {
		return (int) (Math.exp((yPos - sd1.yPos) * sd1.w)
				* (timeSec - centerTimeSec) * sd1.xToTimeSec + getWidth() / 2);
	}

	private int convertTimeSecToXForLabel(int timeSec, StripData sd) {
		return (int) (sd.xToTimeSec * (timeSec - centerTimeSec) + getWidth() / 2);
	}

	private int convertXToTimeSec(float x, double yPos, StripData sd1,
			StripData sd2) {
		return (int) ((x - getWidth() / 2)
				/ (sd1.xToTimeSec * Math.exp((yPos - sd1.yPos) * sd1.w)) + centerTimeSec);
	}

	private int convertXToTimeSecForLabel(int x, StripData sd) {
		return (int) ((x - getWidth() / 2) / sd.xToTimeSec + centerTimeSec);
	}

	private int convertXToTimeSec(int x, double yPos) {
		int i;
		for (i = 0; i < stripData.length - 1; i++) {
			if (stripData[i + 1].yPos >= yPos)
				break;
		}

		StripData sd1, sd2;

		if (i == stripData.length - 1) {
			sd1 = sd2 = stripData[i];
		} else {
			sd1 = stripData[i];
			sd2 = stripData[i + 1];
		}

		return convertXToTimeSec(x, yPos, sd1, sd2);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		GTG.ccRwtm.registerReadingThread();
		try {
			long time = System.currentTimeMillis();
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				lastY = event.getY();
				downX = lastX = event.getX();
				lastDownTime = time;
				return true;
			} else if (event.getAction() == MotionEvent.ACTION_MOVE) {
				double currYPos = Math.min(convertScreenYToY(event.getY()),
						stripData[stripData.length - 1].yPos);

				if (Math.abs(lastY - event.getY()) > Math.abs(lastX
						- event.getX()))
					//if we moved vertically
				{
					double lastYPos = Math.min(convertScreenYToY(lastY),
							stripData[stripData.length - 1].yPos);
					
					int xTimeSec = convertXToTimeSec((int) downX, yPos,
							stripData[yPosSdIndex], stripData[yPosSdIndex + 1]);
					
					yPos -= (currYPos - lastYPos) * MOVE_Y_SCALE;

					if (yPos > stripData[stripData.length - 1].yPos)
						yPos = stripData[stripData.length - 1].yPos;

					if (yPos < 0)
						yPos = 0;
					
					yPosSdIndex = getStripDataIndex(yPos);
					
					//move sideways so we don't scroll wherever the user finger is draped over
					int newXTimeSec = convertXToTimeSec(downX, yPos,
							stripData[yPosSdIndex], stripData[yPosSdIndex + 1]);
					
					centerTimeSec += xTimeSec - newXTimeSec; 
					
				} else {
					int lastTime = convertXToTimeSec((int) lastX, yPos,
							stripData[yPosSdIndex], stripData[yPosSdIndex + 1]);
					int currTime = convertXToTimeSec((int) event.getX(), yPos,
							stripData[yPosSdIndex], stripData[yPosSdIndex + 1]);

					this.centerTimeSec -= (int) ((currTime - lastTime) * MOVE_X_SCALE);

//					if (centerTimeSec < minTimeSec)
//						centerTimeSec = minTimeSec;
//					if (centerTimeSec > maxTimeSec)
//						centerTimeSec = maxTimeSec;
				}

				int startTime = convertXToTimeSec(selectedAreaDim.left,
						yPos);
				int endTime = convertXToTimeSec(selectedAreaDim.right, yPos);

				if (endTime < minTimeSec)
					centerTimeSec += minTimeSec - endTime;
				
				if (startTime > maxTimeSec)
					centerTimeSec -= startTime - maxTimeSec;

				lastX = event.getX();
				lastY = event.getY();

				invalidate();

				selectedTimeStart = convertXToTimeSec(selectedAreaDim.left,
						yPos);
				selectedTimeEnd = convertXToTimeSec(selectedAreaDim.right, yPos);
				
				// selectedTimeStart =
				// roundTo(convertXToTimeSec(selectedAreaDim.left, yPos),
				// stripData[yPosSdIndex+1]);
				// selectedTimeEnd =
				// roundTo(convertXToTimeSec(selectedAreaDim.right, yPos),
				// stripData[yPosSdIndex+1]);

				// Log.d(GTG.TAG,
				// String.format("move currYPos: %10.5f eX: %10.5f eY: %10.5f",
				// currYPos, event.getX(), event.getY()));
				if (listener != null) {
					listener.notifyTimeViewChange();
				}

				return true;
			}
			// else if(event.getAction() == MotionEvent.ACTION_UP)
			// {
			// Log.d("GPS","action up");
			// if(momentumPixelsPerMs != 0)
			// {
			// lastMomentumUpdatedTimeMs = System.currentTimeMillis();
			// timer.start(prefs.dialAnimationDelayMs);
			// }
			//
			//
			// return true;
			// }
			return false;
		} finally {
			GTG.ccRwtm.unregisterReadingThread();
		}
	}

	private int getRoundedStripDataIndex(double currYPos) {
		int i;
		for (i = 0; i < stripData.length - 2; i++) {
			if ((stripData[i + 1].yPos + stripData[i].yPos) / 2 >= currYPos)
				break;
		}

		return i;
	}

	private int roundTo(int timeSec, StripData stripData) {
		return stripData.strip.roundTo(timeSec);
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	public void setMinMaxTime(int minTimeSec,
			int maxTimeSec)
	{
		this.minTimeSec = minTimeSec;
		this.maxTimeSec = maxTimeSec;
		
		// redraw
		invalidate();
	}

	public void setSelectedStartAndEndTime(int startTimeSec, int endTimeSec) 
	{
		centerTimeSec = startTimeSec / 2 + endTimeSec / 2;

		// find the correct ypos

		int i;

		// first find the strip data that contains it
		for (i = 0; i < stripData.length - 2; i++) {
			if (convertXToTimeSecForLabel(
					selectedAreaDim.left - getWidth() / 2, stripData[i + 1]) > startTimeSec)
				break;
		}

		yPos = Math.log((getWidth() / 2 - selectedAreaDim.left)
				/ ((endTimeSec - centerTimeSec) * stripData[i].xToTimeSec))
				/ stripData[i].w + stripData[i].yPos;
		yPosSdIndex = getStripDataIndex(yPos);
		
		if(Double.isInfinite(yPos) || Double.isNaN(yPos))
			throw new IllegalStateException("yPos is bad: "+yPos);
		
		selectedTimeStart = startTimeSec;
		selectedTimeEnd = endTimeSec;
		
		// redraw
		invalidate();
	}

	public int getMinSelectableTimeSec() {
		return convertXToTimeSecForLabel(selectedAreaDim.right,
				stripData[stripData.length - 1])
				- convertXToTimeSecForLabel(selectedAreaDim.left,
						stripData[stripData.length - 1]);
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		int minHeight = (int) Util.convertDpToPixel(SELECTED_AREA_HEIGHT_DP
				+ SELECTED_AREA_Y_POS_FROM_BOTTOM_DP, getContext())
				+ textPaint.getFontMetricsInt(textPaintFontMetrics)
				* MAX_LABEL_LINES;

		setMeasuredDimension(MeasureSpec.getSize(widthSpec),
				Util.chooseAtLeastForOnMeasure(minHeight, heightSpec));
	}

	private void setupSelectedAreaDim() {
		if (selectedAreaDim == null) {
			int width = (int) (getWidth() * SELECTED_AREA_WIDTH_PERC);
			int height = (int) Util.convertDpToPixel(SELECTED_AREA_HEIGHT_DP,
					getContext());
			int centerY = (int) (getHeight() - Util.convertDpToPixel(
					SELECTED_AREA_HEIGHT_DP / 2
							+ SELECTED_AREA_Y_POS_FROM_BOTTOM_DP, getContext()));
			selectedAreaDim = new Rect((getWidth() - width) / 2, centerY
					- height / 2, (getWidth() + width) / 2, centerY + height
					/ 2);

			if (getWidth() == 0)
				throw new IllegalStateException(
						"I hate you, getWidth()!!!!!!!!!!!!!");

		}
	}

	/**
	 * Must be called from ui thread
	 */
	public void updateTimeZone(TimeZoneTimeRow newTimeZone) {
		//if its the current time zone, don't display anything
		if(newTimeZone != null && newTimeZone.isLocalTimeZone())
			newTimeZone = null;
		
		if(currTimeZone != newTimeZone)
		{
			currTimeZone = newTimeZone;
			if(currTimeZone == null || currTimeZone.getTimeZone() == null)
				calendar.setTimeZone(localTimeZone );
			else
				calendar.setTimeZone(currTimeZone.getTimeZone());
			invalidate();
		}
	}

}
