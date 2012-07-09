package aQute.impl.angular;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import org.osgi.framework.*;

import aQute.bnd.annotation.component.*;
import aQute.lib.htmlform.*;
import aQute.lib.tag.*;
import aQute.service.angular.*;
import aQute.service.rest.*;

@Component
public class AngularImpl implements ResourceManager {
	HtmlForm							generator	= new HtmlForm();
	ConcurrentHashMap<String,FormCache>	map			= new ConcurrentHashMap<String,FormCache>();

	class FormCache {
		Bundle			bundle;
		FormProvider	prov;
		Form			form;
		byte[]			view;
		byte[]			edit;

		public synchronized byte[] form(boolean input) throws Exception {
			if (input)
				return edit == null ? edit = getForm(true) : edit;
			else
				return view == null ? view = getForm(false) : view;

		}

		private byte[] getForm(boolean b) throws ClassNotFoundException {
			Class< ? > type = bundle.loadClass(form.type);
			Tag tag = generator.form(form, type, b);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			PrintWriter pw = new PrintWriter(out);
			tag.print(0, pw);
			pw.close();
			return out.toByteArray();
		}
	}

	@Activate
	void activate() {
		System.out.println("Activated angular");
	}

	interface formOptions extends Options {
		boolean edit();
	}

	public byte[] getForm(formOptions opts, String id) throws Exception {
		FormCache cache = map.get(id);
		if (cache == null)
			return null;

		opts._response().setContentType("text/html;charset=utf-8");
		return cache.form(opts.edit());
	}

	@Reference(type = '*')
	void setFormProvider(FormProvider prov) {
		System.out.println("Foudn provider " + prov);
		for (Form form : prov.getForms()) {
			FormCache cache = new FormCache();
			cache.bundle = FrameworkUtil.getBundle(prov.getClass());
			cache.prov = prov;
			cache.form = form;
			map.put(form.id, cache);
		}
	}

	void unsetFormProvider(FormProvider prov) {
		Iterator<FormCache> i = map.values().iterator();
		while (i.hasNext()) {
			if (i.next().prov == prov)
				i.remove();
		}
	}
}
