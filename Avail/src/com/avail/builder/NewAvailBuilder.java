/**
 * NewAvailBuilder.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.builder;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.annotations.Nullable;
import com.avail.compiler.AbstractAvailCompiler;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.utility.Continuation3;
import com.avail.utility.Continuation4;
import com.avail.utility.Graph;

/**
 * TODO: Document NewAvailBuilder!
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class NewAvailBuilder
{
	/**
	 * The {@linkplain AvailRuntime runtime} into which the
	 * {@linkplain AvailBuilder builder} will install the target
	 * {@linkplain ModuleDescriptor module} and its dependencies.
	 */
	final @InnerAccess AvailRuntime runtime;

	/**
	 * The states of the known {@linkplain ModuleDescriptor modules} of the
	 * system.  Access to this {@link Map} should be protected by the {@link
	 * #stateLock}.
	 */
	final Map<ModuleName, ModuleBuildNode> moduleNodesByName = new HashMap<>();

	/**
	 * The states of the known {@linkplain ModuleDescriptor modules} of the
	 * system.  Access to both the graph structure and state transitions within
	 * individual {@link ModuleBuildNode}s should be protected by the {@link
	 * #stateLock}.
	 */
	final Graph<ModuleBuildNode> moduleNodesGraph = new Graph<>();

	/**
	 * The lock used to protect access to the {@link #moduleNodesGraph} and
	 * {@link #moduleNodesByName} {@link Map}, as well as individual state
	 * transitions within each {@link ModuleBuildNode}.
	 */
	final ReentrantLock stateLock = new ReentrantLock();

	/**
	 * A {@linkplain Continuation4 continuation} that is updated to show
	 * progress while compiling or loading a module.  It accepts:
	 * <ol>
	 * <li>the name of the module currently undergoing {@linkplain
	 * AbstractAvailCompiler compilation} as part of the recursive build
	 * of target,</li>
	 * <li>the current line number within the current module,</li>
	 * <li>the position of the ongoing parse (in bytes), and</li>
	 * <li>the size of the module in bytes.</li>
	 */
	@InnerAccess final Continuation4<ModuleName, Long, Long, Long> localTracker;

	/**
	 * A {@linkplain Continuation3} that is updated to show global progress
	 * while compiling or loading modules.  It accepts:
	 * <ol>
	 * <li>the name of the module undergoing compilation,</li>
	 * <li>the number of bytes globally processed, and</li>
	 * <li>the global size (in bytes) of all modules that will be
	 * built.</li>
	 */
	@InnerAccess final Continuation3<ModuleName, Long, Long> globalTracker;

	/**
	 * Construct an {@link AvailBuilder} for the provided runtime.  During a
	 * build, the passed trackers will be invoked to show progress.
	 *
	 * @param runtime
	 *        The {@link AvailRuntime} in which to load modules and execute
	 *        commands.
	 * @param localTracker
	 *        A {@linkplain AbstractAvailCompiler.CompilerProgressReporter continuation} that accepts
	 *        <ol>
	 *        <li>the name of the module currently undergoing {@linkplain
	 *        AbstractAvailCompiler compilation} as part of the recursive build
	 *        of target,</li>
	 *        <li>the current line number within the current module,</li>
	 *        <li>the position of the ongoing parse (in bytes), and</li>
	 *        <li>the size of the module in bytes.</li>
	 *        </ol>
	 * @param globalTracker
	 *        A {@linkplain Continuation3 continuation} that accepts
	 *        <ol>
	 *        <li>the name of the module undergoing compilation,</li>
	 *        <li>the number of bytes globally processed, and</li>
	 *        <li>the global size (in bytes) of all modules that will be
	 *        built.</li>
	 *        </ol>
	 */
	public NewAvailBuilder (
		final AvailRuntime runtime,
		final AbstractAvailCompiler.CompilerProgressReporter localTracker,
		final Continuation3<ModuleName, Long, Long> globalTracker)
	{
		this.runtime = runtime;
		this.localTracker = localTracker;
		this.globalTracker = globalTracker;
	}


	/**
	 * The {@code ModuleValidationState} is used to detect changes to a {@link
	 * ModuleBuildNode}'s consistency with its source file and its compiled
	 * representation in a {@link IndexedRepositoryManager repository}.  Not
	 * only are the source file's timestamp and compiled representation's
	 * timestamp used to determine this, but also the directories and files that
	 * are visible to this module, to detect whether one of this module's
	 * unqualified import names would resolve to a different file.
	 */
	@InnerAccess enum ModuleValidationState
	{
		/**
		 * It is not known whether the source {@linkplain ModuleDescriptor
		 * module} is valid, or even still exists.
		 */
		UNVALIDATED,

		/**
		 * The existence of the source {@linkplain ModuleDescriptor module} is
		 * being determined. If the source module exists, then its timestamp
		 * is compared against any corresponding information in the {@linkplain
		 * IndexedRepositoryManager repository}.
		 */
		VALIDATING,

		/**
		 * The {@linkplain ModuleDescriptor module} is known to exist, be up
		 * to date with its source file, and have the import resolutions that
		 * agree with the existing version.
		 */
		VALID,

		/**
		 * The {@linkplain ModuleDescriptor module} no longer exists, is not up
		 * to date with its source file, or now resolves one or more of its
		 * imported modules to a different location than the current version.
		 */
		INVALID;
	}

	/**
	 * The {@code ModuleScanningState} is used to detect changes to a {@link
	 * ModuleBuildNode}'s consistency with its source file and its compiled
	 * representation in a {@link IndexedRepositoryManager repository}.  Not
	 * only are the source file's timestamp and compiled representation's
	 * timestamp used to determine this, but also the directories and files that
	 * are visible to this module, to detect whether one of this module's
	 * unqualified import names would resolve to a different file.
	 */
	@InnerAccess enum ModuleScanningState
	{
		/**
		 * The {@linkplain ModuleDescriptor module} is known to exist.
		 */
		UNSCANNED,

		/**
		 * The source {@linkplain ModuleDescriptor module} is being scanned to
		 * determine its relationship to other modules.
		 */
		SCANNING,

		/**
		 * The relationship of the {@linkplain ModuleDescriptor module} to other
		 * modules is now understood.
		 */
		SCANNED;
	}

	/**
	 *
	 */
	@InnerAccess enum ModuleLoadingState
	{
		/**
		 *
		 */
		UNLOADED("MARKED_TO_LOAD"),
		MARKED_TO_LOAD("LOADING_PREDECESSORS"),
		LOADING_PREDECESSORS("LOADING", "COMPILING"),
		COMPILING("LOADED"),
		LOADING("LOADED"),
		LOADED("MARKED_TO_UNLOAD"),
		MARKED_TO_UNLOAD("UNLOADING_SUCCESSORS"),
		UNLOADING_SUCCESSORS("UNLOADING"),
		UNLOADING("SCANNED");

		final EnumSet<ModuleLoadingState> successorStates =
			EnumSet.noneOf(ModuleLoadingState.class);

		private final String [] successorNames;

		ModuleLoadingState (final String...successorNames)
		{
			this.successorNames = successorNames;
		}

		static
		{
			for (final ModuleLoadingState value : values())
			{
				for (final String successorName : value.successorNames)
				{
					value.successorStates.add(valueOf(successorName));
				}
			}
		}

		final boolean canTransitionTo (final ModuleLoadingState nextState)
		{
			return successorStates.contains(nextState);
		}
	}

	/**
	 * A {@code ModuleBuildState} represents the current state of a single Avail
	 * {@linkplain ModuleDescriptor module}.
	 */
	@InnerAccess final class ModuleBuildNode
	{
		ModuleLoadingState state = ModuleLoadingState.UNLOADED;

		ResolvedModuleName resolvedModuleName;

		@Nullable A_Module module;

		@Nullable A_Set entryPoints;

		void transitionTo (final ModuleLoadingState nextState)
		{
			if (!state.canTransitionTo(nextState))
			{
				assert false
					: "Cannot transition from "
						+ state
						+ " to "
						+ nextState;
			}
			System.out.format(
				"%s → %s [%s]%n",
				state,
				nextState,
				resolvedModuleName);
			state = nextState;
		}
	}
}
