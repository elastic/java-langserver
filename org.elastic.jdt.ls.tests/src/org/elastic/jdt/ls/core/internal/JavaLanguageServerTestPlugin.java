package org.elastic.jdt.ls.core.internal;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * @author snjeza
 *
 */
public class JavaLanguageServerTestPlugin implements BundleActivator {

	public static final String PLUGIN_ID = "org.elastic.jdt.ls.tests";

	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		TestVMType.setTestJREAsDefault();
		JavaCore.initializeAfterLoad(new NullProgressMonitor());
	}

	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
	}

}