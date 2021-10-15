package avail.plugins.gradle

import java.net.URI

// Herein lies the functionality surrounding the configuration for the Avail
// Gradle plugin.

/**
 * `Avail` Root represents an Avail source root.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property name
 *   The name of the root.
 * @property uri
 *   The [URI] location of the root.
 */
data class AvailRoot constructor(val name: String, val uri: URI)
{
	/** The VM Options, `-DavailRoot`, root string. */
	val rootString: String by lazy { "$name=$uri" }
}
