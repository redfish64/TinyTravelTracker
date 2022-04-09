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
/*
 * Taken from mytrack
 * 
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.igisw.openlocationtracker;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Imports GPX file to My Tracks.
 */
public class GpxReader extends DefaultHandler {

	private static final DateTimeFormatter XML_DATE_TIME_FORMAT = ISODateTimeFormat
			.dateTime();

	// GPX tag names and attributes
	private static final String TAG_ALTITUDE = "ele";
	private static final String TAG_DESCRIPTION = "desc";
	private static final String TAG_NAME = "name";
	private static final String TAG_TIME = "time";
	private static final String TAG_TRACK = "trk";
	private static final String TAG_TRACK_POINT = "trkpt";
	private static final String TAG_TRACK_SEGMENT = "trkseg";
	private static final String TAG_GPX = "gpx";

	private static final String ATT_LAT = "lat";
	private static final String ATT_LON = "lon";

	private static final String TAG_EXTENSIONS = "extensions";
	public static final String TAG_NEW_TIME_ZONE = "new_time_zone";

	public interface GpxReaderCallback {
		public void readGpx();

		public void readTrk();

		public void readTrkSeg();

		/**
		 * 
		 * @param lon
		 * @param lat
		 * @param elevation
		 * @param timeMs
		 * @param tz local timezone associated with gps point. If null, it means
		 *   that timezone is unknown
		 */
		public void readTrkPt(double lon, double lat, double elevation,
				long timeMs, TimeZone tz);
	}

	private GpxReaderCallback grc;
	private String content;
	private boolean isInTrackElement;
	private int trackChildDepth;
	private int numberOfTrackSegments;
	private Locator locator;
	private double latitudeValue;
	private double longitudeValue;
	private double elevation;

	private long timeMs;

	private TimeZone tz;

	public GpxReader(GpxReaderCallback grc) {
		this.grc = grc;
	}

