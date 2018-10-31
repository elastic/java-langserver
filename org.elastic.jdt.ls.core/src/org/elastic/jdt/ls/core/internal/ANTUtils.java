package org.elastic.jdt.ls.core.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.UnknownElement;

import org.eclipse.ant.internal.ui.AntUtil;
import org.eclipse.ant.internal.ui.model.AntProjectNode;
import org.eclipse.ant.internal.ui.model.IAntModel;
import org.eclipse.ant.internal.ui.model.AntTaskNode;
import org.eclipse.ant.internal.ui.model.IAntElement;
import org.eclipse.ant.internal.ui.model.AntTargetNode;

public final class ANTUtils {
	
	private ANTUtils() {
		//No public instantiation
	}
	
	public static AntProjectNode getProjectNode(String filePath) {
		IAntModel fAntModel = AntUtil.getAntModel(filePath, false, false, true);
		AntProjectNode projectNode = fAntModel == null ? null : fAntModel.getProjectNode();
		if (fAntModel != null && projectNode != null) {
			return projectNode;
		} else {
			return null;
		}
	}
	
	public static void getJavacNodes(List<AntTaskNode> javacNodes, IAntElement parent) {
		if (!parent.hasChildren()) {
			return;
		}
		List<IAntElement> children = parent.getChildNodes();
		for (IAntElement node : children) {
			if (node instanceof AntTargetNode) {
				getJavacNodes(javacNodes, node);
			} else if (node instanceof AntTaskNode) {
				AntTaskNode task = (AntTaskNode) node;
				if ("javac".equals(task.getName())) { //$NON-NLS-1$
					javacNodes.add(task);
				}
			}
		}
	}

	public static List<?> resolveJavacTasks(List<?> javacNodes) {
		List<Object> resolvedJavacTasks = new ArrayList<>(javacNodes.size());
		Iterator<?> nodes = javacNodes.iterator();
		while (nodes.hasNext()) {
			AntTaskNode taskNode = (AntTaskNode) nodes.next();
			Task javacTask = taskNode.getTask();
			if (javacTask instanceof UnknownElement) {
				if (((UnknownElement) javacTask).getRealThing() == null) {
					javacTask.maybeConfigure();
				}

				resolvedJavacTasks.add(((UnknownElement) javacTask).getRealThing());
			} else {
				resolvedJavacTasks.add(javacTask);
			}
		}
		return resolvedJavacTasks;
	}


}
