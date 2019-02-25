package org.elastic.jdt.ls.core.internal.manifest;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.elastic.jdt.ls.core.internal.BasicFileDetector;
import org.elastic.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.elastic.jdt.ls.core.internal.PreferenceManager;

public class GradleManifestImporter extends AbstractProjectImporter {
	
	public static final String GRADLE_MANIFEST_FILE = "manifest.json";
	
	@Override
	public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		if (rootFolder == null) {
			return false;
		}
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (directories == null) {
			BasicFileDetector gradleManifestDetector = new BasicFileDetector(rootFolder.toPath(), GRADLE_MANIFEST_FILE).includeNested(false);
			directories = gradleManifestDetector.scan(monitor);
		}
		return !directories.isEmpty();
	}
	
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		
	}

}
