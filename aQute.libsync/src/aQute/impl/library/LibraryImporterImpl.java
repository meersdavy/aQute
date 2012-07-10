package aQute.impl.library;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import aQute.impl.library.LibraryImpl.ProgramImpl;
import aQute.impl.library.LibraryImpl.RevisionImpl;
import aQute.lib.io.*;
import aQute.libg.cryptography.*;
import aQute.libg.reporter.Messages.ERROR;
import aQute.libg.reporter.*;
import aQute.service.library.*;
import aQute.service.library.Library.Importer;
import aQute.service.library.Library.Revision;
import aQute.service.library.Library.RevisionRef;
import aQute.service.reporter.*;

// TODO lock import name

public class LibraryImporterImpl extends ReporterAdapter implements Library.Importer {
	final LibraryImpl		parent;
	String					receipt;
	Callable<InputStream>	getter;
	boolean					existed;
	String					path;
	final URI				url;
	File					file;
	boolean					duplicate;

	interface ImporterMessages {

		ERROR CouldNotImport_(String uniqueId);

		ERROR AlreadyImported_(String uniqueId);

		ERROR Revision_ExistsAsMaster(String id);

		ERROR Revision_EqualsSameVersion_AsAlreadyImportedFrom_(String bsn, String version, String url);

		ERROR Revision_OlderVersion_AsAlreadyImportedFrom_(String bsn, String version, URL url);

		ERROR RequiredField_IsNull(String name);

		ERROR InvalidField_Value_Expected(String name, Object field, Pattern pattern);

	}

	final ImporterMessages	messages	= ReporterMessages.base(this, ImporterMessages.class);
	String					owner;
	String					message;
	private boolean			rescan;

	public LibraryImporterImpl(LibraryImpl parent, URI url) {
		this.parent = parent;
		this.url = url;
	}

	public Revision fetch() throws Exception {
		if (!rescan) {
			// If the caller set a receipt (a unique id)
			// we see if we already imported this.

			if (receipt != null) {
				RevisionImpl prior = parent.revisions.find("receipt=%s", receipt).one();
				if (prior != null) {
					duplicate = true;
					return prior;
				}
			}
		}

		File file = getFile();
		String sha = digest(file);

		// if the caller has a unique receipt than we can
		// use it to find a prior import. Obviously this
		// receipt must be truly unique, like a SHA or so.

		RevisionImpl revision = parent.revisions.find("sha=%s", sha).one();
		if (revision != null && !rescan) {
			duplicate = true;
			return revision;
		}

		revision = new RevisionImpl();
		revision.receipt = receipt;
		revision.url = url;
		revision.sha = sha;
		revision.owner = owner == null ? "rescan" : owner;
		revision.message = message;

		for (MetadataProvider md : parent.mdps) {
			trace("parsing %s with %s", revision.url, md);
			Report report = md.parser(LibraryImporterImpl.this, revision);
			getInfo(report);
		}

		verify(revision);

		if (!isOk())
			return null;

		revision.insertDate = System.currentTimeMillis();
		revision._id = getId(revision);

		// TODO classifier
		RevisionImpl previous = parent.revisions.find("_id=%s", revision._id).one();
		if (previous != null) {
			if (previous.master) {
				messages.Revision_ExistsAsMaster(revision._id);
				return null;
			}
			parent.revisions.update(revision); // TODO what to do with previous
		} else
			parent.revisions.insert(revision);

		ProgramImpl program = parent.programs.find("_id=%s", revision.bsn).one();
		RevisionRef reference = new RevisionRef(revision);

		if (program == null) {
			program = new ProgramImpl();
			program._id = revision.bsn;
			program.revisions.add(reference);
			parent.programs.insert(program);
		} else {
			Iterator<RevisionRef> i = program.revisions.iterator();
			while (i.hasNext()) {
				RevisionRef ref = i.next();
				if (isRef(ref, revision)) {
					i.remove();
					program.history.add(ref);
				}
			}
			program.revisions.add(0, reference);
			program.lastImport = reference.revision;

			int n = parent.programs //
					.find(program) //
					.set("revisions") //
					.set("lastImport") //
					.set("history") //
					.update();
			if (n != 1)
				error("Could not update program %s in database, count was %d", program._id, n);
		}
		return revision;
	}

	private boolean isRef(RevisionRef next, RevisionImpl revision) {
		return next.revision.equals(revision._id);
	}

	private String getId(RevisionImpl revision) {
		return revision.bsn + "-" + revision.version.base;
	}

	private String digest(File file) throws Exception {
		Digester<SHA1> digester = SHA1.getDigester();
		IO.copy(file, digester);

		return digester.digest().asHex();
	}

	public File getFile() throws Exception {

		return parent.fileCache.get(url.toString(), url);
	}

	/**
	 * Verify that all necessary fields are set, have the proper format etc.
	 * 
	 * @param rev
	 */
	private void verify(Revision rev) {
		check(rev.bsn, "bsn", aQute.bnd.osgi.Verifier.SYMBOLICNAME);
		if (rev.version == null) {
			messages.RequiredField_IsNull("version");
			return;
		}
		check(rev.version.base, "version.base", aQute.bnd.osgi.Verifier.VERSION);
	}

	private void check(String field, String name, Pattern pattern) {
		if (field == null) {
			messages.RequiredField_IsNull(name);
			return;
		}
		if (pattern.matcher(field).matches())
			return;

		messages.InvalidField_Value_Expected(name, field, pattern);
	}

	@Override
	public Importer owner(String email) {
		owner = email;
		return this;
	}

	@Override
	public Importer message(String message) {
		this.message = message;
		return this;
	}

	@Override
	public URI getURL() {
		return url;
	}

	@Override
	public boolean isDuplicate() {
		return duplicate;
	}

	@Override
	public Importer receipt(String string) {
		this.receipt = string;
		return this;
	}

	@Override
	public Importer trace(boolean trace) {
		setTrace(trace);
		return this;
	}

	@Override
	public Importer rescan() {
		rescan = true;
		return this;
	}
}
