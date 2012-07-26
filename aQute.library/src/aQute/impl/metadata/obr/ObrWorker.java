package aQute.impl.metadata.obr;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;

import org.osgi.service.indexer.*;

import aQute.bnd.annotation.component.*;
import aQute.impl.metadata.obr.ObrWorker.*;
import aQute.lib.converter.*;
import aQute.lib.io.*;
import aQute.service.blobstore.*;
import aQute.service.filecache.*;
import aQute.service.library.*;
import aQute.service.library.Library.Revision;
import aQute.service.logger.*;
import aQute.service.rest.*;

@Component(properties = "process=obr", designateFactory = Config.class)
public class ObrWorker implements RevisionProcessor, ResourceManager {
	ReentrantReadWriteLock	lock	= new ReentrantReadWriteLock();
	FileCache				fileCache;
	BlobStore				blobStore;
	volatile Library		library;
	ResourceIndexer			indexer;
	File					home	= new File(System.getProperty("user.home"));
	File					obr		= new File(home, ".obr");
	File					file;
	RandomAccessFile		raf;
	String					etag;
	Messages				msgs;

	interface Messages extends LogMessages {
		ERROR fragmentAppend(Exception e, String _id, String etag);
	}

	interface Config {
		String file();

		int timeout();
	}

	Config	config;

	@Activate
	void activate(Map<String,Object> properties) throws Exception {
		try {
			this.config = Converter.cnv(Config.class, properties);
			if (this.config.file() == null)
				this.file = new File(obr, "index");
			else
				this.file = IO.getFile(obr, config.file());

			if (file.isDirectory())
				throw new IllegalStateException("The obr file must be a file, not a dir " + file);

			file.getParentFile().mkdirs();

			// blobStore.read("jpm.meta").ifModifiedSince(file.exists() ?
			// file.lastModified() : 0).to(file);

			this.raf = new RandomAccessFile(this.file, "rws");
			if (raf.length() > 0) {
				byte[] buffer = new byte[20];
				raf.readFully(buffer);
				etag = new String(buffer);
				raf.seek(raf.length());

				// TODO more sanity checks

				if (!etag.startsWith("OBRI"))
					raf.setLength(0);
			}
			if (raf.length() < 512) {
				initialize();
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void initialize() throws IOException {
		UUID uuid = UUID.randomUUID();
		etag = "OBRI" + uuid;
		raf.writeBytes(etag);
		raf.seek(512);
		raf.setLength(512);
	}

	@Deactivate
	void deactivate() throws InterruptedException {}

	@Override
	public boolean process(Library library, Revision revision) throws Exception {
		File file = fileCache.get(revision.url.toString(), revision.url);
		Set<File> set = new HashSet<File>();
		set.add(file);

		Map<String,String> config = new HashMap<String,String>();
		config.put(ResourceIndexer.PRETTY, "true");
		File fragment = File.createTempFile("obr", ".fragment");
		try {
			PrintWriter writer = IO.writer(fragment);
			try {
				indexer.indexFragment(set, writer, config);
				writer.flush();

				long oldLength = raf.length();
				FileOutputStream fout = new FileOutputStream(this.file, true);
				try {
					lock.writeLock().lock();
					try {
						IO.copy(fragment, fout);
						IO.copy(fragment, System.out);
						fout.close();
						return true;
					}
					finally {
						lock.writeLock().unlock();
					}
				}
				catch (Exception e) {
					msgs.fragmentAppend(e, revision._id, etag);
					raf.setLength(oldLength);
				}
				finally {
					fout.close();
				}
			}
			finally {
				writer.close();
			}
		}
		finally {
			fragment.delete();
		}
		return false;
	}

	public void optionObrReindex() throws Exception {
		reindex();
	}

	void reindex() throws Exception {
		lock.writeLock().lock();
		try {
			initialize();
			library.redo(null, "obr");
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	@Reference
	void setFileCache(FileCache cache) {
		this.fileCache = cache;
	}

	@Reference
	void setLog(Log log) {
		msgs = log.logger(Messages.class);
	}

	@Reference
	void setResourceIndexer(ResourceIndexer ri) {
		this.indexer = ri;
	}

	@Reference
	void setLibrary(Library lib) {
		this.library = lib;
	}

	@Reference
	void setBlobStore(BlobStore blobStore) {
		this.blobStore = blobStore;
	}

}
