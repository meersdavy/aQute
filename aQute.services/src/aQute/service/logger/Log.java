package aQute.service.logger;


public interface Log {
	<T> T logger(Class<T> specification);
}
