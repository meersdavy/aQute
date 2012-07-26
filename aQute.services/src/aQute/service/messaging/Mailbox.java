package aQute.service.messaging;

public interface Mailbox {
	/**
	 * An address consists a contiguous combination of letters, digits, and/or
	 * '-' or '_'. You can specify multiple addresses.
	 */
	String	ADDRESS	= "address";

	void deliver(Message message) throws Exception;
}
