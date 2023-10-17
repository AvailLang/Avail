package org.availlang.intellij.plugin.module

import avail.anvil.environment.AVAIL_STDLIB_ROOT_NAME
import avail.anvil.environment.GlobalEnvironmentSettings
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.location.AvailLibraries
import org.availlang.artifact.environment.location.AvailRepositories
import org.availlang.artifact.environment.location.ProjectHome
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.environment.project.*
import org.availlang.artifact.jar.AvailArtifactJar
import org.availlang.json.JSONWriter
import java.io.File

/**
 * The project template you get when you create a new Avail Project.
 *
 * Adapted to IDEA from [avail.anvil.manager.CreateProjectPanel]
 *
 * @author Raul Raja
 *
 */
object AvailProjectTemplate
{

	/**
	 * Represents a configuration for an Avail software project.
	 *
	 * @property projectLocation
	 *   The location of the project.
	 * @property fileName
	 *   The name of the configuration file.
	 * @property rootsDir
	 *   The directory where the project's root files are located.
	 * @property rootName
	 *   The name of the project's root file.
	 * @property importStyles
	 *   A flag indicating whether to import styles.
	 * @property importTemplates
	 *   A flag indicating whether to import templates.
	 * @property libraryName
	 *   The name of the library used in the project (optional).
	 * @property selectedLibrary
	 *   The selected library file (optional).
	 * @property environmentSettings
	 *  The global environment settings for the project.
	 */
	data class Config(
		val projectLocation: String,
		val fileName: String,
		val rootsDir: String,
		val rootName: String,
		val importStyles: Boolean,
		val importTemplates: Boolean,
		val libraryName: String?,
		val selectedLibrary: File?,
		val environmentSettings: GlobalEnvironmentSettings
	)

	/**
	 * Creates a new Avail project.
	 *
	 * @param config The configuration for the project.
	 */
	@JvmStatic
	fun create(config: Config)
	{
		val projectFilePath =
			"${config.projectLocation}/${config.fileName}.json"
		val configPath =
			AvailEnvironment.projectConfigPath(
				config.fileName,
				config.projectLocation
			)
		AvailProject.optionallyInitializeConfigDirectory(configPath)
		val localSettings = LocalSettings.from(File(configPath))
		AvailProjectV1(
			config.fileName,
			true,
			AvailRepositories(rootNameInJar = null),
			localSettings
		).apply {
			File(config.projectLocation).mkdirs()
			val rootsDir = config.rootsDir
			val rootName = config.rootName
			val rootLocationDir = rootsLocationDir(rootsDir, rootName)
			File("${config.projectLocation}/$rootLocationDir").mkdirs()

			val rootConfigDir = AvailEnvironment.projectRootConfigPath(
				config.fileName,
				rootName,
				config.projectLocation
			)
			val root =
				availProjectRoot(
					rootConfigDir,
					config,
					rootName,
					rootLocationDir
				)
			addRoot(root)
			optionallyInitializeConfigDirectory(rootConfigDir)
			config.selectedLibrary?.let { lib ->
				configureAvailStdLib(config, lib)
			}

			updateBackingProjectFile(projectFilePath, config)
		}
	}

	/**
	 * Updates the backing project file with the current [AvailProjectV1]
	 * instance.
	 *
	 * @param projectFilePath
	 *   The file path of the backing project file.
	 * @param config
	 *   The [configuration][Config] settings.
	 */
	private fun AvailProjectV1.updateBackingProjectFile(
		projectFilePath: String,
		config: Config
	)
	{
		if (projectFilePath.isNotEmpty())
		{
			// Update the backing project file.
			val writer = JSONWriter.newPrettyPrinterWriter()
			writeTo(writer)
			File(projectFilePath).writeText(writer.contents())
			config.environmentSettings.add(this, projectFilePath)
		}
	}

