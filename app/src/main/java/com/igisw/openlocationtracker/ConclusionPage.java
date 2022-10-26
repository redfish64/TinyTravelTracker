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
package com.igisw.openlocationtracker;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import com.igisw.openlocationtracker.Util;
import com.igisw.openlocationtracker.GTG;
import com.igisw.openlocationtracker.GTGActivity;

public class ConclusionPage extends GTGActivity
{
	public ConclusionPage()
	{
	}

    private int imageNumber = 1; //int to check which image is displayed
    
    private ImageGetter imgGetter = new ImageGetter() {

        public Drawable getDrawable(String source) {
                Drawable drawable = null;
                if(imageNumber == 1) {
                drawable = getResources().getDrawable(R.drawable.settings_jellybean);
                ++imageNumber;
                } else drawable = getResources().getDrawable(R.drawable.settings_gingerbread);

                drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable
                                        .getIntrinsicHeight());

                return drawable;
        }
     };

     @Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		setContentView(R.layout.wizard_conclusion);
		
		String html = getString(R.string.conclusion_help_menu_tip);
		
		((TextView) findViewById(R.id.help_menu_tip)).setText(Html.fromHtml(html, imgGetter, null));
		
	}

	@Override
	public void doOnResume()
	{
		super.doOnResume();
		//prevents the initial setup screens from showing when the system is already set up
		if(GTG.prefs.initialSetupCompleted)
			finish();
	}

	public void onPrev(View target) {
		finish(); //to go back to the home screen or whatever
	}

	public void onNext(View target) {
		//do some pre defaulting
		GTG.prefs.useMetric = Util.localeIsMetric();
		
		GTG.prefs.initialSetupCompleted = true;
		GTG.prefs.compassData = GTG.COMPASS_DATA_XOR ^ ((int)(System.currentTimeMillis()/1000l));
		
		GTG.savePreferences(this);
		GTG.notifyCollectDataServiceOfUpdate(this);
		
		finish();
	}
	
	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_WIZARD;
	}

}
