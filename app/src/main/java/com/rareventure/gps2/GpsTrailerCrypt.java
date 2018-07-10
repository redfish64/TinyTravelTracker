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
package com.rareventure.gps2;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import junit.framework.Assert;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.rareventure.android.Crypt;
import com.rareventure.android.DbUtil;
import com.rareventure.android.Util;
import com.rareventure.android.AndroidPreferenceSet.AndroidPreferences;
import com.rareventure.gps2.database.GpsLocationRow;
import com.rareventure.gps2.database.TAssert;
import com.rareventure.gps2.database.TimeZoneTimeRow;
import com.rareventure.gps2.database.UserLocationRow;
import com.rareventure.gps2.database.cache.AreaPanel;
import com.rareventure.gps2.database.cache.MediaLocTime;
import com.rareventure.gps2.database.cache.TimeTree;

public class GpsTrailerCrypt {
	private static final int SALT_LENGTH = 32;

	/**
	 * Does the actual encryption and decryption
	 */
	public Crypt crypt;

	public static Preferences prefs = new Preferences();

	private static HashMap<Integer, GpsTrailerCrypt> userDataKeyIdToGpsCrypt = new HashMap<Integer, GpsTrailerCrypt>();

	/**
	 * Encodes userdatakey for encrypting new rows
	 */
	private static PrivateKey privateKey;

	/**
	 * To be funny, we create a password even when there isn't one as specified
	 * by prefs.isNoPassword.
	 */
	public static final String NO_PASSWORD_PASSWORD = Util.rot13(/* ttt_installer:obfuscate_str */"cappadocia");

	private static final String INTERNAL_SYMMETRIC_ENCRYPTION_NAME = "AES";
	
	/**
	 * This is for encrypting the symmetric keys
	 */
	public static final String INTERNAL_SYMMETRIC_ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";
	// We use "AES/ECB/PKCS1Padding"; I think NoPadding (which is the default) is causing the leading zero bytes to be stripped
	// from the data
	
	//we use just AES here because that is what AESObfuscator from the google LVL uses, so if it's good enough
	// for them...
	public static final String SECRET_KEY_SPEC_ALGORITHM = "AES";
	
	public static final String INTERNAL_ASYMMETRIC_ENCRYPTION_NAME = "RSA";
	
	public static final String INTERNAL_ASYMMETRIC_ENCRYPTION_ALGORITHM = "RSA/ECB/PKCS1Padding";
	
	
	
	/**
	 * This uses RSA because we are basically signing timestamps with our private key.
	 * It shouldn't be used for any long messages, since it's using ECB
	 */
	private static final String TTT_ENCRYPTION_ALGORITHM = "RSA/ECB/PKCS1Padding";

	public static final int RSA_KEY_SIZE = 2048;


	/**
	 * Id of USER_DATA_KEY we are using to encrypt. Global to all tables
	 */
	public int userDataKeyId;

	public GpsTrailerCrypt(int userDataKeyId, byte[] key) {
		this.userDataKeyId = userDataKeyId;
		crypt = new Crypt(key);
	}

	
	/**
	 * Initializes the database when the password is not known (for example,
	 * when we are starting the GpsTrailerService on system startup). Existing
	 * data cannot be decrypted, but can encrypt and decrypt new rows. Use the
	 * "instance" field.
	 * 
	 * @param appId no longer used, use MASTER_APP_ID for now
	 */
	public static void initializeWithoutPassword(int appId) {
		Assert.assertTrue("Initial database setup not done",
				prefs.initialWorkPerformed);
		//TODO 3: co: we don't really need a master app id, because we won't be
		//cleaning data all at once, but rather only when we read it. 
//		Assert.assertTrue("Don't use the MASTER_APP_ID", appId != GTG.MASTER_APP_ID);

		GTG.crypt = generateAndInitializeNewUserDataEncryptingKey(appId, GTG.db);
	}

	/**
	 * Initializes the database if necessary for one off first time setup and
	 * then sets up data so the instance() and instance(userDataKeyId) can be
	 * called
	 * 
	 * @param password
	 *            if null, then it is assumed there is no password
	 */
	public static boolean initialize(String password) {

		if(!initializePrivateKey(password))
			return false;

		loadDefaultUserDataKey();
		
		return true;
	}

	/**
	 * Sets a new password for the first time. 
	 * 
	 * All encrypted data and tables will be destroyed if present.
	 * 
	 * @param password is only needed if prefs.isNoPassword is not set
	 */
	public static void deleteAllDataAndSetNewPassword(Context context, String password)
	{
		GpsTrailerDb.dropAndRecreateEncryptedTables(GTG.db);
		
		setupPreferencesForCrypt(context, password);
	}
	
