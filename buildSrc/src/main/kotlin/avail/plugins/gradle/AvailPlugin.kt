/*
 * AvailPlugin.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

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
