package aQute.lib.htmlform;

import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import aQute.lib.data.*;
import aQute.lib.tag.*;
import aQute.service.angular.*;
import aQute.service.data.*;

public class HtmlForm implements FormProvider {
	public Tag form(Form form, Class< ? > type, boolean edit) {
		Tag frm = new Tag("ng-form").addAttribute("id", form.id).addAttribute("title", form.title);

		Tag table = new Tag(frm, "table");
		if (form.model == null)
			form.model = "model";

		for (Entry entry : form.entries) {
			Field f = data.getField(type, entry.field == null ? entry.id : entry.field);
			Input input = entry.input == Input.AUTO ? findType(f.getType()) : entry.input;
			if (input == Input.IGNORE)
				continue;

			Require require = f.getAnnotation(Require.class);
			Match match = f.getAnnotation(Match.class);
			Semantics semantics = f.getAnnotation(Semantics.class);

			Tag tr = new Tag(table, "tr");
			Tag label = new Tag(tr, "td");
			label.addAttribute("class", "label");
			if (semantics != null) {
				label.addAttribute("title", semantics.value());
			}
			label.addContent(entry.label == null ? upperCaseFirstChar(f.getName()) : entry.label);
			Tag value = new Tag(tr, "td");

			Tag html = edit ? value(form, entry, form.model, input, f.getType()) : null;
			if (html == null) {
				value.addContent("{{" + form.model + "." + entry.id + (entry.filter != null ? "| " + entry.filter : "")
						+ "}}");
			} else {
				value.addContent(html);
				html.addAttribute("name", entry.id);
				html.addAttribute("ngModel", form.model + "." + entry.id);
				if (require != null) {
					html.addAttribute("required", true);
					Tag error = new Tag(value, "span").addAttribute("class", "error").addAttribute("ng-show",
							form.id + "." + entry.id + ".$error.required");
					error.addContent("Required");
				}

				if (match != null) {
					if (!match.script().isEmpty())
						html.addAttribute("ngChange", match.script());
					if (!match.value().isEmpty())
						html.addAttribute("ngPattern", match.value());

					if (!match.reason().isEmpty()) {
						Tag error = new Tag(value, "span").addAttribute("class", "error").addAttribute("ng-show",
								form.id + "." + entry.id + ".$error.pattern");
						error.addContent(match.reason());
					}
				}

				if (semantics != null) {
					label.addAttribute("title", semantics.value());
					html.addAttribute("title", semantics.value());
				}
			}
		}
		return frm;
	}

	private Input findType(Class< ? > type) {
		if (Number.class.isAssignableFrom(type))
			return Input.NUMBER;
		if (Enum.class.isAssignableFrom(type))
			return Input.POPUP;
		if (URL.class.isAssignableFrom(type))
			return Input.URL;
		if (URI.class.isAssignableFrom(type))
			return Input.URL;
		if (Collection.class.isAssignableFrom(type))
			return Input.LIST;
		return Input.LINE;
	}

	Tag value(Form form, Entry entry, String model, Input input, Class< ? > type) {
		switch (input) {
			case LINE :
				return new Tag("input").addAttribute("type", "text");

			case TEXT :
				return new Tag("textarea");
			case CHECKBOX :
				return new Tag("input").addAttribute("type", "checkbox");
			case EMAIL :
				return new Tag("input").addAttribute("type", "email");
			case NUMBER :
				return new Tag("input").addAttribute("type", "number");

			default :
				return null;
		}
	}

	private String upperCaseFirstChar(String name) {
		if (name == null || name.isEmpty())
			return name;

		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	@Override
	public List<Form> getForms() {
		// TODO Auto-generated method stub
		return null;
	}
}
