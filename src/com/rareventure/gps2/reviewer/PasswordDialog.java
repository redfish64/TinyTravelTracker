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

import android.app.Dialog;
import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.rareventure.gps2.R;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GpsTrailerCrypt;

public class PasswordDialog extends Dialog implements OnClickListener {
	
	
	private OnEditorActionListener onEditorActionListener = new OnEditorActionListener() {
		
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			((Button)findViewById(R.id.ok_button)).performClick();
			return false;
		}
	};
	
    /* (non-Javadoc)
	 * @see android.app.Dialog#onStart()
	 */
	@Override
	protected void onStart() {
        if(GpsTrailerCrypt.prefs.isNoPassword)
        {
			EditText enterOldPassword = (EditText) findViewById(R.id.enter_old_password_edit_text);
			TextView enterOldPasswordText = (TextView) findViewById(R.id.enter_old_password_desc);
			enterOldPassword.setVisibility(View.GONE);
			enterOldPasswordText.setVisibility(View.GONE);
        }
        
        if(!setNewPassword)
        {
        	setTitle(R.string.turn_off_password);
			EditText enterNewPassword = (EditText) findViewById(R.id.enter_new_password_edit_text);
			TextView enterNewPasswordText = (TextView) findViewById(R.id.enter_new_password_desc);
			EditText reenterNewPassword = (EditText) findViewById(R.id.reenter_new_password_edit_text);
			TextView reenterNewPasswordText = (TextView) findViewById(R.id.reenter_new_password_desc);
			
			enterNewPassword.setVisibility(View.GONE);
			enterNewPasswordText.setVisibility(View.GONE);
			reenterNewPassword.setVisibility(View.GONE);
			reenterNewPasswordText.setVisibility(View.GONE);

			EditText enterOldPassword = (EditText) findViewById(R.id.enter_old_password_edit_text);
			enterOldPassword.setOnEditorActionListener(onEditorActionListener);
        }
        else {
        	if(GpsTrailerCrypt.prefs.isNoPassword)
                setTitle(R.string.enter_new_password);
        	else
        		setTitle(R.string.change_password);

			EditText reenterNewPassword = (EditText) findViewById(R.id.reenter_new_password_edit_text);
			reenterNewPassword.setOnEditorActionListener(onEditorActionListener);
            
        }
        
        ((Button)findViewById(R.id.ok_button)).setOnClickListener(this);
        ((Button)findViewById(R.id.cancel_button)).setOnClickListener(this);
        
	}

	private boolean setNewPassword;

	public PasswordDialog(Context context, boolean setNewPassword) {
		super(context, true, null);
        setContentView(R.layout.dialog_change_password_entry);
        
        this.setNewPassword = setNewPassword;
        
	}
    
	@Override
	public void onClick(View view) {
		switch (view.getId())
		{
		case R.id.ok_button:
			EditText enterOldPassword = (EditText) findViewById(R.id.enter_old_password_edit_text);
			String oldPasswordText = GpsTrailerCrypt.prefs.isNoPassword ? null : enterOldPassword.getText().toString();

			if(!GpsTrailerCrypt.initializePrivateKey(oldPasswordText))
			{
		    	Toast.makeText(this.getContext(), getContext().getText(R.string.old_password_incorrect), Toast.LENGTH_LONG).show();
		    	enterOldPassword.setText("");
		    	enterOldPassword.requestFocus();
		    	return;
			}
			
			String newPasswordText;
			
			if(setNewPassword)
			{
				EditText enterNewPassword = (EditText)findViewById(R.id.enter_new_password_edit_text);
				EditText reenterNewPassword = (EditText)findViewById(R.id.reenter_new_password_edit_text);
				
				if(!enterNewPassword.getText().toString().equals(reenterNewPassword.getText().toString()))
				{
			    	Toast.makeText(this.getContext(), getContext().getText(R.string.new_passwords_dont_match), Toast.LENGTH_LONG).show();
					enterNewPassword.requestFocus();
					return;
				}
				if(enterNewPassword.getText().length() == 0)
				{
					Toast.makeText(getContext(), R.string.error_password_cant_be_empty, Toast.LENGTH_LONG).show();
					return;
				}
				
				newPasswordText = enterNewPassword.getText().toString();
			}
			else
				newPasswordText = null;
			
			GpsTrailerCrypt.resetPassword(getContext(), oldPasswordText, newPasswordText);

			clearFields();
			dismiss();
			break;
		case R.id.cancel_button:
			clearFields();
			dismiss();
			break;
		}
	}

	private void clearFields() {
		((EditText) findViewById(R.id.enter_old_password_edit_text)).getEditableText().clear();
		((EditText) findViewById(R.id.enter_new_password_edit_text)).getEditableText().clear();
		((EditText) findViewById(R.id.reenter_new_password_edit_text)).getEditableText().clear();
	}

}
