package com.avail.build.modules

import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import com.avail.build.relocateGeneratedPropertyFiles
import com.avail.build.generateBootStrap

/**
 * The [dependencies][ModuleDependencies] for the `avail-bootstrap` module
 * `build.gradle.kts` file's `dependencies` section.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object AvailBootstrapModule: ModuleDependencies(
	implementations = listOf(Libraries.jsr305))
{
	/**
	 * Copy the generated bootstrap property files into the build directory, so that
	 * the executable tools can find them as resources..
	 *
	 * @param project
	 *   The entire [Project] the build script is using.
	 * @param task
	 *   The [Copy] task in which this code is executed.
	 */
	fun relocateGeneratedPropertyFiles (project: Project, task: Copy)
	{
		project.relocateGeneratedPropertyFiles(task)
	}

	/**
	 * Generate the new bootstrap Avail modules for the current locale and copy them
	 * to the appropriate location for distribution.
	 *
	 * @param project
	 *   The entire [Project] the build script is using.
	 * @param task
	 *   The [Copy] task in which this code is executed.
	 */
	fun generateBootStrap(project: Project, task: Copy)
	{
		project.generateBootStrap(task)
	}
}
