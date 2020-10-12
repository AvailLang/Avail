/*
 * BuildUnloader.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.builder

import com.avail.AvailTask
import com.avail.builder.AvailBuilder.LoadedModule
import com.avail.descriptor.fiber.FiberDescriptor.Companion.loaderPriority
import com.avail.interpreter.execution.AvailLoader
import com.avail.utility.Graph
import java.util.logging.Level

/**
 * Used for unloading changed modules prior to tracing.
 *
 * @property availBuilder
 *   The [AvailBuilder] in which to unload modules.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Create a `BuildUnloader` for the given [AvailBuilder].
 *
 * @param availBuilder
 *   The [AvailBuilder] for which to unload modules.
 */
internal class BuildUnloader constructor(private val availBuilder: AvailBuilder)
{
	/**
	 * Given a [ResolvedModuleName], see if any predecessor [LoadedModule]s have
	 * their [LoadedModule.deletionRequest] set.  If so, mark the current module
	 * for `deletionRequest` as well. Afterward, execute the given function. The
	 * work is actually done in parallel, and the completionAction might be
	 * invoked on a different Thread, but always after this module is processed.
	 *
	 * @param moduleName
	 *   The name of the [LoadedModule] to propagate the predecessors'
	 *   deletionRequest into.
	 * @param completionAction
	 *   What to execute when this propagation step has completed.
	 */
	private fun determineSuccessorModules(
		moduleName: ResolvedModuleName,
		completionAction: ()->Unit)
	{
		availBuilder.runtime.execute(loaderPriority) {
			for (predecessor
				in availBuilder.moduleGraph.predecessorsOf(moduleName))
			{
				val predecessorLoadedModule =
					availBuilder.getLoadedModule(predecessor)!!
				if (predecessorLoadedModule.deletionRequest)
				{
					val loadedModule =
						availBuilder.getLoadedModule(moduleName)!!
					loadedModule.deletionRequest = true
					break
				}
			}
			completionAction()
		}
	}

	/**
	 * Determine which modules should be unloaded.  Suitable to be invoked by
	 * [Graph.parallelVisit] on the module graph. For each module that should be
	 * unloaded, set its [LoadedModule.deletionRequest].
	 *
	 * Note that the parallel graph visit mechanism blocks for all predecessors
	 * to complete before starting a vertex (module), and this method
	 * automatically marks a module for invalidation if any of its immediate
	 * predecessors (which will have already been processed) is also marked for
	 * invalidation.
	 *
	 * @param moduleName
	 *   The name of the module to check for dirtiness.
	 * @param completionAction
	 *   What to do after testing for dirtiness (and possibly setting the
	 *   `deletionRequest`) of the given module.
	 */
	private fun determineDirtyModules(
		moduleName: ResolvedModuleName,
		completionAction: (()->Unit))
	{
		availBuilder.runtime.execute(loaderPriority) {
			var dirty = false
			for (predecessor
				in availBuilder.moduleGraph.predecessorsOf(moduleName))
			{
				val loadedModule = availBuilder.getLoadedModule(predecessor)!!
				if (loadedModule.deletionRequest)
				{
					dirty = true
					break
				}
			}
			if (!dirty)
			{
				// Look at the file to determine if it's changed.
				val loadedModule = availBuilder.getLoadedModule(moduleName)!!
				val repository = moduleName.repository
				val archive = repository.getArchive(moduleName.rootRelativeName)
				val resolverReference = moduleName.resolverReference
				if (resolverReference.isPackage)
				{
					loadedModule.deletionRequest = dirty
					AvailBuilder.log(
						Level.FINEST, "(Module %s is dirty)", moduleName)
					completionAction()
				}
				else
				{
					archive.digestForFile(
						moduleName,
						false,
						{ latestDigest ->
							!latestDigest.contentEquals(
								loadedModule.sourceDigest)
							loadedModule.deletionRequest = dirty
							AvailBuilder.log(
								Level.FINEST, "(Module %s is dirty)", moduleName)
							completionAction()
						}
					){ code, ex ->
						AvailBuilder.log(
							Level.SEVERE,
							"Could not determineDirtyModules for %s. " +
								"Received %s: %s",
							moduleName,
							code,
							ex ?: "")
						// TODO Probably ok?
						completionAction()
					}
				}
			}
			else
			{
				val loadedModule = availBuilder.getLoadedModule(moduleName)!!
				loadedModule.deletionRequest = dirty
				AvailBuilder.log(
					Level.FINEST, "(Module %s is dirty)", moduleName)
				completionAction()
			}
		}
	}

