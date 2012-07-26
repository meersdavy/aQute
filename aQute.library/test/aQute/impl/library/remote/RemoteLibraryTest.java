package aQute.impl.library.remote;

import java.io.*;
import java.util.*;

import junit.framework.*;
import aQute.impl.library.cache.*;
import aQute.lib.io.*;
import aQute.service.library.Library.Info;
import aQute.service.library.Library.Program;
import aQute.service.library.Library.Revision;
import aQute.util.data.*;

public class RemoteLibraryTest extends TestCase {

	public void testSimple() throws Exception {
		RemoteLibrary rl = new RemoteLibrary();
		rl.url("http://localhost:8080/rest/");
		Info info = rl.getInfo();
		assertNotNull(info);
		System.out.println(new data<Info>(info));

		for (Revision r : rl.findRevision()) {
			System.out.println(r._id);
		}
		for (Program r : rl.findProgram()) {
			System.out.println(r._id + " " + r.description);
		}

		Program p = rl.findProgram().query("aQute.libg").one();
		assertNotNull(p);

		assertEquals(1, rl.findRevision().version("2.7.2").count());
	}

	public void testSimpleCache() throws Exception {
		RemoteLibrary lib = new RemoteLibrary().url("http://localhost:8080/rest/");
		File tmp = new File("tmp");
		IO.delete(tmp);
		try {
			LibraryCache cache = new LibraryCache(lib, tmp);
			cache.open();

			SortedSet<String> list = cache.list(null);
			for (String bsn : list) {
				System.out.printf("%-20s %s\n", bsn, cache.versions(bsn));
			}
		}
		finally {
			IO.delete(tmp);
		}
	}
}
