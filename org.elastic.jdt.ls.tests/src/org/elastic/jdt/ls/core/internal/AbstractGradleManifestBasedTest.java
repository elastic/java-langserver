package org.elastic.jdt.ls.core.internal;

import static org.elastic.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertNotNull;

import org.eclipse.core.resources.IProject;
import org.elastic.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;

/**
 * @author poytr1
 *
 */
public class AbstractGradleManifestBasedTest extends AbstractProjectsManagerBasedTest {
	protected IProject importGradleManifestProject(String name) throws Exception {
		importProjects("manifest/" + name);
		IProject project = getProject(name);
		assertNotNull(project);
		return project;
	}
}
