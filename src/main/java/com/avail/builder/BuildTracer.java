/*
 * BuildTracer.java
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

import com.avail.compiler.AvailCompiler;
import com.avail.compiler.ModuleHeader;
import com.avail.compiler.problems.Problem;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.io.SimpleCompletionHandler;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.persistence.IndexedRepositoryManager.ModuleArchive;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersion;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersionKey;
import com.avail.utility.evaluation.Continuation0;

import javax.annotation.Nullable;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import static com.avail.compiler.problems.ProblemType.TRACE;
import static com.avail.descriptor.FiberDescriptor.tracerPriority;
import static com.avail.utility.Nulls.stripNull;

/**
 * Used for constructing the module dependency graph.
 */
final class BuildTracer
{
	/** The {@link AvailBuilder} for which to trace modules. */
	final AvailBuilder availBuilder;

	/**
	 * The number of trace requests that have been scheduled.
	 */
	private int traceRequests;

	/**
	 * The number of trace requests that have been completed.
	 */
	private int traceCompletions;

	/**
	 * Create a {@code BuildTracer}.
	 *
	 * @param availBuilder
	 *        The {@link AvailBuilder} for which to trace modules.
	 */
	BuildTracer (final AvailBuilder availBuilder)
	{
		this.availBuilder = availBuilder;
	}

	/**
	 * Schedule tracing of the imports of the {@linkplain ModuleDescriptor
	 * module} specified by the given {@linkplain ModuleName module name}.  The
	 * {@link #traceRequests} counter has been incremented already for this
	 * tracing, and the {@link #traceCompletions} will eventually be incremented
	 * by this method, but only <em>after</em> increasing the {@link
	 * #traceRequests} for each recursive trace that is scheduled here.  That
	 * ensures the two counters won't accidentally be equal at any time except
	 * after the last trace has completed.
	 *
	 * <p>When traceCompletions finally does reach traceRequests, a {@link
	 * #notifyAll()} will be sent to the {@code BuildTracer}.</p>
	 *
	 * @param qualifiedName
	 *        A fully-qualified {@linkplain ModuleName module name}.
	 * @param resolvedSuccessor
	 *        The resolved name of the module using or extending this module, or
	 *        {@code null} if this module is the start of the recursive
	 *        resolution (i.e., it will be the last one compiled).
	 * @param recursionSet
	 *        An insertion-ordered {@linkplain Set set} that remembers all
	 *        modules visited along this branch of the trace.
	 * @param problemHandler
	 *        How to handle or report {@link Problem}s that arise during the
	 *        build.
	 */
	private void scheduleTraceModuleImports (
		final ModuleName qualifiedName,
		final @Nullable ResolvedModuleName resolvedSuccessor,
		final LinkedHashSet<ResolvedModuleName> recursionSet,
		final ProblemHandler problemHandler)
	{
		availBuilder.runtime.execute(
			tracerPriority,
			() ->
			{
				if (availBuilder.shouldStopBuild())
				{
					// Even though we're shutting down, we still have to account
					// for the previous increment of traceRequests.
					indicateTraceCompleted();
					return;
				}
				final ResolvedModuleName resolvedName;
				try
				{
					AvailBuilder.log(
						Level.FINEST, "Resolve: %s", qualifiedName);
					resolvedName =
						availBuilder.runtime.moduleNameResolver().resolve(
							qualifiedName, resolvedSuccessor);
				}
				catch (final Exception e)
				{
					AvailBuilder.log(
						Level.WARNING,
						e,
						"Fail resolution: %s",
						qualifiedName);
					availBuilder.stopBuildReason("Module graph tracing failed");
					final Problem problem = new Problem (
						resolvedSuccessor != null
							? resolvedSuccessor
							: qualifiedName,
						1,
						0,
						TRACE,
						"Module resolution problem:\n{0}",
						e)
					{
						@Override
						protected void abortCompilation ()
						{
							indicateTraceCompleted();
						}
					};
					problemHandler.handle(problem);
					return;
				}
				AvailBuilder.log(Level.FINEST, "Trace: %s", resolvedName);
				traceModuleImports(
					resolvedName,
					resolvedSuccessor,
					recursionSet,
					problemHandler);
			});
	}

