package aQute.impl.metadata.osgi;

import java.io.*;
import java.util.*;

import org.osgi.service.indexer.*;

import aQute.bnd.annotation.component.*;
import aQute.service.filecache.*;
import aQute.service.library.Library.Revision;
import aQute.service.library.*;
import aQute.service.reporter.*;

/**
 * 
 */
@Component(properties = "metadata=osgi-0")
public class OSGiMetadataProvider implements MetadataProvider {
	FileCache			fileCache;
	ResourceIndexer		indexer;
	Map<String,String>	properties;

	@Activate
	void activate(Map<String,String> properties) {
		this.properties = properties;
	}

	@Override
	public Report parser(Revision revision) throws Exception {
		File file = fileCache.get(revision.url.toString(), revision.url);
		OSGiMetadataParser parser = new OSGiMetadataParser(file, revision, indexer, properties);
		parser.run();
		return parser;
	}

	@Reference
	void setFileCache(FileCache cache) {
		this.fileCache = cache;
	}

	@Reference
	void setResourceIndexer(ResourceIndexer ri) {
		this.indexer = ri;
	}
}
