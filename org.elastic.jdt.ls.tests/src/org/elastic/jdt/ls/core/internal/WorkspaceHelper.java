package org.elastic.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

/**
 * @author Fred Bricon
 *
 */
public final class WorkspaceHelper {

	private WorkspaceHelper() {
		//No instances allowed
	}

	public static void initWorkspace() throws CoreException {
		JavaLanguageServerPlugin.getProjectsManager().initializeProjects(Collections.emptyList(), new NullProgressMonitor());
		assertEquals(1, getAllProjects().size());
	}

	public static IProject getProject(String name) {
		IProject project = getWorkspaceRoot().getProject(name);
		return project.exists() ? project : null;
	}

	public static void deleteAllProjects() {
		getAllProjects().forEach(p -> delete(p));
	}

	public static List<IProject> getAllProjects() {
		return Arrays.asList(getWorkspaceRoot().getProjects());
	}

	public static IWorkspaceRoot getWorkspaceRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	public static void delete(IProject project) {
		try {
			project.delete(true, new NullProgressMonitor());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

}