	public static boolean resetPassword(Context context, String oldPassword, String newPassword)
	{
		if(!initializePrivateKey(oldPassword))
			return false; //bad old password
		
		encryptPrivateKeyAndStoreInPrefs(privateKey, newPassword);
		
		GTG.savePreferences(context);
		
		return true;
	}

	/**
	 * Initializes privateKey field
	 * 
	 * @param password is only needed if prefs.isNoPassword is not set
	 */
	public static boolean initializePrivateKey(String password) {
		privateKey = decryptPrivateKey(password);
		return privateKey != null;
	}
	
	/**
	 * Prefs must be setup before this is called (because of 
	 * the salt)
	 * @param password
	 * @return
	 */
	public static boolean verifyPassword(String password)
	{
		return decryptPrivateKey(password) != null;
	}

	private static int HACK = 3;

	private static PrivateKey decryptPrivateKey(String password)
	{
		if (prefs.isNoPassword) {
			Assert.assertTrue(password == null);
			password = Util.rot13(GpsTrailerCrypt.NO_PASSWORD_PASSWORD);
		}

		try {
			// Crypt keyDecryptor = new
			// Crypt(Crypt.getRawKeyOldWay(password.getBytes(), prefs.salt),
			// prefs.salt);
			Crypt keyDecryptor = new Crypt(Crypt.getRawKey(password, prefs.salt));
			
			byte[] encryptedPrivateKey = prefs.encryptedPrivateKey;

			byte[] output = new byte[keyDecryptor
					.getNumOutputBytesForDecryption(encryptedPrivateKey.length)];

			int keyLength = keyDecryptor.decryptData(output,
					encryptedPrivateKey);

			byte[] output2 = new byte[keyLength];
			System.arraycopy(output, 0, output2, 0, keyLength);

			// Private keys are encoded with PKCS#8 (or so they say)
			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(output);

			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return keyFactory.generatePrivate(keySpec);
		} catch (Exception e) {
			//we are assuming all errors are because of a wrong password


			//TODO 3: we could alternately store an additional digest of the password, 
			//and check against that, but it seems that it would be another avenue of attack.
			//and not worth it
			Log.d(GTG.TAG,"Password decryption exception",e);

			if(--HACK > 0)
				return null;
			else {
				HACK=3;
				throw new IllegalStateException("HACK!!! Assuming if the user entered the wrong password 3 times then there is something else that is wrong!!!", e);
			}
		}
	}

	private static void loadDefaultUserDataKey() {
		Cursor c = GTG.db.rawQuery(
				"select _id from user_data_key where app_id = ?",
				new String[] { String.valueOf(GTG.MASTER_APP_ID) });
		try {
			c.moveToNext();

			GTG.crypt = instance(c.getInt(0));
		} finally {
			DbUtil.closeCursors(c);
		}

	}

	/**
	 * Initializes the preferences to setup public and private keys
	 * <p>
	 * Basically, we have a public-private key pair, and encrypt the private key
	 * with a password. When we actually encrypt data, we use a random key. We
	 * store the random key encrypted with the public key in the database
	 * <p>
	 * In this way, we can start the gps trailer service on startup on the phone
	 * and encrypt values right away, without needing a password.
	 * 
	 * @param password may be null if one not given
	 */
	public static void setupPreferencesForCrypt(Context context, String password) {
		// The output of the below is to populate:
		// prefs.salt
		// prefs.encryptedPrivateKey
		// prefs.publicKey

		/* ttt_installer:remove_line */Log.d(GTG.TAG,"Initializing database encryption");

		try {
			/* ttt_installer:remove_line */Log.d(GTG.TAG,"Generating public/private keys");
			//
			// Generate public and private key pair
			//
			KeyPairGenerator kpg = KeyPairGenerator.getInstance(INTERNAL_ASYMMETRIC_ENCRYPTION_NAME);
			kpg.initialize(RSA_KEY_SIZE);
			KeyPair kp = kpg.genKeyPair();
			
			PublicKey publicKey = kp.getPublic();
			PrivateKey privateKey = kp.getPrivate();
			
			
			
			encryptPrivateKeyAndStoreInPrefs(privateKey, password);

			prefs.publicKey = publicKey.getEncoded();

			/* ttt_installer:remove_line */Log.d(GTG.TAG,"Saving preferences to database");
			// now we need to save the settings

			prefs.initialWorkPerformed = true;

			GTG.savePreferences(context);


		} catch (Exception e) {
			prefs.publicKey = null;
			prefs.encryptedPrivateKey = null;
			prefs.salt = null;
			prefs.initialWorkPerformed = false;
			throw new IllegalStateException("There is a problem somewhere", e);
		}
	}

