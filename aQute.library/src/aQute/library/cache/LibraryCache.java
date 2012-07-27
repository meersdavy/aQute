package aQute.library.cache;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import jdbm.*;
import aQute.lib.collections.*;
import aQute.lib.io.*;
import aQute.lib.json.*;
import aQute.libg.cryptography.*;
import aQute.libg.glob.*;
import aQute.libg.reporter.*;
import aQute.service.library.*;
import aQute.service.library.Library.Find;
import aQute.service.library.Library.Info;
import aQute.service.library.Library.Revision;
import aQute.service.reporter.*;

/**
 * A Library Cache maintains a local cache in a directory of a (potentially
 * remote) Library. It tries to effectively synchronize with the Library on its
 * meta data and uses its knowledge to optimize downloads. The cache is designed
 * to be used by different processes. It will not lock the cache if it is open,
 * it only locks it for write operations. The read operation check if the cache
 * has been modified and if so, will refresh the memory cache.
 * <p/>
 * This is a complicated beast. It must efficiently synchronize to the
 * repository (or I'll be poor quickly) but it must also be usable between
 * different processes on the same machine, sharing the same directory. In
 * general, there will be one primary process (Eclipse likely) that holds the
 * lock the majority of time. However, one can run multiple eclipses and JPM and
 * ant are also accessing the same repo. However, the assumption is that a
 * single process is the primary holder. Additionally
 * <p/>
 * The basic assumption is that the cache is first protected by a local lock for
 * different threads. This is a reentrant lock managed by {@link #begin()} and
 * {@link #end()}. The code executed between begin and and end should be short
 * lived. The public methods all call begin/end pairwise. Though begin ensure
 * that the db is open, end does not close it.
 * <p/>
 * The first begin will open the database. However, to open the db it is
 * necessary to have a global file lock. The cache that has the db must poll
 * this file to see if the modified time changed. If it did, it must try to get
 * the local lock and close the cache. The next begin will have then to contend
 * again for the file lock.
 * <p/>
 * Complicated but it allows the primary process to keep the db open most of the
 * time, it only has to give it up when another process requires it.
 * 
 * @Threadsafe
 */

public class LibraryCache extends DB {
	public enum Phase {
		master, staged
	};

	interface LibraryCacheMessages extends Messages {

		ERROR FoundStagedFile_ForMaster(File ss);

		ERROR CouldNotRename(File from, File to);

		ERROR MasterOverridden__(Revision older, Revision r);

		ERROR Demotion__(Revision older, Revision r);

		ERROR InvalidChecksum__(Revision r, File mm);

		ERROR FoundMasterFile_ForStaged(File mm);

		ERROR NotInPrimary_(List<String> notInPrimary);

		ERROR NotInBsnIndex_(List<String> notInBsnIndex);

		ERROR BsnIndexSize_DoesNotMatch_(int n, int size);

		ERROR RevisionsSize_DoesNotMatch_(int n, int size);

		ERROR Failure__(Revision r, Exception e);

		ERROR Orphaned_Files_(String type, Collection<File> values);

		ERROR CouldNotDelete_(File f);

		ERROR InvalidRevisions__(Revision r, String report);

		ERROR UnexpectedFile_(File bsn);

		void KeepFailed_(Exception e);

		void Keep_(Exception e);

	}

	final LibraryCacheMessages							msgs		= ReporterMessages.base(this,
																			LibraryCacheMessages.class);

	final static JSONCodec								codec		= new JSONCodec();
	final static Pattern								PREFIX		= Pattern.compile("([a-z0-9.-_]+).*");
	final static Pattern								FILENAME	= Pattern
																			.compile("([^-\\s]+)-([\\d]+\\.[\\d]+\\.[\\d]+).jar");
	final static long									TIMEOUT		= TimeUnit.DAYS.toMillis(1);
	final static int									BATCH_SIZE	= 5;
	final Library										library;
	final File											cache;
	final File											stateFile;
	final File											masters;
	final File											staged;
	final File											locks;
	final File											db;
	RecordManager										recman;
	PrimaryTreeMap<String,Library.Revision>				revisions;
	SecondaryTreeMap<String,String,Library.Revision>	bsnIndex;
	State												state;
	Executor											executor;
	boolean												includeStaged;

