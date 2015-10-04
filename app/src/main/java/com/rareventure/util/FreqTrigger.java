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
package com.rareventure.util;

/**
 * Keeps track of the frequency of an event. When the recent
 * positives go beyond a certain threshold, will tell the caller
 */
public class FreqTrigger {
	private float recentPositivesPercent;
	private float fadeAwayPerc;
	private float maxPositivesPercent;
	
	public FreqTrigger(float recentPositivesPercent, float fadeAwayPerc,
			float maxPositivesPercent) {
		super();
		this.recentPositivesPercent = recentPositivesPercent;
		this.fadeAwayPerc = fadeAwayPerc;
		this.maxPositivesPercent = maxPositivesPercent;
	}
	
	public boolean event(boolean positive)
	{
		if(positive)
		{
			recentPositivesPercent = recentPositivesPercent * (1 - fadeAwayPerc) + fadeAwayPerc;
		}
		else
		{
			recentPositivesPercent = recentPositivesPercent * (1 - fadeAwayPerc);
		}
		
		return recentPositivesPercent > maxPositivesPercent;
	}

	public boolean isPastMaxPositive() {
		return recentPositivesPercent > maxPositivesPercent;
	}
}
