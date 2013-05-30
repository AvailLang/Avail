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
import com.avail.compiler.AbstractAvailCompiler.ModuleHeader;
import com.avail.compiler.AbstractAvailCompiler.ModuleImport;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.persistence.IndexedRepositoryManager;
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
	 * Construct a new {@link AvailBuilder}.
	 *
	 * @param runtime
	 *        The {@linkplain AvailRuntime runtime} into which the {@linkplain
	 *        AvailBuilder builder} will install the target {@linkplain
	 *        ModuleDescriptor module} and its dependencies.
	 */
	public AvailBuilder (final AvailRuntime runtime)
	{
		this.runtime = runtime;
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
		public volatile @Nullable Throwable terminator;

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
			new HashSet<>();

		/**
		 * A {@linkplain Map map} from {@linkplain ResolvedModuleName resolved
		 * module names} to their predecessors.
		 */
		public final
		Map<ResolvedModuleName, Set<ResolvedModuleName>> predecessors =
			new HashMap<>();

		/**
		 * A {@linkplain Map map} from {@linkplain ResolvedModuleName resolved
		 * module names} to their successors.
		 */
		public final
		Map<ResolvedModuleName, Set<ResolvedModuleName>> successors =
			new HashMap<>();

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
			unbuiltPredecessors =
				new HashMap<ResolvedModuleName, AtomicInteger>();
		}

		/**
		 * Trace the imports of the {@linkplain ResolvedModuleName specified}
		 * {@linkplain ModuleDescriptor module}.
		 *
		 * @param resolvedName
		 *        A resolved module name.
		 * @param header
		 *        A {@linkplain ModuleHeader module header}.
		 * @param recursionSet
		 *        An insertion-ordered {@linkplain Set set} that remembers all
		 *        modules visited along this branch of the trace.
		 */
		@InnerAccess void traceModuleHeader (
			final ResolvedModuleName resolvedName,
			final ModuleHeader header,
			final LinkedHashSet<ResolvedModuleName> recursionSet)
		{
			final Set<ModuleName> importedModules =
				new HashSet<>(header.importedModules.size());
			for (final ModuleImport moduleImport : header.importedModules)
			{
				importedModules.add(resolvedName.asSibling(
					moduleImport.moduleName.asNativeString()));
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
				// of the tracing algorithm.
				final LinkedHashSet<ResolvedModuleName> newSet =
					new LinkedHashSet<>(
						recursionSet);
				scheduleTraceModuleImports(
					BuildState.this,
					moduleName,
					resolvedName,
					newSet);
			}
		}

		/**
		 * Trace the imports of the source {@linkplain ModuleDescriptor module}
		 * specified by the given {@linkplain ResolvedModuleName resolved module
		 * name}.
		 *
		 * @param resolvedName
		 *        A resolved module name.
		 * @param recursionSet
		 *        An insertion-ordered {@linkplain Set set} that remembers all
		 *        modules visited along this branch of the trace.
		 * @throws IOException
		 *         If the source module cannot be opened or read.
		 * @throws AvailCompilerException
		 *         If the {@linkplain AbstractAvailCompiler compiler} is unable
		 *         to find a module declaration for this {@linkplain
		 *         ModuleDescriptor module}.
		 */
		private void traceSourceModuleImports (
				final ResolvedModuleName resolvedName,
				final LinkedHashSet<ResolvedModuleName> recursionSet)
			throws IOException, AvailCompilerException
		{
			assert resolvedName.sourceReference() != null;
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
						final ModuleHeader header =
							compiler.parseModuleHeader();
						if (header != null)
						{
							traceModuleHeader(
								resolvedName, header, recursionSet);
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
		 * Trace the imports of the compiled {@linkplain ModuleDescriptor
		 * module} specified by the given {@linkplain ModuleName module name}.
		 *
		 * @param resolvedName
		 *        A resolved module name.
		 * @param recursionSet
		 *        An insertion-ordered {@linkplain Set set} that remembers all
		 *        modules visited along this branch of the trace.
		 * @throws MalformedSerialStreamException
		 *         If the repository contains invalid serialized module data.
		 */
		private void traceCompiledModuleImports (
				final ResolvedModuleName resolvedName,
				final LinkedHashSet<ResolvedModuleName> recursionSet)
			throws
				MalformedSerialStreamException
		{
			final ModuleHeader header = new ModuleHeader(resolvedName);
			A_Module module = ModuleDescriptor.newModule(
				StringDescriptor.from(resolvedName.qualifiedName()));
			AvailLoader loader = new AvailLoader(module);
			// Populate the header.
			try
			{
				final IndexedRepositoryManager repository =
					resolvedName.repository();
				final byte[] bytes = repository.get(resolvedName);
				final ByteArrayInputStream inputStream =
					new ByteArrayInputStream(bytes);
				final Deserializer deserializer = new Deserializer(
					inputStream, runtime);
				deserializer.currentModule(module);
				final AvailObject tag = deserializer.deserialize();
				if (tag != null &&
					!tag.equals(AtomDescriptor.moduleHeaderSectionAtom()))
				{
					throw new RuntimeException(
						"Expected module header tag");
				}
				header.deserializeHeaderFrom(deserializer);
			}
			finally
			{
				module.removeFrom(loader);
				module = null;
				loader = null;
			}
			traceModuleHeader(resolvedName, header, recursionSet);
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
		 * @throws MalformedSerialStreamException
		 *         If the repository contains invalid serialized module data.
		 */
		@InnerAccess void traceModuleImports (
				final ModuleName qualifiedName,
				final @Nullable ResolvedModuleName resolvedSuccessor,
				final LinkedHashSet<ResolvedModuleName> recursionSet)
			throws
				IOException,
				AvailCompilerException,
				RecursiveDependencyException,
				UnresolvedDependencyException,
				MalformedSerialStreamException
		{
			final ResolvedModuleName resolvedName =
				runtime.moduleNameResolver().resolve(qualifiedName);
			if (resolvedName == null)
			{
				assert resolvedSuccessor != null;
				throw new UnresolvedDependencyException(
					resolvedSuccessor,
					qualifiedName.localName());
			}

			// TODO: [TLS/MvG] The recursion detection mechanism does not
			// reliably work because of the early exit below. I don't have time
			// to fix this the right way.

			// Detect recursion into this module.
			if (recursionSet.contains(resolvedName))
			{
				throw new RecursiveDependencyException(
					resolvedName,
					recursionSet);
			}

			// Prevent recursion into this module.
			recursionSet.add(resolvedName);

			synchronized (this)
			{
				final boolean alreadyTraced =
					completionSet.contains(resolvedName);

				// If the module hasn't been traced yet, then set up its
				// predecessor and successor sets.
				if (!alreadyTraced)
				{
					assert !predecessors.containsKey(resolvedName);
					assert !successors.containsKey(resolvedName);
					predecessors.put(
						resolvedName, new HashSet<ResolvedModuleName>());
					successors.put(
						resolvedName, new HashSet<ResolvedModuleName>());
				}

				// Wire together the module and its successor.
				if (resolvedSuccessor != null)
				{
					predecessors.get(resolvedSuccessor).add(resolvedName);
					successors.get(resolvedName).add(resolvedSuccessor);
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
				completionSet.add(resolvedName);
			}

			// Build the set of names of imported modules.
			if (resolvedName.sourceReference() != null)
			{
				traceSourceModuleImports(resolvedName, recursionSet);
			}
			else
			{
				traceCompiledModuleImports(resolvedName, recursionSet);
			}
		}

		/**
		 * A {@linkplain Map map} from {@linkplain ResolvedModuleName resolved
		 * module names} to {@linkplain AtomicInteger atomic counters} (of their
		 * unbuilt predecessors).
		 */
		private final Map<ResolvedModuleName, AtomicInteger>
			unbuiltPredecessors;

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
			assert unbuiltPredecessors.size() == 0;
			final List<ResolvedModuleName> originModules =
				new ArrayList<>(3);
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
					globalCodeSize += resolution.moduleSize();
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
			// Commit the repository.
			moduleName.repository().commit();
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
					moduleName.moduleSize() - lastPosition),
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
			localTracker.value(moduleName, -1L, -1L, -1L);
			// Read the module data from the repository.
			final IndexedRepositoryManager repository = moduleName.repository();
			final byte[] bytes = repository.get(moduleName);
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

			// Run each zero-argument block, one after another.
			final MutableOrNull<Continuation1<AvailObject>> runNext =
				new MutableOrNull<>();
			final MutableOrNull<Continuation1<Throwable>> fail =
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
						fail.value().value(e);
						return;
					}
					if (function != null)
					{
						final A_Fiber fiber =
							FiberDescriptor.newLoaderFiber(
								function.kind().returnType(),
								loader);
						fiber.resultContinuation(runNext.value());
						fiber.failureContinuation(fail.value());
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
			final Mutable<Long> lastPosition = new Mutable<>(0L);
			// Capture the file's modification time *before* compiling.  That
			// way if the file is modified during loading, the next build will
			// simply treat the stored data as invalid and recompile it.
			final File sourceReference = moduleName.sourceReference();
			assert sourceReference != null;
			final long lastModified = sourceReference.lastModified();
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
											localPosition
												- lastPosition.value),
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
									moduleName.repository().put(
										moduleName,
										lastModified,
										appendCRC(stream.toByteArray()));
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
				final File sourceReference = moduleName.sourceReference();
				// If no source is available, then load the most recent version
				// of the compiled module from the repository.
				if (sourceReference == null)
				{
					assert moduleName.repository().hasKey(moduleName);
					loadRepositoryModule(
						moduleName,
						localTracker,
						globalTracker);
				}
				else
				{
					final long moduleTimestamp = sourceReference.lastModified();
					// If the module is already compiled, then load the
					// repository's version.
					if (moduleName.repository().hasKey(
						moduleName, moduleTimestamp))
					{
						loadRepositoryModule(
							moduleName,
							localTracker,
							globalTracker);
					}
					// Compile the module and cache its compiled form.
					else
					{
						compileModule(moduleName, localTracker, globalTracker);
					}
				}
			}
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
			@Nullable Throwable term;
			while (
				(term = state.terminator) == null
				&& state.traceRequests != state.traceCompletions)
			{
				state.wait();
			}
			if (term != null)
			{
				throw term;
			}
		}
		for (final ResolvedModuleName origin : state.originModules())
		{
			scheduleLoadModule(state, origin, localTracker, globalTracker);
		}
		// Wait until the parallel load completes.
		synchronized (state)
		{
			@Nullable Throwable term;
			while (
				(term = state.terminator) == null
				&& state.loadCompletions != state.completionSet.size())
			{
				state.wait();
			}
			if (term != null)
			{
				throw term;
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
