package aQute.impl.library;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import aQute.bnd.annotation.component.*;
import aQute.impl.library.LibraryImpl.Config;
import aQute.lib.converter.*;
import aQute.service.filecache.*;
import aQute.service.library.*;
import aQute.service.logger.*;
import aQute.service.store.*;
import aQute.util.data.*;

@Component(designateFactory = Config.class)
public class LibraryImpl implements Library {
	WeakHashMap<String,ProgramImpl>	cache			= new WeakHashMap<String,LibraryImpl.ProgramImpl>();
	public static int				SEQUENCE_RANGE	= 100;
	static Pattern					QUERY			= Pattern.compile("((bsn|description|cat|jpm):)?([\\w\\d\\.]+)");

	public static class LibraryData extends Info {
		public String	_id	= "librarydata";
	}

	Store<ProgramImpl>		programs;
	Store<RevisionImpl>		revisions;
	Store<LibraryData>		library;
	FileCache				fileCache;
	List<MetadataProvider>	mdps			= new CopyOnWriteArrayList<MetadataProvider>();
	Crawler					crawler;
	Log						logger;
	Messages				msgs;
	int						currentSequence	= 10;
	int						maxSequence		= SEQUENCE_RANGE;
	LibraryData				libraryData;

	interface Messages extends LogMessages {

	}

	interface Config {
		boolean trace();

		Set<String> processes();
	}

	Config	config;

	@Activate
	void activate(Map<String,Object> p) throws Exception {
		this.config = Converter.cnv(Config.class, p);
		this.crawler = new Crawler(logger, this, revisions);
		this.crawler.start();
		libraryData = new LibraryData();

		// Ensure we have out configuration data
		try {
			libraryData.generation = UUID.randomUUID().toString();
			libraryData.name = "Untitled";
			library.insert(libraryData);
		}
		catch (Exception e) {
			libraryData = library.all().one();
		}
	}

	@Deactivate
	void deactivate() throws InterruptedException {
		this.crawler.interrupt();
		this.crawler.join();
	}

	static public class ProgramImpl extends Program {

		public ProgramImpl(Program program) throws Exception {
			data.assign(program, this);
		}

		public ProgramImpl() {}

		public boolean upsert(RevisionRef ref) {
			boolean upsert = revisions.remove(ref);
			revisions.add(ref);
			return upsert;
		}
	}

	static public class RevisionImpl extends Revision {

		// public static final String _type = "revision";

	}

	public ProgramImpl getProgram(String bsn) throws Exception {
		return findProgram().bsn(bsn).one();
	}

	String stagingName(Revision rev) {
		return "staging/" + filename(rev);
	}

	String masterName(Revision rev) {
		return "master/" + filename(rev);
	}

	String filename(Revision rev) {
		return rev.bsn + "/" + rev.version.base + "/" + rev.bsn + "-" + rev.version.base + ".jar";
	}

	void unsetMetadataProviders(MetadataProvider mdp) {
		mdps.remove(mdp);
	}

	@Override
	public RevisionImpl getRevision(String bsn, String version) throws Exception {
		return findRevision().bsn(bsn).version(version).one();
	}

	@Override
	public Importer importer(URI url) throws Exception {
		return new LibraryImporterImpl(this, url);
	}

	@Override
	public Revision rescan(String bsn, String version) throws Exception {
		Revision rev = getRevision(bsn, version);
		if (rev == null)
			return null;

		return importer(rev.url).message("rescan").rescan().fetch();
	}

	@Override
	public void update(Program program) throws Exception {
		ProgramImpl p = new ProgramImpl(program);
		programs.update(p);
	}

	@Override
	public void redo(String where, String process) throws Exception {
		if (where == null)
			where = "_id=*";
		revisions.find(where).append("process", process).update();
	}

