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


Note to users: If you have an issue you would like to see fixed, please click on the smiley on the right,
and select thumbs up (or thumbs down). I'll use this information to decide what to work on next. Thanks


Tiny Travel Tracker could be described as a GPS journal. It's different from other GPS trackers because:

* It can be safely left on all the time. It keeps track of it's own battery usage to prevent excessive battery drain.
* It can handle hundreds of thousands of points-- years with of data, all at once. No need to worry about trying to categorize and save it yourself. It's all there and always available.
* The data is encrypted using 256 AES keys and can be password protected. (It is as secure as communicating with your banking website.) No other malicious apps can make use of the GPS data, and it is completely private. Your data is not uploaded to any website but remains on your phone at all times, and is easily wiped away by uninstalling the app.

It's meant for both traveling to far off places and reviewing your day to day activities.

It can find the answer to questions such as:

* Where did I really go when I got lost on top of that mountain?
* How many times have I been to the gym in the last month?
* Where are some different routes to work that I haven't tried yet, and how long to the routes I choose actually take?
* How many hours have I actually spent in that pool hall?
* What did I do all day on October 27th, 2010?
* Where was that little shop I visited when I was wandering around Tokyo?

It also is useful in situations where you don't have internet access. All map tiles previously viewed are cached on the phone, so even without internet, you can use the map as long as you've looked at previously viewed area. In addition, since the GPS points are stored on the phone, they are always present, regardless of internet availability.

Changelog:

V 1.1.11

* Selecting points and long pressing to select point areas works again

V 1.1.9

* Now uses an opengl vector map implementation by Mapzen

V 1.06

* Smooth zooming
* Pinch to zoom works much better
* Double tap to zoom
* Font size of map can be adjusted in settings

V 1.05

Now uses less battery

----

To install:

Go into home directory and type:

perl other/ttt_install.pl -real /opt/android-sdk-update-manager `pwd` "Tiny Travel Tracker - Free" "Tiny Travel Tracker" com.rareventure.gps2_trial com.rareventure.gps2_premium trial

You can see it here:

https://play.google.com/store/apps/details?id=com.rareventure.gps2_trial

----

You can contact me at engler@gmail.com

Donations:

Bitcoin: 19bit8J9VFY9DMGbcYNrH4PzkUPdS8Rx1f
Patreon: https://www.patreon.com/redfish64
