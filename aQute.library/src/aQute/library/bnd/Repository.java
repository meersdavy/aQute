package aQute.library.bnd;

import java.io.*;
import java.net.*;
import java.util.*;

import aQute.bnd.osgi.*;
import aQute.bnd.service.*;
import aQute.bnd.version.*;
import aQute.lib.collections.*;
import aQute.lib.converter.*;
import aQute.lib.io.*;
import aQute.library.cache.*;
import aQute.library.remote.*;
import aQute.service.reporter.*;

/**
 * A bnd repository based upon the Library Cache.
 */
public class Repository implements RepositoryPlugin, Plugin, Closeable, Refreshable {
	RemoteLibrary	library;
	LibraryCache	cache;
	File			dir	= null;

	interface Options {
		URI url();

		String cacheDir();

		boolean init();

		boolean refresh();

		boolean includeStaged();

		String name();
	}

	Options		options;
	Reporter	reporter;

	@Override
	public File get(String bsn, String range, Strategy strategy, Map<String,String> attrs) throws Exception {
		if (attrs != null && attrs.containsKey("staged"))
			return getCache().getStaged(bsn, range).get();

		if (strategy == Strategy.EXACT)
			return getCache().getMaster(bsn, range).get();

		List<Version> versions = versions(bsn);
		if (versions == null)
			return null;

		VersionRange r = new VersionRange(range);
		Iterable<Version> filter = r.filter(versions);
		SortedList<Version> sl = new SortedList<Version>((Collection< ? extends Comparable< ? >>) filter);
		if (sl.isEmpty())
			return null;

		if (strategy == Strategy.HIGHEST)
			return getCache().getMaster(bsn, sl.last().toString()).get();
		else
			return getCache().getMaster(bsn, sl.first().toString()).get();
	}

	private LibraryCache getCache() {
		try {
			if (cache != null)
				return cache;

			library = new RemoteLibrary();
			if (options != null && options.url() != null)
				library.url(options.url().toString());

			File cacheDir = dir;
			if (options != null && options.cacheDir() != null)
				cacheDir = IO.getFile(options.cacheDir());
			cache = new LibraryCache(library, cacheDir);
			cache.setIncludeStaged(options.includeStaged());
			return cache;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean canWrite() {
		return false;
	}

	@Override
	public File put(Jar jar) throws Exception {
		throw new UnsupportedOperationException("put");
	}

	@Override
	public List<String> list(String regex) throws Exception {
		return new ArrayList<String>(getCache().list(regex));
	}

	@Override
	public List<Version> versions(String bsn) throws Exception {
		return Converter.cnv(new TypeReference<List<Version>>() {}, getCache().versions(bsn));
	}

	@Override
	public String getName() {
		return options.name() == null ? "JPM" : options.name();
	}

	@Override
	public String getLocation() {
		return getCache().getRoot().getAbsolutePath();
	}

	@Override
	public void setProperties(Map<String,String> map) {
		try {
			options = Converter.cnv(Options.class, map);
		}
		catch (Exception e) {
			if (reporter != null)
				reporter.exception(e, "Creating options");
			else
				e.printStackTrace();
		}
	}

	@Override
	public void setReporter(Reporter processor) {
		reporter = processor;
	}

	public void close() throws IOException {
		if (cache != null)
			try {
				cache.close();
			}
			catch (Exception e) {
				throw new IOException(e);
			}
	}

	@Override
	public boolean refresh() {
		try {
			getCache().synchronize();
			return cache.isOk();
		}
		catch (Exception e) {
			if (reporter != null)
				reporter.exception(e, "Trying to synchronize");

			throw new RuntimeException(e);
		}
	}

	@Override
	public File getRoot() {
		return getCache().getRoot();
	}

	public void setCacheDir(File dir) {
		this.dir = dir;
	}

}
