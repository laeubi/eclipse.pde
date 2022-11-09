package org.eclipse.pde.internal.ui.bnd;

import aQute.bnd.osgi.Resource;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.resources.IFile;

public class FileResource implements Resource {

	private IFile file;

	private String extra;

	protected FileResource(IFile file) {
		this.file = file;
	}

	@Override
	public long lastModified() {
		return file.getLocalTimeStamp();
	}

	@Override
	public InputStream openInputStream() throws Exception {
		return file.getContents(true);
	}

	@Override
	public void write(OutputStream out) throws Exception {
		try (InputStream stream = openInputStream()) {
			stream.transferTo(out);
		}
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public void setExtra(String extra) {
		this.extra = extra;
	}

	@Override
	public String getExtra() {
		return extra;
	}

	@Override
	public long size() throws Exception {
		URI location = file.getLocationURI();
		if (location != null) {
			IFileStore store = EFS.getStore(location);
			if (store != null) {
				IFileInfo fetchInfo = store.fetchInfo();
				if (fetchInfo.exists()) {
					return fetchInfo.getLength();
				}
			}
		}
		return -1;
	}

	@Override
	public synchronized ByteBuffer buffer() throws Exception {
		return null;
	}

}
