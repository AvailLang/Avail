package org.availlang.artifact

import java.io.File

/**
 * Represents the types of files inside an Avail Module Root inside the
 * [AvailArtifact].
 *
 * @author Richard Arriaga
 */
sealed interface ResourceType
{
	/**
	 * The name of this [ResourceType].
	 */
	val name: String

	val mimeType: String get() = ""

	/**
	 * `true` if this [ResourceType] is a [File.isDirectory]; `false` otherwise.
	 */
	val isDirectory: Boolean get() = false

	/**
	 * `true` indicates this [ResourceType] is an Avail code module; `false`
	 * otherwise. This covers:
	 *
	 * - [Module]
	 * - [HeaderModule]
	 * - [HeaderlessModule]
	 * - [Representative]
	 */
	val isModule: Boolean get() = false

	/**
	 * `true` indicates this [ResourceType] [is an Avail code module][isModule]
	 * and the module contains the standard Avail module header.
	 *
	 * - [Module]
	 * - [HeaderModule]
	 * - [Representative]
	 */
	val hasModuleHeader: Boolean get() = false

	/**
	 * Represents an ordinary Avail module.
	 *
	 * @property fileExtension
	 *   The file extension for this [Module].
	 */
	data class Module(
		override val fileExtension: String
	): ResourceType, FileExtension
	{
		override val name: String = "Module"
		override val mimeType: String get() = availMimeType
		override val hasModuleHeader: Boolean = true
		override val isModule: Boolean = true
	}

	/**
	 * Represents an ordinary headerless Avail module. It must be associated
	 * with a [HeaderModule] in some way.
	 *
	 * @property fileExtension
	 *   The file extension for [HeaderlessModule]s of this type.
	 * @property headerType
	 *   The associated [HeaderModule] that provides the module header for this
	 *   [HeaderlessModule].
	 * @property fileIcon
	 *   The [ResourceTypeManager.HeaderlessExtension.headerlessFileIcon] or
	 *   `null` if no special icon chosen.
	 */
	data class HeaderlessModule constructor(
		override val fileExtension: String,
		val headerType: HeaderModule,
		val fileIcon: String? = null
	): ResourceType, FileExtension
	{
		override val name: String = "HeaderlessModule"
		override val mimeType: String get() = availMimeType
		override val isModule: Boolean = true
	}

	/**
	 * Represents an Avail module with no header. It must be associated with a
	 * [HeaderlessModule].
	 * @property fileIcon
	 *   The [ResourceTypeManager.HeaderlessExtension.headerFileIcon] or
	 *   `null` if no special icon chosen.
	 */
	data class HeaderModule constructor(
		override val fileExtension: String,
		val fileIcon: String? = null
	): ResourceType, FileExtension
	{
		override val name: String = "HeaderModule"
		override val mimeType: String get() = availMimeType
		override val hasModuleHeader: Boolean = true
		override val isModule: Boolean = true
	}

	/**
	 * Represents an Avail package representative.
	 *
	 * **Note** A uses a `Representative` [fileExtension] **must** use the
	 * [ResourceTypeManager.moduleFileExtension]. It is not permitted to use
	 * a [ResourceTypeManager.HeaderlessExtension.headerlessExtension] nor a
	 * [ResourceTypeManager.HeaderlessExtension.headerExtension].
	 *
	 * @property fileExtension
	 *   The file extension for this [Representative].
	 */
	data class Representative(
		override val fileExtension: String
	): ResourceType, FileExtension
	{
		override val name: String = "Representative"
		override val mimeType: String get() = availMimeType
		override val hasModuleHeader: Boolean = true
		override val isModule: Boolean = true
	}

	/**
	 * Represents an Avail package.
	 *
	 * **Note** If a `Package` uses a [fileExtension], it **must** use the
	 * [ResourceTypeManager.moduleFileExtension].
	 *
	 * @property fileExtension
	 *   The file extension for this [Representative] or an empty string if it
	 *   doesn't use an extra file extension.
	 */
	data class Package(
		override val fileExtension: String
	): ResourceType, FileExtension
	{
		override val name: String = "Package"
		override val isDirectory: Boolean = true
	}

	/** Represents an Avail root. */
	data object Root: ResourceType
	{
		override val name: String = toString()
		override val isDirectory: Boolean = true
	}

	/** Represents an arbitrary directory. */
	data object Directory: ResourceType
	{
		override val name: String = Root.toString()
		override val isDirectory: Boolean = true
	}

	/** Represents an arbitrary resource. */
	data object Resource: ResourceType
	{
		override val name: String = Root.toString()
	}

	/**
	 * An interface that provides a file extension for a file.
	 */
	interface FileExtension
	{
		/**
		 * The file extension of the associated file.
		 */
		val fileExtension: String
	}

	companion object {
		/**
		 * The mime type of an Avail module file.
		 */
		const val availMimeType = "text/avail"
	}
}