	public void doIt(InputStream inputStream)
			throws ParserConfigurationException, SAXException, IOException {
		SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
		SAXParser saxParser = saxParserFactory.newSAXParser();

		saxParser.parse(inputStream, this);
	}

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		String newContent = new String(ch, start, length);
		if (content == null) {
			content = newContent;
		} else {
			/*
			 * In 99% of the cases, a single call to this method will be made
			 * for each sequence of characters we're interested in, so we'll
			 * rarely be concatenating strings, thus not justifying the use of a
			 * StringBuilder.
			 */
			content += newContent;
		}
	}

	@Override
	public void startElement(String uri, String localName, String name,
			Attributes attributes) throws SAXException {
		if (isInTrackElement) {
			trackChildDepth++;
			if (localName.equals(TAG_TRACK)) {
				throw new SAXException(
						createErrorMessage("Invalid GPX. Already inside a track."));
			} else if (localName.equals(TAG_TRACK_SEGMENT)) {
				onTrackSegmentElementStart();
			} else if (localName.equals(TAG_TRACK_POINT)) {
				onTrackPointElementStart(attributes);
			} else if (localName.equals(TAG_NEW_TIME_ZONE)) {
				onNewTimeZoneElementStart(attributes);
			}

		} else if (localName.equals(TAG_TRACK)) {
			isInTrackElement = true;
			trackChildDepth = 0;
			onTrackElementStart();
		} else if (localName.equals(TAG_GPX)) {
			grc.readGpx();
		}
	}

	@Override
	public void endElement(String uri, String localName, String name)
			throws SAXException {
		if (!isInTrackElement) {
			content = null;
			return;
		}

		if (localName.equals(TAG_TRACK)) {
			onTrackElementEnd();
			isInTrackElement = false;
			trackChildDepth = 0;
		} else if (localName.equals(TAG_NAME)) {
			// we are only interested in the first level name element
			if (trackChildDepth == 1) {
				onNameElementEnd();
			}
		} else if (localName.equals(TAG_DESCRIPTION)) {
			// we are only interested in the first level description element
			if (trackChildDepth == 1) {
				onDescriptionElementEnd();
			}
		} else if (localName.equals(TAG_TRACK_SEGMENT)) {
			onTrackSegmentElementEnd();
		} else if (localName.equals(TAG_TRACK_POINT)) {
			onTrackPointElementEnd();
		} else if (localName.equals(TAG_ALTITUDE)) {
			onAltitudeElementEnd();
		} else if (localName.equals(TAG_EXTENSIONS)) {
			onExtensionsElementEnd();
		} else if (localName.equals(TAG_NEW_TIME_ZONE)) {
			onNewTimeZoneElementEnd();
		} else if (localName.equals(TAG_TIME)) {
			onTimeElementEnd();
		}
		trackChildDepth--;

		// reset element content
		content = null;
	}

	private void onNewTimeZoneElementEnd() {

	}

	private void onExtensionsElementEnd() {
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}

	/**
	 * On track element start.
	 */
	private void onTrackElementStart() {
		this.grc.readTrk();

		numberOfTrackSegments = 0;
	}

	/**
	 * On track element end.
	 */
	private void onTrackElementEnd() {
	}

	/**
	 * On name element end.
	 */
	private void onNameElementEnd() {
		// if (content != null) {
		// track.setName(content.toString().trim());
		// }
	}

	/**
	 * On description element end.
	 */
	private void onDescriptionElementEnd() {
		// if (content != null) {
		// track.setDescription(content.toString().trim());
		// }
	}

	/**
	 * On track segment start.
	 */
	private void onTrackSegmentElementStart() {
		numberOfTrackSegments++;

		this.grc.readTrkSeg();
	}

	/**
	 * On track segment element end.
	 */
	private void onTrackSegmentElementEnd() {
	}

	/**
	 * On track point element start.
	 * 
	 * @param attributes
	 *            the attributes
	 */
	private void onTrackPointElementStart(Attributes attributes)
			throws SAXException {
		String latitude = attributes.getValue(ATT_LAT);
		String longitude = attributes.getValue(ATT_LON);

		if (latitude == null || longitude == null) {
			throw new SAXException(
					createErrorMessage("Point with no longitude or latitude."));
		}
		try {
			latitudeValue = Double.parseDouble(latitude);
			longitudeValue = Double.parseDouble(longitude);
		} catch (NumberFormatException e) {
			throw new SAXException(
					createErrorMessage("Unable to parse latitude/longitude: "
							+ latitude + "/" + longitude), e);
		}
	}

	private void onNewTimeZoneElementStart(Attributes attributes)
			throws SAXException {
		String tzId = attributes.getValue("id");

		if (tzId == null) {
			throw new SAXException(
					createErrorMessage("New Time zone extension with no id."));
		}

		//if we can't understand the timezone, we let it be null
		tz = Util.parseTimeZone(tzId); 
	}

	/**
	 * Checks if a given location is a valid (i.e. physically possible) location
	 * on Earth. Note: The special separator locations (which have latitude =
	 * 100) will not qualify as valid. Neither will locations with lat=0 and
	 * lng=0 as these are most likely "bad" measurements which often cause
	 * trouble.
	 * 
	 * @param location
	 *            the location to test
	 * @return true if the location is a valid location.
	 */
	public static boolean isValidLocation(double lon, double lat) {
		return Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
	}

	/**
	 * On track point element end.
	 */
	private void onTrackPointElementEnd() throws SAXException {
		grc.readTrkPt(longitudeValue, latitudeValue, elevation, timeMs, tz);
	}

	/**
	 * On altitude element end.
	 */
	private void onAltitudeElementEnd() throws SAXException {
		try {
			elevation = Double.parseDouble(content);
		} catch (NumberFormatException e) {
			throw new SAXException(
					createErrorMessage("Unable to parse altitude: " + content),
					e);
		}
	}

	/**
	 * On time element end. Sets location time and doing additional calculations
	 * as this is the last value required for the location. Also sets the start
	 * time for the trip statistics builder as there is no start time in the
	 * track root element.
	 */
	private void onTimeElementEnd() throws SAXException {
		// Parse the time
		try {
			timeMs = XML_DATE_TIME_FORMAT.parseMillis(content.trim());
		} catch (IllegalArgumentException e) {
			throw new SAXException(createErrorMessage("Unable to parse time: "
					+ content), e);
		}
	}

	/**
	 * Creates an error message.
	 * 
	 * @param message
	 *            the message
	 */
	public String createErrorMessage(String message) {
		return String.format(Locale.US,
				"Parsing error at line: %d column: %d. %s",
				locator.getLineNumber(), locator.getColumnNumber(), message);
	}
}
