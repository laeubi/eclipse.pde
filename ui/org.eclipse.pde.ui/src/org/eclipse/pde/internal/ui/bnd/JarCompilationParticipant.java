package org.eclipse.pde.internal.ui.bnd;

import aQute.bnd.osgi.Jar;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.compiler.BuildContext;
import org.eclipse.jdt.core.compiler.CompilationParticipant;
import org.eclipse.pde.internal.core.WorkspaceModelManager;
import org.eclipse.pde.internal.core.natures.PDE;

/**
 * CompilationParticipant that incrementally updates the jar for the project (if
 * present)
 */
public class JarCompilationParticipant extends CompilationParticipant {

	private static QualifiedName bnd_jar = new QualifiedName("pde", "bnd-jar");

	@Override
	public boolean isActive(IJavaProject project) {
		IProject iproject = project.getProject();
		if (!iproject.isOpen() || !PDE.hasPluginNature(iproject)
				|| WorkspaceModelManager.isBinaryProject(project.getProject())) {
			return false;
		}
		return true;
	}

	@Override
	public void buildStarting(BuildContext[] files, boolean batch) {
		if (batch) {
			// we can just discard whole state?!
		} else {
			// handle individual files
			for (BuildContext buildContext : files) {
				if (buildContext.isTestCode()) {
					// we are not interested in test code...
					continue;
				}
				IFile file = buildContext.getFile();
				// Update the file!
				Jar jar = new Jar("This is the project jar");
				// TODO full path!
				jar.putResource(file.getProjectRelativePath().toString(), new FileResource(file));
			}
		}
	}

	public static Jar getProjectJar(IJavaProject javaProject, boolean create) {
		if (javaProject == null) {
			return null;
		}
		IProject project = javaProject.getProject();
		if (project == null) {
			return null;
		}
		try {
			Object sessionProperty = project.getSessionProperty(bnd_jar);
			if (sessionProperty instanceof Jar) {
				return (Jar) sessionProperty;
			}
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Jar jar = new Jar(project.getName());
		try {
			IClasspathEntry[] classpath = javaProject.getRawClasspath();
			for (IClasspathEntry entry : classpath) {
				entry.getOutputLocation();
			}
		} catch (JavaModelException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			project.setSessionProperty(bnd_jar, jar);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

}
