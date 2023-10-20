package org.availlang.artifact

/**
 * Represents the types of files inside an Avail Module Root inside the
 * [AvailArtifact].
 *
 * @author Richard Arriaga
 */
sealed interface ResourceType
{
	/** Represents an ordinary Avail module. */
	data object Module: ResourceType

	/**
	 * Represents an ordinary headerless Avail module. It must be associated
	 * with a [ModuleHeader] in some way.
	 */
	data object HeaderlessModule: ResourceType

	/**
	 * Represents an Avail module with no header. It must be associated with a
	 * [HeaderlessModule].
	 */
	data object ModuleHeader: ResourceType

	/** Represents an Avail package representative. */
	data object Representative: ResourceType

	/** Represents an Avail package. */
	data object Package: ResourceType

	/** Represents an Avail root. */
	data object Root: ResourceType

	/** Represents an arbitrary directory. */
	data object Directory: ResourceType

	/** Represents an arbitrary resource. */
	data object Resource: ResourceType

	/** A short description of the receiver. */
	val label get() = toString().lowercase()
}
