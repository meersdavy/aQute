package aQute.service.locks;

import java.util.concurrent.locks.*;

public interface LockManager {

	Lock lock(String key, String value);

	String getKey(Lock lock);

	String getValue(Lock lock);

}
