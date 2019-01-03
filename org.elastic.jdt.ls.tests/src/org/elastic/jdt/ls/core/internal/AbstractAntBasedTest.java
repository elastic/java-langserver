package org.elastic.jdt.ls.core.internal;

import static org.elastic.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertNotNull;

import org.eclipse.core.resources.IProject;
import org.elastic.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;

/**
 * @author Pengcheng Xu
 *
 */
public abstract class AbstractAntBasedTest extends AbstractProjectsManagerBasedTest {

    protected IProject importAntProject(String name) throws Exception {
        importProjects("ant/"+name);
		IProject project = getProject(name);
		assertNotNull(project);
		return project;
	}

}