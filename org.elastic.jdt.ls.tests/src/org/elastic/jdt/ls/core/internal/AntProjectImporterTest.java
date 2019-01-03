package org.elastic.jdt.ls.core.internal;

import org.junit.Test;

/**
 * @author poytr1
 *
 */
public class AntProjectImporterTest extends AbstractAntBasedTest {

	@Test
	public void testImportSimpleJavaProject() throws Exception {
		importAntProject("salut");
	}

}
