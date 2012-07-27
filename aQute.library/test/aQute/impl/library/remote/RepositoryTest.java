package aQute.impl.library.remote;

import java.util.*;

import junit.framework.*;
import aQute.bnd.service.RepositoryPlugin.Strategy;
import aQute.bnd.version.*;
import aQute.library.bnd.*;

public class RepositoryTest extends TestCase {

	public void testSimple() throws Exception {
		Repository repo = new Repository();
		Map<String,String> props = new HashMap<String,String>();
		props.put("url", "http://localhost:8080/rest/");
		repo.setProperties(props);

		System.out.println(repo.list(null));

		for (String bsn : repo.list(null)) {
			System.out.println(bsn + ": " + repo.versions(bsn));
			for (Version v : repo.versions(bsn)) {
				System.out.println(repo.get(bsn, v.toString(), Strategy.EXACT, null));
			}
		}
	}
}
