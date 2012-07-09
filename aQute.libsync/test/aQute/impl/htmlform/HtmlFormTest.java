package aQute.impl.htmlform;

import java.io.*;

import junit.framework.*;
import aQute.lib.htmlform.*;
import aQute.lib.tag.*;
import aQute.service.angular.*;
import aQute.service.data.*;

public class HtmlFormTest extends TestCase {

	static class TD {
		@Require
		public boolean	check;
		@Match(value = "[01]+{8,16}", reason = "Must be a binary number")
		public int		number;
		public String	name;

	}

	public void testSimple() {
		HtmlFormBuilder hfb = new HtmlFormBuilder(TD.class);

		Form form = hfb.form();
		assertEquals("aQute_impl_htmlform_HtmlFormTest$TD", form.id);

		HtmlForm hf = new HtmlForm();
		Tag tag = hf.form(form, TD.class, true);
		PrintWriter pw = new PrintWriter(System.out);
		tag.print(0, pw);
		pw.flush();
	}
}