	/**
	 * Maintains the state of the current cache. Deleting the corresponding file
	 * ./state.json will initialize the cache which is a full download.
	 */
	public static class State {
		public long					marker;
		public Info					info;
		public long					modified;
		public Map<String,Object>	__extra;
	}

	/**
	 * Create a new Library Cache library
	 * 
	 * @throws Exception
	 */

	public LibraryCache(Library lib, File cacheDir) throws Exception {
		this.library = lib;
		if (cacheDir == null) {
			File home = new File(System.getProperty("user.home"));
			this.cache = IO.getFile(home, ".bnd/cached-repo");
		} else
			this.cache = cacheDir.getAbsoluteFile();

		this.cache.mkdirs();
		assert cache.isDirectory();
		locks = new File(cache, "locks");
		db = new File(cache, "db");
		db.mkdir();
		masters = new File(cache, "masters");
		staged = new File(cache, "staged");
		stateFile = new File(cache, "state.json");
		locks.mkdir();
		masters.mkdir();
		staged.mkdir();
		super.setLockFile(new File(cache, "@"));
		trace("initialized");
	}

	/**
	 * Open locally. This is only called when we have the global lock and the
	 * the local lock.
	 * 
	 * @return
	 * @throws Exception
	 */
	protected void open0() throws Exception {
		trace("opening");
		state = readState();
		recman = RecordManagerFactory.createRecordManager(db.getAbsolutePath());

		revisions = recman.treeMap("revisions", new Serializer<Revision>() {

			@Override
			public Revision deserialize(SerializerInput in) throws IOException, ClassNotFoundException {
				try {
					String s = in.readUTF();
					return codec.dec().from(s).get(Revision.class);
				}
				catch (Exception e) {
					throw new IOException(e);
				}
			}

			@Override
			public void serialize(SerializerOutput out, Revision r) throws IOException {
				try {
					String s = codec.enc().put(r).toString();
					out.writeUTF(s);
				}
				catch (Exception e) {
					throw new IOException(e);
				}
			}
		});
		bsnIndex = revisions.secondaryTreeMap("bsnIndex", new SecondaryKeyExtractor<String,String,Revision>() {
			@Override
			public String extractSecondaryKey(String key, Revision value) {
				return value.bsn;
			}
		});
	}

	/**
	 * Close the database, this is called when we really have to give up. Either
	 * because this cache object is closed or we need to yield to another
	 * process.
	 */

	protected void close0() throws IOException {
		trace("closing");
		recman.close();
		recman = null;
		revisions = null;
		bsnIndex = null;
		TimerTask watcher = this.watcher;
		if (watcher != null) {
			watcher.cancel();
		}
	}

	/**
	 * Read the curren state from disk.
	 * 
	 * @return
	 * @throws Exception
	 */
	private State readState() throws Exception {
		if (!stateFile.isFile())
			return new State();

		try {
			return codec.dec().from(stateFile).get(State.class);
		}
		catch (Exception e) {
			e.printStackTrace();
			return new State();
		}
	}

	/**
	 * Write the current state to disk. Must happend after a sync operation.
	 * 
	 * @return
	 * @throws Exception
	 */
	private boolean writeState() throws Exception {
		codec.enc().to(stateFile).put(state).flush();
		return true;
	}

	/**
	 * Synchronize with the repository
	 * 
	 * @return
	 * @throws Exception
	 */

