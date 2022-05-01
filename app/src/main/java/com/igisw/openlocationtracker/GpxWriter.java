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

import com.rareventure.util.OutputStreamToInputStreamPipe;
import com.rareventure.util.OutputStreamToInputStreamPipe.PipeClosedException;

import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Locale;

/**
 * Pass gpx data into this thing and it will make GPX xml available for reading:
 * 
 * Order of operation is
 * 
 * startDoc()
 * startTrack()
 * startSegment()
 * addPoint()
 * endSegment()
 * endTrack()
 * endDoc()
 */
public class GpxWriter
{
	private OutputStreamToInputStreamPipe buf;

	public GpxWriter(OutputStreamToInputStreamPipe buf) {
		this.buf = buf;

	}

	public void startDoc(String appName, String version)
	{
		write("<?xml version=\"1.0\"?>\n"+
				"<gpx\n"+
				" version=\"1.1\"\n"+
				" creator=\""+xmlSanitize(appName+" "+version)+" - http://www.rareventure.com\"\n"+
				" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"+
				" xmlns=\"http://www.topografix.com/GPX/1/1\"\n"+
				" xmlns:rareventure=\"http://www.rareventure.com/xmlschemas/GpxExtensions/v1\"\n"+
				" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n");
	}
	
	private synchronized void write(String string) {
		byte [] bytes = string.getBytes();
		try {
			buf.addBytes(bytes, 0, bytes.length);
		} catch (PipeClosedException e) {
			throw new IllegalStateException(e);
		}
	}

	public void startTrack(String name)
	{
		write("\t<trk>\n"+
"\t\t<name>"+xmlSanitize(name)+"</name>\n");
	}
	
	public void startSegment() 
	{
		write("\t\t<trkseg>\n");
	}
	
	private String xmlSanitize(String content) {
	    return content.replaceAll("[^\\u0009\\u000a\\u000d\\u0020-\\uD7FF\\uE000-\\uFFFD]", "_");
	}

	
//    private static final DateTimeFormatter XML_DATE_TIME_FORMAT =
//        ISODateTimeFormat.dateTimeNoMillis();

    private DateTimeFormatter XML_DATE_TIME_FORMAT =
        ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC);

	
	public void addPoint(double lat, double lon, double alt, long time, TimeZoneTimeRow tz)
	{
		String timeStr = XML_DATE_TIME_FORMAT.print(time);
		
		write(String.format(Locale.ENGLISH,"\t\t\t<trkpt lat=\"%f\" lon=\"%f\" >\n"+
				"\t\t\t\t<ele>%f</ele>\n"+
				"\t\t\t\t<time>%s</time>\n"+
				(tz == null ? "" :

					"\t\t\t\t<extensions><rareventure:"+GpxReader.TAG_NEW_TIME_ZONE+" id=\""+(tz.getTimeZone() == null ? "UNKNOWN" : tz.getTimeZone().getID())+"\" />" +
							"</extensions>\n")+
				"\t\t\t</trkpt>\n", lat, lon, alt, timeStr
				//in some cases we don't know the time zone (if we restored and the input data didn't have it)
				//and in this case we want to show the users current timezone, so we set it as unknown
				));
	}

	public void endSegment() {
		write("\t\t</trkseg>\n");
	}
	
	public void endTrack() {
		write("\t\t</trk>\n");
	}
	
	public void endDoc()
	{
		write("</gpx>\n");
		buf.finishWriting();
	}
}
