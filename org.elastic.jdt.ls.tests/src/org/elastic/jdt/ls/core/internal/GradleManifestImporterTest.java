package org.elastic.jdt.ls.core.internal;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.JavaCore;
import org.junit.Test;

/**
 * @author poytr1
 *
 */
public class GradleManifestImporterTest extends AbstractGradleManifestBasedTest {

	@Test
	public void testImportSingleJavaProject() throws Exception {
		IProject project = importGradleManifestProject("single");
		assert (project.hasNature(JavaCore.NATURE_ID));

	}

}
