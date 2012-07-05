package aQute.impl.library;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.regex.*;

import aQute.impl.library.LibraryImpl.ProgramImpl;
import aQute.impl.library.LibraryImpl.RevisionImpl;
import aQute.lib.data.*;
import aQute.lib.io.*;
import aQute.libg.cryptography.*;
import aQute.libg.reporter.Messages.ERROR;
import aQute.libg.reporter.Messages.WARNING;
import aQute.libg.reporter.*;
import aQute.service.library.*;
import aQute.service.library.Library.Importer;
import aQute.service.library.Library.Revision;
import aQute.service.library.Library.RevisionRef;
import aQute.service.reporter.*;
import aQute.service.store.*;

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

		ERROR Revision__ExistsAsMaster(String bsn, String version);

		ERROR Revision_EqualsSameVersion_AsAlreadyImportedFrom_(String bsn, String version, String url);

		ERROR Revision_OlderVersion_AsAlreadyImportedFrom_(String bsn, String version, URL url);

		WARNING AlreadyImported_(URI url);

		ERROR RequiredField_IsNull(String name);

		ERROR InvalidField_Value_Expected(String name, Object field, Pattern pattern);

	}

	final ImporterMessages	messages	= ReporterMessages.base(this, ImporterMessages.class);
	String					owner;
	String					message;

	public LibraryImporterImpl(LibraryImpl parent, URI url) {
		this.parent = parent;
		this.url = url;
	}

	public Revision fetch() throws Exception {
		File file = getFile();
		String sha = digest(file);

		Cursor<RevisionImpl> cursor;
		if (receipt != null) {
			// if the caller has a unique receipt than we can
			// use it to find a prior import. Obviously this
			// receipt must be truly unique, like a SHA or so.
			cursor = parent.revisions.find("|(_id=%s)(receipt=%s)", sha, receipt);
		} else {
			cursor = parent.revisions.find("_id=%s", sha);
		}
		RevisionImpl revision = cursor.one();
		if (revision != null) {
			duplicate = true;
			messages.AlreadyImported_(url);
			return revision;
		}

		revision = new RevisionImpl();
		revision._id = sha;
		revision.url = url;
		revision.owner = owner;
		revision.message = message;

		for (MetadataProvider md : parent.mdps) {
			trace("parsing %s with %s", revision.url, md);
			Report report = md.parser(LibraryImporterImpl.this, revision);
			getInfo(report);
		}

		verify(revision);

		if (!isOk())
			return null;

		// TODO classifier
		RevisionImpl previous = parent.revisions
				.find("&(bsn=%s)(version.base=%s)", revision.bsn, revision.version.base).one();
		if (previous != null && previous.master) {
			messages.Revision__ExistsAsMaster(revision.bsn, revision.version.base);
			return null;
		}

		parent.revisions.insert(revision);

		ProgramImpl program = parent.programs.find("_id=%s", revision.bsn).one();
		RevisionRef reference = new RevisionRef(revision);

		if (program == null) {
			program = new ProgramImpl();
			program._id = revision.bsn;
			data.assignIfNotSet(revision, program, "docUrl", "vendor", "description", "icon");
			program.revisions.add(reference);
			parent.programs.insert(program);
		} else {
			program.revisions.remove(reference);
			program.revisions.add(0, reference);
			program.lastImport = reference.revision;

			int n = parent.programs //
					.find(program) //
					.set("revisions", program.revisions) //
					.set("lastImport", program.lastImport) //
					.update();
			if (n != 1)
				error("Could not update program %s in database, count was %d", program._id, n);
		}
		return revision;
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
		check(rev.bsn, "bsn", aQute.lib.osgi.Verifier.SYMBOLICNAME);
		if (rev.version == null) {
			messages.RequiredField_IsNull("version");
			return;
		}
		check(rev.version.base, "version.base", aQute.lib.osgi.Verifier.VERSION);
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
}
