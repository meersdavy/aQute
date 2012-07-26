package aQute.jpm.bnd;


public class RemoteCachedLibrary { // implements RepositoryPlugin, Plugin {
	// Library library;
	// Config config;
	// String name = "JPM Repo";
	//
	// public static class State {
	// public String uuid;
	// public long date;
	// public Map<String,Object> __extra;
	// }
	//
	// State state;
	//
	// interface Messages extends aQute.service.reporter.Messages {
	//
	// ERROR CouldNotReadState__(File f, Exception e);
	//
	// ERROR FailedToReadIndex__(URL url, Exception e);
	//
	// ERROR SerializationIssues_(Exception e);
	//
	// ERROR DeSerializationIssues_(Exception e);
	//
	// void DownloadFailed(URL url, int i);
	//
	// void CouldNotRename(File ft, File f);
	//
	// void ChecksumFailed(URL url, byte[] checksum, byte[] sha);
	//
	// }
	//
	// Messages msgs = ReporterMessages.base(null, Messages.class);
	// private String uri;
	//
	// interface Config {
	// URI url();
	//
	// String name();
	//
	// String email();
	//
	// String _password();
	//
	// String cache();
	// }
	//
	// /**
	// * Initialize the repository since it is the first time we're hit.
	// *
	// * @throws InterruptedException
	// * @throws IOException
	// */
	// private void initialize() throws IOException, InterruptedException {
	// if (state != null)
	// return;
	//
	// cache.mkdirs();
	//
	// try {
	// if (readState() && verify())
	// try {
	// setupDb(false);
	// synchronize();
	// return;
	// }
	// catch (Exception e) {
	// closeDb();
	// throw e;
	// }
	// }
	// catch (Exception e) {
	// msgs.Unexpected_Error_("Syncing with server", e);
	// }
	// download();
	// }
	//
	// @Override
	// public File get(String bsn, String range, Strategy strategy,
	// Map<String,String> properties) throws Exception {
	// initialize();
	//
	// if (properties.containsKey("snapshot"))
	// return snapshot(bsn, range);
	//
	// if (strategy == Strategy.EXACT)
	// return get(bsn, Version.parseVersion(range));
	//
	// List<Version> versions = versions(bsn);
	//
	// VersionRange r = new VersionRange(range);
	// for (Iterator<Version> i = versions.iterator(); i.hasNext();) {
	// if (!r.includes(i.next()))
	// i.remove();
	// }
	// Collections.sort(versions);
	// if (versions.isEmpty())
	// return null;
	//
	// if (versions.size() == 1)
	// return get(bsn, versions.get(0));
	//
	// switch (strategy) {
	// case HIGHEST :
	// return get(bsn, versions.get(versions.size() - 1));
	// case LOWEST :
	// return get(bsn, versions.get(0));
	// }
	// return null;
	// }
	//
	// private File snapshot(String bsn, String range) {
	// // TODO Auto-generated method stub
	// return null;
	// }
	//
	// @Override
	// public boolean canWrite() {
	// return false;
	// }
	//
	// @Override
	// public File put(Jar jar) throws Exception {
	// return null;
	// }
	//
	// static Pattern PREFIX = Pattern.compile("([a-z0-9.-_]+).*");
	//
	// @Override
	// public List<String> list(String regex) throws Exception {
	// SortedMap<String,Iterable<String>> search = bsnIndex;
	// Glob p = null;
	// if (regex != null) {
	// p = new Glob(regex);
	// Matcher matcher = PREFIX.matcher(regex);
	// if (matcher.matches()) {
	// String prefix = matcher.group(1);
	// search = bsnIndex.subMap(prefix, prefix);
	// }
	// }
	// List<String> result = new ArrayList<String>();
	//
	// for (String bsn : search.keySet()) {
	// if (p == null || p.matcher(bsn).matches())
	// result.add(bsn);
	// }
	// return result;
	// }
	//
	// @Override
	// public List<Version> versions(String bsn) throws Exception {
	// List<Version> result = new ArrayList<Version>();
	// for (String v : bsnIndex.get(bsn)) {
	// Version version = Version.parseVersion(v);
	// result.add(version);
	// }
	// return result;
	// }
	//
	// @Override
	// public String getName() {
	// return name;
	// }
	//
	// @Override
	// public String getLocation() {
	// return cache.getAbsolutePath();
	// }
	//
	// @Override
	// public void setProperties(Map<String,String> map) {
	// try {
	// config = Converter.cnv(Config.class, map);
	// if (config.url() != null)
	// uri = config.url().toString();
	//
	// if (config.cache() != null) {
	// File f = IO.getFile(config.cache());
	// cache = f;
	// }
	// }
	// catch (Exception e) {
	// msgs.Unexpected_Error_("Converting properties", e);
	// }
	// }
	//
	// @Override
	// public void setReporter(Reporter reporter) {
	// msgs = ReporterMessages.base(reporter, Messages.class);
	// }
	//
	// private boolean readState() throws Exception {
	// state = new State();
	// File f = new File(cache, "state.json");
	// if (!f.isFile())
	// return false;
	// state = codec.dec().from(f).get(State.class);
	// return true;
	// }
	//
	// private boolean writeState() throws Exception {
	// File f = new File(cache, "state.json");
	// codec.enc().to(f).put(state);
	// return true;
	// }
	//
	// /**
	// * Check that the server is a valid file.
	// *
	// * @param index2
	// * @return
	// * @throws IOException
	// */
	// private boolean verify() throws IOException {
	// if (state.uuid == null || state.length <= 512)
	// return false;
	//
	// return true;
	// }
	//
	// private boolean synchronize() throws Exception {
	// URL url = new URL(uri);
	// HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	// conn.setRequestProperty("If-Match", state.uuid);
	// conn.setRequestProperty("Range", "bytes=" + state.length + "-");
	// conn.setRequestProperty("Accept-Encoding", "deflate");
	// int responseCode = conn.getResponseCode();
	// if (responseCode != HttpURLConnection.HTTP_PARTIAL)
	// return false;
	//
	// InputStream in = conn.getInputStream();
	// String encoding = conn.getContentEncoding();
	// if (encoding != null) {
	// if (encoding.equalsIgnoreCase("deflate"))
	// in = new InflaterInputStream(in);
	// else
	// throw new IllegalArgumentException("Unrecognized encoding (" + encoding +
	// ") of stream for " + url);
	// }
	//
	// File tmpFile = File.createTempFile("partial", ".obr");
	// IO.copy(conn.getInputStream(), tmpFile);
	//
	// index(new FileInputStream(tmpFile));
	// state.length += tmpFile.length();
	// tmpFile.delete();
	// writeState();
	// return true;
	// }
	//
	// private void index(InputStream fragment) throws Exception {
	// while (fragment.available() != 0) {
	// Fragment bundle = codec.dec().from(fragment).get(Fragment.class);
	// if (bundle == null)
	// return;
	//
	// obr.put(bundle.rev.bsn, bundle);
	// }
	//
	// }
	//
	// private void download() throws IOException, InterruptedException {
	// setupDb(true);
	//
	// URL url = new URL(uri);
	// int attempt = 3;
	// do {
	// HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	// conn.setRequestProperty("Accept-Encoding", "deflate");
	// try {
	// int responseCode = conn.getResponseCode();
	// if (responseCode != HttpURLConnection.HTTP_OK)
	// throw new FileNotFoundException(url.toString());
	//
	// InputStream in = conn.getInputStream();
	//
	// String encoding = conn.getContentEncoding();
	// if (encoding != null) {
	// if (encoding.equalsIgnoreCase("deflate"))
	// in = new InflaterInputStream(in);
	// else
	// throw new IllegalArgumentException("Unrecognized encoding (" + encoding +
	// ") of stream for "
	// + url);
	// }
	//
	// File tmpFile = File.createTempFile("partial", ".obr");
	// try {
	// IO.copy(in, tmpFile);
	// FileInputStream fin = new FileInputStream(tmpFile);
	// try {
	// byte[] magic = new byte[512];
	// int read = fin.read(magic);
	// if (read != 512)
	// throw new IOException("Could not read header of obr index file " + url);
	//
	// String magic2 = new String(magic, 0, 20); // just 7 bit
	// // chars
	// if (!magic2.startsWith("OBRI"))
	// throw new IOException("Missing magic of obr index file " + url);
	//
	// index(fin);
	// state.uuid = magic2;
	// state.length += tmpFile.length();
	// writeState();
	// return;
	// }
	// finally {
	// fin.close();
	// }
	// }
	// finally {
	// tmpFile.delete();
	// }
	// }
	// catch (Exception e) {
	// msgs.FailedToReadIndex__(url, e);
	// }
	// Thread.sleep(5000); // we try a couple of times
	// } while (attempt-- > 0);
	//
	// msgs.DownloadFailed(url, 3);
	// }
	//
	// /**
	// * Setup the local db
	// *
	// * @throws IOException
	// */
	// private void setupDb(boolean init) throws IOException {
	// File index = new File(cache, "index.obr");
	// if (init)
	// IO.delete(index);
	//
	// recman =
	// RecordManagerFactory.createRecordManager(index.getAbsolutePath());
	// obr = recman.treeMap("obr", new Serializer<Fragment>() {
	//
	// @Override
	// public Fragment deserialize(SerializerInput in) throws IOException,
	// ClassNotFoundException {
	// InputStream din = new InflaterInputStream(in);
	// try {
	// return codec.dec().from(din).get(Fragment.class);
	// }
	// catch (Exception e) {
	// msgs.DeSerializationIssues_(e);
	// throw new IOException(e);
	// }
	// finally {
	// din.close();
	// }
	// }
	//
	// @Override
	// public void serialize(SerializerOutput out, Fragment fragment) throws
	// IOException {
	// OutputStream iout = new DeflaterOutputStream(out);
	// try {
	// codec.enc().to(iout).put(fragment);
	// }
	// catch (Exception e) {
	// msgs.SerializationIssues_(e);
	// }
	// finally {
	// iout.close();
	// }
	// }
	// });
	// bsnIndex = obr.secondaryTreeMap("bsnIndex", new
	// SecondaryKeyExtractor<String,String,Fragment>() {
	//
	// @Override
	// public String extractSecondaryKey(String key, Fragment value) {
	// return value.rev.bsn;
	// }
	// });
	// }
	//
	// private void closeDb() throws IOException {
	// recman.close();
	// }
	//
	// private File get(String bsn, Version range) throws Exception {
	// String key = bsn + "-" + range.getWithoutQualifier();
	// Fragment bundle = obr.get(key);
	//
	// File f = IO.getFile(cache, "repo/" + bsn + "/" +
	// range.getWithoutQualifier() + "/" + key + ".jar");
	// if (f.exists())
	// return f;
	//
	// File ft = IO.getFile(cache, "repo/" + bsn + "/" +
	// range.getWithoutQualifier() + "/" + UUID.randomUUID()
	// + ".jar");
	// URL url = bundle.rev.url.toURL();
	// FileOutputStream fout = new FileOutputStream(ft);
	// Digester<SHA1> digester = SHA1.getDigester(fout);
	//
	// IO.copy(url.openStream(), (OutputStream) digester);
	//
	// byte[] checksum = digester.digest().digest();
	// if (!Arrays.equals(checksum, bundle.rev.sha))
	// msgs.ChecksumFailed(url, checksum, bundle.rev.sha);
	//
	// Closeable lock = lock(ft.getParentFile());
	// try {
	// if (f.exists())
	// return f;
	//
	// boolean success = ft.renameTo(f);
	// if (!success) {
	// msgs.CouldNotRename(ft, f);
	// return ft;
	// }
	//
	// // TODO f.setLastModified(bundle.rev.fileDate);
	// f.setReadOnly();
	// return f;
	// }
	// finally {
	// lock.close();
	// }
	// }
	//
	// private Closeable lock(File parentFile) throws IOException {
	// File f = new File(parentFile, ".lock");
	// f.createNewFile();
	//
	// final RandomAccessFile raf = new RandomAccessFile(f, "r");
	// FileChannel fc = raf.getChannel();
	// final FileLock lock = fc.lock();
	// return new Closeable() {
	//
	// @Override
	// public void close() throws IOException {
	// try {
	// lock.release();
	// }
	// finally {
	// raf.close();
	// }
	// }
	//
	// };
	// }
	//
	// public void close() throws IOException {
	// if (recman != null)
	// recman.close();
	// }
}
