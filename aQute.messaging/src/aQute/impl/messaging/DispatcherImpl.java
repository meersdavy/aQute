package aQute.impl.messaging;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.*;

import aQute.bnd.annotation.component.*;
import aQute.impl.messaging.DispatcherImpl.Config;
import aQute.lib.collections.*;
import aQute.lib.converter.*;
import aQute.lib.json.*;
import aQute.libg.glob.*;
import aQute.service.logger.*;
import aQute.service.messaging.*;

@Component(designateFactory = Config.class)
public class DispatcherImpl implements Dispatcher {
	static TypeReference<Set<String>>	setRef		= new TypeReference<Set<String>>() {};
	static Pattern						ADDRESS		= Pattern
															.compile("([a-zA-Z0-9-_.]{3,20})|([a-zA-Z0-9-_.?*]{3,20})(@([a-zA-Z0-9-_.]{3,40}))?");
	static Converter					converter	= new Converter();
	static JSONCodec					codec		= new JSONCodec();

	Messages							msgs;

	interface Messages extends LogMessages {

		ERROR deliveryFailed(MessageImpl m, Mailbox t, Exception e);

		ERROR invalidAddress(String address, Pattern aDDRESS2);

	}

	interface MailboxProperties {
		Set<String> address();
	}

	interface Config {

	}

	Config						config;

	MultiMap<String,Mailbox>	fixed		= new MultiMap<String,Mailbox>();
	MultiMap<Mailbox,Glob>		wildcards	= new MultiMap<Mailbox,Glob>();

	@Activate
	void activate(Map<String,Object> properties) throws Exception {
		config = converter.convert(Config.class, properties);
	}

	@Override
	public void dispatch(String from, String to, String subject, Object payLoad) throws Exception {
		String data = codec.enc().put(payLoad).toString();

		MessageImpl m = new MessageImpl(from, to, subject, data);
		deliverFixed(fixed, m);
		deliverWildcards(wildcards, m);
	}

	private void deliverFixed(MultiMap<String,Mailbox> mboxes, MessageImpl m) {
		List<Mailbox> snapshot;
		synchronized (this) {
			List<Mailbox> l = mboxes.get(m.to());
			if (l == null || l.isEmpty())
				return;

			snapshot = new ArrayList<Mailbox>(l);
		}

		for (Mailbox t : snapshot) {
			try {
				t.deliver(m);
			}
			catch (Exception e) {
				msgs.deliveryFailed(m, t, e);
			}
		}
	}

	private synchronized void deliverWildcards(MultiMap<Mailbox,Glob> wc, MessageImpl m) {
		String to = m.to();
		for (Entry<Mailbox,List<Glob>> t : wc.entrySet()) {
			try {
				for (Glob g : t.getValue())
					if (g.matcher(to).matches())
						t.getKey().deliver(m);
			}
			catch (Exception e) {
				msgs.deliveryFailed(m, t.getKey(), e);
			}
		}
	}

	@Reference
	void setLog(Log log) {
		msgs = log.logger(Messages.class);
	}

	@Reference(type = '*')
	synchronized void addMailbox(Mailbox mailbox, Map<String,Object> ref) throws Exception {
		Set<String> addresses = converter.convert(setRef, ref);
		for (String address : addresses) {
			Matcher matcher = ADDRESS.matcher(address);
			if (matcher.matches()) {
				if (matcher.group(1) != null)
					fixed.add(address, mailbox);
				else {
					Glob glob = new Glob(address);
					wildcards.add(mailbox, glob);
				}
			} else {
				msgs.invalidAddress(address, ADDRESS);
			}
		}
	}

	synchronized void removeMailbox(Mailbox box, Map<String,Object> ref) throws Exception {
		Set<String> addresses = converter.convert(setRef, ref);
		for (String address : addresses) {
			fixed.remove(address, box);
			wildcards.remove(box);
		}
	}
}
