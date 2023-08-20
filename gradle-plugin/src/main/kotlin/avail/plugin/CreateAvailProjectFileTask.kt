package avail.plugin

import org.availlang.artifact.environment.location.AvailLocation
import org.availlang.artifact.environment.location.ProjectHome
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.environment.project.AvailProject
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * The task that creates the Avail project file.
 *
 * @author Richard Arriaga
 */
abstract class CreateAvailProjectFileTask: DefaultTask()
{
	/**
	 * Get the active [AvailExtension] from the host [Project].
	 */
	private val availExtension: AvailExtension get() =
		(project.extensions.getByName(AvailPlugin.AVAIL) as AvailExtension)

	/**
	 * The name of the [AvailProject] configuration file. This defaults to
	 * [AvailProject.CONFIG_FILE_NAME], "avail-config.json"
	 */
	@Input
	var fileName: String = AvailProject.CONFIG_FILE_NAME

	/**
	 * The [AvailLocation] to write the config file to. This is the written to
	 * the top level of the project directory by default.
	 */
	@Input
	var outputLocation: AvailLocation =
		ProjectHome(
			"",
			Scheme.FILE,
			project.projectDir.absolutePath,
			null)

	/**
	 * Write the file to the specified location.
	 */
	@TaskAction
	internal fun writeFile ()
	{
		val availProject = availExtension.createProject()
		File("${outputLocation.fullPathNoPrefix}${File.separator}$fileName")
			.writeText(availProject.fileContent)
	}
}
