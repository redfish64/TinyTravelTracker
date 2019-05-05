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
package com.rareventure.android;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video.VideoColumns;
import android.telephony.TelephonyManager;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.DatePicker;

import com.mapzen.tangram.LngLat;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.R;
import com.rareventure.gps2.database.TAssert;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

//import com.google.android.maps.MapView;

public class Util {
	
	public static final long MS_PER_YEAR = 1000l * 3600 * 24 * 365;
	public static final long MS_PER_DAY = 1000l * 3600 * 24;
	public static final long MS_PER_WEEK = MS_PER_DAY * 7;
	public static final long MS_PER_HOUR = 1000l * 3600;
	public static final int MIN_LONM = -180 * 1000000;
	public static final int MAX_LONM = 180 * 1000000 - 1;

	public static final int MIN_LON = -180;
	public static final int MAX_LON = 180;
	public static final int LON_PER_WORLD = 360;

	public static void determineMaxBounds(TextPaint tp, Rect bounds, String s) {
		Rect bounds2 = new Rect();
		
		tp.getTextBounds(s, 0, s.length(), bounds2);
		
		bounds.union(bounds2);
	}

	public static int getTextLength(TextPaint tp, String s) {
		Rect bounds = new Rect();
		
		tp.getTextBounds(s, 0, s.length(), bounds);
		
		return bounds.right - bounds.left;
	}
	
	public static int readInt(InputStream is) throws IOException {
		byte [] data = new byte[4];
		Util.readFully(is, data);
		
		return byteArrayToInt(data, 0);
	}

	/**
	 * Gets an id that is unique to the device
	 */
	public static String getDeviceId(Context context)
	{
	    final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

	    final String tmDevice, tmSerial, androidId;
	    tmDevice = "" + tm.getDeviceId();
	    tmSerial = "" + tm.getSimSerialNumber();
	    androidId = "" + android.provider.Settings.Secure.getString(context.getContentResolver(), 
	    		android.provider.Settings.Secure.ANDROID_ID);

	    UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
	    String deviceId = deviceUuid.toString();
	    
	    return deviceId;
	}

//	public static void zoomSmoothlyToSpan(MapView mapView, int latSpanMicroDegrees, int lonSpanMicroDegrees,
//			float widthPaddingPerc, float heightPaddingPerc) {
//		if(latSpanMicroDegrees > mapView.getLatitudeSpan() * (1-heightPaddingPerc) || 
//				lonSpanMicroDegrees > mapView.getLongitudeSpan() * (1-widthPaddingPerc))
//		{
//			do {
//				//zoomout. if we are at the farthest zoom level, exit
//				if(!mapView.getController().zoomOut())
//					return;
//			}
//			while(latSpanMicroDegrees > mapView.getLatitudeSpan() * (1-heightPaddingPerc)
//					|| lonSpanMicroDegrees > mapView.getLongitudeSpan() * (1-widthPaddingPerc));
//		}
//		else {
//			do {
//				//zoomin. if we are at the closest zoom level, exit
//				if(!mapView.getController().zoomIn())
//					return;
//			}
//			while(latSpanMicroDegrees < mapView.getLatitudeSpan() * (1-heightPaddingPerc)
//					&& lonSpanMicroDegrees < mapView.getLongitudeSpan() * (1-widthPaddingPerc));
//			
//			//I know it's weird to zoom in a bunch of times and then zoom out
//			//but if I invoke zoomToSpan, it will be jumpy, not smooth, and
//			//the final zoom out doesn't seem to hurt the animation any
//			//(and there doesn't seem to be a better way to do this)
//			mapView.getController().zoomOut();
//		}
//	}

	public static int getMaximumWidth(TextPaint textPaint, Object[] units) {
		int maxWidth = 0;
		for(Object text : units)
		{
			int width = Util.getTextLength(textPaint, text.toString());
			if(width > maxWidth)
				maxWidth = width;
		}
		
		return maxWidth;
	}

	public static int measureWithPreferredSize(int measureSpec, int preferredSizeWithPadding) {
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            return specSize;
        }

        if (specMode == MeasureSpec.AT_MOST) {
            return Math.min(preferredSizeWithPadding, specSize);
        }
        
