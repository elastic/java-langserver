package org.elastic.jdt.ls.core.internal.ant;

import java.io.File;
import java.net.URI;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.util.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.resources.ResourcesPlugin;

public final class AntLaunchingUtil {
	
	/**
	 * Returns the workspace file associated with the given path in the local file system, or <code>null</code> if none. If the path happens to be a
	 * relative path, then the path is interpreted as relative to the specified parent file.
	 * 
	 * Attempts to handle linked files; the first found linked file with the correct path is returned.
	 * 
	 * @param path
	 * @param buildFileParent
	 * @return file or <code>null</code>
	 * @see org.eclipse.core.resources.IWorkspaceRoot#findFilesForLocation(IPath)
	 */
	public static IFile getFileForLocation(String path, File buildFileParent) {
		if (path == null) {
			return null;
		}
		IPath filePath = new Path(path);
		IFile file = null;
		URI location = filePath.makeAbsolute().toFile().toURI();
		IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(location);
		if (files.length > 0) {
			file = files[0];
		}
		if (file == null) {
			// relative path
			File relativeFile = null;
			try {
				// this call is ok if buildFileParent is null
				relativeFile = FileUtils.getFileUtils().resolveFile(buildFileParent, path);
				filePath = new Path(relativeFile.getAbsolutePath());
				location = filePath.makeAbsolute().toFile().toURI();
				files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(location);
				if (files.length > 0) {
					file = files[0];
				} else {
					return null;
				}
			}
			catch (BuildException be) {
				return null;
			}
		}

		if (file.exists()) {
			return file;
		}
		File ioFile = file.getLocation().toFile();
		if (ioFile.exists()) {// needs to handle case insensitivity on WINOS
			files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(ioFile.toURI());
			if (files.length > 0) {
				return files[0];
			}
		}

		return null;
	}

}
