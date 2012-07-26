package aQute.service.osgimetadata;

import java.util.*;

public class OSGi {
	public int								manifestVersion;
	public boolean							singleton;
	public String							symbolicName;
	public Map<String,Map<String,String>>	fragmentHost;
	public Map<String,Map<String,String>>	requireBundle;
}
