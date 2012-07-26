package aQute.service.messaging;

public interface Dispatcher {
	void dispatch(String from, String to, String subject, Object messageData) throws Exception;
}
