package aQute.service.user;

public interface Authenticator {
	String authenticate(String id, String domain, String assertion) throws Exception;
}