        //specMode == MeasureSpec.UNSPECIFIED
        return preferredSizeWithPadding;
	}

	public static float length(float x,float y)
	//average error of about 0.005%
	//similar error as the 'rounding error' of 32bit floating point)
	//from http://www.osix.net/modules/article/?id=770
	{
		float absX = x < 0 ? -x : x;
		float absY = y < 0 ? -y : y;
		
	    float a,b;
	    if(absX>absY)
	    {
	        a=absX;
	        b=absY;
	    }
	    else
	    {
	        b=absX;
	        a=absY;
	    }
	    a=a+0.42f*b*b/a;
	    return (a+(x*x+y*y)/a)/2;
	}

	public static String varReplace(String data, String ... varName_varValue) {
		//PERF: a little slow
		for(int i = 0; i < varName_varValue.length; i+=2)
		{
			data = data.replaceAll("\\$\\{"+varName_varValue[i]+"\\}", varName_varValue[i+1]);
		}
		return data;
	}

	public static int intToByteArray2(int value, byte [] data, int start) {
        data[start++] = (byte)(value >>> 24);
        data[start++] = (byte)(value >>> 16);
        data[start++] = (byte)(value >>> 8);
        data[start++] = (byte)(value);
        
        return start;
	}
	
	public static void writeInt(OutputStream out, int value) throws IOException {
		out.write((byte)(value >>> 24));
		out.write((byte)(value >>> 16));
		out.write((byte)(value >>> 8));
		out.write((byte)value);
	}



	public static final int byteArrayToInt(byte [] b, int start) {
        return ((b[start++] )<< 24)
                + ((b[start++] & 0xFF) << 16)
                + ((b[start++] & 0xFF) << 8)
                + (b[start] & 0xFF);
	}
		
	/**
	 * data must have at least 8 bytes from start
	 */
	public static int longToByteArray2(long value, byte [] data, int start) {
        data[start++] = (byte)(value >>> 56);
        data[start++] = (byte)(value >>> 48);
        data[start++] = (byte)(value >>> 40);
        data[start++] = (byte)(value >>> 32);
        data[start++] = (byte)(value >>> 24);
        data[start++] = (byte)(value >>> 16);
        data[start++] = (byte)(value >>> 8);
        data[start++] = (byte)(value);
        
        return start;
	}
	
	public static final double byteArrayToDouble(byte [] b, int s)
	{
		return Double.longBitsToDouble(byteArrayToLong(b, s));
	}

	public static final long byteArrayToLong(byte [] b, int s) {
        return ((long)b[s++] << 56)
        + ( (((long)b[s++]) & 0xFF) << 48)
        + (( ((long)b[s++]) & 0xFF) << 40)
        + (( ((long)b[s++]) & 0xFF) << 32)
        + (( ((long)b[s++]) & 0xFF) << 24)
        + ((b[s++] & 0xFF) << 16)
        + ((b[s++] & 0xFF) << 8)
        + ((b[s++] & 0xFF));
	}

	public static void main(String []argv) throws Exception
	{
		byte [] salt = new  byte[5];
		SecretKeySpec skeySpec = new SecretKeySpec(Crypt.getRawKey("my secret","salt".getBytes()), "AES");
		Cipher encryptCipher = Cipher.getInstance("AES");
		encryptCipher.init(Cipher.ENCRYPT_MODE, skeySpec);
		
		Cipher decryptCipher = Cipher.getInstance("AES");
		decryptCipher.init(Cipher.DECRYPT_MODE, skeySpec);

		testEncryptDecrypt(encryptCipher, decryptCipher, "foo",5,Integer.MIN_VALUE);
		testEncryptDecrypt(encryptCipher, decryptCipher, "foo",Integer.MIN_VALUE,Integer.MIN_VALUE);
		testEncryptDecrypt(encryptCipher, decryptCipher, "foo",Integer.MAX_VALUE,Integer.MIN_VALUE);
		testEncryptDecrypt(encryptCipher, decryptCipher, "foo",-1,-1);
		testEncryptDecrypt(encryptCipher, decryptCipher, "foo",-1,Integer.MAX_VALUE);
		
		System.out.println("repeat!");
		testEncryptDecrypt(encryptCipher, decryptCipher, "foo",5,Integer.MIN_VALUE);
		testEncryptDecrypt(encryptCipher, decryptCipher, "foo",Integer.MIN_VALUE,Integer.MIN_VALUE);
		testEncryptDecrypt(encryptCipher, decryptCipher, "foo",Integer.MAX_VALUE,Integer.MIN_VALUE);
		testEncryptDecrypt(encryptCipher, decryptCipher, "foo",-1,-1);
		testEncryptDecrypt(encryptCipher, decryptCipher, "foo",-1,Integer.MAX_VALUE);

		System.out.println("longs");

		testEncryptDecryptLong(encryptCipher, decryptCipher, "foo",5,Long.MIN_VALUE);
		testEncryptDecryptLong(encryptCipher, decryptCipher, "foo",Long.MIN_VALUE,Long.MIN_VALUE);
		testEncryptDecryptLong(encryptCipher, decryptCipher, "foo",Long.MAX_VALUE,Long.MIN_VALUE);
		testEncryptDecryptLong(encryptCipher, decryptCipher, "foo",-1,-1);
		testEncryptDecryptLong(encryptCipher, decryptCipher, "foo",-1,Long.MAX_VALUE);
		
		System.out.println("repeat!");
		testEncryptDecryptLong(encryptCipher, decryptCipher, "foo",5,Long.MIN_VALUE);
		testEncryptDecryptLong(encryptCipher, decryptCipher, "foo",Long.MIN_VALUE,Long.MIN_VALUE);
		testEncryptDecryptLong(encryptCipher, decryptCipher, "foo",Long.MAX_VALUE,Long.MIN_VALUE);
		testEncryptDecryptLong(encryptCipher, decryptCipher, "foo",-1,-1);
		testEncryptDecryptLong(encryptCipher, decryptCipher, "foo",-1,Long.MAX_VALUE);
	}

	public static String toHex(String txt) {
		return toHex(txt.getBytes());
	}

	public static String fromHex(String hex) {
		return new String(toByte(hex));
	}

	public static byte[] toByte(String hexString) {
		int len = hexString.length() / 2;
		byte[] result = new byte[len];
		for (int i = 0; i < len; i++)
			result[i] = Integer.valueOf(hexString.substring(2 * i, 2 * i + 2), 16).byteValue();
		return result;
	}

	public static String toHex(byte[] data, int pos, int length) {
		if (data.length <= pos)
			return "";
		StringBuffer result = new StringBuffer(2 * data.length);
		for (int i = pos; i < pos+length && i < data.length; i++) {
			appendHex(result, data[i]);
		}
		return result.toString();
	}

	public static String toHex(byte[] buf) {
		return toHex(buf,0,buf.length);
	}

	private final static String HEX = "0123456789ABCDEF";
	public static final int LONM_PER_WORLD = 360 * 1000 * 1000;
 	public static final float LATM_TO_METERS = 1000000f / 111131.75f ;
 	public static final float LONM_TO_METERS_AT_EQUATOR = 1000000f / 111131.75f;
	public static final float LON_TO_METERS_AT_EQUATOR = 1f / 111131.75f;
	public static final int MAX_LATM = 180*1000*1000-1;
	public static final int MIN_LATM = -180*1000*1000;
	public static final int LATM_PER_WORLD = 180 * 1000 * 1000;
	
	public static final int SECONDS_IN_DAY = 3600 * 24;
	public static final int SECONDS_IN_YEAR = SECONDS_IN_DAY*365;
	public static final int SECONDS_IN_MONTH = SECONDS_IN_DAY*30;
	
	private static final int MILLIS_IN_MINUTE = 1000*60;
	private static final int MILLIS_IN_HOUR = MILLIS_IN_MINUTE * 60;
	private static final int MILLIS_IN_DAY = MILLIS_IN_HOUR * 24;
	private static final long MILLIS_IN_MONTH = 30l * MILLIS_IN_DAY;
	private static final long MILLIS_IN_YEAR = 365l * MILLIS_IN_DAY;
	private static final double EARTH_RADIUS_M = 6378100;

	

	private static void appendHex(StringBuffer sb, byte b) {
		sb.append(HEX.charAt((b >> 4) & 0x0f)).append(HEX.charAt(b & 0x0f));
	}
	
	private static void testEncryptDecrypt(Cipher c, Cipher d, String string, int key, int value) 
	throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		byte [] data = new byte[3+4+4];
		byte [] output = new byte[c.getOutputSize(data.length)];
		byte [] result = new byte[3+4+4];
		
		byte [] salt = string.getBytes();
		
		System.arraycopy(salt, 0, data, 0, salt.length);
		
		intToByteArray2(key, data, 3);
		intToByteArray2(value, data, 7);
		
		c.doFinal(data,0,data.length,output);
		d.doFinal(output,0,output.length,result);
		
		System.out.println("in: "+toHex(data));
		System.out.println("enc: "+toHex(output));
		System.out.println("dec: "+toHex(result));
		
		for(int i = 0; i < data.length; i++)
		{
			if(data[i] != result[i])
				throw new IllegalStateException("darn");
		}
		
	}

	private static void testEncryptDecryptLong(Cipher c, Cipher d, String string, long key, long value) 
	throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		byte [] data = new byte[3+8+8];
		byte [] output = new byte[c.getOutputSize(data.length)];
		byte [] result = new byte[3+8+8];
		
		byte [] salt = string.getBytes();
		
		System.arraycopy(salt, 0, data, 0, salt.length);
		
		longToByteArray2(key, data, 3);
		longToByteArray2(value, data, 11);
		
		c.doFinal(data,0,data.length,output);
		d.doFinal(output,0,output.length,result);
		
		System.out.println("in: "+toHex(data));
		System.out.println("enc: "+toHex(output));
		System.out.println("dec: "+toHex(result));
		
		for(int i = 0; i < data.length; i++)
		{
			if(data[i] != result[i])
				throw new IllegalStateException("darn gosh");
		}
		
	}

	public static int doubleToByteArray2(double val, byte[] output, int start) {
		return longToByteArray2(Double.doubleToLongBits(val), output, start);
	}

	public static int floatToByteArray2(float val, byte[] output, int start) {
		return intToByteArray2(Float.floatToIntBits(val), output, start);
	}

	public static String rot13(String s) {
		StringBuffer r=  new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if       (c >= 'a' && c <= 'm') c += 13;
            else if  (c >= 'n' && c <= 'z') c -= 13;
            else if  (c >= 'A' && c <= 'M') c += 13;
            else if  (c >= 'A' && c <= 'Z') c -= 13;
            r.append(c);
        }
        
        return r.toString();
	}

	public static boolean isByteArray(Class<?> type) {
		return type.isArray() && type.getComponentType() == byte.class;
	}

	public static boolean isIntArray(Class<? extends Object> type) {
		return type.isArray() && type.getComponentType() == int.class;
	}

	public static float square(float i) {
		return i*i;
	}
	
	public static int normalizeLonm(int lonm)
	{
		return ((lonm+180000000)%360000000+360000000)%360000000 - 180000000;
	}

	public static double normalizeLonm(double lonm) {
		if(lonm < Util.MIN_LONM) 
			lonm += Util.LONM_PER_WORLD;
		
		if(lonm > Util.MAX_LONM) 
			lonm -= Util.LONM_PER_WORLD;
		
		return lonm;
	}


	/**
	 * Given a lon coordinate, returns a lon coordinate where if the items
	 * are subtracted and a distance obtained, it won't wrap the the earth.
	 * i.e. if the ref lonm is -179,000,000 and lonm is 179,000,000 then
	 * -181,000,000 will be returned, so that if the items are subtacted, then
	 * 2,000,000 will be returned, rather than 358,000,000
	 */
	public static int makeContinuousLonm(int refLonm, int lonm) {
		//-358 % 360 is -358
		//-718 % 360 is -358

		int dist = (lonm - refLonm) % 360000000;
		
		if(dist > 180000000)
		{
			dist -= 360000000;
		}
		else if(dist < -180000000)
		{
			dist += 360000000;
		}
		
		return refLonm + dist;
	}

	/**
	 * Given a start lon coordinate and a target lon coordinate, returns a lon coordinate 
	 * which is always greater than the start.
	 */
	public static int makeContinuousFromStartLonm(int startLonm, int lonm) {
		//-358 % 360 is -358
		//-718 % 360 is -358
		
		return ((lonm - startLonm) % 360000000 + 360000000) %  360000000 + startLonm;
	}

	public static double makeContinuousLonm(double lonmRef, double lonm) {
		double sx = lonm - lonmRef;
		//handle lonm wrapping
		if(sx > Util.LONM_PER_WORLD>>1)
			sx -= Util.LONM_PER_WORLD;
		else if(sx < -Util.LONM_PER_WORLD>>1)
			sx += Util.LONM_PER_WORLD;
		
		return lonmRef + sx;
	}
	/**
	 * Determines whether two lon distances overlap
	 */
	public static boolean isLonmOverlaps(int lonmStart, int lonmEnd, int lonmStart2, int lonmEnd2) {
		lonmEnd = Util.makeContinuousFromStartLonm(lonmStart, lonmEnd);
		lonmStart2 = Util.makeContinuousLonm(lonmStart, lonmStart2);
		lonmEnd2 = Util.makeContinuousFromStartLonm(lonmStart2, lonmEnd2); 
		
		return lonmStart2 <= lonmEnd && lonmEnd2 > lonmStart;
			
	}

	public static String toIntList(int[] value) {
		StringBuffer sb = new StringBuffer();
		for(int v : value)
		{
			sb.append(v).append(',');
		}
		
		if(sb.length() > 0)
			sb.deleteCharAt(sb.length()-1);
		
		return sb.toString();
	}

	public static int [] fromStringIntListToIntArray(String value) {
		
		String [] resultS = value.split(",");
		
		int [] result = new int[resultS.length];
		
		for(int i = 0; i < result.length; i++)
			result[i] = Integer.parseInt(resultS[i]);
		
		return result;
	}

	public static int getLonmMin(int l1, int l2) {
		int al1 = Util.makeContinuousLonm(l2, l1);
		
		return normalizeLonm(Math.min(al1, l2));
	}

	public static int getLonmMax(int l1, int l2) {
		int al1 = Util.makeContinuousLonm(l2, l1);
		
		return normalizeLonm(Math.max(al1, l2));
	}

	public static int subtractLonm(int maxLonm, int minLonm) {
		return Util.makeContinuousFromStartLonm(minLonm, maxLonm) - minLonm;
	}

	public static boolean overlaps(int minLatm1, int heightLatm1, int minLatm2, int heightLatm2, 
			int minLonm1, int widthLonm1, int minLonm2,
			int widthLonm2) {
		return minLatm1 < minLatm2 + heightLatm2 && minLatm1 + heightLatm1 > minLatm2 
			&& isLonmOverlaps(minLonm1, widthLonm1, minLonm2, widthLonm2);
			
	}

	public static boolean isLonmOverlapsPoint(int lonmStart, int lonmEnd, int lonm) {
		return makeContinuousFromStartLonm(lonmStart, lonmEnd) > Util.makeContinuousFromStartLonm(lonmStart, lonm);
	}

	public static String gnuPlot2DIt(double ... data) {
		StringBuffer sb = new StringBuffer();
		
		for(int i = 0; i < data.length; i+=2)
		{
			sb.append(data[i]).append(" ");
			sb.append(data[i+1]).append("\n");
		}
		
		sb.append("\n");
		
		return sb.toString();
	}

	public static String gnuPlot3DIt(double ... data) {
		StringBuffer sb = new StringBuffer();
		
		for(int i = 0; i < data.length; i+=3)
		{
			//skip cases where the same point is visited twice (it really freaks out gnuplot)
			if(i >= 3 
					&& data[i] == data[i-3] 
					&& data[i+1] == data[i-2] 
					&& data[i+2] == data[i-1])
				continue; 
			sb.append(data[i]).append(" ");
			sb.append(data[i+1]).append(" ");
			sb.append(data[i+2]).append("\n");
		}
		
		sb.append("\n");
		
		return sb.toString();
	}

	/**
	 *                      7       8
	 *                   5      6
	 *            
	 * 
	 *         3      4
	 *     1       2
	 *      _
	 *      /|
	 *     /
	 * latm
	 * 
	 *  lonm ------>
	 *  
	 *   /|\
	 *    |
	 *  time
	 */
	public static String gnuPlot3DLopsidedBox(int minLatm, int minLonm, int heightLatm, 
			int widthLonm, long startTime, long endTime, 
			int endMinLatm,
			int endMinLonm) {
		return Util.gnuPlot3DIt(minLatm,minLonm,startTime //1
				,minLatm,minLonm+widthLonm,startTime      //2
				,endMinLatm,endMinLonm+widthLonm,endTime        //6
				,endMinLatm,endMinLonm,endTime        //5
				,minLatm,minLonm,startTime      //1
				,minLatm+heightLatm,minLonm,startTime      //3
				,endMinLatm+heightLatm,endMinLonm,endTime        //7
				,endMinLatm,endMinLonm,endTime        //5
				,endMinLatm,endMinLonm+widthLonm,endTime        //6
				,endMinLatm+heightLatm,endMinLonm+widthLonm,endTime        //8
				,minLatm+heightLatm,minLonm+widthLonm,startTime        //4
				,minLatm,minLonm+widthLonm,startTime      //2
				,minLatm+heightLatm,minLonm+widthLonm,startTime        //4
				,minLatm+heightLatm,minLonm,startTime      //3
				,endMinLatm+heightLatm,endMinLonm,endTime        //7
				,endMinLatm+heightLatm,endMinLonm+widthLonm,endTime);        //8
	}

	/**
	 *         7       8
	 *     5      6
	 *            
	 * 
	 *         3      4
	 *     1       2
	 *      _
	 *      /|
	 *     /
	 * latm
	 * 
	 *  lonm ------>
	 *  
	 *   /|\
	 *    |
	 *  time
	 */
	public static String gnuPlot3DSpaceTimeBox(int minLatm, int minLonm, int heightLatm, 
			int widthLonm, long startTime, long endTime) {
		return Util.gnuPlot3DIt(minLatm,minLonm,startTime //1
				,minLatm,minLonm+widthLonm,startTime      //2
				,minLatm,minLonm+widthLonm,endTime        //6
				,minLatm,minLonm,endTime        //5
				,minLatm,minLonm,startTime      //1
				,minLatm+heightLatm,minLonm,startTime      //3
				,minLatm+heightLatm,minLonm,endTime        //7
				,minLatm,minLonm,endTime        //5
				,minLatm,minLonm+widthLonm,endTime        //6
				,minLatm+heightLatm,minLonm+widthLonm,endTime        //8
				,minLatm+heightLatm,minLonm+widthLonm,startTime        //4
				,minLatm,minLonm+widthLonm,startTime      //2
				,minLatm+heightLatm,minLonm+widthLonm,startTime        //4
				,minLatm+heightLatm,minLonm,startTime      //3
				,minLatm+heightLatm,minLonm,endTime        //7
				,minLatm+heightLatm,minLonm+widthLonm,endTime);        //8
	}
