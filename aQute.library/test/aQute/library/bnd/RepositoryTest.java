package aQute.library.bnd;

import java.io.*;

import junit.framework.*;
import aQute.bnd.build.*;
import aQute.bnd.service.RepositoryPlugin.Strategy;
import aQute.lib.io.*;

public class RepositoryTest extends TestCase {

	public void testRepo() throws Exception {
		File tmp = new File("tmp");
		IO.delete(tmp);
		try {
			tmp.mkdirs();
			Workspace ws = Workspace.getWorkspace("test/ws");
			assertNotNull(ws);
			Project p1 = ws.getProject("p1");
			assertNotNull(p1);

			Repository r = ws.getPlugin(Repository.class);
			r.setCacheDir(tmp);
			r.refresh();
			assertTrue(r.cache.check());
			assertNotNull(r.cache.versions("aQute.libg"));
			assertFalse(r.cache.versions("aQute.libg").isEmpty());

			Container bundle = p1.getBundle("aQute.libg", "1.0.0", Strategy.HIGHEST, null);
			assertNotNull(bundle);
			assertNull(bundle.getError());
			System.out.println(bundle.getVersion());
			r.cache.close();
		}
		finally {
			IO.delete(tmp);
		}
	}
}
