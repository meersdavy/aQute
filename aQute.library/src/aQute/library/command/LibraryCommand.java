package aQute.library.command;

import java.io.*;
import java.text.*;
import java.util.*;

import aQute.lib.getopt.*;
import aQute.library.cache.*;
import aQute.library.remote.*;
import aQute.service.reporter.*;

public class LibraryCommand {
	final static SimpleDateFormat	date	= new SimpleDateFormat();
	final Reporter					main;
	final Formatter					out;
	final RemoteLibrary				library;
	final LibraryCache				cache;

	public LibraryCommand(Reporter reporter, Appendable out, LibraryCache cache, RemoteLibrary library) {
		this.main = reporter;
		this.out = new Formatter(out);
		this.cache = cache;
		this.library = library;
	}

	interface ListOptions extends Options {
		boolean revisions();
	}

	public void _list(ListOptions options) throws Exception {
		for (String program : cache.list(null)) {
			out.format("%-20s %s\n", program, options.revisions() ? cache.versions(program) : "");
		}
	}

	interface GetOptions extends Options {
		boolean staged();
	}

	public void _get(GetOptions options) throws Exception {
		for (String id : options._()) {
			String[] split = id.split("-");
			if (split.length != 2)
				main.error("Not a revision identifier %s", id);
			else {
				String bsn = split[0];
				String version = split[1];
				File f = (options.staged() ? cache.getStaged(bsn, version) : cache.getMaster(bsn, version)).get();
				if (f == null)
					main.error("No such revision %s", id);
				else
					out.format("%s\n", f.getAbsolutePath());
			}
		}
	}

	// / String description = program.description;
	// for (int i = 0; i < program.revisions.size() && description == null; i++)
	// description = program.revisions.get(i).description;
	//
	// main.out.printf("%-30s %20s %s %s\n", program._id,
	// date.format(program.modified), notNull(description),
	// notNull(program.vendor));
	// if (options.revisions()) {
	// for (RevisionRef ref : program.revisions) {
	// main.out.printf("  %-10s %c %s %s %s\n", ref.version.base, ref.master ?
	// 'M' : 'S',
	// Hex.toHexString(ref.sha), ref.url, notNull(ref.summary));
	// }
	//
	// }
}
