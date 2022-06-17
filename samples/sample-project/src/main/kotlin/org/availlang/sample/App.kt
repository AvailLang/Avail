/*
 * App.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package org.availlang.sample

import avail.AvailRuntime
import java.util.concurrent.Semaphore
import kotlin.system.exitProcess

/**
 * The core application.
 */
class App
{
	init
	{
		// Make the app accessible globally.
		app = this
	}

	/**
	 * The map from [Project.name] to Avail [Project].
	 */
	private val availProjects = mutableMapOf<String, Project>()

	/**
	 * Add the provided [Project].
	 *
	 * @param project
	 *   The `Project` to add.
	 */
	fun addProject(project: Project)
	{
		app.availProjects[project.name] = project
	}

	/**
	 * Add project with the provided `name` using the
	 * [configuredModuleRootsPath].
	 *
	 * @param name
	 *   The [name][Project.name] of the project to add.
	 * @return
	 *   The created project.
	 */
	fun addConfiguredProject (name: String): Project =
		Project(name, configuredModuleRootsPath).apply { addProject(this) }

	/**
	 * The default module roots path taken from the system property
	 * `availRoots` included as a VM Argument.
	 */
	val configuredModuleRootsPath: String by lazy {
		System.getProperty("availRoots") ?: ""
	}

	/**
	 * Remove the [Project] from the [App].
	 *
	 * @param name
	 *   The [name][Project.name] of the `Project` to remove.
	 * @return
	 *   `true` if the `Project`'s [AvailRuntime] was
	 *   [destroyed][AvailRuntime.destroy] and removed from the `App`; `false`
	 *   if no `Project` was found with that name.
	 */
	fun removeProject(name: String): Boolean
	{
		val toRemove = App[name] ?: return false

		// Destroy all data structures used by this AvailRuntime. Also
		// disassociate it from the current Thread's local storage.
		toRemove.runtime.destroy()

		// Block the current Thread until there are no fibers that can run. If
		// nothing outside the VM is actively creating and running new fibers,
		// this is a permanent state. After this method returns, there will be
		// no more AvailTasks added to the executor.
		toRemove.runtime.awaitNoFibers()

		return true
	}

	companion object
	{
		/**
		 * Answer the [Project] associated with given [Project.name].
		 *
		 * @param name
		 *   The name of the project to get.
		 * @return
		 *   The `Project` if found; `null` otherwise.
		 */
		operator fun get(name: String): Project? = app.availProjects[name]

		/**
		 * The sole running [App] made available statically globally.
		 */
		lateinit var app: App
			private set
	}
}

/**
 * A main function that accepts the:
 * 	1. [Project.name]
 * 	2. [Project.moduleRootsPath]
 *
 * If these aren't provided the app will exit.
 */
fun main(args: Array<String>)
{
	// Check of Project configuration provided.
	if (args.isEmpty())
	{
		println("No Avail Project Name Provided!")
		exitProcess(1)
	}

	// Create the backing App
	App()

	// Check to see if the project is configured
	if (App.app.configuredModuleRootsPath.isEmpty())
	{
		System.err.println("No Avail Roots included as VM argument!")
		exitProcess(1)
	}

	// Confirm has repositories set. If not set, inform user of expected
	// default.
	if ((System.getProperty("avail.repositories") ?: "").isEmpty())
	{
		val home = System.getProperty("user.home")
		println("No Repository location specified. Using system default:")
		println("\t$home/.avail/repositories/\"")
	}

	// The first configuration input is a project name, "My Project"
	val projectName = args[0]

	// Welcome the Avail Project!
	println("Hello $projectName!")

	// Create the project.
	val myProject = App.app.addConfiguredProject(projectName)

	// Add the project to be tracked/made available from App
	App.app.addProject(myProject)

	println("${myProject.name} has been added to the App!")

	// Start
	println("\nStarting Avail Runtime")
	println("=======================")
	println("Runtime Includes ModuleRoot(s):")
	myProject.runtime.moduleRoots().forEach {
		println("\t${it.name}")
	}

	// Perform an integrity check on the parser data structures. Report the
	// findings to System.out. If nothing errant, prints `done`.
	myProject.runtime.integrityCheck()

	// The AvailBuilder will build the Avail Module in an Avail Task running on
	// a separate thread. We need to block the main thread of execution until
	// the build is complete.
	val semaphore = Semaphore(0)
	val module = "/my-avail-root/App/App"
	myProject.build(module) {
		println("Completed compilation of Avail Module: $module")
		// Release the semaphore so the main thread can continue execution.
		semaphore.release()
	}
	// This will block the main thread until the module has been built.
	semaphore.acquireUninterruptibly()

	// Clean up
	val removed = App.app.removeProject(myProject.name)
	println("\nStopped ${myProject.name}: $removed")
}
