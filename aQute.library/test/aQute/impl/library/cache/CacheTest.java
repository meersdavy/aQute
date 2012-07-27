package aQute.impl.library.cache;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import junit.framework.*;
import aQute.bnd.annotation.component.*;
import aQute.impl.library.*;
import aQute.impl.metadata.osgi.*;
import aQute.lib.io.*;
import aQute.library.cache.*;
import aQute.service.library.Library.Program;
import aQute.service.library.Library.Revision;
import aQute.test.dummy.ds.*;
import aQute.test.dummy.log.*;

public class CacheTest extends TestCase {
	File					base	= new File(System.getProperty("user.dir"));

	aQute.service.store.DB	mongo;
	LibraryImpl				lib;

	@Reference
	void setMongo(aQute.service.store.DB mongo) throws Exception {
		this.mongo = mongo;
	}

	@Reference
	void setLibrary(LibraryImpl lib) throws Exception {
		this.lib = lib;
	}

	public void setUp() throws Exception {
		DummyDS ds = new DummyDS();
		ds.add(this);
		ds.add("aQute.impl.store.mongo.MongoDBImpl").$("db", "test");
		ds.add(new DummyLog().direct().stacktrace());
		ds.add(new LibraryImpl());
		ds.add(new OSGiMetadataProvider());
		ds.add("aQute.impl.logger.LoggerImpl").$("logType", "CONSOLE");
		ds.add("aQute.impl.filecache.simple.SimpleFileCacheImpl");
		ds.add(new org.osgi.service.indexer.impl.BIndex2());
		ds.add(new Timer());
		ds.add(Executors.newFixedThreadPool(4));
		ds.wire();
		mongo.getStore(Revision.class, "library.program").all().remove();
		mongo.getStore(Program.class, "library.revision").all().remove();
	}

	public void testCache() throws Exception {
		File tmp = new File("tmp");
		IO.delete(tmp);
		try {
			LibraryCache cache = new LibraryCache(lib, tmp);
			cache.setTrace(true);
			assertTrue(cache.locks().isEmpty());

			cache.synchronize(); // library is empty
			List<String> versions = cache.versions("aQute.libg");
			assertTrue(versions.isEmpty());

			// stage a revision
			Revision v270 = lib.importer(new File("test/repo/aQute.libg-2.7.0.jar").toURI()).fetch();
			assertNotNull(v270);

			// Not synced yet, so should be empty
			versions = cache.versions("aQute.libg");
			assertTrue(versions.isEmpty());

			// Sync, so get a staged artifact
			cache.synchronize();
			assertNotNull(cache.getStaged("aQute.libg", "2.7.0").get());
			assertNull(cache.getMaster("aQute.libg", "2.7.0").get());

			// Staged artifacts do not show up in
			// the version list!
			versions = cache.versions("aQute.libg");
			assertEquals(0, versions.size());

			// Promote the artifact
			lib.master("aQute.libg", "2.7.0");

			// Not synced yet, so still is staged
			assertEquals(0, versions.size());
			assertNotNull(cache.getStaged("aQute.libg", "2.7.0").get());
			assertNull(cache.getMaster("aQute.libg", "2.7.0").get());

			// Now sync so we see the new master
			cache.synchronize();

			versions = cache.versions("aQute.libg");
			assertEquals(1, versions.size());

			// masters are visible as staged and master
			// otherwise we would force them to change all
			// the dependents
			assertNotNull(cache.getMaster("aQute.libg", "2.7.0").get());

			// The staged repo should also provide masters
			assertNotNull(cache.getStaged("aQute.libg", "2.7.0").get());

			// Import a new revision, make it a master
			Revision v271 = lib.importer(new File("test/repo/aQute.libg-2.7.1.jar").toURI()).fetch();
			assertNotNull(v271);
			lib.master("aQute.libg", "2.7.1");

			// make sure not visible
			versions = cache.versions("aQute.libg");
			assertEquals(1, versions.size());

			// get the new master (so now there are 2 masters)
			cache.synchronize();

			versions = cache.versions("aQute.libg");
			assertEquals(2, versions.size());

			assertTrue(versions.contains("2.7.0"));
			assertTrue(versions.contains("2.7.1"));

			// Dump our cache database (which will verify
			cache.initialize(true);
			versions = cache.versions("aQute.libg");
			assertEquals(2, versions.size());

			assertNotNull(cache.getMaster("aQute.libg", "2.7.0").get());
			assertNotNull(cache.getStaged("aQute.libg", "2.7.0").get());
			assertNotNull(cache.getMaster("aQute.libg", "2.7.1").get());
			assertNotNull(cache.getStaged("aQute.libg", "2.7.1").get());

			// Dump it again, now create a false file
			File f = IO.getFile(tmp, "masters/blurb/1.0.0/blurb-1.0.0.jar");
			f.getParentFile().mkdirs();
			IO.store("blurb", f);
			assertTrue(f.exists());
			cache.initialize(true);
			assertTrue(cache.check());
			assertFalse(f.exists());
			cache.verify(false);
			assertTrue(cache.check());

			cache.close();

			IO.delete(tmp);
			assertFalse(tmp.exists());
			LibraryCache cache2 = new LibraryCache(lib, tmp);
			cache2.setTrace(true);
			cache2.synchronize();
			versions = cache2.versions("aQute.libg");
			assertEquals(2, versions.size());
			assertNotNull(cache2.getMaster("aQute.libg", "2.7.0").get());
			assertNotNull(cache2.getStaged("aQute.libg", "2.7.0").get());
			assertNotNull(cache2.getMaster("aQute.libg", "2.7.1").get());
			assertNotNull(cache2.getStaged("aQute.libg", "2.7.1").get());
			cache2.close();
		}
		finally {
			IO.delete(tmp);
		}
	}

	public void test2Caches() throws Exception {
		File tmp = new File("tmp");
		IO.delete(tmp);
		try {
			LibraryCache a = new LibraryCache(lib, tmp);
			LibraryCache b = new LibraryCache(lib, tmp);
			a.synchronize();
			assertTrue(a.isOpen());
			assertFalse(b.isOpen());
			b.synchronize();
			assertTrue(b.isOpen());
			assertFalse(a.isOpen());

			Revision v270 = lib.importer(new File("test/repo/aQute.libg-2.7.0.jar").toURI()).fetch();
			assertNotNull(v270);
			lib.master("aQute.libg", "2.7.0");
			a.synchronize();
			assertTrue(a.isOpen());
			assertEquals(1, a.versions("aQute.libg").size());
			assertEquals(1, b.versions("aQute.libg").size());
			assertTrue(b.isOpen());
			assertFalse(a.isOpen());

		}
		finally {
			IO.delete(tmp);
		}
	}
}
