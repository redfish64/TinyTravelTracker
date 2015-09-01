package de.idyl.winzipaes;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Hack to ignore CRC value when reading input stream. Useful when it's inconvienent to calculate
 *
 */
public class CrcIgnoringZipInputStream extends ZipInputStream {

	private ZipEntry entry;
	private CRC32 crc = new CRC32();

	public CrcIgnoringZipInputStream(InputStream in) {
		super(in);
	}
	
	

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int res = super.read(b, off, len);
		
		if(res > 0)
		{
			crc.update(b,off,res);
			entry.setCrc(crc.getValue());
		}
		
		return res;
	}



	@Override
	public ZipEntry getNextEntry() throws IOException {
		crc.reset();
		return entry = super.getNextEntry();
	}

	
}
