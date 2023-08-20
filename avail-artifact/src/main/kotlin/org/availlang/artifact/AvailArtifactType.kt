package org.availlang.artifact


/**
 * The enumeration that identifies the purpose of an Avail artifact.
 *
 * @author Richard Arriaga
 */
enum class AvailArtifactType
{
	/** The artifact is a runnable application. */
	APPLICATION,

	/**
	 * The artifact is a group of Avail Modules that represent a reusable
	 * library.
	 */
	LIBRARY;
}
