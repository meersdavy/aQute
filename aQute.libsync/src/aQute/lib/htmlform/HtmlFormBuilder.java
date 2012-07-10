package aQute.lib.htmlform;

import java.lang.reflect.*;
import java.util.*;

import aQute.lib.data.*;
import aQute.service.angular.*;

public class HtmlFormBuilder {
	final Form	form	= new Form();
	Class< ? >	type;

	public HtmlFormBuilder(String id) {
		this.form.id = id;
	}

	public HtmlFormBuilder(Class< ? > type) {
		type(type);
		auto();
	}

	public HtmlFormBuilder model(String model) {
		this.form.model = model;
		return this;
	}

	public HtmlFormBuilder id(String id) {
		this.form.id = id;
		return this;
	}

	public HtmlFormBuilder title(String title) {
		this.form.title = title;
		return this;
	}

	public HtmlFormBuilder type(Class< ? > type) {
		this.type = type;
		this.form.type = type.getName();
		return this;
	}

	public HtmlFormBuilder auto() {
		assert type != null;
		generate(type, null);
		return this;
	}

	public HtmlFormBuilder remove(String prefix) {
		for (Iterator<Entry> i = form.entries.iterator(); i.hasNext();) {
			if (i.next().id.startsWith(prefix))
				i.remove();
		}
		return this;
	}

	public class EntryBuilder {
		final Entry	entry;

		public EntryBuilder(String id) {
			this.entry = new Entry();
			this.entry.id = id;
		}

		public EntryBuilder(Entry e) {
			this.entry = e;
		}

		public EntryBuilder label(String label) {
			this.entry.label = label;
			return this;
		}

		public EntryBuilder filter(String filter) {
			this.entry.filter = filter;
			return this;
		}

		public EntryBuilder input(Input input) {
			this.entry.input = input;
			return this;
		}

		public EntryBuilder model(String model) {
			this.entry.model = model;
			return this;
		}

		public EntryBuilder readOnly() {
			this.entry.readOnly = true;
			return this;
		}

		public EntryBuilder field(String field) {
			this.entry.field = field;
			return this;
		}

		public EntryBuilder link(String field) {
			this.entry.link = field;
			remove(field);
			return this;
		}

		HtmlFormBuilder add() {
			form.entries.add(entry);
			return HtmlFormBuilder.this;
		}

	}

	public EntryBuilder entry(String id) {
		for (Entry e : form.entries)
			if (e.id.equals(id))
				return new EntryBuilder(e);

		return new EntryBuilder(id);
	}

	private void generate(Class< ? > type, String model) {
		Field[] fields = data.fields(type);
		form.id = type.getName().replace('.', '_');
		form.title = simpleName(type.getName());
		form.type = type.getName();
		form.model = simpleName(type.getName()).toLowerCase();
		for (Field f : fields) {
			Entry e = new Entry();
			e.id = f.getName();
			e.input = Input.AUTO;
			form.entries.add(e);
			e.model = model;
		}
	}

	private String simpleName(String name) {
		name = name.substring(name.lastIndexOf('.') + 1);
		return name.substring(name.lastIndexOf('$') + 1);
	}

	public Form form() {
		return form;
	}

}
