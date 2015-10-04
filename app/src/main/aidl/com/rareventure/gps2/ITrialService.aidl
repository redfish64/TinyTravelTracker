package com.rareventure.gps2;

/**
 * All these methods are meant to be called by the premium application to the trial
 * application.
 */
interface ITrialService {
	/**
	 * This should be called only when the trial app is not visible (ie. when the premium
	 * app is visible). It returns all the preferences of the trial app
	 */
	Map giveMePreferences();
	
	/**
	 * Notifies the trial app that it's preferences have been saved, so it can
	 * go ahead and clear out all of its preferences (so that if the premium app
	 * is uninstalled for some reason, the trial will revert back to welcome pages
	 */
	void notifyReplaced();
	
	/**
	 * Returns the external file dir of the trial app, so the premium app can load that 
	 * information.
	 */
	String getExtFileDir();

	/**
	 * Called by the premium service to force the trial process to shut itself down.
	 */ 
	void shutdown();


}
