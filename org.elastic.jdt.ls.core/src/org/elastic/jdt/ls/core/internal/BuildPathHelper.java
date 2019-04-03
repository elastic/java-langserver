package org.elastic.jdt.ls.core.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;

public class BuildPathHelper {
	
	class InfoRecorder {
		File dir;
		boolean shouldBeIncluded = false;
		List<InfoRecorder> children = new ArrayList<InfoRecorder>();
	}
	
	private final File rootFolder;
	private final IJavaProject project;
	private final InfoRecorder rootInfoRecorder;
	
	public BuildPathHelper(IPath rootPath) {
		this.rootFolder = rootPath.toFile();
		this.project = JavaCore.create(findBelongedProject(rootPath));
		this.rootInfoRecorder = new InfoRecorder();
		this.rootInfoRecorder.dir = this.rootFolder;
		crawler(rootInfoRecorder);
	}
	
	private void crawler(InfoRecorder infoRecorder) {
		File[] listOfFilesAndDirectory = infoRecorder.dir.listFiles();
		
		if (listOfFilesAndDirectory != null)
		{
			for (File file : listOfFilesAndDirectory)
			{
				if (file.isDirectory()) {
					InfoRecorder childInfoRecorder = new InfoRecorder();
					childInfoRecorder.dir = file;
					infoRecorder.children.add(childInfoRecorder);
					if (inBuildPath(file)) {
						childInfoRecorder.shouldBeIncluded = true;
					} else {
						crawler(childInfoRecorder);
					}
				} else if ("java".equals(FilenameUtils.getExtension(file.getName()))) {
					infoRecorder.shouldBeIncluded = true;
				}
			}
		}
		if (!infoRecorder.shouldBeIncluded) {
			boolean childrenAllIncluded = true;
			for (InfoRecorder child: infoRecorder.children) {
				childrenAllIncluded &= child.shouldBeIncluded;
			}
			infoRecorder.shouldBeIncluded = childrenAllIncluded;
		}

	}
	
	public void IncludeAllJavaFiles() {
		includeJavaFiles(this.rootInfoRecorder);
	}
	
	// top-down
	private void includeJavaFiles(InfoRecorder infoRecorder) {
		if (infoRecorder.shouldBeIncluded) {
			try {
				ProjectUtils.addSourcePath(new Path(infoRecorder.dir.getPath()), new IPath[0], this.project);
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				JavaLanguageServerPlugin.logException("Fail to add source:" + infoRecorder.dir.getPath(), e);
			}
		} else {
			infoRecorder.children.forEach(c -> includeJavaFiles(c));
		}
	}
	
	
	private boolean inBuildPath(File path) {
		IClasspathEntry[] existingEntries;
		try {
			existingEntries = project.getRawClasspath();
			for (IClasspathEntry entry : existingEntries) {
				if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					if (entry.getPath().toFile().equals(path)) {
						return true;
					} else {
						// do nothing
					}
				}
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Cannot get classpath for" + project.toString(), e);
		}
		return false;
	}
	
	private IProject findBelongedProject(IPath sourceFolder) {
		List<IProject> projects = Stream.of(ProjectUtils.getAllProjects()).filter(ProjectUtils::isJavaProject).sorted(new Comparator<IProject>() {
			@Override
			public int compare(IProject p1, IProject p2) {
				return p2.getLocation().toOSString().length() - p1.getLocation().toOSString().length();
			}
		}).collect(Collectors.toList());

		for (IProject project : projects) {
			if (project.getLocation().isPrefixOf(sourceFolder)) {
				return project;
			}
		}
		return null;
	}

}
