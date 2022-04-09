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
package com.rareventure.gps2;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;

import pl.tajchert.nammu.Nammu;

/**
 * WARNING: this  code is duplicated in GTGActivity, GTGFragmentActivity and GTGPreferencesActiity. Be sure to also
 * change in there what is in here
 */
public abstract class GTGFragmentActivity extends FragmentActivity implements IGTGActivity
{
	GTGActivityHelper helper = new GTGActivityHelper(this, this.getRequirements());

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		Nammu.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	protected final void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		helper.onCreate(bundle);
	}
	
	@Override
	protected final void onResume() {
		super.onResume();
		helper.onResume();
	}
	
	public void doOnResume()
	{
	}
	
	public void startInternalActivityForResult(Intent i, int s)
	{
		helper.startInternalActivityForResult(i, s);
	}

	public void doOnCreate(Bundle b)
	{
	}

	
	protected final void onPause()
	{
		super.onPause();
		helper.onPause();
	}
	
	public void doOnPause(boolean doOnResumeCalled)
	{
	}

	@Override
	public void onBackPressed() {
		helper.onBackPressed();
	}

	@Override
	public void  performCancel() {
		helper.performCancel();
	}

	public void finish()
	{
		helper.finish();
	}
	
	@Override
	public void superFinish()
	{
		super.finish();
	}
	

	/**
	 * Starts an internal activity and keeps password if already present
	 */
	public void startInternalActivity(Intent intent)
	{
		helper.startInternalActivity(intent);
	}
	
	/**
	 * Starts an internal activity and keeps password if already present
	 */
	public void startInternalActivity(Intent intent, boolean requireBackStack)
	{
		helper.startInternalActivity(intent, requireBackStack);
	}
	
	public void exitFromApp()
	{
		helper.exitFromApp();
	}
}
