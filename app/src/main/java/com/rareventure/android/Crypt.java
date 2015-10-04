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

import java.security.AlgorithmParameters;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GpsTrailerCrypt;

public class Crypt {

	//TODO 3.5: This is a hack to use the same IV for every instance. The data is very short, and pretty
	//much all different so this should be ok
	
	private static final int IV_LENGTH = 16;
	public static final int SECRET_KEY_ROUNDS = 2048;
	private static final String SECRET_KEY_FACTORY_IMPL = "PBKDF2WithHmacSHA1";
	
	private SecretKeySpec skeySpec;
	
	private SecureRandom sr = new SecureRandom();
	private byte [] ivData = new byte[IV_LENGTH];
	private Cipher encryptCipher;
	private Cipher decryptCipher;
	
	//TODO 3.5: Review secret algorithms and such, do we really want to use AES as opposed to something else?
	
	public Crypt(byte [] key)
	{
		_init(new SecretKeySpec(key, GpsTrailerCrypt.SECRET_KEY_SPEC_ALGORITHM));
	}
	
	private void _init(SecretKeySpec skeySpec)
	{
		this.skeySpec = skeySpec;
		try {

			encryptCipher = Cipher.getInstance(GpsTrailerCrypt.INTERNAL_SYMMETRIC_ENCRYPTION_ALGORITHM);
			//we have to initialize so that getNumOutputBytes... methods work, so we do so with a crap iv
			encryptCipher.init(Cipher.ENCRYPT_MODE, skeySpec, new IvParameterSpec(ivData));
			
			decryptCipher = Cipher.getInstance(GpsTrailerCrypt.INTERNAL_SYMMETRIC_ENCRYPTION_ALGORITHM);
			//we have to initialize so that getNumOutputBytes... methods work, so we do so with a crap iv
			decryptCipher.init(Cipher.DECRYPT_MODE, skeySpec, new IvParameterSpec(ivData));			
		}
		catch(Exception e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	
	/**
	 * Encrypts some data 
	 * 
	 * @param offset
	 * @param length
	 * @return size of encrypted value
	 * @throws BadPaddingException 
	 * @throws IllegalBlockSizeException 
	 * @throws ShortBufferException 
	 */
	public synchronized int encryptData(byte [] output, int outputPos, byte [] input, int inputOffset, int inputLength)
	{
		try {
			//write the iv
			sr.nextBytes(ivData);
			System.arraycopy(ivData, 0, output, outputPos, IV_LENGTH);
			
			encryptCipher.init(Cipher.ENCRYPT_MODE, skeySpec, new IvParameterSpec(ivData));
			encryptCipher.doFinal(input, inputOffset, inputLength, output, outputPos + IV_LENGTH);
			return IV_LENGTH+encryptCipher.getOutputSize(inputLength);
		}
		catch(Exception e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	/**
	 * Decrypts some data 
	 * 
	 * @return size of encrypted value
	 * @throws BadPaddingException 
	 * @throws IllegalBlockSizeException 
	 * @throws ShortBufferException 
	 */
	public synchronized int decryptData(byte [] output, byte [] input)
	{
		return decryptData(output, input, 0, input.length);
	}
	
	public synchronized int decryptData(byte [] output, byte [] input, int inputOffset, int inputLength)
	{
		try {
			decryptCipher.init(Cipher.DECRYPT_MODE, skeySpec, new IvParameterSpec(input, inputOffset, IV_LENGTH));			

			int outputSize = decryptCipher.doFinal(input, inputOffset+IV_LENGTH, inputLength - IV_LENGTH, output);
			
			return outputSize;
		}
		catch(Exception e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	public static byte[] getRawKey(String password, byte[] salt) {
		try {
			/* Derive the key, given password and salt. */
			SecretKeyFactory factory = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY_IMPL);
			
			//note, this takes a long time (on purpose) for an actual password. If we're using a fake one, there is no reason to loop so many rounds
			KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, GpsTrailerCrypt.prefs.isNoPassword ? 1 : SECRET_KEY_ROUNDS, 
					GpsTrailerCrypt.prefs.aesKeySize);
			SecretKey tmp = factory.generateSecret(spec);
			SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
			
			return secret.getEncoded();
		}
		catch(Exception e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	public static String getEncryptDesc()
	{
		try {
			return GpsTrailerCrypt.prefs.aesKeySize+" bit "+Cipher.getInstance(GpsTrailerCrypt.INTERNAL_SYMMETRIC_ENCRYPTION_ALGORITHM).
					getAlgorithm();
		}
		catch(Exception e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	public static String getSecretKeyDesc()
	{
		try {
			return SECRET_KEY_ROUNDS+" round "+SecretKeyFactory.getInstance(SECRET_KEY_FACTORY_IMPL).getAlgorithm();
		}
		catch(Exception e)
		{
			throw new IllegalStateException(e);
		}
			
	}

	public static String getAsymmetricEncryptionDesc()
	{
		try {
			return GpsTrailerCrypt.RSA_KEY_SIZE+" bit "+Cipher.getInstance(GpsTrailerCrypt.INTERNAL_ASYMMETRIC_ENCRYPTION_ALGORITHM).getAlgorithm();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
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

	public static String toHex(byte[] buf) {
		if (buf == null)
			return "";
		StringBuffer result = new StringBuffer(2 * buf.length);
		for (int i = 0; i < buf.length; i++) {
			appendHex(result, buf[i]);
		}
		return result.toString();
	}

	private final static String HEX = "0123456789ABCDEF";

	private static void appendHex(StringBuffer sb, byte b) {
		sb.append(HEX.charAt((b >> 4) & 0x0f)).append(HEX.charAt(b & 0x0f));
	}

	public synchronized int getNumOutputBytesForEncryption(int length) {
		return encryptCipher.getOutputSize(length) + IV_LENGTH;
	}

	public synchronized int getNumOutputBytesForDecryption(int encryptedLength) {
		return decryptCipher.getOutputSize(encryptedLength - IV_LENGTH);
	}



}