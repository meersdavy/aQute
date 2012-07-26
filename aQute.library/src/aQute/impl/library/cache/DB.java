package aQute.impl.library.cache;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import aQute.libg.reporter.*;

public abstract class DB extends ReporterAdapter {
	final static Timer	timer	= new Timer();
	final ReentrantLock	lock	= new ReentrantLock();
	final AtomicBoolean	open	= new AtomicBoolean();
	File				lockFile;
	long				lockTime;
	TimerTask			watcher	= null;

	DB() {
		super(System.out);
	}

	protected void setLockFile(File lf) {
		this.lockFile = lf;
	}

	public void begin() throws Exception {
		lock.lock();
		if (open.getAndSet(true))
			return;

		systemLock();
		open0();
	}

	public void end() {
		assert lock.getHoldCount() > 0;
		lock.unlock();
	}

	private void systemLock() throws Exception {
		trace("system lock");
		do {
			while (lockFile.exists()) {
				trace("waiting for file lock " + lockFile);
				Thread.sleep(500);
				if (lockFile.exists())
					lockFile.setLastModified(System.currentTimeMillis());
				else {}
			}
		} while (!lockFile.createNewFile());

		lockTime = lockFile.lastModified();
		lockFile.deleteOnExit();
		timer.schedule(watcher = new TimerTask() {

			@Override
			public void run() {
				if (lock.tryLock()) {
					try {
						if (lockTime != lockFile.lastModified())
							try {
								close();
							}
							catch (Exception e) {
								e.printStackTrace();
							}
					}
					finally {
						lock.unlock();
					}
				}
			}

		}, 500, 500);
	}

	private void unlockFile() {
		trace("system unlock");
		assert lockFile.exists();
		watcher.cancel();
		watcher = null;
		if (!lockFile.delete()) {
			error("Could not delete system lock file %s", lockFile);
		}
		assert !lockFile.exists();
	}

	public final void open() throws Exception {
		begin();
		end();
	}

	public final void close() throws Exception {
		lock.lock();
		try {
			if (open.getAndSet(false) == false)
				return;

			close0();
			unlockFile();
		}
		finally {
			lock.unlock();
		}
	}

	protected abstract void close0() throws Exception;

	protected abstract void open0() throws Exception;

	public boolean isOpen() {
		return open.get();
	}

}
