package aQute.service.logger;

public interface LogMessages {
	interface DEBUG {}

	interface WARNING {}

	interface INFO {}

	interface ERROR {}

	interface TRACE {}

	boolean isTraceOn();

	ERROR failed(String context, Throwable t);

	ERROR failed(String context);

	INFO succeed(String context);

	DEBUG step(String context);
}
