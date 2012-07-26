package aQute.service.library;

import aQute.service.library.Library.Revision;

public interface RevisionProcessor {
	boolean process(Library lib, Revision revision) throws Exception;
}
