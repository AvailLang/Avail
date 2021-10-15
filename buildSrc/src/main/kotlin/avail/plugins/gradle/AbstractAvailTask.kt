package avail.plugins.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.property

/**
 * A `AbstractAvailTask` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
abstract class AbstractAvailTask: DefaultTask()
{
	// TODO get group id, artifact id, version to bring in `avail-stdlib` jar
	//  as dependency
	@Input
	val roots: MutableList<AvailRoot> = mutableListOf()

	@Input
	val stdlibVersion: Property<String> =
		project.objects.property<String>().convention(Versions.avail)
}
