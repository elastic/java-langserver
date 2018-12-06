package org.elastic.jdt.ls.core.internal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.elastic.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Before;
import org.junit.Test;

/**
 * @author poytr1
 *
 */
public class FullHandlerTest extends AbstractProjectsManagerBasedTest {

	private FullHandler handler;

	private IProject project;

	private IPackageFragmentRoot sourceFolder;

	private PreferenceManager preferenceManager;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		IJavaProject javaProject = JavaCore.create(project);
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		preferenceManager = mock(PreferenceManager.class);
		when(preferenceManager.getPreferences()).thenReturn(new Preferences());
		handler = new FullHandler(preferenceManager);
	}

	@Test
	public void testFullWithoutRef() throws Exception {
		//given
		//fulls on the System.out
		FullParams fullParams = getFullParams("src/java/Foo.java", false);

		//when
		Full full = handler.full(fullParams, monitor);

		//then
		assertNotNull(full);
		assertNotSame(0, full.getSymbols().size());
		assertSame(0, full.getReferences().size());
	}

	@Test
	public void testFullWithRef() throws Exception {
		//given
		//fulls on the System.out
		FullParams fullParams = getFullParams("src/java/Foo.java", true);

		//when
		Full full = handler.full(fullParams, monitor);

		//then
		assertNotNull(full);
		assertNotSame(0, full.getSymbols().size());
		assertNotSame(0, full.getReferences().size());
	}

	FullParams getFullParams(String file, boolean isRef) {
		URI uri = project.getFile(file).getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(fileURI);
		return new FullParams(textDocument, isRef);
	}

}
