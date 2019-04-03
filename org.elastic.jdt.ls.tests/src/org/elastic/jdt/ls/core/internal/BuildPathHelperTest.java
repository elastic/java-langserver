package org.elastic.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.elastic.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Test;

/**
 * @author poytr1
 *
 */
public class BuildPathHelperTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testEclipseProject() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");

		IJavaProject javaProject = JavaCore.create(project);
		assertEquals(2, getSourceEntriesNum(javaProject));

		BuildPathHelper buildPathHelper = new BuildPathHelper(project.getLocation());
		buildPathHelper.IncludeAllJavaFiles();

		assertEquals(3, getSourceEntriesNum(javaProject));

	}

	private int getSourceEntriesNum(IJavaProject javaProject) throws JavaModelException {
		int sum = 0;
		IClasspathEntry[] existingEntries = javaProject.getRawClasspath();
		for (IClasspathEntry entry : existingEntries) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sum += 1;
			}
		}
		return sum;
	}

}
