package aQute.webserver;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.osgi.framework.*;
import org.osgi.service.http.*;
import org.osgi.service.log.*;
import org.osgi.util.tracker.*;

import aQute.bnd.annotation.component.*;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.lib.hex.*;
import aQute.lib.io.*;
import aQute.libg.cryptography.*;
import aQute.webserver.WebServer.Config;

@Component(provide = {}, configurationPolicy = ConfigurationPolicy.optional, immediate = true, designateFactory = Config.class)
public class WebServer extends HttpServlet {
	static String				BYTE_RANGE_SET_S	= "(\\d+)?\\s*-\\s*(\\d+)?";
	static Pattern				BYTE_RANGE_SET		= Pattern.compile(BYTE_RANGE_SET_S);
	static Pattern				BYTE_RANGE			= Pattern.compile("bytes\\s*=\\s*(" + BYTE_RANGE_SET_S
															+ "(\\s*,\\s*" + BYTE_RANGE_SET_S + "))\\s*");
	private static final long	serialVersionUID	= 1L;
	static SimpleDateFormat		format				= new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
	Map<String,Cache>			cached				= new HashMap<String,Cache>();
	File						cache;
	LogService					log;

	static class Range {
		Range	next;
		long	start;
		long	end;

		public long length() {
			if (next == null)
				return end - start;

			return next.length() + end - start;
		}

		Range(String range, long length) {
			if (range != null) {
				if (!BYTE_RANGE.matcher(range).matches())
					throw new IllegalArgumentException("Bytes ranges does not match specification " + range);

				Matcher m = BYTE_RANGE_SET.matcher(range);
				m.find();
				init(m, length);
			} else {
				start = 0;
				end = length;
			}
		}

		private Range() {}

		void init(Matcher m, long length) {
			String s = m.group(1);
			String e = m.group(2);
			if (s == null && e == null)
				throw new IllegalArgumentException("Invalid range, both begin and end not specified: " + m.group(0));

			if (s == null) { // -n == l-n -> l
				start = length - Long.parseLong(e);
				end = length - 1;
			} else if (e == null) { // n- == n -> l
				start = Long.parseLong(s);
				end = length - 1;
			} else {
				start = Long.parseLong(s);
				end = Long.parseLong(e);
			}
			end++; // e is specified as inclusive, Java uses exclusive

			if (end > length)
				end = length;

			if (start < 0)
				start = 0;

			if (start >= end)
				throw new IllegalArgumentException("Invalid range, start higher than end " + m.group(0));

			if (m.find()) {
				next = new Range();
				next.init(m, length);
			}
		}

		void copy(FileChannel from, WritableByteChannel to) throws IOException {
			from.transferTo(start, end, to);
			if (next != null)
				next.copy(from, to);
		}
	}

	class Cache {
		long	time;
		String	etag;
		File	file;
		Bundle	bundle;

		Cache(File f, Bundle b) throws Exception {
			this(f, b, getEtag(f));
		}

		Cache(File f, Bundle b, byte[] etag) {
			this.time = f.lastModified();
			this.bundle = b;
			this.file = f;
			this.etag = Hex.toHexString(etag);
		}

		public Cache(File f) throws Exception {
			this(f, null);
		}

		boolean isExpired() {
			if (!file.isFile())
				return true;

			if (time < file.lastModified())
				return true;

			if (bundle == null)
				return false;

			if (bundle.getLastModified() > time)
				return true;

			return false;
		}
	}

	static byte[] getEtag(File f) throws Exception {
		Digester<MD5> digester = MD5.getDigester();
		IO.copy(f, digester);
		return digester.digest().digest();
	}

	interface Config {
		String alias();

		boolean noBundles();

		File[] directories();

		int expires();

		boolean exceptions();
	}

	Config			config;
	HttpService		http;
	BundleTracker	tracker;

	@Activate
	void activate(Map<String,Object> props, BundleContext context) throws NamespaceException, ServletException {
		this.config = Configurable.createConfigurable(Config.class, props);
		String alias = config.alias();
		if (alias == null || alias.isEmpty())
			alias = "/";

		this.http.registerServlet(alias, this, null, null);
		this.cache = context.getDataFile("cache");
		cache.mkdir();

		tracker = new BundleTracker(context, Bundle.ACTIVE | Bundle.STARTING, null) {
			public Object addingBundle(Bundle bundle, BundleEvent event) {
				if (bundle.getEntryPaths("static/") != null)
					return bundle;
				return null;
			}
		};
		tracker.open();
	}

