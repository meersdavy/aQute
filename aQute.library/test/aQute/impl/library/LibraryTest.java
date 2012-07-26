package aQute.impl.library;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import junit.framework.*;
import aQute.bnd.annotation.component.*;
import aQute.impl.metadata.osgi.*;
import aQute.lib.io.*;
import aQute.service.library.Library.Program;
import aQute.service.library.Library.Revision;
import aQute.service.library.Library.RevisionRef;
import aQute.test.dummy.ds.*;
import aQute.test.dummy.log.*;

public class LibraryTest extends TestCase {
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

	public void testSimple() throws Exception {
		assertNotNull(lib);
		final File v270 = IO.getFile(base, "test/repo/aQute.libg-2.7.0.jar");
		Revision r270 = lib.importer(v270.toURI()).fetch();

		assertNotNull("Should have created a revision 270", r270);
		assertEquals(r270.bsn, "aQute.libg");
		assertEquals(r270.version.base, "2.7.0");

		final File v271 = IO.getFile(base, "test/repo/aQute.libg-2.7.1.jar");

		Revision r271 = lib.importer(v271.toURI()).fetch();
		assertNotNull("Should have created a revision 271", r271);
		assertEquals(r271.bsn, "aQute.libg");
		assertEquals(r271.version.base, "2.7.1");

		Program program = lib.getProgram("aQute.libg");
		assertNotNull(program);
		assertEquals(2, program.revisions.size());
		RevisionRef x270 = program.revisions.get(1);
		RevisionRef x271 = program.revisions.get(0);

		assertNotNull(x270);
		assertEquals("aQute.libg", x270.bsn);
		assertEquals("2.7.0", x270.version.base);
		assertNotNull(x271);
		assertEquals("aQute.libg", x271.bsn);
		assertEquals("2.7.1", x271.version.base);
	}
}