	private static void encryptPrivateKeyAndStoreInPrefs(PrivateKey privateKey, String password) {
		

		prefs.salt = new byte[SALT_LENGTH];

		/* ttt_installer:remove_line */Log.d(GTG.TAG, "Generating salt");
		//
		// Generate salt
		//
		SecureRandom sr = new SecureRandom();
		sr.nextBytes(prefs.salt);

		/* ttt_installer:remove_line */Log.d(GTG.TAG, "Encrypting private key");
		//
		// Encrypt private key
		//
		if (password == null) {
			prefs.isNoPassword = true;
			password = Util.rot13(NO_PASSWORD_PASSWORD);
		}
		else prefs.isNoPassword = false;

		Crypt keyEncryptor = new Crypt(Crypt.getRawKey(password, prefs.salt));

		byte[] privateKeyData = privateKey.getEncoded();

		prefs.encryptedPrivateKey = new byte[keyEncryptor
				.getNumOutputBytesForEncryption(privateKeyData.length)];
		keyEncryptor.encryptData(prefs.encryptedPrivateKey, 0, privateKeyData,
				0, privateKeyData.length);

	}

	/**
	 * Creates a key used to encrypt and decrypt new rows.
	 * 
	 * @param appId
	 *            identifies the application that created the row.
	 * @param db 
	 * @return GpsTrailerCrypt instance created
	 */
	public static GpsTrailerCrypt generateAndInitializeNewUserDataEncryptingKey(int appId, SQLiteDatabase db) {

		try {

			byte[] userDataKey = new byte[prefs.aesKeySize/8];

			// this is really only going to called once per application, so we
			// generate and forget our
			// SecureRandom instance even though it's slow to create
			new SecureRandom().nextBytes(userDataKey);

			// now we need to encrypt the key using our public key, and store
			// the encrypted key into
			// the database so we can decrypt it using a private key when
			// starting the reviewer
			// (because the user will have entered the password at that point
			// for decrypting
			// the private key)
			PublicKey publicKey = constructPublicKey();

			Cipher cipher = Cipher.getInstance(INTERNAL_ASYMMETRIC_ENCRYPTION_ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);

			byte[] cipherData = cipher.doFinal(userDataKey);

			SQLiteStatement s = DbUtil
					.createOrGetStatement(db,
							"insert into USER_DATA_KEY (app_id, encrypted_key) values (?,?)");
			s.bindLong(1, appId);
			s.bindBlob(2, cipherData);
			int userDataKeyId = (int) s.executeInsert();
			// finally we create an instance using our non-encrypted key
			return new GpsTrailerCrypt(userDataKeyId, userDataKey);

		} catch (Exception e) {
			throw new IllegalStateException("Can't seem to encrypt a key", e);
		}

	}
	
	//TODO 2.5 we don't need app id anymore
	/**
	 * Used to pull a particular crypt setup to decrypt a row
	 */
	public static GpsTrailerCrypt instance(int userDataKeyId) {
		GpsTrailerCrypt crypt = userDataKeyIdToGpsCrypt.get(userDataKeyId);

		// if we haven't cached a gpstrailercrypt for this row
		if (crypt == null) {
			Cursor c = GTG.db.rawQuery(
					"select encrypted_key from user_data_key where _id = ?",
					new String[] { String.valueOf(userDataKeyId) });

			try {
				c.moveToNext();
				byte[] encryptedSymKey = c.getBlob(0);

				Cipher cipher = Cipher.getInstance(INTERNAL_ASYMMETRIC_ENCRYPTION_ALGORITHM);
				cipher.init(Cipher.DECRYPT_MODE, privateKey);

				byte[] symKey = cipher.doFinal(encryptedSymKey);

				crypt = new GpsTrailerCrypt(userDataKeyId, symKey);

				userDataKeyIdToGpsCrypt.put(userDataKeyId, crypt);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			} finally {
				DbUtil.closeCursors(c);
			}
		}

		return crypt;
	}

	public static PublicKey constructPublicKey() throws InvalidKeySpecException,
			NoSuchAlgorithmException {
		// Public keys are encrypted with X.509 (or so they say)
		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(prefs.publicKey);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");

		return keyFactory.generatePublic(keySpec);
	}

	public static PrivateKey getPrivateKey() throws InvalidKeySpecException,
			NoSuchAlgorithmException {
		return privateKey;
	}