	public void synchronize() throws Exception {

		begin();
		try {
			trace("synchronizing");
			Info info = library.getInfo();

			assert info != null;

			trace("getting info %s %s %s", info.location, info.name, info.version);

			if (state.info == null) {
				state.marker = 0;
			} else if (!state.info.generation.equals(info.generation))
				state.marker = 0;
			else if (state.info.updates == info.updates && state.info.masterUpdates == info.masterUpdates)
				if (!bsnIndex.isEmpty()) // if there is nothing, check anyway
				{
					trace("no sync necessary");
					return;
				}

			trace("syncing from %s required", state.marker);

			long begin = state.marker;
			int n = 0;
			boolean found;
			do {
				trace("beginning batch %s", BATCH_SIZE);
				alive();
				Find< ? extends Revision> find = library.findRevision().where("modified>=%s", begin)
						.ascending("modified").skip(n).limit(BATCH_SIZE);
				found = false;
				for (Revision r : find) {
					state.marker = Math.max(r.modified, state.marker);
					Revision older = revisions.get(r._id);

					File from = null;
					File to = null;

					if (older != null) {
						boolean idsha = Arrays.equals(older.sha, r.sha);

						if (older.master == false && r.master == false) {
							trace("staged->staged");

							if (!idsha) {
								from = getFile(staged, r);
							}
							assert !getFile(masters, r).exists();

						} else if (older.master == false && r.master == true) {
							trace("staged->master");

							assert !getFile(masters, r).exists();

							from = getFile(staged, r);
							if (idsha)
								to = getFile(masters, r);

						} else if (older.master == true && r.master == false) {
							trace("master->staged (not good)");

							msgs.Demotion__(older, r);

							assert !getFile(staged, r).exists();

							// But we follow it anyway, the db is the boss

							from = getFile(masters, r);
							if (idsha)
								to = getFile(staged, r);

						} else if (older.master == true && r.master == true) {
							trace("master->master");

							// master -> master

							assert !getFile(staged, r).exists();

							if (!idsha) {
								// This should not happen
								msgs.MasterOverridden__(older, r);
								from = getFile(masters, r);
							}
						}
					} else {
						assert !getFile(staged, r).exists();
						assert !getFile(masters, r).exists();
					}

					if (to == null && from != null) {
						IO.delete(from.getParentFile());
						if (from.exists())
							msgs.CouldNotDelete_(to);
					} else if (to != null && from != null) {
						to.getParentFile().mkdirs();
						if (!from.renameTo(to)) {
							msgs.CouldNotRename(from, to);
						}
					}

					revisions.put(r._id, r);
					n++;
					found = true;
				}
				recman.commit();
				trace("finished batch");
			} while (found && (n % BATCH_SIZE) == 0);

			state.info = info;
			writeState();
		}
		finally {
			end();
		}
	}

	/**
	 * List the bsns that match the query (Globbing)
	 * 
	 * @param query
	 * @return
	 * @throws Exception
	 */

	public SortedSet<String> list(String query) throws Exception {
		begin();
		try {
			if (query == null || query.isEmpty())
				return new TreeSet<String>(bsnIndex.keySet());

			SortedMap<String,Iterable<String>> search = bsnIndex;
			Glob p = null;

			p = new Glob(query);
			Matcher matcher = PREFIX.matcher(query);
			if (matcher.matches()) {
				String prefix = matcher.group(1);
				StringBuilder suffix = new StringBuilder(prefix);
				int last = suffix.length() - 1;
				suffix.delete(last, last + 1);
				suffix.append((char) (prefix.charAt(last) + 1));
				search = bsnIndex.subMap(prefix, suffix.toString());
			}
			SortedSet<String> result = new TreeSet<String>();

			for (String bsn : search.keySet()) {
				if (p.matcher(bsn).find())
					result.add(bsn);
			}
			return result;
		}
		finally {
			end();
		}
	}

	public List<String> versions(String bsn) throws Exception {
		begin();
		try {
			List<String> versions = new ArrayList<String>();
			for (Revision r : bsnIndex.getPrimaryValues(bsn)) {
				if (r.master || includeStaged)
					versions.add(r.version.base);
			}
			return versions;
		}
		finally {
			end();
		}
	}

