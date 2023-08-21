package org.availlang.artifact.roots

import org.availlang.artifact.environment.location.AvailLocation
import org.availlang.artifact.environment.project.StylingGroup
import org.availlang.artifact.environment.project.TemplateGroup
import org.availlang.artifact.manifest.AvailRootManifest
import java.security.MessageDigest

/**
 * `CreateAvailRoot` is an [AvailRoot] that is intended to be created.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an [AvailRoot].
 *
 * @param name
 *   The name of the root.
 * @param location
 *   The [AvailLocation] of the root.
 * @param digestAlgorithm
 *   The [MessageDigest] algorithm to use to create the digests for all the
 *   root's contents. This must be a valid algorithm accessible from
 *   [java.security.MessageDigest.getInstance].
 * @param availModuleExtensions
 *   The file extensions that signify files that should be treated as Avail
 *   modules.
 * @param entryPoints
 *   The Avail entry points exposed by this root.
 * @param templates
 *   The templates that should be available when editing Avail source
 *   modules in the workbench.
 * @param styles
 *   The [StylingGroup] for this [AvailRoot].
 * @param description
 *   An optional description of the root.
 * @param action
 *   A lambda that accepts this [AvailRoot] and is executed after all roots have
 *   been added.
 */
class CreateAvailRoot constructor(
	name: String,
	location: AvailLocation,
	digestAlgorithm: String = "SHA-256",
	availModuleExtensions: MutableList<String> = mutableListOf("avail"),
	entryPoints: MutableList<String> = mutableListOf(),
	templates: TemplateGroup = TemplateGroup(),
	styles: StylingGroup = StylingGroup(),
	description: String = "",
	action: (AvailRoot) -> Unit = {}
) : AvailRoot(
	name,
	location,
	digestAlgorithm,
	availModuleExtensions,
	entryPoints,
	templates,
	styles,
	description,
	action)
{
	/**
	 * Construct a [CreateAvailRoot].
	 *
	 * @param location
	 *   The [AvailLocation] of the root.
	 * @param manifestRoot
	 *   The [AvailRootManifest] that describes this [AvailRoot].
	 * @param action
	 *   A lambda that accepts this [AvailRoot] and is executed after all roots have
	 *   been added.
	 */
	@Suppress("unused")
	constructor(
		location: AvailLocation,
		manifestRoot: AvailRootManifest,
		action: (AvailRoot) -> Unit
	): this(
		manifestRoot.name,
		location,
		manifestRoot.digestAlgorithm,
		manifestRoot.availModuleExtensions,
		manifestRoot.entryPoints,
		manifestRoot.templates,
		manifestRoot.styles,
		manifestRoot.description,
		action)

	override val configString: String get() = buildString {
		append("\n\t\t$name")
		append("\n\t\t\tRoot Contents:")
		this@CreateAvailRoot.appendRootHierarchy(this)
	}

	/**
	 * Add an [AvailModule] with the given name to the top level of this
	 * [CreateAvailRoot].
	 *
	 * @param name
	 *   The name of the [AvailModule] to create and add.
	 * @param extension
	 *   The Module's file extension. Defaults to `"avail"`.
	 *   Do not prefix with ".".
	 * @return
	 *   The created [AvailModule].
	 */
	@Suppress("unused")
	fun module (name: String, extension: String = "avail"): AvailModule =
		AvailModule(name, extension).apply {
			modules.add(this)
		}

	/**
	 * Add an [AvailModulePackage] with the given name to the top level of this
	 * [CreateAvailRoot].
	 *
	 * @param name
	 *   The name of the [AvailModulePackage] to create and add.
	 * @param extension
	 *   The Module's file extension. Defaults to `"avail"`.
	 *   Do not prefix with ".".
	 * @return
	 *   The created [AvailModulePackage].
	 */
	@Suppress("unused")
	fun modulePackage (
		name: String, extension: String = "avail"): AvailModulePackage =
			AvailModulePackage(name, extension).apply {
				modulePackages.add(this)
			}

	/**
	 * The set of [AvailModule]s to add to the top level of this [AvailRoot].
	 */
	private val modules =
		mutableSetOf<AvailModule>()

	/**
	 * The set of [AvailModulePackage]s to add to the top level of this
	 * [AvailRoot].
	 */
	private val modulePackages =
		mutableSetOf<AvailModulePackage>()

	/**
	 * Create the [modules] and [modulePackages] in [roots directory][absolutePath].
	 *
	 * @param moduleHeaderCommentBody
	 *   Raw module header comment. This is typically for a copyright. Will be
	 *   wrapped in comment along with file name. If comment body is empty, will
	 *   only provide the file name in the header comment.
	 */
	fun create (moduleHeaderCommentBody: String)
	{
		modulePackages.forEach {
			if (it.moduleHeaderCommentBody.isEmpty()
				&& moduleHeaderCommentBody.isNotEmpty())
			{
				it.moduleHeaderCommentBody = moduleHeaderCommentBody
			}
			it.create(absolutePath)
		}
		modules.forEach {
			it.create(absolutePath)
		}
	}

	/**
	 * Append a printable tree representation of this entire root.
	 *
	 * @param sb
	 *   The [StringBuilder] to add the hierarchy to.
	 */
	fun appendRootHierarchy (sb: StringBuilder)
	{
		modulePackages.forEach { it.hierarchyPrinter(1, sb) }
		modules.forEach { it.hierarchyPrinter(1, sb) }
	}
}
