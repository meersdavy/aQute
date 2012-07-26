package aQute.service.messaging;

public interface Message {
	String to();

	String from();

	String subject();

	public <T> T getContent(Class<T> c) throws Exception;
}
