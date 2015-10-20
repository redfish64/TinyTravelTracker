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
package com.rareventure.gps2.reviewer;

import com.rareventure.android.Util;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GTGActivity;
import com.rareventure.gps2.R;

import android.app.Activity;
import android.graphics.Picture;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebView.PictureListener;
import android.webkit.WebViewClient;

public class ShowManual extends GTGActivity {
	
	/**
	 * We want to keep these values around for the session, so that
	 * if the user scrolls to a position on the web page, they will
	 * automatically go back there
	 */
	private static int lastScrollX, lastScrollY;
	private WebView webView;

	public ShowManual() {
	}

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		setContentView(R.layout.manual_screen);
		
		webView = (WebView)findViewById(R.id.webview);
		webView.loadUrl("file:///android_asset/manual/manual.html");
	}

	@Override
	public void doOnResume() {
		//from http://stackoverflow.com/questions/9392031/webview-scrollto-is-not-working
		//co: makes hyperlinks not work
//		webView.setPictureListener(new PictureListener() {
//
//	        @Override
//	        public void onNewPicture(WebView view, Picture picture) {
//				webView.scrollTo(lastScrollX, lastScrollY);
//	        }
//
//	    });
				
	}
	
	public void doOnPause(boolean doOnResumeCalled) {
		if(!doOnResumeCalled)
			return;
		lastScrollX = webView.getScrollX();
		lastScrollY = webView.getScrollY();
	}
	
	
	public void onOk(View v)
	{
		finish();
	}

	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_BASIC_UI;
	}
	
}
