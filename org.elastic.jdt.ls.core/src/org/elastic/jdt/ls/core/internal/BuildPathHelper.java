package org.elastic.jdt.ls.core.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.handlers.InitHandler;

public class BuildPathHelper {
	
	private final InfoRecorder rootInfoRecorder;
	private JavaClientConnection connection;
	
	public BuildPathHelper(IPath rootPath, JavaClientConnection connection) {
		this.rootInfoRecorder = new InfoRecorder(rootPath.toFile());
		this.connection = connection;
	}

	public void IncludeAllJavaFiles() {
		Job job = new WorkspaceJob("Include java files") {

			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) {
				long start = System.currentTimeMillis();
				connection.sendStatus(ServiceStatus.Starting, "Begin to include all java files...");
				try {
					crawler(rootInfoRecorder);
					includeJavaFiles(rootInfoRecorder);
					JavaLanguageServerPlugin.logInfo("Include all Java paths in " + (System.currentTimeMillis() - start) + "ms");
					connection.sendStatus(ServiceStatus.Started, "Ready");
				} catch (Exception e) {
					JavaLanguageServerPlugin.logException("Include Java paths failed ", e);
					connection.sendStatus(ServiceStatus.Error, e.getMessage());
				}
				return Status.OK_STATUS;
			}
			
			@Override
			public boolean belongsTo(Object family) {
				return InitHandler.JAVA_LS_INITIALIZATION_JOBS.equals(family);
			}
			
		};
		job.setPriority(Job.DECORATE);
		job.setRule(ResourcesPlugin.getWorkspace().getRoot());
		job.schedule();
		
	}
	
	private void crawler(InfoRecorder infoRecorder) {
		
		File[] listOfFilesAndDirectory = infoRecorder.dir.listFiles();
		
		if (listOfFilesAndDirectory != null)
		{
			for (File file : listOfFilesAndDirectory)
			{	
				String fileName = FilenameUtils.getName(file.toString());
				if (fileName.startsWith(".") || fileName == "build") {
					continue;
				}
				if (file.isDirectory()) {
					InfoRecorder childInfoRecorder = new InfoRecorder(file);
					if (inBuildPath(childInfoRecorder)) {
						continue;
					} else {
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
	// top-down
	private void includeJavaFiles(InfoRecorder infoRecorder) {
		if (infoRecorder.shouldBeIncluded) {
			try {
				ProjectUtils.addSourcePath(infoRecorder.sourcePath, new IPath[0], infoRecorder.javaProject);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Fail to add source:" + infoRecorder.dir.getPath(), e);
			}
		} else {
			infoRecorder.children.forEach(c -> includeJavaFiles(c));
		}
	}
	
	
	private boolean inBuildPath(InfoRecorder recorder) {
		if (recorder.javaProject == null) {
			return false;
		}
		IClasspathEntry[] existingEntries;
		try {
			existingEntries = recorder.javaProject.getRawClasspath();
			for (IClasspathEntry entry : existingEntries) {
				if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					if (entry.getPath().equals(recorder.sourcePath)) {
						return true;
					} else {
						// do nothing
					}
				}
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Cannot get classpath for" + recorder.javaProject.toString(), e);
		}
		return false;
	}
	
	class InfoRecorder {
		File dir;
		IPath sourcePath;
		IJavaProject javaProject;
		boolean shouldBeIncluded = false;
		List<InfoRecorder> children = new ArrayList<InfoRecorder>();
		
		public InfoRecorder(File dir) {
			this.dir = dir;
			IPath path = new Path(dir.getPath());
			IProject project = findBelongedProject(path);
			if (project != null) {
				this.javaProject = JavaCore.create(project);
				IPath relativeSourcePath = path.makeRelativeTo(project.getLocation());
				this.sourcePath = relativeSourcePath.isEmpty() ? project.getFullPath() : project.getFolder(relativeSourcePath).getFullPath();
			}
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
	
}