	@Override
	public Revision master(String bsn, String version) throws Exception {
		RevisionImpl rev = getRevision(bsn, version);
		if (rev == null)
			return null;
		ProgramImpl program = getProgram(bsn);

		rev.master = true;
		rev.modified = System.currentTimeMillis();

		for (RevisionRef ref : program.revisions) {
			if (ref.url.equals(rev.url)) {
				program.revisions.remove(ref);
				break;
			}
		}
		RevisionRef ref = new RevisionRef(rev);
		program.revisions.add(0, ref);
		revisions.find(rev).set("master", true).set("modified", rev.modified).update();
		programs.find(program).set("revisions", program.revisions).update();
		masterUpdates();
		return rev;
	}

	@Override
	public Revision delete(String bsn, String version) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * References
	 */
	@Reference
	void setStore(DB db) throws Exception {
		programs = db.getStore(ProgramImpl.class, "library.program");
		revisions = db.getStore(RevisionImpl.class, "library.revision");
		library = db.getStore(LibraryData.class, "library.data");
	}

	@Reference
	void setLog(Log log) throws Exception {
		logger = log;
		msgs = log.logger(Messages.class);
	}

	@Reference(type = '*')
	void setMetadataProviders(MetadataProvider mdp) {
		mdps.add(mdp);
	}

	@Reference
	void setFileCache(FileCache cache) {
		this.fileCache = cache;
	}

	@Reference(type = '*')
	void addRevisionProcessor(RevisionProcessor rp, Map<String,Object> props) throws Exception {
		crawler.addProcessor((String) props.get("process"), rp);
	}

	void removeRevisionProcessor(RevisionProcessor rp) throws Exception {
		crawler.removeProcessor(rp);
	}

	class FindImpl<T> implements Find<T> {
		Cursor<T>	cursor;
		boolean		program;
		int			generation	= -1;

		public FindImpl(Cursor<T> cursor, boolean program) {
			this.cursor = cursor;
			this.program = program;
		}

		@Override
		public Find<T> skip(int n) throws Exception {
			cursor.skip(n);
			return this;
		}

		@Override
		public Find<T> limit(int n) throws Exception {
			cursor.limit(n);
			return this;
		}

		@Override
		public Find<T> ascending(String field) throws Exception {
			cursor.ascending(field);
			return this;
		}

		@Override
		public Find<T> descending(String field) throws Exception {
			cursor.descending(field);
			return this;
		}

		@Override
		public Find<T> where(String field, Object... args) throws Exception {
			cursor.where(field, args);
			return this;
		}

		@Override
		public T one() throws Exception {
			return cursor.one();
		}

		@Override
		public T first() throws Exception {
			return cursor.first();
		}

		@Override
		public int count() throws Exception {
			return cursor.count();
		}

		@Override
		public Iterator<T> iterator() {
			return cursor.iterator();
		}

		@Override
		public Find<T> bsn(String bsn) throws Exception {
			if (program)
				return where("_id=%s", bsn);
			else
				return where("bsn=%s", bsn);
		}

		@Override
		public Find<T> version(String version) throws Exception {
			assert !program;
			return where("version.base=%s", version);
		}

		@Override
		public Find<T> from(long date) throws Exception {
			return where("insertDate>=%s", date);
		}

		@Override
		public Find<T> until(long date) throws Exception {
			return where("insertDate<%s", date);
		}

		@Override
		public Find<T> query(String q) throws Exception {
			assert q != null;
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
			return where(q);
		}

	}

	@Override
	public FindImpl<RevisionImpl> findRevision() throws Exception {
		return new FindImpl<RevisionImpl>(revisions.all(), false);
	}

	@Override
	public FindImpl<ProgramImpl> findProgram() throws Exception {
		return new FindImpl<ProgramImpl>(programs.all(), true);
	}

	@Override
	public Info getInfo() throws Exception {
		return library.all().first();
	}

	void updates() throws Exception {
		library.find(libraryData).inc("updates", 1).update();
	}

	void masterUpdates() throws Exception {
		library.find(libraryData).inc("masterUpdates", 1).inc("updates", 1).update();
	}

}
