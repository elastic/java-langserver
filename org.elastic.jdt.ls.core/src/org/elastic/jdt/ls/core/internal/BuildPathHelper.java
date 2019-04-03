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
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;

public class BuildPathHelper {
	
	class InfoRecorder {
		File dir;
		boolean shouldBeIncluded = false;
		List<InfoRecorder> children = new ArrayList<InfoRecorder>();
	}
	
	private final File rootFolder;
	private final InfoRecorder rootInfoRecorder;
	
	public BuildPathHelper(IPath rootPath) {
		this.rootFolder = rootPath.toFile();
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
				if (FilenameUtils.getName(file.toString()).startsWith(".")) {
					continue;
				}
				if (file.isDirectory()) {
					if (inBuildPath(file)) {
						//
					} else {
						InfoRecorder childInfoRecorder = new InfoRecorder();
						childInfoRecorder.dir = file;
						infoRecorder.children.add(childInfoRecorder);
						crawler(childInfoRecorder);
					}
				} else if ("java".equals(FilenameUtils.getExtension(file.getName()))) {
					infoRecorder.shouldBeIncluded = true;
				}
			}
		}
		if (!infoRecorder.children.isEmpty() && !infoRecorder.shouldBeIncluded) {
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
			IPath path = new Path(infoRecorder.dir.getPath());
			IProject project = findBelongedProject(path);
			IPath relativeSourcePath = path.makeRelativeTo(project.getLocation());
			IPath sourcePath = relativeSourcePath.isEmpty() ? project.getFullPath() : project.getFolder(relativeSourcePath).getFullPath();
			try {
				ProjectUtils.addSourcePath(sourcePath, new IPath[0], JavaCore.create(project));
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				JavaLanguageServerPlugin.logException("Fail to add source:" + infoRecorder.dir.getPath(), e);
			}
		} else {
			infoRecorder.children.forEach(c -> includeJavaFiles(c));
		}
	}
	
	
	private boolean inBuildPath(File file) {
		IPath path = new Path(file.getPath());
		IProject project = findBelongedProject(path);
		if (project == null) {
			return false;
		}
		IClasspathEntry[] existingEntries;
		try {
			existingEntries = JavaCore.create(project).getRawClasspath();
			for (IClasspathEntry entry : existingEntries) {
				if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					IPath relativeSourcePath = path.makeRelativeTo(project.getLocation());
					IPath sourcePath = relativeSourcePath.isEmpty() ? project.getFullPath() : project.getFolder(relativeSourcePath).getFullPath();
					
					if (entry.getPath().equals(sourcePath)) {
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
