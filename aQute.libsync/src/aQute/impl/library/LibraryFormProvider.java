package aQute.impl.library;

import java.util.*;

import aQute.bnd.annotation.component.*;
import aQute.lib.htmlform.*;
import aQute.service.angular.*;
import aQute.service.library.Library.Program;
import aQute.service.library.Library.Revision;

@Component
public class LibraryFormProvider implements FormProvider {
	HtmlForm	generator	= new HtmlForm();

	@Override
	public List<Form> getForms() {
		List<Form> result = new ArrayList<Form>();

		result.add(revision());
		result.add(program());
		return result;
	}

	private Form revision() {
		HtmlFormBuilder hb = new HtmlFormBuilder(Revision.class).remove("_").remove("logo").id("revision")
				.title("Revision");
		hb.entry("insertDate").filter("date:'medium'");
		hb.entry("sha").link("url");
		return hb.form();
	}

	private Form program() {
		return new HtmlFormBuilder(Program.class).remove("_").remove("logo").id("program").form();
	}

}