	/**
	 * Configures the Avail standard library for the given Avail project.
	 *
	 * @param config
	 *   The [configuration][Config] settings for the Avail project.
	 * @param lib
	 *   The file representing the Avail standard library.
	 */
	private fun AvailProjectV1.configureAvailStdLib(
		config: Config,
		lib: File
	)
	{
		val libName = config.libraryName ?: AVAIL_STDLIB_ROOT_NAME
		val libConfigDir = AvailEnvironment.projectRootConfigPath(
			config.fileName, libName, config.projectLocation
		)
		optionallyInitializeConfigDirectory(libConfigDir)
		val jar = AvailArtifactJar(lib.toURI())

		val rootManifest = jar.manifest.roots[AVAIL_STDLIB_ROOT_NAME]
		var sg = importStyles(config, jar)
		var tg = importTemplates(config, jar)
		val extensions = mutableListOf<String>()
		var description = ""
		rootManifest?.let {
			sg = it.styles
			tg = it.templates
			extensions.addAll(it.availModuleExtensions)
			description = it.description
		}
		val stdLib = AvailProjectRoot(
			libConfigDir,
			config.projectLocation,
			libName,
			AvailLibraries(
				"org/availlang/${lib.name}",
				Scheme.JAR,
				AVAIL_STDLIB_ROOT_NAME
			),
			LocalSettings(
				AvailEnvironment.projectRootConfigPath(
					config.fileName,
					libName,
					config.projectLocation
				)
			),
			sg,
			tg,
			extensions
		)
		stdLib.description = description
		stdLib.saveLocalSettingsToDisk()
		stdLib.saveTemplatesToDisk()
		stdLib.saveStylesToDisk()
		addRoot(stdLib)
	}

	/**
	 * Imports the templates from the given [AvailArtifactJar] and returns them as
	 * a [TemplateGroup].
	 *
	 * @param config
	 *   The [configuration][Config] object that specifies whether to import
	 *   templates.
	 * @param jar
	 *   The [AvailArtifactJar] from which to import the templates.
	 * @return
	 *   The imported templates as a [TemplateGroup].
	 */
	private fun importTemplates(
		config: Config,
		jar: AvailArtifactJar
	) = if (config.importTemplates)
	{
		jar.manifest.templatesFor(AVAIL_STDLIB_ROOT_NAME)
			?: TemplateGroup()
	}
	else
	{
		TemplateGroup()
	}

	/**
	 * Imports the styles for the given Avail artifact jar.
	 *
	 * @param config
	 *   The [configuration][Config] object containing import options.
	 * @param jar
	 *   The Avail artifact jar from which to import styles.
	 * @return
	 *   The imported [styling group][StylingGroup] if importStyles is enabled
	 *   in the config object, otherwise an empty styling group.
	 */
	private fun importStyles(
		config: Config,
		jar: AvailArtifactJar
	) = if (config.importStyles)
	{
		jar.manifest.stylesFor(AVAIL_STDLIB_ROOT_NAME)
			?: StylingGroup()
	}
	else
	{
		StylingGroup()
	}

	/**
	 * Creates a new [AvailProjectRoot] object initialized with the provided
	 * parameters.
	 *
	 * @param rootConfigDir
	 *   The root configuration directory.
	 * @param config
	 *   The project [configuration][Config].
	 * @param rootName
	 *   The [root name][AvailProjectRoot.name].
	 * @param rootLocationDir
	 *   The [root location directory][AvailProjectRoot.location].
	 * @return
	 *   The newly created [AvailProjectRoot] object.
	 */
	private fun availProjectRoot(
		rootConfigDir: String,
		config: Config,
		rootName: String,
		rootLocationDir: String
	) = AvailProjectRoot(
		rootConfigDir,
		config.projectLocation,
		rootName,
		ProjectHome(
			rootLocationDir,
			Scheme.FILE,
			config.projectLocation,
			null
		),
		LocalSettings(rootConfigDir),
		StylingGroup(),
		TemplateGroup()
	)

	/**
	 * Concatenates the given root directory and root name to form a new
	 * location directory.
	 *
	 * @param rootsDir
	 *   The root directory string.
	 * @param rootName
	 *   The root name string.
	 * @return
	 *   The location directory string formed by concatenating rootsDir and
	 *   rootName.
	 */
	private fun rootsLocationDir(rootsDir: String, rootName: String): String =
		if (rootsDir.isNotEmpty()) "$rootsDir/$rootName"
		else rootName

}