	/**
	 * Trace the imports of the {@linkplain ModuleDescriptor module} specified
	 * by the given {@linkplain ModuleName module name}.  If a {@link Problem}
	 * occurs, log it and set {@link AvailBuilder#stopBuildReason}. Whether a
	 * success or failure happens, end by invoking {@link
	 * #indicateTraceCompleted()}.
	 *
	 * @param resolvedName
	 *        A resolved {@linkplain ModuleName module name} to trace.
	 * @param resolvedSuccessor
	 *        The resolved name of the module using or extending this module, or
	 *        {@code null} if this module is the start of the recursive
	 *        resolution (i.e., it will be the last one compiled).
	 * @param recursionSet
	 *        A {@link LinkedHashSet} that remembers all modules visited along
	 *        this branch of the trace, and the order they were encountered.
	 * @param problemHandler
	 *        How to handle or report {@link Problem}s that arise during the
	 *        build.
	 */
	private void traceModuleImports (
		final ResolvedModuleName resolvedName,
		final @Nullable ResolvedModuleName resolvedSuccessor,
		final LinkedHashSet<ResolvedModuleName> recursionSet,
		final ProblemHandler problemHandler)
	{
		// Detect recursion into this module.
		if (recursionSet.contains(resolvedName))
		{
			final Problem problem = new Problem(
				resolvedName,
				1,
				0,
				TRACE,
				"Recursive module dependency:\n\t{0}",
				recursionSet)
			{
				@Override
				protected void abortCompilation ()
				{
					availBuilder.stopBuildReason(
						"Module graph tracing failed due to recursion");
					indicateTraceCompleted();
				}
			};
			problemHandler.handle(problem);
			return;
		}
		final boolean alreadyTraced;
		synchronized (availBuilder)
		{
			alreadyTraced =
				availBuilder.moduleGraph.includesVertex(resolvedName);
			if (!alreadyTraced)
			{
				availBuilder.moduleGraph.addVertex(resolvedName);
			}
			if (resolvedSuccessor != null)
			{
				// Note that a module can be both Extended and Used from the
				// same module.  That's to support selective import and renames.
				availBuilder.moduleGraph.includeEdge(
					resolvedName, resolvedSuccessor);
			}
		}
		if (alreadyTraced)
		{
			indicateTraceCompleted();
			return;
		}
		final IndexedRepositoryManager repository = resolvedName.repository();
		repository.commitIfStaleChanges(AvailBuilder.maximumStaleRepositoryMs);
		final File sourceFile = resolvedName.sourceReference();
		final ModuleArchive archive = repository.getArchive(
			resolvedName.rootRelativeName());
		final byte [] digest = archive.digestForFile(resolvedName);
		final ModuleVersionKey versionKey =
			new ModuleVersionKey(resolvedName, digest);
		final @Nullable ModuleVersion version = archive.getVersion(versionKey);
		if (version != null)
		{
			// This version was already traced and recorded for a
			// subsequent replay… like right now.  Reuse it.
			final List<String> importNames = version.getImports();
			traceModuleNames(
				resolvedName, importNames, recursionSet, problemHandler);
			indicateTraceCompleted();
			return;
		}
		// Trace the source and write it back to the repository.
		AvailCompiler.create(
			resolvedName,
			availBuilder.textInterface,
			availBuilder.pollForAbort,
			(moduleName, moduleSize, position) ->
			{
				// don't report progress from tracing imports.
			},
			compiler ->
			{
				compiler.compilationContext.diagnostics
					.setSuccessAndFailureReporters(
						() ->
						{
							assert false
								: "Should not succeed from header parsing";
						},
						this::indicateTraceCompleted);
				compiler.parseModuleHeader(
					afterHeader ->
					{
						final ModuleHeader header = stripNull(
							compiler.compilationContext.getModuleHeader());
						final List<String> importNames =
							header. importedModuleNames();
						final List<String> entryPoints =
							header.entryPointNames();
						final ModuleVersion newVersion =
							repository.new ModuleVersion(
								sourceFile.length(), importNames, entryPoints);
						availBuilder.serialize(header, newVersion);
						archive.putVersion(versionKey, newVersion);
						traceModuleNames(
							resolvedName,
							importNames,
							recursionSet,
							problemHandler);
						indicateTraceCompleted();
					});
			},
			this::indicateTraceCompleted,
			problemHandler);
	}

