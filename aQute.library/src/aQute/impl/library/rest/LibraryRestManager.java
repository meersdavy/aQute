package aQute.impl.library.rest;

import java.net.*;
import java.util.regex.*;

import javax.servlet.http.*;

import aQute.bnd.annotation.component.*;
import aQute.lib.json.*;
import aQute.service.library.*;
import aQute.service.library.Library.Find;
import aQute.service.library.Library.Info;
import aQute.service.library.Library.Program;
import aQute.service.library.Library.Revision;
import aQute.service.rest.*;
import aQute.service.user.*;

/**
 * This class represents a libary on the web. It provides a convenient REST
 * interface.
 */
@Component
public class LibraryRestManager implements ResourceManager {
	static JSONCodec	codec	= new JSONCodec();

	Library				library;

	/**
	 * Options for /rest/program
	 */
	interface GetOptions extends Options {
		enum Type {
			one, first, iterator, count;
		}

		/**
		 * The type of the request: one, first, iterator, or count
		 * 
		 * @return the type of the request
		 */
		Type type();

		/**
		 * @return Number of records to return
		 */
		int limit();

		/**
		 * @return A free search query. Supports prefixes like bsn:, package:
		 *         etc.
		 */
		String query();

		/**
		 * @return Number of records to skip, for batches
		 */
		int skip();

		/**
		 * @return Field sort order, a prefix + indicates ascending, a -
		 *         indicates descending.
		 */
		String[] order();

		/**
		 * @return a where clause, may be complex
		 */
		String[] where();

	}

	public Object getProgram(GetOptions o) throws Exception {
		Find< ? extends Program> find = library.findProgram();
		return option2find(o, find);
	}

	public Program getProgram(String bsn) throws Exception {
		return library.getProgram(bsn);
	}

	public Object getRevision(GetOptions options) throws Exception {
		Find< ? extends Revision> find = library.findRevision();
		return option2find(options, find);
	}

	private Object option2find(GetOptions o, Find< ? > find) throws Exception {
		find.limit(o.limit()).skip(o.skip());

		if (o.order() != null)
			for (String s : o.order())
				if (s.startsWith("+"))
					find.ascending(s.substring(1));
				else if (s.startsWith("-"))
					find.descending(s.substring(1));
				else
					find.ascending(s);

		if (o.where() != null)
			for (String s : o.where())
				find.where(s);

		if (o.query() != null)
			find.query(o.query());

		GetOptions.Type type = o.type();
		if (type == null)
			type = GetOptions.Type.iterator;

		Object result = null;
		switch (type) {
			case one :
				result = find.one();
				break;

			case first :
				result = find.first();
				break;
			case count :
				result = find.count();
				break;
			case iterator :
				result = find;
				break;
		}

		return result;
	}

	public Revision getProgramRevision(String bsn, String version) throws Exception {
		return library.getRevision(bsn, version);
	}

	interface OptionOptions extends Options {
		public enum Cmds {
			rescan, master, delete
		}

		Cmds cmd();
	}

	public Revision optionProgramRevision(OptionOptions opts, String bsn, String version) throws Exception {
		// Authorize?
		if (opts._user() == null)
			throw new SecurityException("Not authorized");

		switch (opts.cmd()) {
			case rescan :
				return library.rescan(bsn, version);
			case master :
				return library.master(bsn, version);
			case delete :
				return library.delete(bsn, version);
		}

		return null;
	}

	interface postProgramOptions extends Options {
		User user();
	}

	public void postProgram(Options opts) throws Exception {
		if (opts._user() == null)
			throw new SecurityException("Must be logged in");

		Program program = codec.dec().from(opts._request().getInputStream()).get(Program.class);

		// TODO security check

		library.update(program);
	}

	Pattern	JARFILE	= Pattern.compile("([\\d\\w_+.-]+)-(\\d+\\.\\d+\\.\\d+).jar");

	public void getRevision(Options options, String name) throws Exception {
		Matcher m = JARFILE.matcher(name);
		if (!m.matches())
			throw new IllegalArgumentException("Invalid name " + name + ", must match " + JARFILE);

		Revision revision = library.getRevision(m.group(1), m.group(2));
		if (revision == null)
			throw new IllegalArgumentException("No such revision: " + revision);

		URI uri = revision.url;
		if (uri == null)
			throw new IllegalArgumentException("No such revision (have no url for it): " + revision);

		HttpServletResponse rsp = options._response();
		rsp.sendRedirect(uri.toString());
		rsp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
	}

	@Reference
	void setLibrary(Library lib) {
		this.library = lib;
	}

	public Info getLibrary() throws Exception {
		return library.getInfo();
	}
}
