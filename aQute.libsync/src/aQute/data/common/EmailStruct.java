package aQute.data.common;

import aQute.service.data.*;

public class EmailStruct {
	@Match(value = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,4}", reason = "Improper email address")
	@Require()
	public String	email;
	public String	name;

}