	public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
		return true;
	}

	public synchronized void doGet(HttpServletRequest rq, HttpServletResponse rsp) throws IOException, ServletException {
		try {
			String path = rq.getPathInfo();
			if (path == null || path.isEmpty() || path.equals("/"))
				path = "";
			else if (path.startsWith("/"))
				path = path.substring(1);

			boolean is404 = false;

			Cache c;
			synchronized (cached) {
				c = cached.get(path);
				if (c == null || c.isExpired()) {
					c = find(path);
					if (c == null) {
						c = do404(path);
						is404 = true;
					} else
						cached.put(path, c);
				}
			}

			rsp.setDateHeader("Last-Modified", c.time);
			rsp.setHeader("Etag", c.etag);
			rsp.setHeader("Allow", "GET, HEAD");
			rsp.setHeader("Accept-Ranges", "bytes");

			Range range = new Range(rq.getHeader("Range"), c.file.length());
			long length = range.length();
			if (length >= Integer.MAX_VALUE)
				throw new IllegalArgumentException("Range to read is too high: " + length);

			rsp.setContentLength((int) range.length());

			if (config.expires() != 0) {
				Date expires = new Date(System.currentTimeMillis() + 60000 * config.expires());
				rsp.setHeader("Expires", format.format(expires));
			}

			String ifModifiedSince = rq.getHeader("If-Modified-Since");
			if (ifModifiedSince != null) {
				long time = 0;
				time = format.parse(ifModifiedSince).getTime();
				if (time <= c.time) {
					rsp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
					return;
				}
			}

			String ifNoneMatch = rq.getHeader("If-None-Match");
			if (ifNoneMatch != null) {
				if (ifNoneMatch.indexOf(c.etag) >= 0) {
					rsp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
					return;
				}
			}

			if (is404)
				rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			else
				rsp.setStatus(HttpServletResponse.SC_OK);

			if (rq.getMethod().equalsIgnoreCase("GET")) {
				String acceptEncoding = rq.getHeader("Accept-Encoding");
				boolean deflate = acceptEncoding != null && acceptEncoding.indexOf("deflate") >= 0;
				OutputStream out = rsp.getOutputStream();
				if (deflate) {
					out = new DeflaterOutputStream(out);
					rsp.setHeader("Content-Encoding", "deflate");
				}

				FileInputStream file = new FileInputStream(c.file);
				try {
					FileChannel from = file.getChannel();
					WritableByteChannel to = Channels.newChannel(out);
					range.copy(from, to);
				}
				finally {
					file.close();
				}
				out.close();
			}
		}
		catch (Exception e) {
			log.log(LogService.LOG_ERROR, "Internal webserver error", e);
			if (config.exceptions())
				throw new RuntimeException(e);

			try {
				PrintWriter pw = rsp.getWriter();
				pw.println("Internal server error\n");
				rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
			catch (Exception ee) {
				log.log(LogService.LOG_ERROR, "Second level internal webserver error", ee);
			}
		}
	}

	private Cache do404(String path) throws Exception {
		log.log(LogService.LOG_INFO, "404 " + path);
		Cache c = find("404.html");
		if (c != null)
			return c;
		return findBundle("default/404.html");
	}

	public void doHead(HttpServletRequest rq, HttpServletResponse rsp) throws IOException, ServletException {
		doGet(rq, rsp);
	}

	Cache find(String path) throws Exception {
		Cache c = findFile(path);
		if (c != null)
			return c;
		// if (config.noBundles())
		// return null;
		return findBundle(path);
	}

	Cache findFile(String path) throws Exception {

		for (File base : config.directories()) {
			File f = IO.getFile(base, path);

			if (f.isDirectory())
				f = new File(f, "index.html");

			if (f.isFile()) {
				return new Cache(f);
			}
		}
		return null;
	}

	Cache findBundle(String path) throws Exception {
		Bundle[] bundles = tracker.getBundles();
		if (bundles != null) {
			for (Bundle b : bundles) {
				URL url = b.getResource("static/" + path);
				if (url == null)
					url = b.getResource("static/" + path + "/index.html");
				if (url != null) {
					File cached = IO.getFile(cache, path);
					if (!cached.exists() || cached.lastModified() <= b.getLastModified()) {
						cached.delete();
						cached.getAbsoluteFile().getParentFile().mkdirs();
						FileOutputStream out = new FileOutputStream(cached);
						Digester<MD5> digester = MD5.getDigester(out);
						IO.copy(url.openStream(), digester);
						digester.close();
						cached.setLastModified(b.getLastModified() + 1000);
						return new Cache(cached, b, digester.digest().digest());
					}
					return new Cache(cached, b);
				}
			}
		}
		return null;
	}

	public String getMimeType(String name) {
		// TODO
		return null;
	}

	@Deactivate
	void deactivate() {
		tracker.close();
		http.unregister(config.alias());
	}

	@Reference
	void setHttp(HttpService http) throws NamespaceException {
		this.http = http;
	}

	@Reference
	void setLog(LogService log) throws NamespaceException {
		this.log = log;
	}
}