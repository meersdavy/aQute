package aQute.service.library;

import java.io.*;
import java.net.*;
import java.util.*;

import aQute.service.reporter.*;

public interface Library {
	public enum PackageType {
		IMPORT, EXPORT, PRIVATE
	};

	class Version {
		public String	base;
		public String	qualifier;
		public String	classifier;
		public String	original;
	}

	public class Revision {
		public String				_id;												// SHA
																						// of
																						// file
		public String				receipt;											// Importer
																						// receipt
		public URI					url;
		public String				sha;
		public String				bsn;
		public Version				version;
		public boolean				updated;
		public String				tag;
		public boolean				master;
		public long					insertDate;
		public URI					docUrl;
		public String				vendor;
		public String				description;
		public List<License>		licenses;
		public SCM					scm;
		public List<Developer>		developers;
		public List<Developer>		contributors;
		public Set<String>			category;
		public String				summary;
		public List<PackageDef>		packages	= new ArrayList<Library.PackageDef>();
		public Map<String,Object>	metadata	= new HashMap<String,Object>();
		public URI					icon;
		public String				message;
		public String				owner;												// email
		public Map<String,Object>	__extra;
	}

	public class PackageDef {
		public String		name;
		public String		version;	// range or version
		public PackageType	type;
		public Set<String>	uses;
	}

	public class Dependency {
		public String	bsn;
		public String	range;

	}

	public class License {
		public String				name;
		public String				description;
		public URI					link;
		public Map<String,Object>	__extra;
	}

	public class SCM {
		public String				connection;
		public String				developerConnection;
		public URI					url;
		public Map<String,Object>	__extra;
	}

	public class Developer {
		public String				id;
		public String				name;
		public String				email;
		public Map<String,Object>	__extra;
	}

	public class OrganizationRef {
		public String				organizationId;
		public String				name;
		public URI					url;
		public Map<String,Object>	__extra;
	}

	class RevisionRef {

		public RevisionRef() {}

		public RevisionRef(Revision revision) {
			this.revision = revision._id;
			this.bsn = revision.bsn;
			this.url = revision.url;
			this.master = revision.master;
			this.version = revision.version;
			this.summary = revision.summary;
			this.tag = revision.tag;
			this.updated = revision.updated;
		}

		public URI		url;
		public String	bsn;
		public Version	version;
		public String	revision;
		public String	tag;
		public boolean	master;
		public String	release;
		public String	summary;
		public boolean	updated;
	}

	class Program {
		// The _id must be the bsn (which is repeated)
		public String				_id;
		public String				mailingList;
		public String				scm;
		public List<RevisionRef>	revisions	= new ArrayList<Library.RevisionRef>();
		public URI					logo;
		public URI					docUrl;
		public String				vendor;
		public String				description;
		public URI					icon;
		public String				lastImport;
		public List<RevisionRef>	history		= new ArrayList<RevisionRef>();
		public Map<String,Object>	__extra;
	}

	Program getProgram(String bsn) throws Exception;

	Iterable< ? extends Program> find(String where, int start, int limit) throws Exception;

	// void master(RevisionRef rev);

	Revision getRevision(String bsn, String version) throws Exception;

	interface Importer extends Report {
		Revision fetch() throws Exception;

		Importer owner(String email);

		Importer message(String msg);

		File getFile() throws Exception;

		URI getURL();

		boolean isDuplicate();

		/**
		 * If the caller has a truly unique id (like a SHA or MD5) for the file
		 * to parse then this can be given as a unique receipt. The library must
		 * not import a revision if it already contains a revision with this
		 * receipt. The importer will have the {@link #isDuplicate()} flag set
		 * if there was no import.
		 * 
		 * @param A
		 *            unique id for the imported file targeted by the URL.
		 * @return this
		 */
		Importer receipt(String unique);

		Importer trace(boolean b);

		Importer rescan();
	}

	Importer importer(URI url) throws Exception;

	Revision rescan(String bsn, String version) throws Exception;
}
