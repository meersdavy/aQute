package aQute.service.logger;

/**
 * Must be used to override the standard message generated from the method name.
 */
public @interface Format {
	String value();
}
