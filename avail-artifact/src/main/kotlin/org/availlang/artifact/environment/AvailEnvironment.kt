package org.availlang.artifact.environment

import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProjectRoot
import java.io.File
import java.lang.System.getenv

/**
 * Contains state and behavior for managing the Avail environment on a computer.
 *
 * @author Richard Arriaga
 */
@Suppress("unused")
object AvailEnvironment
{
	/**
	 * The Avail home directory inside the user's home directory.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	val availHome: String get() = getenv("AVAIL_HOME")
		?: "${System.getProperty("user.home")}${File.separator}.avail"

	/**
	 * The repositories directory inside the [Avail home directory][availHome]
	 * where the repositories, the persistent Avail indexed files of compiled
	 * modules, are stored.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	val availHomeRepos: String get() =
		"$availHome${File.separator}repositories"

	/**
	 * The libraries directory inside the [Avail home directory][availHome]
	 * where imported Avail libraries are stored.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	val availHomeLibs: String get() =
		"$availHome${File.separator}libraries"

	/**
	 * The Avail SDK directory inside the [Avail home directory][availHome]
	 * where the globally available Avail SDK is stored.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	val availSdk: String get() = "$availHome${File.separator}sdk"

	/**
	 * The name of the top level configuration directory for an [AvailProject].
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	const val projectConfigDirectory = ".avail"

	/**
	 * Construct the absolute path of the [AvailProject] configuration files
	 * directory. The project configuration directory name is the same name as
	 * the project's associated config file without the `.json` extension.
	 *
	 * @param projectFileName
	 *   The name of the project file.
	 * @param projectPath
	 *   The absolute path to the [AvailProject] directory.
	 * @return
	 *   The absolute path to the [AvailProject] configuration files directory.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun projectConfigPath (
		projectFileName: String,
		projectPath: String
	): String =
		"$projectPath${File.separator}$projectConfigDirectory" +
			"${File.separator}${projectFileName.removeSuffix(".json")}"

	/**
	 * Construct the absolute path of the [AvailProject] configuration files
	 * directory for a particular [AvailProjectRoot] inside the
	 * [project configuration directory][projectConfigPath]. The name of the
	 * root's configuration is the same as the [AvailProjectRoot.name].
	 *
	 * @param projectFileName
	 *   The name of the project file.
	 * @param rootName
	 *   The [AvailProjectRoot.name] of the root the config directory is for.
	 * @param projectPath
	 *   The absolute path to the [AvailProject] directory.
	 * @return
	 *   The absolute path to the [AvailProject] configuration files directory.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun projectRootConfigPath (
		projectFileName: String,
		rootName: String,
		projectPath: String
	): String = "$projectPath${File.separator}$projectConfigDirectory" +
		"${File.separator}${projectFileName.removeSuffix(".json")}" +
		"${File.separator}$rootName"

	/**
	 * Add the
	 *   1. [`.avail` home directory][availHomeLibs]
	 *   2. [`.avail/repositories` repositories directory][availHomeRepos]
	 *   3. [`.avail/libraries` libraries directory][availHomeLibs]
	 *
	 * in the user's home directory if it doesn't already exist. Also adds the ;[]
	 */
	@Suppress("unused")
	fun optionallyCreateAvailUserHome ()
	{
		val availRepos = File(availHomeRepos)
		if (!availRepos.exists())
		{
			availRepos.mkdirs()
		}
		val availLibs = File(availHomeLibs)
		if (!availLibs.exists())
		{
			availLibs.mkdirs()
		}
		val availWb = File(availSdk)
		if (!availWb.exists())
		{
			availWb.mkdirs()
		}
	}

	/**
	 * Answer the root directory path of the Avail project from the provided
	 * string location. If the location is empty, it is presumed this is being
	 * run from the Avail project home.
	 *
	 * @param location
	 *   The relative string path to the Avail project home.
	 * @return
	 *   The absolute path to the Avail project home.
	 */
	@Suppress("unused")
	fun getProjectRootDirectory (location: String): String =
		if (location.isEmpty())
		{
			File("").absolutePath
		}
		else
		{
			File(location).apply {
				if (!isDirectory)
				{
					throw RuntimeException("")
				}
			}.absolutePath
		}
}