	/**
	 * Unload a module previously determined to be dirty, ensuring that the
	 * completionAction runs when this is done.  Suitable for use by
	 * [Graph.parallelVisit] on the [reverse][Graph.reverse] of the module
	 * graph.
	 *
	 * @param moduleName
	 *   The name of a module to unload.
	 * @param completionAction
	 *   What to do after unloading completes.
	 */
	private fun scheduleUnloadOneModule(
		moduleName: ResolvedModuleName,
		completionAction: ()->Unit)
	{
		// No need to lock dirtyModules any more, since it's purely read-only at
		// this point.
		availBuilder.runtime.whenLevelOneSafeDo(loaderPriority) {
			val loadedModule = availBuilder.getLoadedModule(moduleName)!!
			if (!loadedModule.deletionRequest)
			{
				completionAction()
				return@whenLevelOneSafeDo
			}
			AvailBuilder.log(
				Level.FINER,
				"Beginning unload of: %s",
				moduleName)
			val module = loadedModule.module
			// It's legal to just create a loader here, since it won't have any
			// pending forwards to remove.
			module.removeFrom(
				AvailLoader.forUnloading(module, availBuilder.textInterface)
			) {
				availBuilder.runtime.unlinkModule(module)
				AvailBuilder.log(
					Level.FINER,
					"Finished unload of: %s",
					moduleName)
				completionAction()
			}
		}
	}

	/**
	 * Find all loaded modules that have changed since compilation, then unload
	 * them and all successors in reverse dependency order.
	 */
	fun unloadModified()
	{
		availBuilder.moduleGraph.parallelVisit { moduleName, done ->
			availBuilder.runtime.execute(
				AvailTask(loaderPriority) {
					determineDirtyModules(moduleName, done)
				})
		}
		availBuilder.moduleGraph.reverse.parallelVisit { moduleName, done ->
			availBuilder.runtime.execute(
				AvailTask(loaderPriority) {
					scheduleUnloadOneModule(moduleName, done)
				})
		}
		// Unloading of each A_Module is complete.  Update my local structures
		// to agree.
		for (loadedModule in availBuilder.loadedModulesCopy())
		{
			if (loadedModule.deletionRequest)
			{
				val moduleName = loadedModule.name
				availBuilder.removeLoadedModule(moduleName)
				availBuilder.moduleGraph.exciseVertex(moduleName)
			}
		}
	}

	/**
	 * Find all loaded modules that are successors of the specified module, then
	 * unload them and all successors in reverse dependency order.  If `null` is
	 * passed, then unload all loaded modules.
	 *
	 * @param targetName
	 *   The [name][ResolvedModuleName] of the module that should be unloaded,
	 *   or `null` if all modules are to be unloaded.
	 */
	fun unload(targetName: ResolvedModuleName?)
	{
		if (targetName === null)
		{
			for (loadedModule in availBuilder.loadedModulesCopy())
			{
				loadedModule.deletionRequest = true
			}
		}
		else
		{
			val target = availBuilder.getLoadedModule(targetName)
			if (target !== null)
			{
				target.deletionRequest = true
			}
		}
		var moduleCount = availBuilder.moduleGraph.vertexCount
		availBuilder.moduleGraph.parallelVisit { moduleName, done ->
			availBuilder.runtime.execute(
				AvailTask(loaderPriority) {
					determineSuccessorModules(moduleName, done)
				})
		}
		availBuilder.moduleGraph.reverse.parallelVisit { moduleName, done ->
			availBuilder.runtime.execute(
				AvailTask(loaderPriority) {
					scheduleUnloadOneModule(moduleName, done)
				})
		}
		// Unloading of each A_Module is complete.  Update my local structures
		// to agree.
		for (loadedModule in availBuilder.loadedModulesCopy())
		{
			if (loadedModule.deletionRequest)
			{
				val moduleName = loadedModule.name
				availBuilder.removeLoadedModule(moduleName)
				availBuilder.moduleGraph.exciseVertex(moduleName)
				moduleCount--
			}
		}
		assert(availBuilder.moduleGraph.vertexCount == moduleCount)
	}
}
