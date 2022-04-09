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

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.igisw.openlocationtracker.GTG;
import com.igisw.openlocationtracker.GTGActivity;
import com.rareventure.gps2.database.TAssert;

public class EnterPasswordActivity extends GTGActivity {

	public static final String EXTRA_DECRYPT_OR_VERIFY_PASSWORD_BOOL = EnterPasswordActivity.class
			.getName() + ".EXTRA_DECRYPT_OR_VERIFY_PASSWORD_BOOL";

	private OnEditorActionListener onEditorActionListener = new OnEditorActionListener() {

		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			((Button) findViewById(R.id.ok)).performClick();
			return false;
		}
	};

	private EditText enterPasswordField;

	private View passwordIncorrectText;

	public EnterPasswordActivity() {
	}

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);

		setContentView(R.layout.enter_password);
		
		passwordIncorrectText = findViewById(R.id.password_incorrect_text);

		enterPasswordField = ((EditText) findViewById(R.id.enter_password_text));
		
		
		enterPasswordField.setOnEditorActionListener(onEditorActionListener);
		
		enterPasswordField.setOnKeyListener(new OnKeyListener() {
			
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				passwordIncorrectText.setVisibility(View.INVISIBLE);
				return false;
			}
		});

	}

	@Override
	public void doOnPause(boolean doOnResumeCalled) {
		super.doOnPause(doOnResumeCalled);
		finish();
	}

	public void onOk(View target) {
		final String password = ((TextView) findViewById(R.id.enter_password_text))
				.getText().toString();
		enterPasswordField.getEditableText()
				.clear();

		GTG.initRwtm.registerWritingThread();
		try {
			if(getIntent().getBooleanExtra(EXTRA_DECRYPT_OR_VERIFY_PASSWORD_BOOL, false))
			{
				int status = GTG.requireEncryptAndDecrypt(password);
				
				if(status == GTG.REQUIRE_DECRYPT_BAD_PASSWORD)
				{
					passwordIncorrectText.setVisibility(
							View.VISIBLE);
	
					enterPasswordField.requestFocus();
	
					//TODO 2.5 co: can't get this to work!
	//		        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
					
					return;
				}
				if(status != GTG.REQUIRE_DECRYPT_OK)
					TAssert.fail("what status is "+status);
				
				finish();
				
				return;
			}
			//else we just need to verify the password, decrypt is already done
			
			if (!GTG.requirePasswordEntered(password, GTG.lastGtgClosedMS)) {
				passwordIncorrectText.setVisibility(
						View.VISIBLE);
	
				enterPasswordField.requestFocus();
	
				//TODO 2.5 co: can't get this to work!
	//	        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
				return;
			}
	
			finish();
	
			return;
		}
		finally {
			GTG.initRwtm.unregisterWritingThread();
		}
	}

	public void onCancel(View target) {
		((TextView) findViewById(R.id.enter_password_text)).getEditableText()
				.clear();
		performCancel();
	}

	@Override
	public int getRequirements() {
		//expired ok so the user can create a backup when trial is expired
		return GTG.REQUIREMENTS_ENTER_PASSWORD;
	}
}