	/**
	 * Cleans up old keys in user_data_key table
	 */
	public static void cleanUp() {
		if (privateKey == null)
			TAssert.fail("Must call initialize with a password to clean up data");

		SQLiteDatabase db = GTG.db;

		// this will select only rows that are not from the latest instance of
		// each application
		// that encrypts data. So if another application such as
		// GpsTrailerService is actively
		// creating rows, we won't touch it's row. But any key that was
		// generated by GpsTrailerService
		// before the current one is fair game.
		Cursor c = db
				.rawQuery(
						"select _id, encrypted_data from user_data_key udk where "
								+ "udk.app_id != ? and udk._id != "
								+ "(select max(_id) from user_data_key udk2 where udk2.app_id = udk.app_id)",
						new String[] { String.valueOf(GTG.MASTER_APP_ID) });

		Cursor c2 = null;

		// TODO 2.5: NOTE :: THIS DOESN'T WORK!!!!!!! where is the while loop for
		// c??? It's also not being used

		// try {
		//
		// for(EncryptedRow er : new EncryptedRow [] { allocateGpsLocationRow(),
		// allocateUserLocationRow(),
		// allocateAreaPanel(), allocateTimeTree(), allocateMediaLocTime()})
		// {
		// c2 = er.query(db, "user_data_key_fk = ?",c.getString(0));
		//
		// while(c2.moveToNext())
		// {
		// //this will read the row using it's key and update it using
		// //our key (the master key)
		// er.readRow(c2);
		// er.updateRow2(db);
		// }
		//
		// c2.close();
		// }
		//
		// SQLiteStatement s = DbUtil.createOrGetStatement(db,
		// "delete from user_data_key where _id = ?");
		// s.bindLong(1, c.getLong(0));
		// }
		// finally
		// {
		// DbUtil.closeCursors(c, c2);
		// }

	}

	public static class Preferences implements AndroidPreferences {


		/**
		 * True if the salt, password, public and private keys, etc. are filled
		 * out and encryption is ready to go
		 */
		public boolean initialWorkPerformed;

		/**
		 * True if there is no password (in which case we encrypt using "")
		 * (saved to db)
		 */
		public boolean isNoPassword;

		/**
		 * The salt for you know, well if you don't, look it up. (saved to db)
		 */
		public byte[] salt;

		/**
		 * Used for encrypting an AES key used to encrypt sensitive user data
		 * (such as the location points and saved user data) (saved to db)
		 */
		public byte[] publicKey;

		/**
		 * Used for decrypting tge AES key for sensitive user data (such as the
		 * location points and saved user data) (saved to db)
		 */
		public byte[] encryptedPrivateKey;

		public int aesKeySize = calcMaxKeySize();

	}

	public static int calcMaxKeySize()
	{
		//co: this returns Integer.MAX_VALUE
//		   System.out.println("Allowed Key Length: "
//		     + cipher.getMaxAllowedKeyLength("AES"));
		
		int [] keySizes = new int [] { 256, 192, 128 };
		
		for(int keySize : keySizes)
		{
			try {
				KeyGenerator keyGenerator = KeyGenerator.getInstance(INTERNAL_SYMMETRIC_ENCRYPTION_NAME);
				keyGenerator.init(keySize);
				SecretKey key = keyGenerator.generateKey();
				
				Cipher cipher = Cipher.getInstance(INTERNAL_SYMMETRIC_ENCRYPTION_ALGORITHM);
				cipher.init(Cipher.ENCRYPT_MODE, key);
			}
			catch(Exception e)
			{
				Log.d(GTG.TAG, "can't use keysize "+keySize+": "+e);
				continue;
			}
			
			return keySize;
		}
		
		throw new IllegalStateException("can't find a good keysize");
	}

	public int getDecryptedSize(int length) {
		return crypt.getNumOutputBytesForDecryption(length);
	}

	public boolean canDecrypt() {
		return privateKey != null;
	}

	//TODO 3.5 we can probably get rid of these and just use constructors
	public static GpsLocationRow allocateGpsLocationRow() {
		return new GpsLocationRow();
	}

	public static UserLocationRow allocateUserLocationRow() {
		return new UserLocationRow();
	}

	public static AreaPanel allocateAreaPanel() {
		return new AreaPanel();
	}

	public static TimeTree allocateTimeTree() {
		return new TimeTree();
	}

	public static MediaLocTime allocateMediaLocTime() {
		return new MediaLocTime();
	}

	public static TimeZoneTimeRow allocateTztRow() {
		return new TimeZoneTimeRow();
	}

	
	/**
	 * Uses private key directly to encrypt the data. Note that this
	 * is a slow method and not meant for high performance. See
	 * crypt.encryptData() for an alternative
	 */
	public byte[] encryptDataWithPrivateKey(byte[] buffer, int length) {
		try {
			Cipher cipher = Cipher.getInstance(TTT_ENCRYPTION_ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, privateKey);

			byte[] cipherData = cipher.doFinal(buffer,0, length);

			return cipherData;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	public byte [] decryptDataWithPrivateKey(byte[] data) {
		try {
			Cipher cipher = Cipher.getInstance(TTT_ENCRYPTION_ALGORITHM);
			
			cipher.init(Cipher.DECRYPT_MODE, privateKey);

			byte[] cipherData = cipher.doFinal(data);

			return cipherData;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}


	public byte[] encryptDataWithPrivateKey(byte[] bytes) {
		return encryptDataWithPrivateKey(bytes, bytes.length);
	}

}
