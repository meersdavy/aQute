package aQute.impl.gitposthook.servlet;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.osgi.service.http.*;

import aQute.bnd.annotation.component.*;
import aQute.impl.gitposthook.Data.ImportData;
import aQute.impl.gitposthook.Data.PosthookData;
import aQute.impl.gitposthook.*;
import aQute.lib.hex.*;
import aQute.lib.json.*;
import aQute.service.logger.*;

/**
 * 
 *
 */
@Component(provide = {}, properties = {
	"alias=/github"
})
public class GithubPostHookServlet extends HttpServlet {
	private static final long	serialVersionUID	= 1L;
	JSONCodec					codec				= new JSONCodec();
	GithubWorker				worker;
	Messages					msgs;

	interface Messages extends LogMessages {

		INFO request(String ip, String s);

		ERROR invalidCharacter(int c, StringBuilder sb);

	}

	public void doPost(HttpServletRequest rq, HttpServletResponse rsp) {

		try {
			ImportData imp = new ImportData();
			imp.ip = rq.getRemoteAddr();
			imp.user = rq.getRemoteUser();
			imp.time = System.currentTimeMillis();

			String s = convertToJson(rq);

			msgs.request(imp.ip, s);

			PosthookData work = codec.dec().from(s).get(PosthookData.class);
			imp.posthook = work;
			worker.execute(imp);
			rsp.setStatus(HttpServletResponse.SC_OK);
			msgs.succeed(s);
		}
		catch (Exception e) {
			msgs.failed("posthook", e);
			rsp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	private String convertToJson(HttpServletRequest rq) throws IOException {
		Reader r = rq.getReader();
		StringBuilder sb = new StringBuilder();

		int c;
		while ((c = r.read()) >= 0) {
			if (c == '%') {
				char a = (char) r.read();
				char b = (char) r.read();
				c = Hex.nibble(a) * 16 + Hex.nibble(b);
			}
			if (c > 127) {
				msgs.invalidCharacter(c, sb);
			}
			sb.append((char) c);
		}
		sb.delete(0, 8);
		String s = sb.toString();
		return s;
	}

	@Reference
	void setWorker(GithubWorker worker) {
		this.worker = worker;
	}

	@Reference
	void setHttp(HttpService http) throws ServletException, NamespaceException {
		http.registerServlet("/github", this, null, null);
	}

	@Reference
	void setLog(Log log) throws ServletException, NamespaceException {
		msgs = log.logger(Messages.class);
	}
}