	/**
	 * Trace the imports of the {@linkplain ResolvedModuleName specified}
	 * {@linkplain ModuleDescriptor module}.  Return only when these new
	 * <em>requests</em> have been accounted for, so that the current request
	 * can be considered completed in the caller.
	 *
	 * @param moduleName
	 *        The name of the module being traced.
	 * @param importNames
	 *        The local names of the modules referenced by the current one.
	 * @param recursionSet
	 *        An insertion-ordered {@linkplain Set set} that remembers all
	 *        modules visited along this branch of the trace.
	 * @param problemHandler
	 *        How to handle or report {@link Problem}s that arise during the
	 *        build.
	 */
	void traceModuleNames (
		final ResolvedModuleName moduleName,
		final List<String> importNames,
		final LinkedHashSet<ResolvedModuleName> recursionSet,
		final ProblemHandler problemHandler)
	{
		// Copy the recursion set to ensure the independence of each path of the
		// tracing algorithm.
		final LinkedHashSet<ResolvedModuleName> newSet =
			new LinkedHashSet<>(recursionSet);
		newSet.add(moduleName);

		synchronized (this)
		{
			traceRequests += importNames.size();
		}

		// Recurse in parallel into each import.
		for (final String localImport : importNames)
		{
			final ModuleName importName = moduleName.asSibling(localImport);
			scheduleTraceModuleImports(
				importName, moduleName, newSet, problemHandler);
		}
	}

	/**
	 * A module was just traced, so record that fact.  Note that the trace was
	 * either successful or unsuccessful.
	 */
	synchronized void indicateTraceCompleted ()
	{
		traceCompletions++;
		AvailBuilder.log(
			Level.FINEST,
			"Traced one (%d/%d)",
			traceCompletions,
			traceRequests);
		// Avoid spurious wake-ups.
		if (traceRequests == traceCompletions)
		{
			notifyAll();
		}
	}

	/**
	 * Determine the ancestry graph of the indicated module, recording it in the
	 * {@link AvailBuilder#moduleGraph}.
	 *
	 * @param target
	 *        The ultimate module to load.
	 * @param problemHandler
	 *        How to handle or report {@link Problem}s that arise during the
	 *        build.
	 * @param afterAll
	 *        What to do after the entire trace completes.
	 */
	public void traceThen (
		final ModuleName target,
		final ProblemHandler problemHandler,
		final Continuation0 afterAll)
	{
		synchronized (this)
		{
			traceRequests = 1;
			traceCompletions = 0;
		}
		scheduleTraceModuleImports(
			target, null, new LinkedHashSet<>(), problemHandler);
		// Wait until the parallel recursive trace completes.
		synchronized (this)
		{
			while (traceRequests != traceCompletions)
			{
				try
				{
					wait();
				}
				catch (final InterruptedException e)
				{
					availBuilder.stopBuildReason("Trace was interrupted");
				}
			}
			availBuilder.runtime.moduleNameResolver().commitRepositories();
		}
		if (availBuilder.shouldStopBuild())
		{
			availBuilder.textInterface.getErrorChannel().write(
				"Load failed.\n",
				null,
				new SimpleCompletionHandler<Integer, Void>(
					r -> { },
					t -> { }));
		}
		else
		{
			final int graphSize;
			synchronized (this)
			{
				graphSize = availBuilder.moduleGraph.size();
			}
			AvailBuilder.log(
				Level.FINER,
				"Traced or kept %d modules (%d edges)",
				graphSize,
				traceCompletions);
		}
		afterAll.value();
	}
}
