package org.availlang.artifact.roots

import java.io.File

/**
 * Represents an Avail module file to be added. Will only be created if it does
 * not exist when the initialization runs.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property baseName
 *   The name of the module without the file extension.
 *
 * @constructor
 * Construct an [AvailModule].
 *
 * @param baseName
 *   The name of the module without the file extension.
 * @param fileExtension
 *   The file extension to use for the module. This defaults to `avail`.
 *   Do not prefix with ".".
 */
open class AvailModule constructor(
	private val baseName: String,
	fileExtension: String = "avail"): Comparable<AvailModule>
{
	/**
	 * The file name. *e.g. Avail.avail*.
	 */
	val fileName: String = "$baseName.$fileExtension"

	/**
	 * Raw module header comment. This is typically for a copyright. Will be
	 * wrapped in comment along with file name. If comment body is empty
	 * (*default*), will only provide the file name in the header comment.
	 */
	var moduleHeaderCommentBody: String = ""

	/**
	 * The list of module `Versions` to populate the `Versions` section of the
	 * module header.
	 */
	@Suppress("unused")
	var versions: List<String> = listOf()

	/**
	 * The list of Avail Modules this [AvailModule] will `Extend` for Avail
	 * Modules that `Use`/`Extend` this module as well as use in the `Body`
	 */
	@Suppress("unused")
	var extends: List<String> = listOf()

	/**
	 * The list of Avail Modules this [AvailModule] will be able to `Use`
	 * in the `Body` of this module..
	 */
	@Suppress("unused")
	var uses: List<String> = listOf()

	/**
	 * The file contents that will be written to the file upon a call to create.
	 */
	internal val fileContents: String get() =
		buildString {
			// File header comment
			append("/*\n")
			append(" * ")
			append(fileName)
			if (moduleHeaderCommentBody.isNotEmpty())
			{
				moduleHeaderCommentBody.split("\n").forEach {
					append("\n * ")
					append(it)
				}
			}
			append("\n */")

			// Module
			append("\n\nModule \"")
			append(baseName)
			append('"')

			// Versions
			if (versions.isNotEmpty())
			{
				append("\nVersions")
				append(versions.joinToString(",\n\t", "\n\t") { "\"$it\"" })
			}

			// Uses
			if (uses.isNotEmpty())
			{
				append("\nUses")
				append(uses.joinToString(",\n\t", "\n\t") { "\"$it\"" })
			}

			// Extends
			if (extends.isNotEmpty())
			{
				append("\nExtends")
				append(extends.joinToString(",\n\t", "\n\t") { "\"$it\"" })
			}
			append("\nBody\n")
		}

	/**
	 * Create the Avail Module File. This will do nothing if the file exists;
	 * will only be created if it does not exist when the initialization runs.
	 *
	 * @param directory
	 *  The location to place the Module.
	 */
	internal open fun create (directory: String)
	{
		val module = File("$directory${File.separator}$fileName")
		if (!module.exists())
		{
			File(directory).mkdirs()
			module.writeText(fileContents)
		}
	}

	/**
	 * Add the printable hierarchical position representation of this
	 * [AvailModule] in its respective [AvailRoot].
	 *
	 * @param level
	 *   The depth in the tree where this module sits.
	 * @param sb
	 *   The [StringBuilder] to add the printable representation to.
	 */
	open fun hierarchyPrinter (level: Int, sb: StringBuilder): StringBuilder =
		sb.apply {
			val prefix = hierarchyPrinterPrefix(level)
			append(prefix)
			append(" ")
			append(fileName)
		}

	/**
	 * Create the prefix string for each line printed in [hierarchyPrinter].
	 *
	 * @param level
	 *   The depth in the tree where this module sits.
	 */
	protected fun hierarchyPrinterPrefix(level: Int) =
		(0 until level)
			.map { "－" }
			.joinToString(prefix = "\n\t\t\t\t|", separator = "") { it }

	override fun compareTo(other: AvailModule): Int =
		fileName.compareTo(other.fileName)

	override fun equals(other: Any?): Boolean =
		when
		{
			this === other -> true
			other !is AvailModule -> false
			fileName != other.fileName -> false
			else -> true
		}

	override fun hashCode(): Int = fileName.hashCode()
}
