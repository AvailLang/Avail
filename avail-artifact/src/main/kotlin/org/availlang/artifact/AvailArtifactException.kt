package org.availlang.artifact

/**
 * A [RuntimeException] associated with constructing/deconstructing an Avail
 * artifact.
 *
 * @author Richard Arriaga
 */
class AvailArtifactException: RuntimeException
{
	/**
	 * Construct an [AvailArtifactException].
	 *
	 * @param message
	 *   The message that describes the exception.
	 */
	constructor(message: String): super(message)

	/**
	 * Construct an [AvailArtifactException].
	 *
	 * @param message
	 *   The message that describes the exception.
	 * @param cause
	 *   The cause of this [AvailArtifactException].
	 */
	constructor(message: String, cause: Throwable): super(message, cause)
}
