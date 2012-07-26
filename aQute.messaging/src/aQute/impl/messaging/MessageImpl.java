package aQute.impl.messaging;

import java.io.*;

import aQute.lib.json.*;
import aQute.service.messaging.*;

public class MessageImpl implements Message, Serializable {
	private static final long	serialVersionUID	= 1L;
	static JSONCodec			codec				= new JSONCodec();

	final String				from;
	final String				to;
	final String				subject;
	final String				data;

	public MessageImpl(String from, String to, String subject, String data) {
		this.from = from;
		this.to = to;
		this.subject = subject;
		this.data = data;
	}

	@Override
	public String to() {
		return to;
	}

	@Override
	public String from() {
		return from;
	}

	@Override
	public String subject() {
		// TODO Auto-generated method stub
		return subject;
	}

	@Override
	public <T> T getContent(Class<T> c) throws Exception {
		if (c == String.class)
			return c.cast(data);

		return codec.dec().from(data).get(c);
	}

}
