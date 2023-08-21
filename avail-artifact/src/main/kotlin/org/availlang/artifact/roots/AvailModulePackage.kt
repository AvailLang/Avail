package org.availlang.artifact.roots

import java.io.File

/**
 * `AvailModulePackage` is an Avail package with a module representative.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an [AvailModulePackage].
 *
 * @param baseName
 *   The name of the module without the file extension.
 * @param fileExtension
 *   The file extension to use for the module. This defaults to `avail`
 */
class AvailModulePackage constructor(
	baseName: String,
	fileExtension: String = "avail"
) : AvailModule(baseName, fileExtension)
{
	/**
	 * The set of [AvailModule]'s to create in this module.
	 */
	private val otherModules = mutableSetOf<AvailModule>()

	/**
	 * Add an [AvailModule] to be added to this [AvailModulePackage].
	 *
	 * @param baseName
	 *   The name of the module without the file extension.
	 * @param fileExtension
	 *   The file extension to use for the module. This defaults to `avail`.
	 * @return
	 *   The created [AvailModule].
	 */
	@Suppress("Unused")
	fun addModule(
		baseName: String, fileExtension: String = "avail"): AvailModule =
			AvailModule(baseName, fileExtension).apply {
				this@AvailModulePackage.otherModules.add(this)
			}

	/**
	 * Add an [AvailModulePackage] to be added to this [AvailModulePackage].
	 *
	 * @param baseName
	 *   The name of the module without the file extension.
	 * @param fileExtension
	 *   The file extension to use for the module. This defaults to `avail`.
	 * @return
	 *   The created [AvailModulePackage].
	 */
	@Suppress("Unused")
	fun addModulePackage(
		baseName: String, fileExtension: String = "avail"): AvailModulePackage =
			AvailModulePackage(baseName, fileExtension).apply {
				this@AvailModulePackage.otherModules.add(this)
			}

	override fun create (directory: String)
	{
		File(directory).mkdirs()
		val modulePackage = "$directory${File.separator}$fileName"
		File(modulePackage).mkdirs()
		val module = File("$modulePackage${File.separator}$fileName")
		if (!module.exists())
		{
			module.writeText(fileContents)
		}
		// Create modules in this module package.
		otherModules.forEach {
			it.create(modulePackage)
		}
	}

	override fun hierarchyPrinter (level: Int, sb: StringBuilder): StringBuilder =
		sb.apply {
			val prefix = hierarchyPrinterPrefix(level)
			append(prefix)
			append(" ")
			append(fileName)
			append(prefix)
			append("－ ")
			append(fileName)
			otherModules.toList().sorted().forEach {
				it.hierarchyPrinter(level + 1, sb)
			}
		}
}
