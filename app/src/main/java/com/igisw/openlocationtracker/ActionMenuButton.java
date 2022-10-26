/** 
    Copyright 2022 Igor Calì <igor.cali0@gmail.com>

    This file is part of Open Travel Tracker.

    Open Travel Tracker is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Open Travel Tracker is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Open Travel Tracker.  If not, see <http://www.gnu.org/licenses/>.

*/
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.igisw.openlocationtracker;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.util.AttributeSet;

/**
 * TextView that draws a bubble behind the text. We cannot use a
 * LineBackgroundSpan because we want to make the bubble taller than the text
 * and TextView's clip is too aggressive.
 */
public class ActionMenuButton extends androidx.appcompat.widget.AppCompatTextView {
    private static final int CORNER_RADIUS = 8;
    private static final int PADDING_H = 5;
    private static final int PADDING_V = 1;

    private final RectF mRect = new RectF();
    private Paint mPaint;

    public ActionMenuButton(Context context) {
        super(context);
        init();
    }

    public ActionMenuButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ActionMenuButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setFocusable(true);
        // We need extra padding below to prevent the bubble being cut.
        setPadding(PADDING_H, 0, PADDING_H, PADDING_V);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(getContext().getResources()
                .getColor(R.color.bubble_dark_background));
    }

    @Override
    protected void drawableStateChanged() {
        invalidate();
        super.drawableStateChanged();
    }

    @Override
    public void draw(Canvas canvas) {
        final Layout layout = getLayout();
        final RectF rect = mRect;
        final int left = getCompoundPaddingLeft();
        final int top = getExtendedPaddingTop();

        rect.set(left + layout.getLineLeft(0) - PADDING_H,
                 top + layout.getLineTop(0) - PADDING_V,
                 Math.min(left + layout.getLineRight(0) + PADDING_H,
                          getScrollX() + getRight() - getLeft()),
                 top + layout.getLineBottom(0) + PADDING_V);
        canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, mPaint);

        super.draw(canvas);
    }
}
