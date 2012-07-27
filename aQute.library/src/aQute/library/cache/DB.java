package aQute.library.cache;

import java.io.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import aQute.libg.reporter.*;

public abstract class DB extends ReporterAdapter {
	final static Timer	timer		= new Timer(true);
	final ReentrantLock	lock		= new ReentrantLock();
	final AtomicBoolean	open		= new AtomicBoolean();
	long				TIMEOUT		= 60000;
	File				lockFile;
	long				lockTime;
	TimerTask			watcher		= null;
	Thread				shutdown	= new Thread("clear lock") {
										public void run() {
											try {
												System.out.println("exiting VM, ensuring lock deletion");
												if (watcher != null)
													lockFile.delete();
											}
											catch (Exception e) {
												e.printStackTrace();
											}
										}
									};
	long				useTime;

	DB() {
		super(System.out);
		setTrace(true);
	}

	protected void setLockFile(File lf) {
		this.lockFile = lf;
	}

	public void begin() throws Exception {
		alive();
		lock.lock();
		if (open.getAndSet(true))
			return;

		systemLock();
		open0();
	}

	public void alive() {
		useTime = System.currentTimeMillis();
	}

	public void end() {
		assert lock.getHoldCount() > 0;
		lock.unlock();
	}

	static class Watcher extends TimerTask {
		final WeakReference<DB>	db;
		final File				lockFile;

		Watcher(DB db) {
			this.db = new WeakReference<DB>(db);
			this.lockFile = db.lockFile;
		}

		@Override
		public void run() {
			DB db = this.db.get();

			if (db == null) {
				System.out.println("db gced, deleting lock");
				cancel(); // stop running
				lockFile.delete();
				return;
			}

			if (db.lock.tryLock()) {
				try {
					if (db.lockTime != db.lockFile.lastModified())
						try {
							db.close();
						}
						catch (Exception e) {
							e.printStackTrace();
						}
				}
				finally {
					db.lock.unlock();
				}
			}
		}
	}

	private void systemLock() throws Exception {
		trace("system lock");
		assert watcher == null;
		do {
			while (lockFile.exists()) {
				trace("waiting for file lock " + lockFile);
				Thread.sleep(500);
				if (lockFile.exists())
					lockFile.setLastModified(System.currentTimeMillis());
				else {}
			}
		} while (!lockFile.createNewFile());

		// Make sure we do not hold on to this
		Runtime.getRuntime().addShutdownHook(shutdown);

		lockTime = lockFile.lastModified();
		timer.schedule(watcher = new Watcher(this), 500, 500);
	}

	private void unlockFile() {
		trace("system unlock");
		assert lockFile.exists();
		watcher.cancel();
		watcher = null;
		if (!lockFile.delete()) {
			error("Could not delete system lock file %s", lockFile);
		}
		Runtime.getRuntime().removeShutdownHook(shutdown);
		assert !lockFile.exists();
	}

	public final void open() throws Exception {
		begin();
		end();
	}

	public final void close() throws Exception {
		lock.lock();
		try {
			if (open.getAndSet(false) == true)
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

	public void finalize() {
		try {
			System.out.println("Finalizer, ensuring lock deleted");
			if (watcher != null) {
				lockFile.delete();
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
