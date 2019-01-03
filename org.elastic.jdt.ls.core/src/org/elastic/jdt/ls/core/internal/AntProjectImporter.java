package org.elastic.jdt.ls.core.internal;

import org.apache.tools.ant.taskdefs.Javac;

import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.BasicFileDetector;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.ant.internal.ui.datatransfer.*;
import org.eclipse.ant.internal.ui.model.AntProjectNode;
import org.eclipse.ant.internal.ui.model.AntTaskNode;

public class AntProjectImporter extends AbstractProjectImporter {

	public static final String IMPORTING_ANT_PROJECTS = "Importing Ant project(s)";

	public static final String ANT_FILE = "build.xml";

	private Collection<Path> directories;

	@Override
	public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		if (rootFolder == null) {
			return false;
		}
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager != null && !preferencesManager.getPreferences().isImportMavenEnabled()) {
			return false;
		}
		if (directories == null) {
			BasicFileDetector antDetector = new BasicFileDetector(rootFolder.toPath(), ANT_FILE).includeNested(false);
			directories = antDetector.scan(monitor);
		}
		return !directories.isEmpty();
	}

	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		if (!applies(monitor)) {
			return;
		}
		SubMonitor subMonitor = SubMonitor.convert(monitor, 105);
		subMonitor.setTaskName(IMPORTING_ANT_PROJECTS);
		JavaLanguageServerPlugin.logInfo(IMPORTING_ANT_PROJECTS);

		ProjectCreator pc = new ProjectCreator();
		for (Path project: directories) {
			AntProjectNode antProjectNode = ANTUtils.getProjectNode(project.toString() + '/' + ANT_FILE);
			if (antProjectNode != null) {
				List<AntTaskNode> javacNodes = new ArrayList<>();
				ANTUtils.getJavacNodes(javacNodes, antProjectNode);
				List<?> javacTasks = ANTUtils.resolveJavacTasks(javacNodes);
				// TODO:pcxu add a configuration to figure out which javac task to use
				try {
					Javac javacTask = (Javac) javacTasks.get(0);
					IJavaProject javaProject = pc.createJavaProjectFromJavacNode(project.getFileName().toString(), javacTask, subMonitor);
					if (javaProject == null) {
						JavaLanguageServerPlugin.logError("ant project is null");
					}
				} catch (Exception e) {
					JavaLanguageServerPlugin.logException("import ant project error", e);
				}
			}

		}
		subMonitor.done();
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub

	}

}