//	public static boolean connectableToGoogleMaps(Context context) {
//		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
//		cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
//	} 

	public static String convertToSingleLine(String foo) {
		return foo.replace('\n', '~');
	}

	public static double getDist(double lonm1, double latm1,
			double lonm2, double latm2) {
		lonm2 = Util.makeContinuousLonm(lonm1, lonm2) - lonm1;
		latm2 = latm2 - latm1;
		
		return Math.sqrt(lonm2*lonm2 + latm2*latm2);
	}

	public static double getDistSquared(double lonm1, double latm1,
			double lonm2, double latm2) {
		lonm2 = Util.makeContinuousLonm(lonm1, lonm2) - lonm1;
		latm2 = latm2 - latm1;
	
		return lonm2*lonm2 + latm2*latm2;
	}

	/**
	 * Either does a floor or ceiling to roundTo
	 * @param val
	 * @param roundTo
	 * @param ceil
	 * @return
	 */
	public static int granularize(int val, int roundTo, boolean ceil) {
		if(roundTo == 0)
			return val;
		
		int mod = val % roundTo;
		
		if(ceil)
			return val + mod;
		return val - mod;
	}

	public static int maxAll(int ... vals) {
		int max = vals [0];
		
		for(int i = 1; i < vals.length; i++)
			if(vals[i] > max) max = vals[i];

		return max;
	}

	/**
	 * @return x where x is the minimum value where 2 ** x > i, x >= 0  
	 */
	public static int minIntegerLog2(long i) {
		if(i < 0)
			throw new IllegalStateException("WHA??? "+i);
		int res = 0;
		while(i != 0)
		{
			i = i >> 1;
			res ++;
		}
		return res;
	}

	public static int readFully(RandomAccessFile raf, byte [] buffer) throws IOException
	{
		return readFully(raf, buffer, 0, buffer.length);
	}

	public static int readFully(RandomAccessFile raf, byte[] buffer, int offset, int length) throws IOException {
		int totalRead = 0;
		
		while (totalRead < length) {
			int numRead = raf.read(buffer, offset + totalRead, length - totalRead);
			if (numRead < 0) {
				break;
			}
			
			totalRead += numRead;
		}
		return totalRead;
	}

	public static int readFully(InputStream raf, byte [] buffer) throws IOException
	{
		return readFully(raf, buffer, 0, buffer.length);
	}

	public static int readFully(InputStream raf, byte[] buffer, int offset, int length) throws IOException {
		int totalRead = 0;
		
		while (totalRead < length) {
			int numRead = raf.read(buffer, offset + totalRead, length - totalRead);
			if (numRead < 0) {
				break;
			}
			
			totalRead += numRead;
		}
		return totalRead;
	}

	public static float convertSpToPixel(float sp,Context context){
	    Resources resources = context.getResources();
	    DisplayMetrics metrics = resources.getDisplayMetrics();
	    float px = sp * metrics.scaledDensity;
	    return px;
	}

	public static float convertPixelsToSp(float px,Context context){
	    Resources resources = context.getResources();
	    DisplayMetrics metrics = resources.getDisplayMetrics();
	    return px / metrics.scaledDensity;
	}
	
	/**
	 * This method convets dp unit to equivalent device specific value in pixels. 
	 * 
	 * @param dp A value in dp(Device independent pixels) unit. Which we need to convert into pixels
	 * @param context Context to get resources and device specific display metrics
	 * @return A float value to represent Pixels equivalent to dp according to device
	 */
	public static float convertDpToPixel(float dp,Context context){
	    Resources resources = context.getResources();
	    DisplayMetrics metrics = resources.getDisplayMetrics();
	    float px = dp * metrics.density;
	    return px;
	}
	/**
	 * This method converts device specific pixels to device independent pixels.
	 * 
	 * @param px A value in px (pixels) unit. Which we need to convert into db
	 * @param context Context to get resources and device specific display metrics
	 * @return A float value to represent db equivalent to px value
	 */
	public static float convertPixelsToDp(float px,Context context){
	    Resources resources = context.getResources();
	    DisplayMetrics metrics = resources.getDisplayMetrics();
	    float dp = px / metrics.density;
	    return dp;

	}

	public static int parseIntIfPresent(String val, int defaultValue) {
		if(val == null || val.length() == 0)
			return defaultValue;
		return Integer.parseInt(val);
	}
	
	public static long parseLongIfPresent(String val, int defaultValue) {
		if(val == null || val.length() == 0)
			return defaultValue;
		return Long.parseLong(val);
	}

	//2010:05:27 22:24:46
	public static SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
	
	static
	{
		utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
	/**
	 * Parses the exif date string in the UTC timezone (which is probably not correct, but
	 * the local timezone may not be either
	 */
	public static long getExifDateInUTC(ExifInterface ei) {
		String dateTimeStr = ei.getAttribute(ExifInterface.TAG_DATETIME);
		
		if(dateTimeStr != null)
		{
			synchronized (utcFormat)
			{
				try {
					return utcFormat.parse(dateTimeStr).getTime();
				}
				catch(ParseException e)
				{
					return 0;
				}
			}
		}
		
		return 0;
	}

	public static double getExifDouble(ExifInterface ei, String tag, double defaultValue) {
		String value = ei.getAttribute(tag);
		
		if(value == null)
			return defaultValue;
		
		try {
			return Double.parseDouble(value);
		}
		catch(NumberFormatException e)
		{
			return defaultValue;
		}
	}

	public static boolean isLonLatSane(double lon, double lat) {
		return lon >= -180 && lon < 180 && lat >= -90 && lat <= 90;
	}

	public static void viewMediaInGallery(
			Activity activity, String filename, boolean isImage) {
        Intent intent = new Intent(Intent.ACTION_VIEW);  
        intent.setDataAndType(Uri.fromFile(new File(filename)), isImage ? "image/*" :
        	"video/*");  
        activity.startActivity(intent); 
	}
	
	
	
	public static Bitmap getBitmap(Context context, int id, boolean isImage)
	{
		String filename = getDataFilepathForMedia(context.getContentResolver(), id, isImage);
		
		/* ttt_installer:remove_line */Log.d(GTG.TAG,"Loading bitmap for "+filename);
		
		if(filename == null)
			return null;

		if(isImage)
			return new BitmapDrawable(context.getResources(), filename).getBitmap();
		
		MediaMetadataRetriever mmr = new MediaMetadataRetriever();
		mmr.setDataSource(filename);
		return mmr.getFrameAtTime();
	}

	public static String getMimeTypeForMedia(ContentResolver cr, int id,
			boolean isImage) {
		Cursor cursor;
		
		if(isImage)
			cursor = cr.query(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
	                new String [] {MediaStore.Images.Media.MIME_TYPE}, ImageColumns._ID+" = ?", 
	                new String [] { String.valueOf(id) }, null);
		else
			cursor = cr.query(
					MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
	                new String [] {MediaStore.Video.Media.MIME_TYPE}, VideoColumns._ID+" = ?", 
	                new String [] { String.valueOf(id) }, null);
			
		try {
			if(!cursor.moveToFirst())
				return null;
			
			return cursor.getString(0);
		}
		finally {
			cursor.close();
		}
	}

	public static String getDataFilepathForMedia(ContentResolver cr, int id, boolean isImage)
	{
		Cursor cursor;
		
		if(isImage)
			cursor = cr.query(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
	                new String [] {MediaStore.Images.Media.DATA}, ImageColumns._ID+" = ?", 
	                new String [] { String.valueOf(id) }, null);
		else
			cursor = cr.query(
					MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
	                new String [] {MediaStore.Video.Media.DATA}, VideoColumns._ID+" = ?", 
	                new String [] { String.valueOf(id) }, null);
			
		try {
			if(!cursor.moveToFirst())
				return null;
			
			return cursor.getString(0);
		}
		finally {
			cursor.close();
		}
	}

	/**
	 * True if the media exists, false otherwise
	 * @param context
	 * @param id
	 * @param isImage false == video
	 * @return
	 */
	public static boolean mediaExists(Context context, int id, boolean isImage) {
		ContentResolver cr = context.getContentResolver();
		
		Cursor cursor;
		if(isImage)
			cursor = cr.query(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
	                new String [] {MediaStore.Images.Media.DATA}, ImageColumns._ID+" = ?", 
	                new String [] { String.valueOf(id) }, null);
		else
			cursor = cr.query(
					MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
	                new String [] {MediaStore.Video.Media.DATA}, VideoColumns._ID+" = ?", 
	                new String [] { String.valueOf(id) }, null);
			
		try {
			if(!cursor.moveToFirst())
				return false;
			
			if(!new File(cursor.getString(0)).exists())
				return false;
		}
		finally {
			cursor.close();
		}
		
		return true;
	}

	public static void clearCalendarValuesUnder(Calendar calendar, int calendarId) {
		switch (calendarId)
		{
		case Calendar.YEAR:
			calendar.set(Calendar.MONTH, calendar.getActualMinimum(Calendar.MONTH));
			//no break intentional
		case Calendar.MONTH:
			calendar.set(Calendar.DATE, calendar.getActualMinimum(Calendar.DATE));
			//no break intentional
		case Calendar.DATE:
			calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMinimum(Calendar.HOUR_OF_DAY));
			//no break intentional
		case Calendar.HOUR_OF_DAY:
		case Calendar.HOUR:
			calendar.set(Calendar.MINUTE, calendar.getActualMinimum(Calendar.MINUTE));
			//no break intentional
		case Calendar.MINUTE:
			calendar.set(Calendar.SECOND, calendar.getActualMinimum(Calendar.SECOND));
			calendar.set(Calendar.MILLISECOND, calendar.getActualMinimum(Calendar.MILLISECOND));
			break;
		default:
			throw new IllegalStateException("What is "+calendarId);
		}
	}

	public static void printAllStackTraces() {
		for(Entry<Thread, StackTraceElement[]> ent : Thread.getAllStackTraces().entrySet())
		{
			Log.e(GTG.TAG,"Thread "+ent.getKey()+": trace: "+Arrays.toString(ent.getValue()));
		}
		Log.e(GTG.TAG,"------");
	}

	/**
	 * Makes a rect for cross hairs. Assumes width of cross hairs is 1
	 * @param x
	 * @param y
	 * @param crossHairLength
	 * @return
	 */
	public static Rect makeRectForCrossHairs(int x, int y,
			int crossHairLength) {
		return new Rect(x - crossHairLength, y - crossHairLength,
				x+crossHairLength+1, y+crossHairLength+1);
	}

	public static void updateDatePicker(DatePicker datePicker,
			long timeMs, boolean subtractOneDay) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(timeMs);
		
		if(subtractOneDay)
			calendar.add(Calendar.DATE, -1);
		
		datePicker.updateDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), 
				calendar.get(Calendar.DAY_OF_MONTH));
	}

	public static long getTimeMsFromDatePicker(DatePicker datePicker, boolean addOneDay) {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.YEAR, datePicker.getYear());
		calendar.set(Calendar.MONTH, datePicker.getMonth());
		calendar.set(Calendar.DATE, datePicker.getDayOfMonth());
		clearCalendarValuesUnder(calendar, Calendar.DATE);
		if(addOneDay)
			calendar.add(Calendar.DATE, 1);
		
		return calendar.getTimeInMillis();
	}

	public static boolean localeIsMetric() {
        String countryCode = Locale.getDefault().getCountry();
        // USA, Liberia, or Burma is imperial
        if ("US".equals(countryCode) ||
        		"LR".equals(countryCode) ||
        		"MM".equals(countryCode))
        	return false;
        
        return true;
	}

	/**
	 * For View.onMeasure, chooses a measurement that is at least the given value,
	 *   unless overridden by measure spec
	 */
	public static int chooseAtLeastForOnMeasure(int minValue, int measureSpec) {
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY || specMode == MeasureSpec.AT_MOST && minValue > specSize) 
        	return specSize;

        return minValue;
    }

	/**
	 * Calculates difference in full value units. (ie if c1 is
	 * August 1st, 2011 and c2 is July 31st, 2012, the difference
	 * in years is 0, months is 11, days is 364, etc., but
	 * if c2 is August 1st, 2012, then the diff is
	 * 1 year, or 12 months, or 365 days)
	 * 
	 * @param c1
	 * @param nowMs
	 * @param id
	 * @return
	 */
	public static int calcDiff(Calendar c1, long nowMs, int id) {
		int presumedDiff;
		
		int step = 1;

		switch (id) {
		case Calendar.YEAR:
			// divide by a impossibly short year, so we can get an estimate
			presumedDiff = (int) ((nowMs - c1.getTimeInMillis()) / (Util.MILLIS_IN_YEAR - Util.MILLIS_IN_DAY * 3));
			break;
		case Calendar.MONTH:
			presumedDiff = (int) ((nowMs - c1.getTimeInMillis()) / (Util.MILLIS_IN_MONTH - Util.MILLIS_IN_DAY * 3));
			break;
		case Calendar.WEEK_OF_MONTH:
		case Calendar.WEEK_OF_YEAR:
			presumedDiff = (int) ((nowMs - c1.getTimeInMillis()) / (Util.MILLIS_IN_DAY * 7 - Util.MILLIS_IN_HOUR * 2));
			
			step = 7;
			id = Calendar.DATE;
			break;
		case Calendar.DATE:
			presumedDiff = (int) ((nowMs - c1.getTimeInMillis()) / (Util.MILLIS_IN_HOUR * 23 - Util.MILLIS_IN_MINUTE * 30));
			break;
		case Calendar.HOUR:
		case Calendar.HOUR_OF_DAY:
			return (int) ((nowMs - c1.getTimeInMillis()) / Util.MILLIS_IN_HOUR);
		case Calendar.MINUTE:
			return (int) ((nowMs - c1.getTimeInMillis()) / Util.MILLIS_IN_MINUTE);
		default:
			throw new IllegalStateException("What... is " + id + "?");
		}
		
		return calcDiff2(c1, nowMs, presumedDiff, id, step);
	}

	private static int calcDiff2(Calendar c1, long nowMs, int presumedDiff, int id, int step) {
		//if its shorter than our minimum estimate
		if(presumedDiff < 1)
		{
			return 0;
		}
		
		long oldTimeInMillis = c1.getTimeInMillis();

		try {
			//so now we need to verify that we were right.
			c1.add(id, presumedDiff * step);
			
			while(c1.getTimeInMillis() > nowMs)
			{
				presumedDiff --;
				c1.add(id, -step);
			}
			
			return presumedDiff;
		}
		finally
		{
			c1.setTimeInMillis(oldTimeInMillis);
		}
		
	}

	public static boolean isCallable(Context context, Intent intent) {
		List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(
				intent, 0);
		return list.size() > 0;
	}

	public static String unobfuscate(byte [] data) {
		int v = 0;
		int s = data[0];
		StringBuffer sb = new StringBuffer();
		sb.append((char)(data[1] ^ s ^ 42));
		for(int i = 2; i < data.length; i++)
		{
			sb.append((char)(v = data[i] ^ data[i-1] ^ 42 ^ s));
		}
		
		return sb.toString();
	}
	
	/**
	 * Runs runnable on ui and waits for it to finish. If the current thread is
	 * the ui thread, just runs it
	 */
	public static void runOnUiThreadSynchronously(Activity a, final Runnable runnable) {
		final boolean done []  = new boolean[1];
		
		a.runOnUiThread(new Runnable()
			{
	
				@Override
				public void run() {
					runnable.run();
					
					synchronized (done) {
						done[0] = true;
						done.notify();
					}
					
				}
			});
			
			synchronized(done)
			{
				while(!done[0])
				{
					try {
						done.wait();
					} catch (InterruptedException e) {
						throw new IllegalStateException(e);
					}
				}
			}
	
		
	}

	/**
	 * Runs runnable on handler and waits for it to finish. If the current thread is
	 * the handler thread, just runs it.
	 */
	public static void runOnHandlerSynchronously(Handler h, final Runnable runnable) {
		final boolean done []  = new boolean[1];
		
		if(h.getLooper().getThread() == Thread.currentThread())
		{
			runnable.run();
		}
		else {
			h.postAtFrontOfQueue(new Runnable()
			{
	
				@Override
				public void run() {
					runnable.run();
					
					synchronized (done) {
						done[0] = true;
						done.notify();
					}
					
				}
			});
			
			synchronized(done)
			{
				while(!done[0])
				{
					try {
						done.wait();
					} catch (InterruptedException e) {
						throw new IllegalStateException(e);
					}
				}
			}
		}
		
	}

	/**
	 * 
	 * @param in
	 * @param out
	 * @return 
	 * @throws IOException if there is an error reading from in
	 * @return exception if exception occurs when writing
	 */
	public static IOException copy(byte [] buffer, InputStream in, OutputStream out) throws IOException 
	{
		int read;

		while ((read = in.read(buffer)) != -1) 
		{
			try {
				out.write(buffer, 0, read);
			} 
			catch (IOException e) 
			{
				return e;
			}
		}
		
		return null;
	}

	/**
	 * Creates a zip file an opens a single entry for writing
	 * @param filePath
	 * @param zipEntry
	 * @return 
	 * @throws IOException 
	 */
	public static ZipOutputStream createZipOutputStream(String filePath,
			String zipEntryName) throws IOException {
		 OutputStream os = new FileOutputStream(filePath);
		 ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));
		 ZipEntry entry = new ZipEntry(zipEntryName);
		 zos.putNextEntry(entry);
		 
		 return zos;
	}

	
	public static double calcDistFromLonmLatm(double lonm1, double latm1, double lonm2,
			double latm2) {
		double lon1R = lonm1 * .000001 /180 * Math.PI;
		double lat1R = latm1 * .000001 /180 * Math.PI;
		double lon2R = lonm2 * .000001 /180 * Math.PI;
		double lat2R = latm2 * .000001 /180 * Math.PI;
		
		// from http://www.movable-type.co.uk/scripts/latlong.html
//		var R = 6371; // km
//		var dLat = (lat2-lat1).toRad();
//		var dLon = (lon2-lon1).toRad();
//		var lat1 = lat1.toRad();
//		var lat2 = lat2.toRad();
//
//		var a = Math.sin(dLat/2) * Math.sin(dLat/2) +
//		        Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat1) * Math.cos(lat2); 
//		var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
//		var d = R * c;
		
		double dLatR = lat2R - lat1R;
		double dLonR = lon2R - lon1R;
		
		double a = Math.sin(dLatR/2) * Math.sin(dLatR/2) +
			Math.sin(dLonR/2) * Math.sin(dLonR/2) * Math.cos(lat1R) * Math.cos(lat2R);
		double c = 2* Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
		return EARTH_RADIUS_M * c; 
	}
	
	public static String[] gmtTimeZoneNames;
	public static TimeZone androidPuntTimeZone;
	public static final Pattern TIME_ZONE_EXTRA_PATTERN = Pattern.compile("GMT[-+]?\\d\\d?(:?\\d\\d?)?");
	
	public static TimeZone parseTimeZone(String tzId) {
		tzId = tzId.trim();
		
		TimeZone tz = TimeZone.getTimeZone(tzId);
		
		//getTimeZone returns GMT if it doesn't understand tzId, so if it does return
		// GMT, we need to check if it's lying to us
		if(gmtTimeZoneNames == null)
		{
			gmtTimeZoneNames = TimeZone.getAvailableIDs(0);
			
			//use a dummy value to get the "punt" timezone
			androidPuntTimeZone = TimeZone.getTimeZone("android y u so weird");
		}
		if(tz.hasSameRules(androidPuntTimeZone) 
				&& Arrays.binarySearch(gmtTimeZoneNames, 0, gmtTimeZoneNames.length, tzId) < 0
				&& !TIME_ZONE_EXTRA_PATTERN.matcher(tzId).matches())
		{
			return null; //I think android punted
		}
		
		return tz;
	}
	
	public static TimeZone getCurrTimeZone() {
		Calendar cal = Calendar.getInstance();
		TimeZone tz = cal.getTimeZone();
		
		return tz;
	}

	/**
	 * mapzens MapController sometimes returns longitudes outside of -180/+180
	 * so we convert it to a normal value. Alters given lngLat and returns it.
     */
	public static LngLat normalizeLngLat(LngLat p1) {
		if(p1.longitude < Util.MIN_LON || p1.longitude> Util.MAX_LON) {
			int wraps = (int) Math.floor((p1.longitude - Util.MIN_LON) / Util.LON_PER_WORLD);
			p1.longitude -= wraps * Util.LON_PER_WORLD;
		}

		return p1;
	}

	public static interface LongComparator<T>
	{
		public int compare(T obj, long key);
	}

	public static <T> int binarySearch(List<T> list, long key, LongComparator<T> c) {
		int size = list.size();
		int low = 0;
		int high = size - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1;
			T midVal = list.get(mid);
			int cmp = c.compare(midVal, key);

			if (cmp < 0)
				low = mid + 1;
			else if (cmp > 0)
				high = mid - 1;
			else
				return mid; // key found
		}
		return -(low + 1); // key not found.
	}

	
	private static final int MAX_MENTIONED_ITEMS = 3;
	
	
	private static class TimeLabel
	{
		long timeInMillis;
		int labelId, pluralLabelId;
		
		public TimeLabel(long timeInMillis, int labelId, int pluralLabelId) {
			super();
			this.timeInMillis = timeInMillis;
			this.labelId = labelId;
			this.pluralLabelId = pluralLabelId;
		}

		public long getValue(long l) {
			return l/timeInMillis;
		}

		public long getRemainder(long l) {
			return l%timeInMillis;
		}

		public void appendText(Context c, StringBuffer res, long v) {
			if(res.length() != 0)
				res.append(", ");
			
			res.append(v).append(" ");
			if(v != 1)
				res.append(c.getString(pluralLabelId));
			else
				res.append(c.getString(labelId));
		}
		
		
	}

	private static TimeLabel[] timeLabels =
	{
			new TimeLabel(MILLIS_IN_YEAR, R.string.year, R.string.year_plural),
			new TimeLabel(MILLIS_IN_MONTH, R.string.month, R.string.month_plural),
			new TimeLabel(MILLIS_IN_DAY * 7, R.string.week, R.string.week_plural),
			new TimeLabel(MILLIS_IN_DAY, R.string.day, R.string.day_plural),
			new TimeLabel(MILLIS_IN_HOUR, R.string.hour, R.string.hour_plural),
			new TimeLabel(MILLIS_IN_MINUTE, R.string.minute, R.string.minute_plural),
			new TimeLabel(1000, R.string.second, R.string.second_plural),
	};

	public static String convertMsToText(Context context, long l) {

		int mentionedItems = 0;
		
		StringBuffer res = new StringBuffer();
		
		for(int i = 0; i < timeLabels.length; i++)
		{
			TimeLabel tl = timeLabels[i];
			long v = tl.getValue(l);
			l = tl.getRemainder(l);

			if(v != 0)
			{
				tl.appendText(context, res, v);
				
				if(++mentionedItems >= MAX_MENTIONED_ITEMS)
					break;
			}
		}
		
		if(mentionedItems == 0)
			return "--";
		
		return res.toString();
	}

	public static String doubleToHex(double d) {
		byte [] out = new byte[8];
		doubleToByteArray2(d, out, 0);
		return toHex(out);
	}

	public static double hexToDouble(String s) {
		byte[] b = toByte(s);
		return byteArrayToDouble(b, 0);
	}

	/**
	 * Runs runnable when the views getWidth() will not return 0
	 * (after it is layed out)
	 */
	public static void runWhenGetWidthWorks(final View view, final Runnable runnable) {
		ViewTreeObserver viewTreeObserver = view.getViewTreeObserver();
		
		if (viewTreeObserver.isAlive()) {
		  viewTreeObserver.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
		    @Override
		    public void onGlobalLayout() {
		    	view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
		    	
		    	runnable.run();
		    }
		  });
		}
	}

	/**
	 * Reads a reader line by line into an output string, applying all patterns and replacing
	 * with given replacements.
	 * @throws IOException 
	 */
	public static String readReaderIntoStringWithMatchReplace(BufferedReader reader, Pattern [] patterns, String [] replacements) throws IOException
	{
		if(patterns.length != replacements.length)
			TAssert.fail();
		
		String line = null;
		
		StringBuilder sb = new StringBuilder();

		while ((line = reader.readLine()) != null) {
			for(int i = 0; i < patterns.length; i++)
			{
				line = patterns[i].matcher(line).replaceAll(replacements[i]);
			}
			sb.append(line).append('\n');
		}
		
		return sb.toString();
	}

	public static void deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory())
			for (File child : fileOrDirectory.listFiles())
				deleteRecursive(child);

		fileOrDirectory.delete();
	}

}  


