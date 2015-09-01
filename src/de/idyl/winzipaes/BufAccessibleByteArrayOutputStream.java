package de.idyl.winzipaes;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class BufAccessibleByteArrayOutputStream extends ByteArrayOutputStream {

	public BufAccessibleByteArrayOutputStream() {
		super();
	}

	public BufAccessibleByteArrayOutputStream(int size) {
		super(size);
	}
	
	public byte [] getBuf()
	{
		return super.buf;
	}
	
}
