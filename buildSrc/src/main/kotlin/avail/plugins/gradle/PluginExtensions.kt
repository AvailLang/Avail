package avail.plugins.gradle

import org.gradle.api.provider.Property

/**
 * A `AvailPluginExtension` declares the state/behavior for an [AvailPlugin]
 * extension
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
interface AvailPluginExtension {
	val roots: Property<MutableList<AvailRoot>>
}
