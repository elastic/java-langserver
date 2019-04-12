package org.elastic.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.elastic.jdt.ls.core.internal.manifest.GradleManifestImporter;
import org.junit.Test;


public class GradleManifestImporterTest extends AbstractGradleManifestBasedTest {

	@Test
	public void testImportSingleJavaProject() throws Exception {
		IProject project = importGradleManifestProject("single");
		assertTaskCompleted(GradleManifestImporter.IMPORTING_GRADLE_MANIFEST_PROJECTS);
	}

	@Test
	public void testImportAndroidProject() throws Exception {
		IProject project = importGradleManifestProject("android");
		IProject app = WorkspaceHelper.getProject("android.app");
		assertTaskCompleted(GradleManifestImporter.IMPORTING_GRADLE_MANIFEST_PROJECTS);
		assertIsJavaProject(app);
	}

	@Test
	public void importNestedManifestProject() throws Exception {
		List<IProject> projects = importProjects("manifest/nested");
		assertEquals(3, projects.size());//default + 2 gradle projects
		IProject manifest1 = WorkspaceHelper.getProject("manifest1");
		assertIsJavaProject(manifest1);
		IProject manifest2 = WorkspaceHelper.getProject("manifest2");
		assertIsJavaProject(manifest2);
	}


}
