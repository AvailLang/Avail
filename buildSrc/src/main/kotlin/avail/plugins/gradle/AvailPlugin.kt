package avail.plugins.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named

/**
 * `AvailPlugin` is TODO: WIP
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailPlugin : Plugin<Project>
{
	override fun apply(target: Project)
	{
		val extension =
			target.extensions.create<AvailPluginExtension>("roots")
		val stdlibVersion =
			target.extensions.create<String>("stdlibVersion")
	}
}

class ConfigureAvail: AbstractAvailTask()
{
	// TODO this is where the roots can be set. The `avail-stdlib`, which just
	//  builds a jar with all of the stdlib roots (Avail.avail), should always
	//  be available to get by the plugin using the jar from maven. The default
	//  version should be the version build at the time, but alternate versions
	//  could be set. The option to include the stdlib as a root should be set
	//  to true by default, but can be set to false to allow for alternate
	//  libraries or direct Avail standard library development.
	//
	// TODO The plugin should support:
	//  - Set stdlib version
	//  - Setting the roots
	//  - Setting the repository location, but lets the system use the default
	//    location ("${System.getProperty("user.home")}/.avail/repositories/
	//  - Launch a workbench (includes set roots)
	//  - Clean repo
	//  - Unload module
	//  - Load/Build targeted module
	//  - configure `avail-server`
	//  - Launch Avail server
}

val TaskContainer.`configureAvail`: TaskProvider<ConfigureAvail>
	get() = named<ConfigureAvail>("configureAvail")
