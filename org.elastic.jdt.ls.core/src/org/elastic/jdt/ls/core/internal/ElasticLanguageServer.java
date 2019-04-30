package org.elastic.jdt.ls.core.internal;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class ElasticLanguageServer implements IApplication {

  private volatile boolean shutdown;
  private long parentProcessId;
  private final Object waitLock = new Object();

	@Override
	public Object start(IApplicationContext context) throws Exception {

    ElasticJavaLanguageServerPlugin.startLanguageServer(this);
    synchronized(waitLock){
          while (!shutdown) {
            try {
              context.applicationRunning();
              ElasticJavaLanguageServerPlugin.logInfo("Main thread is waiting");
              waitLock.wait();
            } catch (InterruptedException e) {
            	ElasticJavaLanguageServerPlugin.logException(e.getMessage(), e);
            }
          }
    }
		return IApplication.EXIT_OK;
	}

	@Override
	public void stop() {
    synchronized(waitLock){
      waitLock.notifyAll();
    }
	}

	public void exit() {
    shutdown = true;
    ElasticJavaLanguageServerPlugin.logInfo("Shutdown received... waking up main thread");
    synchronized(waitLock){
      waitLock.notifyAll();
    }
  }

	public void setParentProcessId(long pid) {
		this.parentProcessId = pid;
	}

	/**
	 * @return the parentProcessId
	 */
	long getParentProcessId() {
		return parentProcessId;
	}
}
