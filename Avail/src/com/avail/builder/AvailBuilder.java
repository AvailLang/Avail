/**
 * AvailBuilder.java Copyright © 1993-2013, Mark van Gulik and Todd L Smith. All
 * rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.*;
import com.avail.*;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.compiler.AbstractAvailCompiler.ModuleHeader;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.serialization.*;
import com.avail.utility.*;

/**
 * An {@code AvailBuilder} {@linkplain AbstractAvailCompiler compiles} and
 * installs into an {@linkplain AvailRuntime Avail runtime} a target
 * {@linkplain ModuleDescriptor module} and each of its dependencies.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailBuilder
{
	/**
	 * The {@linkplain AvailRuntime runtime} into which the
	 * {@linkplain AvailBuilder builder} will install the target
	 * {@linkplain ModuleDescriptor module} and its dependencies.
	 */
	final @InnerAccess AvailRuntime runtime;

	/**
	 * The {@link Repository} of compiled modules to populate and reuse in order
	 * to accelerate builds.
	 */
	final @InnerAccess Repository repository;

	/**
	 * Construct a new {@link AvailBuilder}.
	 *
	 * @param runtime
	 *        The {@linkplain AvailRuntime runtime} into which the {@linkplain
	 *        AvailBuilder builder} will install the target {@linkplain
	 *        ModuleDescriptor module} and its dependencies.
	 * @param repository
	 *        The {@link Repository} which holds compiled modules to accelerate
	 *        builds.
	 */
	public AvailBuilder (
		final AvailRuntime runtime,
		final Repository repository)
	{
		this.runtime = runtime;
		this.repository = repository;
	}

	/**
	 * A {@code BuildState} represents the complete state of a build process.
	 * It allows {@link AvailBuilder} to be stateless.
	 */
	private final class BuildState
	{
		/**
		 * The {@linkplain Throwable throwable} (if any) responsible for an
		 * abnormal termination of the build.
		 */
		public volatile Throwable terminator;

		/**
		 * Fail the current build due to the specified {@linkplain Throwable
		 * throwable}. Only the first throwable will be reported.
		 *
		 * @param killer
		 *        A throwable responsible for build failure.
		 */
		public synchronized void failBuild (final Throwable killer)
		{
			if (terminator == null)
			{
				terminator = killer;
				notifyAll();
			}
		}

		/**
		 * The set of module names that have already been encountered and
		 * completely recursed through.
		 */
		public final
		Set<ResolvedModuleName> completionSet =
			new HashSet<ResolvedModuleName>();

		/**
		 * A {@linkplain Map map} from {@linkplain ResolvedModuleName resolved
		 * module names} to their predecessors.
		 */
		public final
		Map<ResolvedModuleName, Set<ResolvedModuleName>> predecessors =
			new HashMap<ResolvedModuleName, Set<ResolvedModuleName>>();

		/**
		 * A {@linkplain Map map} from {@linkplain ResolvedModuleName resolved
		 * module names} to their successors.
		 */
		public final
		Map<ResolvedModuleName, Set<ResolvedModuleName>> successors =
			new HashMap<ResolvedModuleName, Set<ResolvedModuleName>>();

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
		public BuildState ()
		{
			// No implementation required, but this prevents a synthetic
			// accessor being generated in the enclosing class for this class's
			// default constructor.
		}

		/**
		 * Trace the imports of the {@linkplain ModuleDescriptor module}
		 * specified by the given {@linkplain ModuleName module name}.
		 *
		 * @param qualifiedName
		 *        A fully-qualified {@linkplain ModuleName module name}.
		 * @param resolvedSuccessor
		 *        The resolved name of the module using or extending this
		 *        module, or {@code null} if this module is the start of the
		 *        recursive resolution (i.e., it will be the last one compiled).
		 * @param recursionSet
		 *        An insertion-ordered {@linkplain Set set} that remembers all
		 *        modules visited along this branch of the trace.
		 * @throws IOException
		 *         If the source module cannot be opened or read.
		 * @throws AvailCompilerException
		 *         If the {@linkplain AbstractAvailCompiler compiler} is unable
		 *         to find a module declaration for this {@linkplain
		 *         ModuleDescriptor module}.
		 * @throws RecursiveDependencyException
		 *         If the specified {@linkplain ModuleDescriptor module}
		 *         recursively depends upon itself.
		 * @throws UnresolvedDependencyException
		 *         If the specified {@linkplain ModuleDescriptor module} name
		 *         could not be resolved to a module.
		 */
		@InnerAccess void traceModuleImports (
				final ModuleName qualifiedName,
				final @Nullable ResolvedModuleName resolvedSuccessor,
				final LinkedHashSet<ResolvedModuleName> recursionSet)
			throws
				IOException,
				AvailCompilerException,
				RecursiveDependencyException,
				UnresolvedDependencyException
		{
			final ResolvedModuleName resolution =
				runtime.moduleNameResolver().resolve(qualifiedName);
			if (resolution == null)
			{
				assert resolvedSuccessor != null;
				throw new UnresolvedDependencyException(
					resolvedSuccessor,
					qualifiedName.localName());
			}

			// Detect recursion into this module.
			if (recursionSet.contains(resolution))
			{
				recursionSet.add(resolution);
				throw new RecursiveDependencyException(recursionSet);
			}

			// Prevent recursion into this module.
			recursionSet.add(resolution);

			synchronized (this)
			{
				final boolean alreadyTraced =
					completionSet.contains(resolution);

				// If the module hasn't been traced yet, then set up its
				// predecessor and successor sets.
				if (!alreadyTraced)
				{
					assert !predecessors.containsKey(resolution);
					assert !successors.containsKey(resolution);
					predecessors.put(
						resolution, new HashSet<ResolvedModuleName>());
					successors.put(
						resolution, new HashSet<ResolvedModuleName>());
				}

				// Wire together the module and its successor.
				if (resolvedSuccessor != null)
				{
					predecessors.get(resolvedSuccessor).add(resolution);
					successors.get(resolution).add(resolvedSuccessor);
				}

				// If the module has already been traced, then update the trace
				// completion count and exit.
				if (alreadyTraced)
				{
					traceCompletions++;
					// Avoid spurious wake-ups.
					if (traceRequests == traceCompletions)
					{
						notifyAll();
					}
					return;
				}

				// Arrange for early exit from unnecessary subsequent visits.
				completionSet.add(resolution);
			}

			// Build the set of names of imported modules.
			AbstractAvailCompiler.create(
				resolution,
				true,
				new Continuation1<AbstractAvailCompiler>()
				{
					@Override
					public void value (
						final @Nullable AbstractAvailCompiler compiler)
					{
						assert compiler != null;
						final ModuleHeader header =
							compiler.parseModuleHeader();
						final Set<ModuleName> importedModules =
							new HashSet<ModuleName>(
								header.extendedModules.size()
									+ header.usedModules.size());
						for (final A_Tuple extendedModule
							: header.extendedModules)
						{
							importedModules.add(resolution.asSibling(
								extendedModule.tupleAt(1).asNativeString()));
						}
						for (final A_Tuple usedModule
							: header.usedModules)
						{
							importedModules.add(resolution.asSibling(
								usedModule.tupleAt(1).asNativeString()));
						}
						// Update the count of trace requests and completions.
						synchronized (BuildState.this)
						{
							traceRequests += importedModules.size();
							traceCompletions++;
							// Avoid spurious wake-ups.
							if (traceRequests == traceCompletions)
							{
								BuildState.this.notifyAll();
							}
						}
						// Recurse into each import.
						for (final ModuleName moduleName : importedModules)
						{
							// Copy the recursion set to ensure the independence of each
							// continuation of the tracing algorithm.
							final LinkedHashSet<ResolvedModuleName> newSet =
								new LinkedHashSet<ResolvedModuleName>(
									recursionSet);
							scheduleTraceModuleImports(
								BuildState.this,
								moduleName,
								resolution,
								newSet);
						}
					}
				},
				new Continuation1<Throwable>()
				{
					@Override
					public void value (final @Nullable Throwable killer)
					{
						assert killer != null;
						failBuild(killer);
					}
				});
		}

		/**
		 * A {@linkplain Map map} from {@linkplain ResolvedModuleName resolved
		 * module names} to {@linkplain AtomicInteger atomic counters} (of their
		 * unbuilt predecessors).
		 */
		private Map<ResolvedModuleName, AtomicInteger> unbuiltPredecessors;

		/**
		 * Traverse {@linkplain #predecessors} to find the {@linkplain
		 * ModuleDescriptor modules} that were determined to be
		 * origins (i.e., modules with no predecessors).
		 *
		 * <p>This method has the side effect of preparing {@linkplain
		 * #unbuiltPredecessors} for use. Possibly ugly, but more efficient than
		 * yet another traversal of {@linkplain #predecessors}.
		 * </p>
		 *
		 * @return The origin modules discovered during tracing.
		 */
		List<ResolvedModuleName> originModules ()
		{
			unbuiltPredecessors =
				new HashMap<ResolvedModuleName, AtomicInteger>(
					predecessors.size());
			final List<ResolvedModuleName> originModules =
				new ArrayList<ResolvedModuleName>(3);
			for (final Map.Entry<ResolvedModuleName, Set<ResolvedModuleName>> e
				: predecessors.entrySet())
			{
				final ResolvedModuleName name = e.getKey();
				final Set<ResolvedModuleName> imports = e.getValue();
				if (imports.isEmpty())
				{
					originModules.add(name);
				}
				unbuiltPredecessors.put(
					name, new AtomicInteger(imports.size()));
			}
			return originModules;
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
				for (final ResolvedModuleName resolution : completionSet)
				{
					globalCodeSize += resolution.fileReference().length();
				}
			}
			return globalCodeSize;
		}

		/** The number of bytes compiled so far. */
		final @InnerAccess AtomicLong bytesCompiled = new AtomicLong(0L);

		/** The number of module loads that have completed already. */
		public int loadCompletions = 0;

		/**
		 * Adjust the unbuilt predecessors and schedule build tasks
		 * appropriately.
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that just finished loading.
		 * @param lastPosition
		 *        The last local file position reported.
		 * @param localTracker
		 *        A {@linkplain Continuation4 continuation} that accepts
		 *        <ol>
		 *        <li>the name of the module undergoing {@linkplain
		 *        AbstractAvailCompiler compilation},</li>
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
		@InnerAccess void postLoad (
			final ResolvedModuleName moduleName,
			final long lastPosition,
			final Continuation4<ModuleName, Long, Long, Long> localTracker,
			final Continuation3<ModuleName, Long, Long> globalTracker)

		{
			// Decrement the count of unbuilt predecessors of each successor
			// module, scheduling it to load if this count reaches zero.
			for (final ResolvedModuleName successorName
				: successors.get(moduleName))
			{
				final int count =
					unbuiltPredecessors.get(successorName).decrementAndGet();
				if (count == 0)
				{
					scheduleLoadModule(
						this, successorName, localTracker, globalTracker);
				}
			}
			globalTracker.value(
				moduleName,
				bytesCompiled.addAndGet(
					moduleName.fileReference().length() - lastPosition),
				globalCodeSize());
			synchronized (this)
			{
				loadCompletions++;
				if (loadCompletions == completionSet.size())
				{
					notifyAll();
				}
			}
		}

		/**
		 * Load the specified {@linkplain ModuleDescriptor module} from the
		 * {@linkplain Repository repository} and into the {@linkplain
		 * AvailRuntime Avail runtime}.
		 *
		 * <p>
		 * Note that the predecessors of this module must have already been
		 * loaded.
		 * </p>
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that should be loaded.
		 * @param localTracker
		 *        A {@linkplain Continuation4 continuation} that accepts
		 *        <ol>
		 *        <li>the name of the module undergoing {@linkplain
		 *        AbstractAvailCompiler compilation},</li>
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
		 * @throws AvailCompilerException
		 *         If the compiler cannot build a module.
		 * @throws MalformedSerialStreamException
		 *         If the repository contains invalid serialized module data.
		 */
		private void loadRepositoryModule (
				final ResolvedModuleName moduleName,
				final Continuation4<ModuleName, Long, Long, Long> localTracker,
				final Continuation3<ModuleName, Long, Long> globalTracker)
			throws MalformedSerialStreamException
		{
			// Read the module data from the repository.
			final File fileReference = moduleName.fileReference();
			localTracker.value(moduleName, -1L, -1L, -1L);
			final byte[] bytes = repository.get(
				fileReference.getAbsolutePath(),
				fileReference.lastModified());
			final ByteArrayInputStream inputStream =
				new ByteArrayInputStream(bytes);
			final AvailObject module = ModuleDescriptor.newModule(
				StringDescriptor.from(moduleName.qualifiedName()));
			final AvailLoader loader = new AvailLoader(module);
			final Deserializer deserializer = new Deserializer(
				inputStream, runtime);
			deserializer.currentModule(module);
			try
			{
				AvailObject tag = deserializer.deserialize();
				if (tag != null &&
					!tag.equals(
						AtomDescriptor.moduleHeaderSectionAtom()))
				{
					throw new RuntimeException(
						"Expected module header tag");
				}
				final ModuleHeader header =
					new ModuleHeader(moduleName);
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

			// Run each zero-argument block, one after another.
			final Mutable<Continuation1<AvailObject>> runNext =
				new Mutable<Continuation1<AvailObject>>();
			final Mutable<Continuation1<Throwable>> fail =
				new Mutable<Continuation1<Throwable>>();
			runNext.value = new Continuation1<AvailObject>()
			{
				@Override
				public void value (final @Nullable AvailObject ignored)
				{
					final AvailObject function;
					try
					{
						function = deserializer.deserialize();
					}
					catch (
						final MalformedSerialStreamException|RuntimeException e)
					{
						fail.value.value(e);
						return;
					}
					if (function != null)
					{
						final AvailObject fiber =
							FiberDescriptor.newLoaderFiber(loader);
						fiber.resultContinuation(runNext.value);
						fiber.failureContinuation(fail.value);
						Interpreter.runOutermostFunction(
							fiber,
							function,
							Collections.<AvailObject>emptyList());
					}
					else
					{
						runtime.addModule(module);
						module.cleanUpAfterCompile();
						postLoad(moduleName, 0L, localTracker, globalTracker);
					}
				}
			};
			fail.value = new Continuation1<Throwable>()
			{
				@Override
				public void value (final @Nullable Throwable killer)
				{
					assert killer != null;
					try
					{
						module.removeFrom(loader);
					}
					finally
					{
						failBuild(killer);
					}
				}
			};
			// The argument is ignored, so it doesn't matter what gets passed.
			runNext.value.value(NilDescriptor.nil());
		}

		/**
		 * Compile the specified {@linkplain ModuleDescriptor module}, store it
		 * into the {@linkplain Repository repository}, and then load it into
		 * the {@linkplain AvailRuntime Avail runtime}.
		 *
		 * <p>
		 * Note that the predecessors of this module must have already been
		 * loaded.
		 * </p>
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that should be loaded.
		 * @param localTracker
		 *        A {@linkplain Continuation4 continuation} that accepts
		 *        <ol>
		 *        <li>the name of the module undergoing {@linkplain
		 *        AbstractAvailCompiler compilation},</li>
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
		 * @throws IOException
		 *         If the source module cannot be opened or read.
		 * @throws AvailCompilerException
		 *         If the compiler cannot build a module.
		 */
		private void compileModule (
				final ResolvedModuleName moduleName,
				final Continuation4<ModuleName, Long, Long, Long> localTracker,
				final Continuation3<ModuleName, Long, Long> globalTracker)
			throws IOException, AvailCompilerException
		{
			final Mutable<Long> lastPosition = new Mutable<Long>(0L);
			final Continuation1<AbstractAvailCompiler> continuation =
				new Continuation1<AbstractAvailCompiler>()
				{
					@Override
					public void value (
						final @Nullable
							AbstractAvailCompiler compiler)
					{
						assert compiler != null;
						compiler.parseModule(
							new Continuation4<ModuleName, Long, Long, Long>()
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
							new Continuation1<AvailObject>()
							{
								@Override
								public void value (
									final @Nullable AvailObject module)
								{
									final File fileReference =
										moduleName.fileReference();
									repository.put(
										fileReference.getAbsolutePath(),
										fileReference.lastModified(),
										compiler.serializerOutputStream
											.toByteArray());
									postLoad(
										moduleName,
										lastPosition.value,
										localTracker,
										globalTracker);
								}
							},
							new Continuation1<Throwable>()
							{
								@Override
								public void value (
									final @Nullable Throwable killer)
								{
									assert killer != null;
									failBuild(killer);
								}
							});
					}
				};
			AbstractAvailCompiler.create(
				moduleName,
				false,
				continuation,
				new Continuation1<Throwable>()
				{
					@Override
					public void value (final @Nullable Throwable killer)
					{
						assert killer != null;
						failBuild(killer);
					}
				});
		}

		/**
		 * Load the specified {@linkplain ModuleDescriptor module} into the
		 * {@linkplain AvailRuntime Avail runtime}. If a current compiled module
		 * is available from the {@linkplain Repository repository}, then simply
		 * load it. Otherwise, {@linkplain AbstractAvailCompiler compile} the
		 * module, store it into the repository, and then load it.
		 *
		 * <p>
		 * Note that the predecessors of this module must have already been
		 * loaded.
		 * </p>
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that should be loaded.
		 * @param localTracker
		 *        A {@linkplain Continuation4 continuation} that accepts
		 *        <ol>
		 *        <li>the name of the module undergoing {@linkplain
		 *        AbstractAvailCompiler compilation},</li>
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
		 * @throws IOException
		 *         If the source module cannot be opened or read.
		 * @throws AvailCompilerException
		 *         If the compiler cannot build a module.
		 * @throws MalformedSerialStreamException
		 *         If the repository contains invalid serialized module data.
		 */
		@InnerAccess void loadModule (
				final ResolvedModuleName moduleName,
				final Continuation4<ModuleName, Long, Long, Long> localTracker,
				final Continuation3<ModuleName, Long, Long> globalTracker)
			throws
				IOException,
				AvailCompilerException,
				MalformedSerialStreamException
		{
			globalTracker.value(
				moduleName, bytesCompiled.get(), globalCodeSize());
			// If the module is already loaded into the runtime, then we must
			// not reload it.
			if (!runtime.includesModuleNamed(
				StringDescriptor.from(moduleName.qualifiedName())))
			{
				final File fileReference = moduleName.fileReference();
				final String filePath = fileReference.getAbsolutePath();
				final long moduleTimestamp = fileReference.lastModified();
				// If the module is already compiled, then load the repository's
				// version.
				if (repository.hasKey(filePath, moduleTimestamp))
				{
					loadRepositoryModule(
						moduleName,
						localTracker,
						globalTracker);
				}
				// Actually compile the module and cache its compiled form.
				else
				{
					compileModule(moduleName, localTracker, globalTracker);
				}
			}
		}
	}

	/**
	 * Scheduling tracing of the imports of the {@linkplain ModuleDescriptor
	 * module} specified by the given {@linkplain ModuleName module name}.
	 *
	 * @param state
	 *        The {@linkplain BuildState build state}.
	 * @param qualifiedName
	 *        A fully-qualified {@linkplain ModuleName module name}.
	 * @param resolvedSuccessor
	 *        The resolved name of the module using or extending this module,
	 *        or {@code null} if this module is the start of the recursive
	 *        resolution (i.e., it will be the last one compiled).
	 * @param recursionSet
	 *        An insertion-ordered {@linkplain Set set} that remembers all
	 *        modules visited along this branch of the trace.
	 */
	@InnerAccess void scheduleTraceModuleImports (
		final BuildState state,
		final ModuleName qualifiedName,
		final @Nullable ResolvedModuleName resolvedSuccessor,
		final LinkedHashSet<ResolvedModuleName> recursionSet)
	{
		// Don't schedule any new tasks if an exception has been encountered.
		if (state.terminator != null)
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
					// If an exception has been encountered, then exit quickly.
					if (state.terminator != null)
					{
						return;
					}
					Throwable killer = null;
					try
					{
						state.traceModuleImports(
							qualifiedName,
							resolvedSuccessor,
							recursionSet);
					}
					catch (final RecursiveDependencyException e)
					{
						e.prependModule(runtime.moduleNameResolver().resolve(
							qualifiedName));
						killer = e;
					}
					catch (final Throwable e)
					{
						killer = e;
					}
					if (killer != null)
					{
						state.failBuild(killer);
					}
				}
			}));
	}

	/**
	 * Schedule a build of the specified {@linkplain ModuleDescriptor module},
	 * on the assumption that its predecessors have already been built.
	 *
	 * @param state
	 *        The current {@linkplain BuildState build state}.
	 * @param target
	 *        The {@linkplain ResolvedModuleName resolved name} of the module
	 *        that should be loaded.
	 * @param localTracker
	 *        A {@linkplain Continuation4 continuation} that accepts
	 *        <ol>
	 *        <li>the name of the module undergoing {@linkplain
	 *        AbstractAvailCompiler compilation},</li>
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
	@InnerAccess void scheduleLoadModule (
		final BuildState state,
		final ResolvedModuleName target,
		final Continuation4<ModuleName, Long, Long, Long> localTracker,
		final Continuation3<ModuleName, Long, Long> globalTracker)
	{
		// Don't schedule any new tasks if an exception has been encountered.
		if (state.terminator != null)
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
					// If an exception has been encountered, then exit quickly.
					if (state.terminator != null)
					{
						return;
					}
					try
					{
						state.loadModule(target, localTracker, globalTracker);
					}
					catch (final Throwable killer)
					{
						state.failBuild(killer);
					}
				}
			}));
	}

	/**
	 * Build the {@linkplain ModuleDescriptor target} and its dependencies.
	 *
	 * @param target
	 *        The {@linkplain ModuleName canonical name} of the module that the
	 *        {@linkplain AvailBuilder builder} must (recursively) load into the
	 *        {@linkplain AvailRuntime runtime}.
	 * @param localTracker
	 *        A {@linkplain Continuation4 continuation} that accepts
	 *        <ol>
	 *        <li>the name of the module undergoing {@linkplain
	 *        AbstractAvailCompiler compilation},</li>
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
	 * @throws Throwable
	 *         If anything went wrong.
	 */
	@InnerAccess void buildTarget (
			final ModuleName target,
			final Continuation4<ModuleName, Long, Long, Long> localTracker,
			final Continuation3<ModuleName, Long, Long> globalTracker)
		throws Throwable
	{
		final BuildState state = new BuildState();
		state.traceRequests = 1;
		scheduleTraceModuleImports(
			state,
			target,
			null,
			new LinkedHashSet<ResolvedModuleName>());
		// Wait until the parallel recursive trace completes.
		synchronized (state)
		{
			while (
				state.terminator == null
				&& state.traceRequests != state.traceCompletions)
			{
				state.wait();
			}
			if (state.terminator != null)
			{
				throw state.terminator;
			}
		}
		for (final ResolvedModuleName origin : state.originModules())
		{
			scheduleLoadModule(state, origin, localTracker, globalTracker);
		}
		// Wait until the parallel load completes.
		synchronized (state)
		{
			while (
				state.terminator == null
				&& state.loadCompletions != state.completionSet.size())
			{
				state.wait();
			}
			if (state.terminator != null)
			{
				throw state.terminator;
			}
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
	 * @param localTracker
	 *        A {@linkplain Continuation4 continuation} that accepts
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
			final ModuleName target,
			final Continuation4<ModuleName, Long, Long, Long> localTracker,
			final Continuation3<ModuleName, Long, Long> globalTracker)
		throws
			AvailCompilerException,
			RecursiveDependencyException,
			UnresolvedDependencyException,
			InterruptedException
	{
		final Mutable<Throwable> killer = new Mutable<Throwable>();
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
						buildTarget(target, localTracker, globalTracker);
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
		if (killer.value != null)
		{
			if (killer.value instanceof AvailCompilerException)
			{
				throw (AvailCompilerException) killer.value;
			}
			else if (killer.value instanceof FiberTerminationException)
			{
				throw (FiberTerminationException) killer.value;
			}
			else if (killer.value instanceof RecursiveDependencyException)
			{
				throw (RecursiveDependencyException) killer.value;
			}
			else if (killer.value instanceof UnresolvedDependencyException)
			{
				throw (UnresolvedDependencyException) killer.value;
			}
			else if (killer.value instanceof Error)
			{
				throw (Error) killer.value;
			}
			else if (killer.value instanceof RuntimeException)
			{
				throw (RuntimeException) killer.value;
			}
			else
			{
				throw new RuntimeException(killer.value);
			}
		}
	}
}
