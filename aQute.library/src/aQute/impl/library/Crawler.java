package aQute.impl.library;

import java.util.*;

import aQute.impl.library.LibraryImpl.RevisionImpl;
import aQute.service.library.*;
import aQute.service.logger.*;
import aQute.service.store.*;

/**
 * Is responsible for updating Revision Processors. Each revision has a process
 * (and failed) list of processes it has gone through. The library, when it
 * creates a revision sets these processes. They can also be added by other
 * parties. This crawler will ensure that a process is finally executed some
 * day.
 */
public class Crawler extends Thread {
	final Map<String,RevisionProcessor>	processors	= new HashMap<String,RevisionProcessor>();
	final Store<RevisionImpl>			store;
	final LibraryImpl					library;

	interface Messages extends LogMessages {}

	Messages	msgs;

	public Crawler(Log logger, LibraryImpl lib, Store<RevisionImpl> revisions) {
		super("Library Crawler");
		this.store = revisions;
		this.library = lib;
		msgs = logger.logger(Messages.class);
		setDaemon(true);
	}

	synchronized void addProcessor(String process, RevisionProcessor processor) throws Exception {
		if (processors.containsKey(process))
			throw new IllegalArgumentException("Processor already registered");

		processors.put(process, processor);
	}

	synchronized void removeProcessor(RevisionProcessor rp) {
		processors.values().remove(rp);
	}

	boolean updateRevision(RevisionImpl revision) throws Exception {
		boolean foundOne = false;
		if (revision.process != null) {
			for (String process : revision.process) {

				RevisionProcessor p;
				synchronized (this) {
					p = processors.get(process);
					if (p == null)
						continue;
				}

				try {
					if (p.process(library, revision)) {
						revision.process.remove(process);
						store.find(revision).remove("process", process).update();
						foundOne = true;
						continue;
					}
				}
				catch (Exception e) {
					msgs.failed(process, e);
				}
				store.find(revision).remove("process", process).append("failed", process).update();
			}
		}
		return foundOne;
	}

	int redo(String process) throws Exception {
		return store.find("!(process=%s)", process).append("process", process).update();
	}

	public void run() {
		try {
			crawl();
		}
		catch (Exception e) {
			msgs.failed("Crawling", e);
			try {
				Thread.sleep(10000);
			}
			catch (InterruptedException e1) {
				interrupt();
			}
		}
	}

	void crawl() throws Exception {
		while (!interrupted()) {
			boolean atLeastOne = false;
			StringBuilder sb = new StringBuilder("|");
			for (String p : processors.keySet()) {
				sb.append("(process=").append(p).append(")");
			}
			if (sb.length() > 1) {
				Cursor<RevisionImpl> cursor = store.find(sb.toString());
				for (RevisionImpl r : cursor) {
					atLeastOne |= updateRevision(r);
				}
			}
			if (!atLeastOne)
				Thread.sleep(10000); // Do not overload the system with polling
		}
	}
}
