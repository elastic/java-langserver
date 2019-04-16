package org.elastic.jdt.ls.core.internal.ant;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URISyntaxException;

import org.eclipse.ant.core.AntCorePlugin;
import org.eclipse.ant.core.AntCorePreferences;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.URIUtil;

public class AntDefiningTaskNode {
	
	/*
	 * Sets the Java class path in org.apache.tools.ant.types.Path so that the classloaders defined by these "definer" tasks will have the correct
	 * classpath.
	 */
	public static void setJavaClassPath() {

		AntCorePreferences prefs = AntCorePlugin.getPlugin().getPreferences();
		URL[] antClasspath = prefs.getURLs();

		setJavaClassPath(antClasspath);
	}

	/*
	 * Sets the Java class path in org.apache.tools.ant.types.Path so that the classloaders defined by these "definer" tasks will have the correct
	 * classpath.
	 */
	public static void setJavaClassPath(URL[] antClasspath) {

		StringBuffer buff = new StringBuffer();
		File file = null;
		for (int i = 0; i < antClasspath.length; i++) {
			try {
				try {
					URL thisURL = URIUtil.toURI(antClasspath[i]).toURL();
					file = URIUtil.toFile(FileLocator.toFileURL(thisURL).toURI());
				}
				catch (URISyntaxException e) {
					file = new File(FileLocator.toFileURL(antClasspath[i]).getPath());
					e.printStackTrace();
				}
			}
			catch (IOException e) {
				continue;
			}
			buff.append(file.getAbsolutePath());
			buff.append("; "); //$NON-NLS-1$
		}

		org.apache.tools.ant.types.Path systemClasspath = new org.apache.tools.ant.types.Path(null, buff.substring(0, buff.length() - 2));
		org.apache.tools.ant.types.Path.systemClasspath = systemClasspath;
	}

}
