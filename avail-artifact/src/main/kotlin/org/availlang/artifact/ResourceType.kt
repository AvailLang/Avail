package org.availlang.artifact

/**
 * Represents the types of files inside an Avail Module Root inside the
 * [AvailArtifact].
 *
 * @author Richard Arriaga
 */
enum class ResourceType
{
	/** Represents an ordinary Avail module. */
	MODULE,

	/** Represents an ordinary, headerless Avail module. */
	HEADERLESS_MODULE,

	/** Represents an Avail package representative. */
	REPRESENTATIVE,

	/** Represents an Avail package. */
	PACKAGE,

	/** Represents an Avail root. */
	ROOT,

	/** Represents an arbitrary directory. */
	DIRECTORY,

	/** Represents an arbitrary resource. */
	RESOURCE;

	/** A short description of the receiver. */
	val label get() = name.lowercase()
}
