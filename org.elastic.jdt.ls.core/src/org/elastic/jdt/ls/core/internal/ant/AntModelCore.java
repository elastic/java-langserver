package org.elastic.jdt.ls.core.internal.ant;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com.ibm.icu.text.MessageFormat;

public class AntModelCore {

	private static AntModelCore inst;

	public static AntModelCore getDefault() {
		if (inst == null) {
			inst = new AntModelCore();
		}

		return inst;
	}

	private List<IAntModelListener> fModelChangeListeners = new ArrayList<>();

	private AntModelCore() {
	}

	public void addAntModelListener(IAntModelListener listener) {
		synchronized (fModelChangeListeners) {
			fModelChangeListeners.add(listener);
		}
	}

	public void removeAntModelListener(IAntModelListener listener) {
		synchronized (fModelChangeListeners) {
			fModelChangeListeners.remove(listener);
		}
	}

	public void notifyAntModelListeners(AntModelChangeEvent event) {
		Iterator<IAntModelListener> i;
		synchronized (fModelChangeListeners) {
			i = new ArrayList<>(fModelChangeListeners).iterator();
		}
		while (i.hasNext()) {
			i.next().antModelChanged(event);
		}
	}
}
