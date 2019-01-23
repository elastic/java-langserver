package org.elastic.jdt.ls.core.internal;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.elastic.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class EDefinitionHandlerTest extends AbstractProjectsManagerBasedTest {

	private EDefinitionHandler handler;
	private IProject project;

	@Before
	public void setUp() throws Exception {
		handler = new EDefinitionHandler(preferenceManager);
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
	}

	@Test
	public void testGetEmptyDefinition() throws Exception {
		SymbolLocator definition = handler.eDefinition(new TextDocumentPositionParams(new TextDocumentIdentifier("/foo/bar"), new Position(1, 1)), monitor);
		assertNull(definition);
	}

	@Test
	public void testAttachedSource() throws Exception {
		testClass("org.apache.commons.lang3.StringUtils", 20, 26);
	}

	@Test
	public void testNoClassContentSupport() throws Exception {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(true);
		String uri = ClassFileUtil.getURI(project, "org.apache.commons.lang3.StringUtils");
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(false);
		SymbolLocator definition = handler.eDefinition(new TextDocumentPositionParams(new TextDocumentIdentifier(uri), new Position(20, 26)), monitor);
		assertNull(definition.getLocation());
		assertEquals(SymbolKind.Class, definition.getSymbolKind());
		assertEquals("java.text.Normalizer", definition.getQname());
	}

	@Test
	public void test3rdPartyQname() throws Exception {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(false);
		URI uri = project.getFile("src/main/java/java/Foo.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);
		SymbolLocator definition = handler.eDefinition(new TextDocumentPositionParams(new TextDocumentIdentifier(fileURI), new Position(2, 38)), monitor);
		assertNull(definition.getLocation());
		assertEquals(SymbolKind.Class, definition.getSymbolKind());
		assertEquals("org.apache.commons.lang3.StringUtils", definition.getQname());
	}

	@Test
	public void testJDKQname() throws Exception {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(false);
		URI uri = project.getFile("src/main/java/java/Foo2.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);
		SymbolLocator definition = handler.eDefinition(new TextDocumentPositionParams(new TextDocumentIdentifier(fileURI), new Position(2, 21)), monitor);
		assertNull(definition.getLocation());
		assertEquals(SymbolKind.Class, definition.getSymbolKind());
		assertEquals("java.io.IOException", definition.getQname());
	}
	
	@Test
	public void testDisassembledSource() throws Exception {
		testClass("javax.tools.Tool", 6, 44);
	}

	private void testClass(String className, int line, int column) throws JavaModelException {
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		SymbolLocator definition = handler.eDefinition(new TextDocumentPositionParams(identifier, new Position(line, column)), monitor);
		assertNotNull(definition);
		assertNotNull(definition.getLocation().getUri());
		assertTrue(definition.getLocation().getRange().getStart().getLine() >= 0);
	}

}
