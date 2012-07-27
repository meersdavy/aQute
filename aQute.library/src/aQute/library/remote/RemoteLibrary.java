package aQute.library.remote;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;

import aQute.lib.collections.*;
import aQute.lib.converter.*;
import aQute.lib.json.*;
import aQute.service.library.*;

public class RemoteLibrary implements Library {
	static JSONCodec								codec				= new JSONCodec();
	String											url					= "https://repo.jpm4j.org/rest/";
	final static TypeReference<Info>				singletonInfo		= new TypeReference<Info>() {};
	final static TypeReference<Program>				singletonProgram	= new TypeReference<Program>() {};
	final static TypeReference<Revision>			singletonRevision	= new TypeReference<Revision>() {};
	final static TypeReference<Iterable<Program>>	pluralProgram		= new TypeReference<Iterable<Program>>() {};
	final static TypeReference<Iterable<Revision>>	pluralRevision		= new TypeReference<Iterable<Revision>>() {};

	public RemoteLibrary url(String base) {
		this.url = base;
		if (!url.endsWith("/"))
			this.url = this.url + "/";
		return this;
	}

	@Override
	public Program getProgram(String bsn) throws Exception {
		return findProgram().bsn(bsn).one();
	}

	@Override
	public Revision getRevision(String bsn, String version) throws Exception {
		return findRevision().bsn(bsn).version(version).one();
	}

	@Override
	public Importer importer(URI url) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void update(Program program) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void redo(String where, String process) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public Revision rescan(String bsn, String version) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public Revision master(String bsn, String version) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public Revision delete(String bsn, String version) {
		throw new UnsupportedOperationException();
	}

	class FindImpl<T> implements Find<T> {
		final MultiMap<String,Object>		arguments	= new MultiMap<String,Object>();
		final String						verb;
		final TypeReference<T>				singleton;
		final TypeReference<Iterable<T>>	plural;

		public FindImpl(String verb, TypeReference<T> singleton, TypeReference<Iterable<T>> plural) {
			this.verb = verb;
			this.singleton = singleton;
			this.plural = plural;
		}

		@Override
		public Find<T> skip(int n) throws Exception {
			arguments.add("skip", n);
			return this;
		}

		@Override
		public Find<T> limit(int n) throws Exception {
			arguments.add("limit", n);
			return this;
		}

		@Override
		public Find<T> ascending(String field) throws Exception {
			arguments.add("order", "+" + field);
			return this;
		}

		@Override
		public Find<T> descending(String field) throws Exception {
			arguments.add("order", "-" + field);
			return this;
		}

		@Override
		public Find<T> where(String where, Object... args) throws Exception {
			String formatted = String.format(where, args);
			arguments.add("where", formatted);
			return this;
		}

		@Override
		public T one() throws Exception {
			arguments.add("type", "one");
			return get(singleton);
		}

		@Override
		public T first() throws Exception {
			arguments.add("type", "first");
			return get(singleton);
		}

		@Override
		public int count() throws Exception {
			arguments.add("type", "count");
			return get(new TypeReference<Integer>() {});
		}

		@Override
		public Iterator<T> iterator() {
			try {
				arguments.add("type", "iterator");
				return get(plural).iterator();
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		private <X> X get(TypeReference<X> ref) throws Exception {
			StringBuilder sb = new StringBuilder(url);
			sb.append(verb);
			String del = "?";
			for (Entry<String,List<Object>> e : arguments.entrySet()) {
				for (Object o : e.getValue()) {
					sb.append(del);
					del = "&";
					sb.append(e.getKey());
					sb.append('=');
					sb.append(URLEncoder.encode(o.toString(), "UTF-8"));
				}
			}
			URL url = new URL(sb.toString());
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			InputStream in = con.getInputStream();
			try {
				return codec.dec().from(in).get(ref);
			}
			finally {
				in.close();
			}
		}

		@Override
		public Find<T> bsn(String bsn) throws Exception {
			if (plural.equals(pluralProgram))
				return where("_id=%s", bsn);
			else
				return where("bsn=%s", bsn);
		}

		@Override
		public Find<T> version(String version) throws Exception {
			assert (!plural.equals(pluralProgram));
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
		public Find<T> query(String query) throws Exception {
			arguments.add("query", query);
			return this;
		}
	}

	@Override
	public Find< ? extends Program> findProgram() throws Exception {
		return new FindImpl<Program>("program", singletonProgram, pluralProgram);
	}

	@Override
	public Find<Revision> findRevision() throws Exception {
		return new FindImpl<Revision>("revision", singletonRevision, pluralRevision);
	}

	@Override
	public Info getInfo() throws Exception {
		return new FindImpl<Info>("library", singletonInfo, null).one();
	}

}
