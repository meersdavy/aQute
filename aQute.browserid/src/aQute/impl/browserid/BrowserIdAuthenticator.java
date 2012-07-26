package aQute.impl.browserid;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.net.ssl.*;

import aQute.bnd.annotation.component.*;
import aQute.lib.json.*;

@Component
public class BrowserIdAuthenticator implements aQute.service.user.Authenticator {
	static JSONCodec	codec	= new JSONCodec();

	public enum Status {
		okay, failure;
	}

	public static class Response {
		public String				status;	// "status":"okay",
		public String				email;		// "email": "lloyd@example.com",
		public String				audience;	// "audience":
												// "https://mysite.com",
		public long					expires;	// "expires": 1308859352261,
		public String				issuer;	// "issuer": "browserid.org"
		public Map<String,Object>	__extra;
	}

	public String authenticate(String id, String audience, String assertion) throws Exception {
		StringBuilder sb = new StringBuilder("audience=");
		sb.append(URLEncoder.encode(audience, "UTF-8"));
		sb.append("&assertion=");
		sb.append(URLEncoder.encode(assertion, "UTF-8"));

		byte[] data = sb.toString().getBytes("UTF-8");

		HttpsURLConnection https = (HttpsURLConnection) new URL("https://browserid.org/verify").openConnection();
		https.setDoOutput(true);
		https.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
		https.setRequestProperty("Content-Length", data.length + "");
		https.setRequestMethod("POST");
		https.getOutputStream().write(data);

		InputStream in = https.getInputStream();
		Response response = codec.dec().from(in).get(Response.class);
		boolean authenticated = response.status.equals("okay");
		if (authenticated) {
			if (id == null || id.isEmpty() || id.equals(response.email))
				return response.email;

		}
		return null;
	}

}
