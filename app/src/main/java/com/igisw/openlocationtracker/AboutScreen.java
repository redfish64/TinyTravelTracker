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
package com.igisw.openlocationtracker;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

import com.igisw.openlocationtracker.Crypt;
import com.igisw.openlocationtracker.Util;
import com.igisw.openlocationtracker.GTG;
import com.igisw.openlocationtracker.GTGActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

public class AboutScreen extends GTGActivity {

	public AboutScreen() {
	}
	
	private static final Pattern [] PATTERNS =
			{
		Pattern.compile("\\$\\{aes_desc\\}"),
		Pattern.compile("\\$\\{rsa_desc\\}"),
		Pattern.compile("\\$\\{pbkdf2_desc\\}"),
				Pattern.compile("\\$\\{version\\}"),
					Pattern.compile("\\$\\{build\\}")
			};
	
	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		setContentView(R.layout.about_screen);
		
		String [] replacements = 
			{
				Crypt.getEncryptDesc(),
				Crypt.getAsymmetricEncryptionDesc(),
				Crypt.getSecretKeyDesc(),
					BuildConfig.VERSION_NAME,
					String.valueOf(BuildConfig.VERSION_CODE)
			};
		
		try {
			
			AssetManager am = getAssets();
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					am.open("about.html")));
			
			String data = Util.readReaderIntoStringWithMatchReplace(reader, 
					PATTERNS, replacements);
			
			reader.close();
			
			((WebView)findViewById(R.id.webview)).loadDataWithBaseURL(
					"file:///android_asset/", data, null, "UTF-8",null);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public void onOk(View v)
	{
		finish();
	}

	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_FULL_PASSWORD_PROTECTED_UI;
	}
	
}