	public Future<File> getMaster(String bsn, String version) throws Exception {
		if (includeStaged)
			return getStaged(bsn, version);

		File f = getFile(masters, bsn, version);
		if (f.isFile())
			return future(null, f);

		begin();
		try {
			Revision r = getRevision(bsn, version);
			if (r != null && r.master)
				return future(r, f);
			else
				return future(null, null);
		}
		finally {
			end();
		}
	}

	public Future<File> getStaged(String bsn, String version) throws Exception {
		File sf = getFile(staged, bsn, version);
		if (sf.isFile())
			return future(null, sf);

		File mf = getFile(masters, bsn, version);
		if (mf.isFile())
			return future(null, mf);

		begin();
		try {
			return future(getRevision(bsn, version), sf);
		}
		finally {
			end();
		}
	}

	private Future<File> future(final Revision r, final File base) {
		FutureTask<File> ft = new FutureTask<File>(new Callable<File>() {

			@Override
			public File call() throws Exception {
				if (r == null)
					return base;

				download(r, base);
				return base;
			}
		});
		if (r != null && executor != null)
			executor.execute(ft);
		else
			ft.run();
		return ft;
	}

	private void download(Revision r, File base) throws Exception {
		File tmp = new File(base.getAbsolutePath() + "-tmp.jar");
		String id = r.bsn + "-" + r.version.base;
		Closeable c = lock(id);
		try {
			trace("download lock %s", id);
			// Check for the race condition that somebody beat us
			if (base.isFile())
				return;

			base.delete();

			trace("fetching %s:%s -> %s", id, r.url, tmp);
			fetch(r, tmp);
			if (!tmp.renameTo(base)) {
				msgs.CouldNotRename(tmp, base);
				// TODO Throw exception??
			}
			base.setLastModified(r.modified);
			base.setReadOnly();
		}
		finally {
			c.close();
			trace("released download lock %s", id);
		}
	}

	/**
	 * Initialize the cache area. This will require a full synchronization.
	 * 
	 * @throws Exception
	 */
	public void initialize(boolean keep) throws Exception {
		trace("initialize keep=%s", keep);
		begin();
		try {
			recman.close();
			IO.delete(db);
			IO.delete(stateFile);
			open0();
			synchronize();
			if (!keep) {
				IO.delete(masters);
				IO.delete(staged);
			} else {
				keep(masters, true);
				keep(staged, false);
			}
		}
		finally {
			end();
		}
	}

	/**
	 * Collect all the revisions in a repo in a map.
	 * 
	 * @param dir
	 *            the root directory
	 * @return
	 */
	private void keep(File dir, boolean master) {
		for (File bsnDir : dir.listFiles()) {
			if (bsnDir.isDirectory()) {
				String bsn = bsnDir.getName();
				for (File versionDir : bsnDir.listFiles()) {
					String version = versionDir.getName();
					if (versionDir.isDirectory()) {
						try {
							File f = getFile(dir, bsn, version);
							Revision revision = getRevision(bsn, version);
							if (revision == null || revision.master != master || !checksum(revision.sha, f)) {
								IO.delete(versionDir);
								trace("deleted %s since it is not in the db, wrong sha, or wrong phase", f);
								if (f.exists())
									msgs.CouldNotDelete_(f);
							}
						}
						catch (Exception e) {
							msgs.Keep_(e);
						}

					}
				}
			}
		}
	}

	/**
	 * Kill a locks
	 */

	public boolean unlock(String name) {
		if (!name.matches("[-_\\w\\d.]+|@"))
			throw new IllegalArgumentException("Invalid lock name " + name);

		return new File(locks, name).delete();
	}

	/**
	 * Show the current locks
	 */

	public List<String> locks() {
		return new ExtList<String>(locks.list());
	}

	/**
	 * If the includeStaged is true, staged versions are included in versions()
	 */

	public void setIncludeStaged(boolean include) {
		this.includeStaged = include;
	}

