package aQute.impl.gravatar;

import javax.servlet.http.*;

import aQute.bnd.annotation.component.*;
import aQute.libg.cryptography.*;
import aQute.service.rest.*;

@Component
public class Gravatar implements ResourceManager {

	interface gravatarOptions extends Options {
		int s(); // size
	}

	public void getGravatar(gravatarOptions opts, String email) throws Exception {
		// TODO verify is correct email
		email = email.toLowerCase();
		Digester<MD5> digester = MD5.getDigester();
		digester.write(email.getBytes("UTF-8"));
		String asHex = digester.digest().asHex();
		HttpServletResponse response = opts._response();
		String query = "";
		if (opts.s() > 0)
			query = "?s=" + opts.s();

		response.setHeader("Location", "http://www.gravatar.com/avatar/" + asHex.toLowerCase() + query);
		response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
	}
}
