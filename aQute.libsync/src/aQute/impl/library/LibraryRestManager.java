package aQute.impl.library;

import java.util.regex.*;

import aQute.bnd.annotation.component.*;
import aQute.service.library.*;
import aQute.service.library.Library.Program;
import aQute.service.library.Library.Revision;
import aQute.service.rest.*;

@Component
public class LibraryRestManager implements ResourceManager {
	Library			library;
	static Pattern	QUERY	= Pattern.compile("((bsn|description|cat|jpm):)?([\\w\\d\\.]+)");

	interface POptions extends Options {
		int limit();

		String query();

		int start();
	}

	public Iterable< ? extends Program> getProgram(POptions o) throws Exception {
		String q = o.query();
		if (q == null)
			q = "*";
		else {
			StringBuilder sb = new StringBuilder("(&");
			String parts[] = q.split("\\s+");
			for (String p : parts) {
				Matcher m = QUERY.matcher(p);
				if (m.matches()) {
					String type = m.group(1);
					String word = m.group(3);
					if (type != null) {
						sb.append("(").append(type).append("=*").append(word).append("*)");
					} else {
						sb.append("(|(_id=*").append(word).append("*)").append("(description=*").append(word)
								.append("*)").append("(name=*").append(word).append("*)").append("(category=*")
								.append(word).append("*))");
					}
				}
			}
			sb.append(")");
			q = sb.toString();
		}
		return library.find(q, o.start(), o.limit());
	}

	public Program getProgram(String bsn) throws Exception {
		return library.getProgram(bsn);
	}

	public Revision getProgramRevision(String bsn, String version) throws Exception {
		return library.getRevision(bsn, version);
	}

	interface OptionOptions extends Options {
		String cmd();
	}

	public Revision optionProgramRevision(OptionOptions opts, String bsn, String version) throws Exception {
		// Authorize?
		if (opts._user() == null)
			throw new SecurityException("Not authorized to rescan");
		return library.rescan(bsn, version);
	}

	@Reference
	void setLibrary(Library lib) {
		this.library = lib;
	}
}
