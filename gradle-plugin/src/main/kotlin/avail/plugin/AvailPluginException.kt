package avail.plugin

/**
 * A [RuntimeException] explicitly encountered by the [AvailPlugin].
 *
 * @author Richard Arriaga
 */
class AvailPluginException: RuntimeException
{
	/**
	 * Construct an [AvailPluginException].
	 *
	 * @param message
	 *   The message that describes the issue causing this exception.
	 */
	constructor(message: String): super(message)

	/**
	 * Construct an [AvailPluginException].
	 *
	 * @param message
	 *   The message that describes the issue causing this exception.
	 * @param cause
	 *   The cause of the exception.
	 */
	constructor(message: String, cause: Throwable): super(message, cause)
}
