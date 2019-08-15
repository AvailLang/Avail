/*
 * BuildUnloader.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.builder;
import com.avail.annotations.InnerAccess;
import com.avail.builder.AvailBuilder.LoadedModule;
import com.avail.descriptor.A_Module;
import com.avail.interpreter.AvailLoader;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.persistence.IndexedRepositoryManager.ModuleArchive;
import com.avail.utility.Graph;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation2NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.logging.Level;

import static com.avail.descriptor.FiberDescriptor.loaderPriority;
import static com.avail.utility.Nulls.stripNull;

/**
 * Used for unloading changed modules prior to tracing.
 */
class BuildUnloader
{
	/** The {@link AvailBuilder} in which to unload modules. */
	private final AvailBuilder availBuilder;

	/**
	 * Create a {@code BuildUnloader} for the given {@link AvailBuilder}.
	 *
	 * @param availBuilder The {@link AvailBuilder} for which to unload modules.
	 */
	BuildUnloader (final AvailBuilder availBuilder)
	{
		this.availBuilder = availBuilder;
	}

	/**
	 * Given a {@link ResolvedModuleName}, see if any predecessor {@link
	 * LoadedModule}s have their {@link LoadedModule#deletionRequest} set.  If
	 * so, mark the current module for {@code deletionRequest} as well.
	 * Afterward, execute the given {@linkplain Continuation0 completionAction}.
	 * The work is actually done in parallel, and the completionAction might be
	 * invoked on a different Thread, but always after this module is processed.
	 *
	 * @param moduleName
	 *        The name of the {@link LoadedModule} to propagate the
	 *        predecessors' deletionRequest into.
	 * @param completionAction
	 *        What to execute when this propagation step has completed.
	 */
	private void determineSuccessorModules (
		final ResolvedModuleName moduleName,
		final Continuation0 completionAction)
	{
		availBuilder.runtime.execute(
			loaderPriority,
			() ->
			{
				for (final ResolvedModuleName predecessor
					: availBuilder.moduleGraph.predecessorsOf(moduleName))
				{
					final LoadedModule predecessorLoadedModule =
						stripNull(availBuilder.getLoadedModule(predecessor));
					if (predecessorLoadedModule.deletionRequest)
					{
						final LoadedModule loadedModule =
							stripNull(availBuilder.getLoadedModule(moduleName));
						loadedModule.deletionRequest = true;
						break;
					}
				}
				completionAction.value();
			});
	}

	/**
	 * Determine which modules should be unloaded.  Suitable to be invoked
	 * by {@link Graph#parallelVisit(Continuation2NotNull)} on the module
	 * graph. For each module that should be unloaded, set its {@link
	 * LoadedModule#deletionRequest deletionRequest}.
	 *
	 * <p>Note that the parallel graph visit mechanism blocks for all
	 * predecessors to complete before starting a vertex (module), and this
	 * method automatically marks a module for invalidation if any of its
	 * immediate predecessors (which will have already been processed) is
	 * also marked for invalidation.</p>
	 *
	 * @param moduleName
	 *        The name of the module to check for dirtiness.
	 * @param completionAction
	 *        What to do after testing for dirtiness (and possibly setting
	 *        the deletionRequest) of the given module.
	 */
	private void determineDirtyModules (
		final @Nullable ResolvedModuleName moduleName,
		final @Nullable Continuation0 completionAction)
	{
		assert moduleName != null;
		assert completionAction != null;
		availBuilder.runtime.execute(
			loaderPriority,
			() ->
			{
				boolean dirty = false;
				for (final ResolvedModuleName predecessor :
					availBuilder.moduleGraph.predecessorsOf(moduleName))
				{
					final LoadedModule loadedModule =
						stripNull(availBuilder.getLoadedModule(predecessor));
					if (loadedModule.deletionRequest)
					{
						dirty = true;
						break;
					}
				}
				if (!dirty)
				{
					// Look at the file to determine if it's changed.
					final LoadedModule loadedModule =
						stripNull(availBuilder.getLoadedModule(moduleName));
					final IndexedRepositoryManager repository =
						moduleName.repository();
					final ModuleArchive archive = repository.getArchive(
						moduleName.rootRelativeName());
					final File sourceFile = moduleName.sourceReference();
					if (!sourceFile.isFile())
					{
						dirty = true;
					}
					else
					{
						final byte [] latestDigest =
							archive.digestForFile(moduleName);
						dirty = !Arrays.equals(
							latestDigest, loadedModule.sourceDigest);
					}
				}
				final LoadedModule loadedModule =
					stripNull(availBuilder.getLoadedModule(moduleName));
				loadedModule.deletionRequest = dirty;
				AvailBuilder.log(
					Level.FINEST, "(Module %s is dirty)", moduleName);
				completionAction.value();
			});
	}

