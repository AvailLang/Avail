/**
 * AvailBuilder.java
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
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
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

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import com.avail.*;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.compiler.AbstractAvailCompiler.*;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.persistence.IndexedRepositoryManager.ModuleCompilation;
import com.avail.persistence.IndexedRepositoryManager.ModuleCompilationKey;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersion;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersionKey;
import com.avail.serialization.*;
import com.avail.utility.*;
import com.avail.utility.evaluation.*;

/**
 * An {@code AvailBuilder} {@linkplain AbstractAvailCompiler compiles} and
 * installs into an {@linkplain AvailRuntime Avail runtime} a target
 * {@linkplain ModuleDescriptor module} and each of its dependencies.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public final class AvailBuilder
{
	/**
	 * Whether to debug the builder.
	 */
	@InnerAccess static final boolean debugBuilder = false;

	/**
	 * The maximum age, in milliseconds, that changes should be left uncommitted
	 * in the repository.  A higher value saves space by causing the updated
	 * metadata to be rewritten at a slower rate, but the next build may have to
	 * repeat a bit more work if the previous build attempt failed before its
	 * data could be committed.
	 */
	@InnerAccess static final long maximumStaleRepositoryMs = 2000L;

	/**
	 * The {@linkplain AvailRuntime runtime} into which the
	 * {@linkplain AvailBuilder builder} will install the target
	 * {@linkplain ModuleDescriptor module} and its dependencies.
	 */
	@InnerAccess final AvailRuntime runtime;

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
	 * A {@link Graph} of {@link ResolvedModuleName}s, representing the
	 * relationships between all modules currently loaded or involved in the
	 * current build action.
	 */
	public final Graph<ResolvedModuleName> moduleGraph =
		new Graph<ResolvedModuleName>();

	/**
	 * A map from each {@link ResolvedModuleName} to its currently loaded
	 * {@link ModuleCompilation}.
	 */
	public final Map<ResolvedModuleName, ModuleCompilation>
		moduleCompilations =
			Collections
				.<ResolvedModuleName, ModuleCompilation>synchronizedMap(
					new HashMap<ResolvedModuleName, ModuleCompilation>());

	/**
	 * Construct an {@link AvailBuilder} for the provided runtime.  During a
	 * build, the passed trackers will be invoked to show progress.
	 *
	 * @param runtime
	 *        The {@link AvailRuntime} in which to load modules and execute
	 *        commands.
	 * @param localTracker
	 *        A {@linkplain CompilerProgressReporter continuation} that accepts
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
	public AvailBuilder (
		final AvailRuntime runtime,
		final CompilerProgressReporter localTracker,
		final Continuation3<ModuleName, Long, Long> globalTracker)
	{
		this.runtime = runtime;
		this.localTracker = localTracker;
		this.globalTracker = globalTracker;
	}

	/**
	 * A {@code BuildState} represents the complete state of a build process.
	 * It allows {@link AvailBuilder} to be stateless.
	 */
	private final class BuildState
	{
		/**
		 * The {@link Exception} (if any) responsible for an abnormal
		 * termination of the build.  In the event of multiple exceptions, only
		 * one will be captured.
		 */
		private volatile @Nullable Exception terminator;

		/**
		 * Fail the current trace due to the specified {@link Exception}.  Only
		 * the first exception will be reported.
		 *
		 * @param exception
		 *        An {@link Exception} responsible for the trace failure.
		 */
		@InnerAccess synchronized void failTrace (
			final Exception exception)
		{
			if (terminator == null)
			{
				terminator = exception;
			}
		}

		/**
		 * Answer the reason for the failure of the current build, or {@code
		 * null} if none.
		 *
		 * @return The {@link Exception}, if any, responsible for the trace
		 *         failure.
		 */
		public synchronized @Nullable Exception terminator ()
		{
			return terminator;
		}

		/**
		 * The number of trace requests that have been scheduled.
		 */
		public int traceRequests = 0;

		/**
		 * The number of trace requests that have been completed.
		 */
		public int traceCompletions = 0;

		/**
		 * Construct a new {@link BuildState}.
		 */
		@InnerAccess BuildState ()
		{
			// No implementation required, but this prevents a synthetic
			// accessor being generated in the enclosing class for this class's
			// default constructor.
		}

		/**
		 * Schedule tracing of the imports of the {@linkplain ModuleDescriptor
		 * module} specified by the given {@linkplain ModuleName module name}.
		 * The {@link #traceRequests} counter has been incremented already for
		 * this tracing, and the {@link #traceCompletions} will eventually be
		 * incremented by this method, but only <em>after</em> increasing the
		 * {@link #traceRequests} for each recursive trace that is scheduled
		 * here.  That ensures the two counters won't accidentally be equal at
		 * any time except after the last trace has completed.
		 *
		 * <p>When traceCompletions finally does reach traceRequests, a {@link
		 * #notifyAll()} will be sent to the {@link BuildState}.</p>
		 *
		 * @param qualifiedName
		 *        A fully-qualified {@linkplain ModuleName module name}.
		 * @param resolvedSuccessor
		 *        The resolved name of the module using or extending this
		 *        module, or {@code null} if this module is the start of the
		 *        recursive
		 *        resolution (i.e., it will be the last one compiled).
		 * @param recursionSet
		 *        An insertion-ordered {@linkplain Set set} that remembers all
		 *        modules visited along this branch of the trace.
		 */
		@InnerAccess void scheduleTraceModuleImports (
			final ModuleName qualifiedName,
			final @Nullable ResolvedModuleName resolvedSuccessor,
			final LinkedHashSet<ResolvedModuleName> recursionSet)
		{
			runtime.execute(new AvailTask(
				FiberDescriptor.loaderPriority,
				new Continuation0()
				{
					@Override
					public void value ()
					{
						if (terminator() == null)
						{
							ResolvedModuleName resolvedName = null;
							try
							{
								resolvedName =
									runtime.moduleNameResolver().resolve(
										qualifiedName, resolvedSuccessor);
								traceModuleImports(
									resolvedName,
									resolvedSuccessor,
									recursionSet);
							}
							catch (final Exception e)
							{
								failTrace(e);
								indicateTraceCompleted();
							}
						}
					}
				}));
		}

		/**
		 * Trace the imports of the {@linkplain ModuleDescriptor module}
		 * specified by the given {@linkplain ModuleName module name}.
		 *
		 * @param resolvedName
		 *        A resolved {@linkplain ModuleName module name} to trace.
		 * @param resolvedSuccessor
		 *        The resolved name of the module using or extending this
		 *        module, or {@code null} if this module is the start of the
		 *        recursive resolution (i.e., it will be the last one compiled).
		 * @param recursionSet
		 *        An insertion-ordered {@linkplain Set set} that remembers all
		 *        modules visited along this branch of the trace.
		 *
		 * @throws IOException
		 *         If the source module cannot be opened or read.
		 * @throws AvailCompilerException
		 *         If the {@linkplain AbstractAvailCompiler compiler} is unable
		 *         to find a module declaration for this {@linkplain
		 *         ModuleDescriptor module}.
		 * @throws RecursiveDependencyException
		 *         If the specified {@linkplain ModuleDescriptor module}
		 *         recursively depends upon itself.
		 * @throws MalformedSerialStreamException
		 *         If the repository contains invalid serialized module data.
		 */
		@InnerAccess void traceModuleImports (
				final ResolvedModuleName resolvedName,
				final @Nullable ResolvedModuleName resolvedSuccessor,
				final LinkedHashSet<ResolvedModuleName> recursionSet)
			throws
				IOException,
				AvailCompilerException,
				RecursiveDependencyException,
				MalformedSerialStreamException
		{
			// Detect recursion into this module.
			if (recursionSet.contains(resolvedName))
			{
				throw new RecursiveDependencyException(
					resolvedName,
					recursionSet);
			}
			final boolean alreadyTraced;
			synchronized (this)
			{
				alreadyTraced = moduleGraph.includesVertex(resolvedName);
				moduleGraph.includeVertex(resolvedName);
				if (resolvedSuccessor != null)
				{
					// Note that a module can be both Extended and Used from the
					// same module.  That's to support selective import and
					// renames.
					moduleGraph.includeEdge(resolvedName, resolvedSuccessor);
				}
			}
			if (alreadyTraced)
			{
				indicateTraceCompleted();
			}
			else
			{
				final IndexedRepositoryManager repository =
					resolvedName.repository();
				repository.commitIfStaleChanges(maximumStaleRepositoryMs);
				final File sourceFile = resolvedName.sourceReference();
				assert sourceFile != null;
				final ModuleVersionKey versionKey =
					new ModuleVersionKey(resolvedName);
				final ModuleVersion version = repository.getVersion(versionKey);
				if (version != null)
				{
					// This version was already traced in the past.  Reuse it.
					final List<String> importNames = version.getImports();
					traceModuleNames(resolvedName, importNames, recursionSet);
				}
				else
				{
					// Trace the source and write it back to the repository.
					AbstractAvailCompiler.create(
						resolvedName,
						true,
						new Continuation1<AbstractAvailCompiler>()
						{
							@Override
							public void value (
								final @Nullable AbstractAvailCompiler compiler)
							{
								assert compiler != null;
								compiler.parseModuleHeader();
								final List<String> importNames =
									compiler.moduleHeader.importedModuleNames();
								repository.putVersion(
									versionKey,
									repository.new ModuleVersion(
										sourceFile.length(),
										importNames));
								traceModuleNames(
									resolvedName,
									importNames,
									recursionSet);
							}
						},
						new Continuation1<Exception>()
						{
							@Override
							public void value (
								final @Nullable Exception exception)
							{
								assert exception != null;
								failTrace(exception);
							}
						});
				}
			}
		}

		/**
		 * Trace the imports of the {@linkplain ResolvedModuleName specified}
		 * {@linkplain ModuleDescriptor module}.
		 *
		 * @param moduleName
		 *        The name of the module being traced.
		 * @param importNames
		 *        The local names of the modules referenced by the current one.
		 * @param recursionSet
		 *        An insertion-ordered {@linkplain Set set} that remembers all
		 *        modules visited along this branch of the trace.
		 */
		@InnerAccess void traceModuleNames (
			final ResolvedModuleName moduleName,
			final List<String> importNames,
			final LinkedHashSet<ResolvedModuleName> recursionSet)
		{
			synchronized (BuildState.this)
			{
				traceRequests += importNames.size();
			}
			// Copy the recursion set to ensure the independence of each
			// path of the tracing algorithm.
			final LinkedHashSet<ResolvedModuleName> newSet =
				new LinkedHashSet<>(recursionSet);
			newSet.add(moduleName);

			// Recurse in parallel into each import.
			for (final String localImport : importNames)
			{
				final ModuleName importName = moduleName.asSibling(localImport);
				scheduleTraceModuleImports(importName, moduleName, newSet);
			}
			indicateTraceCompleted();
		}

		/**
		 * Fail the current build due to the specified {@link Exception}.  Only
		 * the first exception will be reported.
		 *
		 * @param moduleName
		 *        The resolved module name that failed to build or scan.
		 * @param lastPosition
		 *        The last source position that has been previously reported.
		 * @param killer
		 *        An {@link Exception} responsible for build failure.
		 */
		@InnerAccess synchronized void failBuild (
			final ResolvedModuleName moduleName,
			final long lastPosition,
			final Exception killer)
		{
			postLoad(moduleName, lastPosition);
			if (terminator == null)
			{
				terminator = killer;
			}
		}

		/**
		 * A module was just traced, so record that fact.  Note that the trace
		 * was either successful or unsuccessful.
		 */
		public synchronized void indicateTraceCompleted ()
		{
			traceCompletions++;
			// Avoid spurious wake-ups.
			if (traceRequests == traceCompletions)
			{
				notifyAll();
			}
		}

		/** The size, in bytes, of all source files that will be built. */
		private long globalCodeSize = 0L;

		/**
		 * Answer the size, in bytes, of all source files that will be built.
		 *
		 * @return The size, in bytes, of all source files that will be built.
		 */
		@InnerAccess synchronized long globalCodeSize ()
		{
			if (globalCodeSize == 0L)
			{
				for (final ResolvedModuleName mod : moduleGraph.vertices())
				{
					globalCodeSize += mod.moduleSize();
				}
			}
			return globalCodeSize;
		}

		/** The number of bytes compiled so far. */
		final @InnerAccess AtomicLong bytesCompiled = new AtomicLong(0L);

		/**
		 * Schedule a build of the specified {@linkplain ModuleDescriptor
		 * module}, on the assumption that its predecessors have already been
		 * built.
		 *
		 * @param target
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that should be loaded.
		 * @param completionAction
		 *        The {@linkplain Continuation0 action} to perform after this
		 *        module has been loaded.
		 */
		@InnerAccess void scheduleLoadModule (
			final ResolvedModuleName target,
			final Continuation0 completionAction)
		{
			// Avoid scheduling new tasks if an exception has been encountered.
			if (terminator() != null)
			{
				return;
			}
			runtime.execute(new AvailTask(
				FiberDescriptor.loaderPriority,
				new Continuation0()
				{
					@Override
					public void value ()
					{
						// An exception has been encountered since the previous
						// check.  Exit quickly.
						if (terminator() != null)
						{
							completionAction.value();
							return;
						}
						try
						{
							loadModule(target, completionAction);
						}
						catch (final Exception exception)
						{
							failBuild(target, 0L, exception);
							completionAction.value();
						}
					}
				}));
		}

		/**
		 * Load the specified {@linkplain ModuleDescriptor module} into the
		 * {@linkplain AvailRuntime Avail runtime}. If a current compiled module
		 * is available from the {@linkplain IndexedRepositoryManager
		 * repository}, then simply load it. Otherwise, {@linkplain
		 * AbstractAvailCompiler compile} the module, store it into the
		 * repository, and then load it.
		 *
		 * <p>
		 * Note that the predecessors of this module must have already been
		 * loaded.
		 * </p>
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that should be loaded.
		 * @param completionAction
		 *        What to do after loading the module successfully.
		 * @throws IOException
		 *         If the source module cannot be opened or read.
		 * @throws AvailCompilerException
		 *         If the compiler cannot build a module.
		 * @throws MalformedSerialStreamException
		 *         If the repository contains invalid serialized module data.
		 */
		@InnerAccess void loadModule (
				final ResolvedModuleName moduleName,
				final Continuation0 completionAction)
			throws
				IOException,
				AvailCompilerException,
				MalformedSerialStreamException
		{
			globalTracker.value(
				moduleName, bytesCompiled.get(), globalCodeSize());
			// If the module is already loaded into the runtime, then we must
			// not reload it.
			if (runtime.includesModuleNamed(
				StringDescriptor.from(moduleName.qualifiedName())))
			{
				// The module is already loaded.
				if (debugBuilder)
				{
					System.out.format(
						"Already loaded: \"%s\"%n",
						moduleName.qualifiedName());
				}
				assert moduleCompilations.containsKey(moduleName);
				postLoad(moduleName, 0L);
				completionAction.value();
			}
			else
			{
				assert !moduleCompilations.containsKey(moduleName);
				final IndexedRepositoryManager repository =
					moduleName.repository();
				final ModuleVersionKey versionKey =
					new ModuleVersionKey(moduleName);
				final ModuleVersion version = repository.getVersion(versionKey);
				assert version != null
				: "Module version should have been populated during tracing";
				final Map<String, ModuleCompilation> compilationsByName =
					new HashMap<>();
				for (final ResolvedModuleName predecessorName :
					moduleGraph.predecessorsOf(moduleName))
				{
					final String localName = predecessorName.localName();
					final ModuleCompilation predecessorCompilation =
						moduleCompilations.get(predecessorName);
					assert predecessorCompilation != null;
					compilationsByName.put(localName, predecessorCompilation);
				}
				final List<String> imports = version.getImports();
				final long [] predecessorCompilationTimes =
					new long [imports.size()];
				for (int i = 0; i < predecessorCompilationTimes.length; i++)
				{
					final ModuleCompilation predecessorCompilation =
						compilationsByName.get(imports.get(i));
					predecessorCompilationTimes[i] =
						predecessorCompilation.compilationTime;
				}
				final ModuleCompilationKey compilationKey =
					new ModuleCompilationKey(predecessorCompilationTimes);
				final ModuleCompilation compilation =
					version.getCompilation(compilationKey);
				if (compilation != null)
				{
					// The current version of the module is already
					// compiled, so load the repository's version.
					synchronized (this)
					{
						moduleCompilations.put(moduleName, compilation);
					}
					loadRepositoryModule(
						moduleName,
						compilation,
						completionAction);
				}
				else
				{
					// Compile the module and cache its compiled form.
					compileModule(
						moduleName,
						compilationKey,
						completionAction);
				}
			}
		}

		/**
		 * Load the specified {@linkplain ModuleDescriptor module} from the
		 * {@linkplain IndexedRepositoryManager repository} and into the
		 * {@linkplain AvailRuntime Avail runtime}.
		 *
		 * <p>
		 * Note that the predecessors of this module must have already been
		 * loaded.
		 * </p>
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that should be loaded.
		 * @param compilation
		 *        The {@link ModuleCompilation} containing information about the
		 *        particular stored compilation of this module in the
		 *        repository.
		 * @param completionAction
		 *        What to do after loading the module successfully.
		 * @throws AvailCompilerException
		 *         If the compiler cannot build a module.
		 * @throws MalformedSerialStreamException
		 *         If the repository contains invalid serialized module data.
		 */
		private void loadRepositoryModule (
				final ResolvedModuleName moduleName,
				final ModuleCompilation compilation,
				final Continuation0 completionAction)
			throws MalformedSerialStreamException
		{
			localTracker.value(moduleName, -1L, -1L, -1L);
			// Read the module data from the repository.
			final byte [] bytes = compilation.getBytes();
			assert bytes != null;
			final ByteArrayInputStream inputStream = validatedBytesFrom(bytes);
			final A_Module module = ModuleDescriptor.newModule(
				StringDescriptor.from(moduleName.qualifiedName()));
			final AvailLoader loader = new AvailLoader(module);
			final Deserializer deserializer = new Deserializer(
				inputStream, runtime);
			deserializer.currentModule(module);
			try
			{
				A_Atom tag = deserializer.deserialize();
				if (tag != null &&
					!tag.equals(AtomDescriptor.moduleHeaderSectionAtom()))
				{
					throw new RuntimeException(
						"Expected module header tag");
				}
				final ModuleHeader header = new ModuleHeader(moduleName);
				header.deserializeHeaderFrom(deserializer);
				module.isSystemModule(header.isSystemModule);
				final String errorString = header.applyToModule(
					module, runtime);
				if (errorString != null)
				{
					throw new RuntimeException(errorString);
				}
				tag = deserializer.deserialize();
				if (tag != null &&
					!tag.equals(AtomDescriptor.moduleBodySectionAtom()))
				{
					throw new RuntimeException(
						"Expected module body tag");
				}
			}
			catch (final MalformedSerialStreamException|RuntimeException e)
			{
				module.removeFrom(loader);
				throw e;
			}
			loader.createFilteredBundleTree();

			final Continuation1<Exception> fail =
				new Continuation1<Exception>()
				{
					@Override
					public void value (final @Nullable Exception exception)
					{
						assert exception != null;
						try
						{
							module.removeFrom(loader);
						}
						finally
						{
							failBuild(moduleName, 0L, exception);
						}
					}
				};
			// Run each zero-argument block, one after another.
			final MutableOrNull<Continuation1<AvailObject>> runNext =
				new MutableOrNull<>();
			runNext.value = new Continuation1<AvailObject>()
			{
				@Override
				public void value (final @Nullable AvailObject ignored)
				{
					final A_Function function;
					try
					{
						function = deserializer.deserialize();
					}
					catch (
						final MalformedSerialStreamException|RuntimeException e)
					{
						fail.value(e);
						return;
					}
					if (function != null)
					{
						final A_RawFunction code = function.code();
						final A_Fiber fiber =
							FiberDescriptor.newLoaderFiber(
								function.kind().returnType(),
								loader,
								StringDescriptor.from(
									String.format(
										"Load repo module %s, in %s:%d",
										code.methodName(),
										code.module().moduleName(),
										code.startingLineNumber())));
						fiber.resultContinuation(runNext.value());
						fiber.failureContinuation(fail);
						Interpreter.runOutermostFunction(
							runtime,
							fiber,
							function,
							Collections.<AvailObject>emptyList());
					}
					else
					{
						runtime.addModule(module);
						module.cleanUpAfterCompile();
						postLoad(moduleName, 0L);
						completionAction.value();
					}
				}
			};
			// The argument is ignored, so it doesn't matter what gets passed.
			runNext.value().value(NilDescriptor.nil());
		}

		/**
		 * Compile the specified {@linkplain ModuleDescriptor module}, store it
		 * into the {@linkplain IndexedRepositoryManager repository}, and then
		 * load it into the {@linkplain AvailRuntime Avail runtime}.
		 *
		 * <p>
		 * Note that the predecessors of this module must have already been
		 * loaded.
		 * </p>
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that should be loaded.
		 * @param compilationKey
		 *        The circumstances of compilation of this module.  Currently
		 *        this is just the compilation times ({@code long}s) of the
		 *        module's currently loaded predecessors, listed in the same
		 *        order as the module's {@linkplain ModuleHeader#importedModules
		 *        imports}.
		 * @param completionAction
		 *        What to do after loading the module successfully or
		 *        unsuccessfully.
		 * @throws IOException
		 *         If the source module cannot be opened or read.
		 * @throws AvailCompilerException
		 *         If the compiler cannot build a module.
		 */
		private void compileModule (
				final ResolvedModuleName moduleName,
				final ModuleCompilationKey compilationKey,
				final Continuation0 completionAction)
			throws IOException, AvailCompilerException
		{
			final Mutable<Long> lastPosition = new Mutable<>(0L);
			// Capture the file's modification time *before* compiling.  That
			// way if the file is modified during compiling, the next build will
			// simply treat the stored data as invalid and recompile it.
			final ModuleVersionKey versionKey =
				new ModuleVersionKey(moduleName);
			final IndexedRepositoryManager repository = moduleName.repository();
			final Continuation1<AbstractAvailCompiler> continuation =
				new Continuation1<AbstractAvailCompiler>()
				{
					@Override
					public void value (
						final @Nullable AbstractAvailCompiler compiler)
					{
						assert compiler != null;
						compiler.parseModule(
							new CompilerProgressReporter()
							{
								@Override
								public void value (
									final @Nullable ModuleName moduleName2,
									final @Nullable Long lineNumber,
									final @Nullable Long localPosition,
									final @Nullable Long moduleSize)
								{
									assert moduleName2 != null;
									assert lineNumber != null;
									assert localPosition != null;
									assert moduleSize != null;
									assert moduleName.equals(moduleName2);
									localTracker.value(
										moduleName,
										lineNumber,
										localPosition,
										moduleSize);
									globalTracker.value(
										moduleName,
										bytesCompiled.addAndGet(
											localPosition - lastPosition.value),
										globalCodeSize());
									lastPosition.value = localPosition;
								}
							},
							new Continuation1<A_Module>()
							{
								@Override
								public void value (
									final @Nullable A_Module module)
								{
									final ByteArrayOutputStream stream =
										compiler.serializerOutputStream;
									// This is the moment of compilation.
									final long compilationTime =
										System.currentTimeMillis();
									final ModuleCompilation compilation =
										repository.new ModuleCompilation(
											compilationTime,
											appendCRC(stream.toByteArray()));
									repository.putCompilation(
										versionKey,
										compilationKey,
										compilation);
									repository.commitIfStaleChanges(
										maximumStaleRepositoryMs);
									postLoad(moduleName, lastPosition.value);
									moduleCompilations.put(
										moduleName, compilation);
									completionAction.value();
								}
							},
							new Continuation1<Exception>()
							{
								@Override
								public void value (
									final @Nullable Exception killer)
								{
									assert killer != null;
									failBuild(
										moduleName,
										lastPosition.value,
										killer);
									completionAction.value();
								}
							});
					}
				};
			AbstractAvailCompiler.create(
				moduleName,
				false,
				continuation,
				new Continuation1<Exception>()
				{
					@Override
					public void value (final @Nullable Exception exception)
					{
						assert exception != null;
						failBuild(moduleName, lastPosition.value, exception);
					}
				});
		}

		/**
		 * Report progress related to this module.  In particular, note that the
		 * current module has advanced from its provided lastPosition to the
		 * end of the module.
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that just finished loading.
		 * @param lastPosition
		 *        The last local file position previously reported.
		 */
		@InnerAccess void postLoad (
			final ResolvedModuleName moduleName,
			final long lastPosition)
		{
			globalTracker.value(
				moduleName,
				bytesCompiled.addAndGet(moduleName.moduleSize() - lastPosition),
				globalCodeSize());
		}

		/**
		 * Given an array of bytes, check that the last four bytes, when treated
		 * as a Big Endian unsigned int, agree with the {@link CRC32} checksum
		 * of the bytes excluding the last four.  Fail if they disagree.  Answer
		 * a ByteArrayInputStream on the bytes excluding the last four.
		 *
		 * @param bytes An array of bytes.
		 * @return A ByteArrayInputStream on the non-CRC portion of the bytes.
		 * @throws MalformedSerialStreamException If the CRC check fails.
		 */
		private ByteArrayInputStream validatedBytesFrom (final byte[] bytes)
		throws MalformedSerialStreamException
		{
			final int storedChecksum =
				ByteBuffer.wrap(bytes).getInt(bytes.length - 4);
			final Checksum checksum = new CRC32();
			checksum.update(bytes, 0, bytes.length - 4);
			if ((int)checksum.getValue() != storedChecksum)
			{
				throw new MalformedSerialStreamException(null);
			}
			return new ByteArrayInputStream(bytes, 0, bytes.length - 4);
		}

		/**
		 * Given a byte array, compute the {@link CRC32} checksum and append the
		 * int value as four bytes (Big Endian), answering the new augmented
		 * byte array.
		 *
		 * @param bytes The input bytes.
		 * @return The bytes followed by the checksum.
		 */
		public byte[] appendCRC (final byte[] bytes)
		{
			final CRC32 checksum = new CRC32();
			checksum.update(bytes);
			final int checksumInt =
				(int)checksum.getValue();
			final ByteBuffer combined =
				ByteBuffer.allocate(bytes.length + 4);
			combined.put(bytes);
			combined.putInt(checksumInt);
			final byte[] combinedBytes =
				new byte[bytes.length + 4];
			combined.flip();
			combined.get(combinedBytes);
			return combinedBytes;
		}
	}

	/**
	 * Build the {@linkplain ModuleDescriptor target} and its dependencies.
	 *
	 * @param target
	 *        The {@linkplain ModuleName canonical name} of the module that the
	 *        {@linkplain AvailBuilder builder} must (recursively) load into the
	 *        {@linkplain AvailRuntime runtime}.
	 * @throws Exception
	 *         If anything went wrong.
	 */
	@InnerAccess void buildTarget (
			final ModuleName target)
		throws Exception
	{
		final BuildState state = new BuildState();
		state.traceRequests = 1;
		state.scheduleTraceModuleImports(
			target,
			null,
			new LinkedHashSet<ResolvedModuleName>());
		// Wait until the parallel recursive trace completes.
		synchronized (state)
		{
			while (state.traceRequests != state.traceCompletions)
			{
				state.wait();
			}
			runtime.moduleNameResolver().commitRepositories();
			final @Nullable Exception term = state.terminator();
			if (term != null)
			{
				throw term;
			}
		}
		System.out.format(
			"Traced %d modules (%d edges).%n",
			moduleGraph.size(),
			state.traceCompletions);
		System.out.flush();
		moduleGraph.parallelVisit(
			new Continuation2<ResolvedModuleName, Continuation0>()
			{
				@Override
				public void value (
					final @Nullable ResolvedModuleName moduleName,
					final @Nullable Continuation0 completionAction)
				{
					assert moduleName != null;
					assert completionAction != null;
					state.scheduleLoadModule(moduleName, completionAction);
				}
			});
		// Parallel load has now completed or failed.
		final @Nullable Exception exception = state.terminator();
		if (exception != null)
		{
			throw exception;
		}
	}

	/**
	 * Schedule the recursive build of the {@linkplain ModuleDescriptor target}
	 * and its dependencies. Any {@linkplain Exception exceptions} raised during
	 * the build are rerouted to the current {@linkplain Thread thread} and
	 * rethrown.
	 *
	 * @param target
	 *        The {@linkplain ModuleName canonical name} of the module that the
	 *        {@linkplain AvailBuilder builder} must (recursively) load into the
	 *        {@linkplain AvailRuntime runtime}.
	 * @throws AvailCompilerException
	 *         If an encountered module could not be built.
	 * @throws RecursiveDependencyException
	 *         If an encountered module recursively depends upon itself.
	 * @throws UnresolvedDependencyException
	 *         If a module name could not be resolved.
	 * @throws FiberTerminationException
	 *         If compilation has been terminated forcibly.
	 * @throws InterruptedException
	 *         If any {@linkplain AvailThread thread} is interrupted during the
	 *         build.
	 */
	public void build (
			final ModuleName target)
		throws
			AvailCompilerException,
			RecursiveDependencyException,
			UnresolvedDependencyException,
			InterruptedException
	{
		final MutableOrNull<Throwable> killer = new MutableOrNull<>();
		final Semaphore semaphore = new Semaphore(0);
		runtime.execute(new AvailTask(
			FiberDescriptor.loaderPriority,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					try
					{
						buildTarget(target);
					}
					catch (final Throwable e)
					{
						killer.value = e;
					}
					finally
					{
						semaphore.release();
					}
				}
			}));
		// Wait for the builder to finish.
		semaphore.acquire();
		final Throwable e = killer.value;
		if (e != null)
		{
			if (e instanceof AvailCompilerException)
			{
				throw (AvailCompilerException) e;
			}
			else if (e instanceof FiberTerminationException)
			{
				throw (FiberTerminationException) e;
			}
			else if (e instanceof RecursiveDependencyException)
			{
				throw (RecursiveDependencyException) e;
			}
			else if (e instanceof UnresolvedDependencyException)
			{
				throw (UnresolvedDependencyException) e;
			}
			else if (e instanceof Error)
			{
				throw (Error) e;
			}
			else if (e instanceof RuntimeException)
			{
				throw (RuntimeException) e;
			}
			else
			{
				throw new RuntimeException(e);
			}
		}
	}
}
