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

import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

//import com.rareventure.gps2.R;

public class PasswordDialogFragment extends DialogFragment {
	
	
	private String message;
	public String title;


	public static class Builder {

		private PasswordDialogFragment pdf;
		private FragmentActivity activity;

		public Builder(FragmentActivity activity) {
			this.activity = activity;
			
			pdf = new PasswordDialogFragment();
			
		}

	    public Builder setOnOk(final OnOkListener onOkListener)
	    {
	    	pdf.onOkListener = onOkListener;
	    	
	    	return this;
	    }

	    public Builder setMessage(String message)
	    {
	    	pdf.message = message;
	    	
	    	return this;
	    }

		public void show() {
		    // Create and show the dialog.
		    pdf.show(activity.getSupportFragmentManager().beginTransaction(), "dialog");		
		}

		public Builder setTitle(String title) {
			pdf.title = title;
			
			return this;
		}
	    
	}

	private OnEditorActionListener onEditorActionListener = new OnEditorActionListener() {
		
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			okButton.performClick();
			return false;
		}
	};
	private View v;

	private Button cancelButton;

	private TextView passwordEditText;

	private TextView messageText;
	private Button okButton;
	private OnOkListener onOkListener;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.dialog_enter_password, container, false);

        okButton = ((Button)v.findViewById(R.id.ok_button));
        okButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				onOkListener.onOk(passwordEditText.getText());
				dismiss();
			}
		});
        
        cancelButton = ((Button)v.findViewById(R.id.cancel_button));
        cancelButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
        
        messageText = ((TextView)v.findViewById(R.id.message));
        messageText.setText(message);
        
        passwordEditText = ((EditText)v.findViewById(R.id.enter_password));

        passwordEditText.setOnEditorActionListener(onEditorActionListener);
        
        getDialog().setTitle(title);
        
        return v;
	}
    
    public void setPassword(String password)
    {
    	passwordEditText.setText(password);
    }
    
    public static interface OnOkListener
    {

		void onOk(CharSequence text);
    }
    
    
}
