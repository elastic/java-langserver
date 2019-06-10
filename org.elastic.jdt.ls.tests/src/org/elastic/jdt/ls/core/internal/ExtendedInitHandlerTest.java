package org.elastic.jdt.ls.core.internal;

import org.junit.Assert;
import org.junit.Test;

import org.eclipse.lsp4j.InitializeParams;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.jobs.Job;
import org.elastic.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;

public class ExtendedInitHandlerTest extends AbstractProjectsManagerBasedTest {
	
	@Test
	public void testCancelInitJob() throws Exception {
		ExtendedInitHandler handler = new ExtendedInitHandler(projectsManager, preferenceManager);
		InitializeParams params = new InitializeParams();
		File file = copyFiles("maven/salut", true);
		String rootURI = file.toURI().toString();
		params.setRootUri(rootURI);
		handler.initialize(params);        
		Job[] initJobs = Job.getJobManager().find(ExtendedInitHandler.JAVA_LS_INITIALIZATION_JOBS);
		Assert.assertSame(1, initJobs.length);
		Job initJob = initJobs[0];
		Assert.assertSame(Job.RUNNING, initJob.getState());
		ExtendedInitHandler.cancelInitJobFromURI(rootURI);
        	// Wait for cancelling for initialize job
		TimeUnit.MILLISECONDS.sleep(100);
		Assert.assertSame(Job.NONE, initJob.getState());
	}

}