	/**
	 * Unload a module previously determined to be dirty, ensuring that the
	 * completionAction runs when this is done.  Suitable for use by {@link
	 * Graph#parallelVisit(Continuation2NotNull)} on the {@linkplain
	 * Graph#reverse() reverse} of the module graph.
	 *
	 * @param moduleName The name of a module to unload.
	 * @param completionAction What to do after unloading completes.
	 */
	private void unloadModules (
		final @Nullable ResolvedModuleName moduleName,
		final @Nullable Continuation0 completionAction)
	{
		// No need to lock dirtyModules any more, since it's
		// purely read-only at this point.
		assert moduleName != null;
		assert completionAction != null;
		availBuilder.runtime.whenLevelOneSafeDo(
			loaderPriority,
			() ->
			{
				final LoadedModule loadedModule =
					stripNull(availBuilder.getLoadedModule(moduleName));
				if (!loadedModule.deletionRequest)
				{
					completionAction.value();
					return;
				}
				AvailBuilder.log(
					Level.FINER,
					"Beginning unload of: %s",
					moduleName);
				final A_Module module = loadedModule.module;
				// It's legal to just create a loader
				// here, since it won't have any pending
				// forwards to remove.
				module.removeFrom(
					AvailLoader.forUnloading(
						module, availBuilder.textInterface),
					() ->
					{
						availBuilder.runtime.unlinkModule(module);
						AvailBuilder.log(
							Level.FINER,
							"Finished unload of: %s",
							moduleName);
						completionAction.value();
					});
			});
	}

	/**
	 * Find all loaded modules that have changed since compilation, then
	 * unload them and all successors in reverse dependency order.
	 */
	@InnerAccess
	void unloadModified ()
	{
		availBuilder.moduleGraph.parallelVisit(this::determineDirtyModules);
		availBuilder.moduleGraph.reverse().parallelVisit(this::unloadModules);
		// Unloading of each A_Module is complete.  Update my local
		// structures to agree.
		for (final LoadedModule loadedModule : availBuilder.loadedModulesCopy())
		{
			if (loadedModule.deletionRequest)
			{
				final ResolvedModuleName moduleName = loadedModule.name;
				availBuilder.removeLoadedModule(moduleName);
				availBuilder.moduleGraph.exciseVertex(moduleName);
			}
		}
	}

	/**
	 * Find all loaded modules that are successors of the specified module,
	 * then unload them and all successors in reverse dependency order.  If
	 * {@code null} is passed, then unload all loaded modules.
	 *
	 * @param targetName
	 *        The {@linkplain ResolvedModuleName name} of the module that
	 *        should be unloaded, or {@code null} if all modules are to be
	 *        unloaded.
	 */
	@InnerAccess void unload (final @Nullable ResolvedModuleName targetName)
	{
		if (targetName == null)
		{
			for (final LoadedModule loadedModule : availBuilder
				.loadedModulesCopy())
			{
				loadedModule.deletionRequest = true;
			}
		}
		else
		{
			final @Nullable LoadedModule target =
				availBuilder.getLoadedModule(targetName);
			if (target != null)
			{
				target.deletionRequest = true;
			}
		}
		int moduleCount = availBuilder.moduleGraph.vertexCount();
		availBuilder.moduleGraph.parallelVisit(this::determineSuccessorModules);
		availBuilder.moduleGraph.reverse().parallelVisit(this::unloadModules);
		// Unloading of each A_Module is complete.  Update my local
		// structures to agree.
		for (final LoadedModule loadedModule : availBuilder.loadedModulesCopy())
		{
			if (loadedModule.deletionRequest)
			{
				final ResolvedModuleName moduleName = loadedModule.name;
				availBuilder.removeLoadedModule(moduleName);
				availBuilder.moduleGraph.exciseVertex(moduleName);
				moduleCount--;
			}
		}
		assert availBuilder.moduleGraph.vertexCount() == moduleCount;
	}
}