	/**
	 * Lock the revision on disk so that no other user of the cache starts to
	 * download the file at the same time. The closeable returned must be closed
	 * to clear the lock.
	 * 
	 * @param revision
	 *            the revision to lock
	 * @return a Closeable that must be closed when the lock is no longer
	 *         needed.
	 * @throws Exception
	 */
	private Closeable lock(String name) throws Exception {
		final File lock = new File(locks, name);
		while (!lock.createNewFile()) {
			trace("Waiting on lock " + lock);
			Thread.sleep(500);
			// TODO timeout
		}
		lock.deleteOnExit();
		IO.store(this.toString(), lock);

		return new Closeable() {
			@Override
			public void close() throws IOException {
				lock.delete();
			}
		};
	}

	/**
	 * Attempt to get a revision from the library. Since this likely involves
	 * transfers, accept IO errors etc. We try this a couple of time with an
	 * increasing delay. This method throws an exception or otherwise
	 * successfully transferred the file after verification of the SHA
	 * 
	 * @param r
	 *            The revision to fetch
	 * @param tmp
	 *            the place where to store the file
	 * @throws Exception
	 */
	private void fetch(Revision r, File tmp) throws Exception {
		int n = 0;
		tmp.getParentFile().mkdirs();
		assert tmp.getParentFile().isDirectory();

		while (true) {
			try {
				Digester<SHA1> digester = SHA1.getDigester(new FileOutputStream(tmp));
				IO.copy(r.url.toURL().openStream(), digester);
				if (!Arrays.equals(r.sha, digester.digest().digest()))
					throw new IllegalArgumentException(r._id + " checksum error on download");

				return;
			}
			catch (Exception e) {
				if (n++ > 2)
					throw e;
			}
			Thread.sleep(2000 * n);
		}
	}

	/**
	 * Find the revision of the bsn/version. If not found, an attempt is made to
	 * synchronize if there has been no synchronization for some time as
	 * specified by {@link #TIMEOUT}.
	 * 
	 * @param bsn
	 *            the name of the resource
	 * @param version
	 *            the version of the resource
	 * @return the revision or null
	 * @throws Exception
	 */
	private Revision getRevision(String bsn, String version) throws Exception {
		for (Revision r : bsnIndex.getPrimaryValues(bsn)) {
			if (r.version.base.equals(version))
				return r;
		}
		// not found, should we sync?
		if (state.modified + TIMEOUT > System.currentTimeMillis())
			return null;

		synchronize();

		// Try again.
		for (Revision r : bsnIndex.getPrimaryValues(bsn)) {
			if (r.version.base.equals(version))
				return r;
		}
		return null;
	}

	/**
	 * For informational purposes.
	 * 
	 * @return the root of our cache
	 */
	public File getRoot() {
		return cache;
	}

	/**
	 * Verify the database and the file structure.
	 * 
	 * @throws Exception
	 */

	public void verify(boolean fix) throws Exception {
		begin();
		trace("obtained lock %s", cache);
		try {
			List<String> notInPrimary = new ArrayList<String>();
			List<String> notInBsnIndex = new ArrayList<String>();
			Map<String,File> m = traverse(masters);
			Map<String,File> s = traverse(staged);

			int n = 0;
			for (Revision r : revisions.values()) {
				n++;
				try {
					// TODO finalize validation
					// String report = Data.validate(r);
					// if (report != null)
					// msgs.InvalidRevisions__(r, report);

					trace("processing %s", r._id);
					if (!revisions.containsKey(r._id)) {
						notInPrimary.add(r._id);
					}
					if (!bsnIndex.containsKey(r.bsn)) {
						notInBsnIndex.add(r._id);
					}
					File mm = m.get(r._id);
					File ss = s.get(r._id);
					verifyFileCache(fix, r, mm, ss);
					m.remove(r._id);
					s.remove(r._id);
				}
				catch (Exception e) {
					msgs.Failure__(r, e);
				}
			}

			if (notInPrimary.size() > 0)
				msgs.NotInPrimary_(notInPrimary);

			if (notInBsnIndex.size() > 0)
				msgs.NotInBsnIndex_(notInBsnIndex);

			if (revisions.size() != n)
				msgs.RevisionsSize_DoesNotMatch_(n, revisions.size());

			verifyOrphans("master", m, fix);
			verifyOrphans("staged", s, fix);

		}
		finally {
			end();
		}
	}

	/**
	 * Check if there are any orphans in our cache. Delete them if they should
	 * not be there and fix is true.
	 * 
	 * @param type
	 *            master/staged
	 * @param files
	 *            the files that are orphans
	 * @param fix
	 *            to fix or not to fix
	 */
	private void verifyOrphans(String type, Map<String,File> files, boolean fix) {
		if (files.isEmpty())
			return;

		msgs.Orphaned_Files_(type, files.values());
		if (fix) {
			for (File f : files.values()) {
				if (!f.delete())
					msgs.CouldNotDelete_(f);
			}
		}
	}

	/**
	 * Verify that the revision master state matches the appropriate repo.
	 * 
	 * @param fix
	 * @param r
	 * @param master
	 * @param staged
	 * @throws Exception
	 */
	private void verifyFileCache(boolean fix, Revision r, File master, File staged) throws Exception {
		if (r.master) {
			if (staged != null) {
				msgs.FoundStagedFile_ForMaster(staged);
				if (fix) {
					trace("deleting %s because is in staged area and is master in db", staged);
					staged.delete();
				}
			}
			if (master != null) {
				if (!checksum(r.sha, master)) {
					msgs.InvalidChecksum__(r, master);
					if (fix) {
						trace("deleting %s because checksum is wrong", master);
						master.delete();
					}
				}
			}
		} else {
			if (master != null) {
				msgs.FoundMasterFile_ForStaged(master);
				if (fix) {
					trace("deleting %s because is in master area but is master in db", master);
					master.delete();
				}
			}
			if (staged != null) {
				if (!checksum(r.sha, staged)) {
					msgs.InvalidChecksum__(r, staged);
					if (fix) {
						trace("deleting %s because checksum is wrong", master);
						staged.delete();
					}
				}
			}

		}
	}

	/**
	 * Checksum the given file, return true if the give sha matches the file.
	 * 
	 * @param sha
	 *            the given sha
	 * @param file
	 *            the file
	 * @return true if the sha matches, otherwise false
	 * @throws Exception
	 */
	private boolean checksum(byte[] sha, File file) throws Exception {
		Digester<SHA1> digester = SHA1.getDigester();
		IO.copy(file, digester);
		return Arrays.equals(sha, digester.digest().digest());
	}

	/**
	 * Collect all the revisions in a repo in a map.
	 * 
	 * @param dir
	 *            the root directory
	 * @return
	 */
	private Map<String,File> traverse(File dir) {
		int ufo = 1;

		Map<String,File> map = new HashMap<String,File>();

		for (File bsnDir : dir.listFiles()) {
			if (bsnDir.isDirectory()) {
				for (File versionDir : bsnDir.listFiles()) {
					if (versionDir.isDirectory()) {
						for (File f : versionDir.listFiles()) {
							Matcher m = FILENAME.matcher(f.getName());
							if (m.matches()) {
								String bsn = m.group(1);
								String version = m.group(2);
								map.put(bsn + "-" + version, f);
							}
						}
					} else {
						map.put("ufoversion-" + ufo++, versionDir);
					}
				}
			} else {
				map.put("ufobsn-" + ufo++, bsnDir);
			}
		}
		return map;
	}

	/**
	 * Return the file in the repo for a bsn/version
	 * 
	 * @param base
	 *            the base directory
	 * @param bsn
	 *            the bsn
	 * @param version
	 *            the version (3 parts)
	 * @return a file
	 */
	private File getFile(File base, String bsn, String version) {
		return IO.getFile(base, bsn + "/" + version + "/" + bsn + "-" + version + ".jar");
	}

	/**
	 * Return the filename in the repo for a revision
	 * 
	 * @param base
	 *            the base directory
	 * @param bsn
	 *            the bsn
	 * @param version
	 *            the version (3 parts)
	 * @return a file
	 */
	private File getFile(File repo, Revision r) {
		return getFile(repo, r.bsn, r.version.base);
	}
}
