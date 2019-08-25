/*
 * BuildLoader.java
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

import com.avail.AvailRuntime;
import com.avail.builder.AvailBuilder.LoadedModule;
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.AvailCompiler.CompilerProgressReporter;
import com.avail.compiler.AvailCompiler.GlobalProgressReporter;
import com.avail.compiler.ModuleHeader;
import com.avail.compiler.problems.Problem;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.descriptor.*;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.AvailLoader.Phase;
import com.avail.interpreter.Interpreter;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.persistence.IndexedRepositoryManager.*;
import com.avail.serialization.Deserializer;
import com.avail.serialization.MalformedSerialStreamException;
import com.avail.serialization.Serializer;
import com.avail.utility.MutableLong;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Continuation3;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import static com.avail.AvailRuntimeSupport.captureNanos;
import static com.avail.builder.AvailBuilder.appendCRC;
import static com.avail.compiler.problems.ProblemType.EXECUTION;
import static com.avail.descriptor.FiberDescriptor.loaderPriority;
import static com.avail.descriptor.FiberDescriptor.newLoaderFiber;
import static com.avail.descriptor.ModuleDescriptor.newModule;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.interpreter.Interpreter.runOutermostFunction;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.evaluation.Combinator.recurse;
import static java.lang.String.format;
import static java.util.Collections.emptyList;

/**
 * Used for parallel-loading modules in the {@linkplain
 * AvailBuilder#moduleGraph}.
 */
final class BuildLoader
{
	/** The {@link AvailBuilder} for which we're loading. */
	final AvailBuilder availBuilder;

	/**
	 * A {@linkplain CompilerProgressReporter} that is updated to show progress
	 * while compiling or loading a module.  It accepts:
	 *
	 * <ol>
	 * <li>the name of the module currently undergoing {@linkplain AvailCompiler
	 * compilation} as part of the recursive build of target,</li>
	 * <li>the size of the module in bytes,</li>
	 * <li>the current token at which parsing is taking place.</li>
	 * </ol>
	 */
	private final CompilerProgressReporter localTracker;

	/**
	 * A {@linkplain Continuation3} that is updated to show global progress
	 * while compiling or loading modules.  It accepts:
	 *
	 * <ol>
	 * <li>the name of the module undergoing compilation,</li>
	 * <li>the number of bytes globally processed, and</li>
	 * <li>the global size (in bytes) of all modules that will be built.</li>
	 * </ol>
	 */
	private final GlobalProgressReporter globalTracker;

	/**
	 * Construct a new {@code BuildLoader}.
	 *
	 * @param availBuilder
	 *        The {@link AvailBuilder} for which to load modules.
	 * @param localTracker
	 *        A {@linkplain CompilerProgressReporter continuation} that accepts
	 *        <ol>
	 *        <li>the name of the module currently undergoing {@linkplain
	 *        AvailCompiler compilation} as part of the recursive
	 *        build of target,</li>
	 *        <li>the current line number within the current module,</li>
	 *        <li>the position of the ongoing parse (in bytes), and</li>
	 *        <li>the size of the module in bytes.</li>
	 *        </ol>
	 * @param globalTracker
	 *        A {@link GlobalProgressReporter} that accepts
	 *        <ol>
	 *        <li>the number of bytes globally processed, and</li>
	 *        <li>the global size (in bytes) of all modules that will be
	 *        built.</li>
	 *        </ol>
	 * @param problemHandler
	 *        How to handle or report {@link Problem}s that arise during the
	 *        build.
	 */
	BuildLoader (
		final AvailBuilder availBuilder,
		final CompilerProgressReporter localTracker,
		final GlobalProgressReporter globalTracker,
		final ProblemHandler problemHandler)
	{
		this.availBuilder = availBuilder;
		this.localTracker = localTracker;
		this.globalTracker = globalTracker;
		this.problemHandler = problemHandler;
		long size = 0L;
		for (final ResolvedModuleName mod : availBuilder.moduleGraph.vertices())
		{
			size += mod.moduleSize();
		}
		globalCodeSize = size;
	}

	/** The size, in bytes, of all source files that will be built. */
	private final long globalCodeSize;

	/**
	 * The {@link ProblemHandler} to use when compilation {@link Problem}s are
	 * encountered.
	 */
	private final ProblemHandler problemHandler;

	/** The number of bytes compiled so far. */
	private final AtomicLong bytesCompiled = new AtomicLong(0L);

	/**
	 * Schedule a build of the specified {@linkplain ModuleDescriptor module},
	 * on the assumption that its predecessors have already been built.
	 *
	 * @param target
	 *        The {@linkplain ResolvedModuleName resolved name} of the module
	 *        that should be loaded.
	 * @param completionAction
	 *        The {@linkplain Continuation0 action} to perform after this module
	 *        has been loaded.
	 */
	private void scheduleLoadModule (
		final ResolvedModuleName target,
		final Continuation0 completionAction)
	{
		// Avoid scheduling new tasks if an exception has happened.
		if (availBuilder.shouldStopBuild())
		{
			postLoad(target, 0L);
			completionAction.value();
			return;
		}
		availBuilder.runtime.execute(
			loaderPriority,
			() ->
			{
				if (availBuilder.shouldStopBuild())
				{
					// An exception has been encountered since the earlier
					// check.  Exit quickly.
					completionAction.value();
				}
				else
				{
					loadModule(target, completionAction);
				}
			});
	}

	/**
	 * Load the specified {@linkplain ModuleDescriptor module} into the
	 * {@linkplain AvailRuntime Avail runtime}. If a current compiled module is
	 * available from the {@linkplain IndexedRepositoryManager repository}, then
	 * simply load it. Otherwise, {@linkplain AvailCompiler compile} the module,
	 * store it into the repository, and then load it.
	 *
	 * <p>Note that the predecessors of this module must have already been
	 * loaded.</p>
	 *
	 * @param moduleName
	 *        The {@linkplain ResolvedModuleName resolved name} of the module
	 *        that should be loaded.
	 * @param completionAction
	 *        What to do after loading the module successfully.
	 */
	private void loadModule (
		final ResolvedModuleName moduleName,
		final Continuation0 completionAction)
	{
		globalTracker.value(bytesCompiled.get(), globalCodeSize);
		// If the module is already loaded into the runtime, then we must not
		// reload it.
		final boolean isLoaded =
			availBuilder.getLoadedModule(moduleName) != null;
		//noinspection AssertWithSideEffects
		assert isLoaded == availBuilder.runtime.includesModuleNamed(
			stringFrom(moduleName.qualifiedName()));
		if (isLoaded)
		{
			// The module is already loaded.
			AvailBuilder.log(
				Level.FINEST,
				"Already loaded: %s",
				moduleName.qualifiedName());
			postLoad(moduleName, 0L);
			completionAction.value();
		}
		else
		{
			final IndexedRepositoryManager repository = moduleName.repository();
			final ModuleArchive archive = repository.getArchive(
				moduleName.rootRelativeName());
			final byte [] digest = archive.digestForFile(moduleName);
			final ModuleVersionKey versionKey =
				new ModuleVersionKey(moduleName, digest);
			final @Nullable ModuleVersion version =
				archive.getVersion(versionKey);
			assert version != null
				: "Version should have been populated during tracing";
			final List<String> imports = version.getImports();
			final ModuleNameResolver resolver =
				availBuilder.runtime.moduleNameResolver();
			final Map<String, LoadedModule> loadedModulesByName =
				new HashMap<>();
			for (final String localName : imports)
			{
				final ResolvedModuleName resolvedName;
				try
				{
					resolvedName = resolver.resolve(
						moduleName.asSibling(localName), moduleName);
				}
				catch (final UnresolvedDependencyException e)
				{
					availBuilder.stopBuildReason(
						format(
							"A module predecessor was malformed or absent: "
								+ "%s -> %s\n",
							moduleName.qualifiedName(),
							localName));
					completionAction.value();
					return;
				}
				final LoadedModule loadedPredecessor =
					stripNull(availBuilder.getLoadedModule(resolvedName));
				loadedModulesByName.put(localName, loadedPredecessor);
			}
			final long [] predecessorCompilationTimes =
				new long [imports.size()];
			for (int i = 0; i < predecessorCompilationTimes.length; i++)
			{
				final LoadedModule loadedPredecessor =
					stripNull(loadedModulesByName.get(imports.get(i)));
				predecessorCompilationTimes[i] =
					loadedPredecessor.compilation.getCompilationTime();
			}
			final ModuleCompilationKey compilationKey =
				new ModuleCompilationKey(predecessorCompilationTimes);
			final @Nullable ModuleCompilation compilation =
				version.getCompilation(compilationKey);
			if (compilation != null)
			{
				// The current version of the module is already compiled, so
				// load the repository's version.
				loadRepositoryModule(
					moduleName,
					version,
					compilation,
					versionKey.getSourceDigest(),
					completionAction);
			}
			else
			{
				// Compile the module and cache its compiled form.
				compileModule(moduleName, compilationKey, completionAction);
			}
		}
	}

	/**
	 * Load the specified {@linkplain ModuleDescriptor module} from the
	 * {@linkplain IndexedRepositoryManager repository} and into the {@linkplain
	 * AvailRuntime Avail runtime}.
	 *
	 * <p>Note that the predecessors of this module must have already been
	 * loaded.</p>
	 *
	 * @param moduleName
	 *        The {@linkplain ResolvedModuleName resolved name} of the module
	 *        that should be loaded.
	 * @param version
	 *        The {@link ModuleVersion} containing information about this
	 *        module.
	 * @param compilation
	 *        The {@link ModuleCompilation} containing information about the
	 *        particular stored compilation of this module in the repository.
	 * @param sourceDigest
	 *        The cryptographic digest of the module's source code.
	 * @param completionAction
	 *        What to do after loading the module successfully.
	 */
	private void loadRepositoryModule (
		final ResolvedModuleName moduleName,
		final ModuleVersion version,
		final ModuleCompilation compilation,
		final byte[] sourceDigest,
		final Continuation0 completionAction)
	{
		localTracker.value(moduleName, moduleName.moduleSize(), 0L);
		final A_Module module = newModule(
			stringFrom(moduleName.qualifiedName()));
		final AvailLoader availLoader =
			new AvailLoader(module, availBuilder.textInterface);
		availLoader.prepareForLoadingModuleBody();
		final Continuation1NotNull<Throwable> fail =
			e -> module.removeFrom(
				availLoader,
				() ->
				{
					postLoad(moduleName, 0L);
					final Problem problem = new Problem(
						moduleName,
						1,
						1,
						EXECUTION,
						"Problem loading module: {0}",
						e.getLocalizedMessage())
					{
						@Override
						public void abortCompilation ()
						{
							availBuilder.stopBuildReason(
								"Problem loading module");
							completionAction.value();
						}
					};
					problemHandler.handle(problem);
				});
		// Read the module header from the repository.
		try
		{
			final byte[] bytes = stripNull(version.getModuleHeader());
			final ByteArrayInputStream inputStream =
				AvailBuilder.validatedBytesFrom(bytes);
			final Deserializer deserializer =
				new Deserializer(inputStream, availBuilder.runtime);
			final ModuleHeader header = new ModuleHeader(moduleName);
			header.deserializeHeaderFrom(deserializer);
			final @Nullable String errorString =
				header.applyToModule(module, availBuilder.runtime);
			if (errorString != null)
			{
				throw new RuntimeException(errorString);
			}
		}
		catch (final MalformedSerialStreamException | RuntimeException e)
		{
			fail.value(e);
			return;
		}
		final Deserializer deserializer;
		try
		{
			// Read the module data from the repository.
			final byte[] bytes = stripNull(compilation.getBytes());
			final ByteArrayInputStream inputStream =
				AvailBuilder.validatedBytesFrom(bytes);
			deserializer = new Deserializer(inputStream, availBuilder.runtime);
			deserializer.setCurrentModule(module);
		}
		catch (final MalformedSerialStreamException | RuntimeException e)
		{
			fail.value(e);
			return;
		}

		// Run each zero-argument block, one after another.
		recurse(
			runNext ->
			{
				availLoader.setPhase(Phase.LOADING);
				final @Nullable A_Function function;
				try
				{
					function = availBuilder.shouldStopBuild()
						? null : deserializer.deserialize();
				}
				catch (
					final MalformedSerialStreamException
						| RuntimeException e)
				{
					fail.value(e);
					return;
				}
				if (function != null)
				{
					final A_Fiber fiber = newLoaderFiber(
						function.kind().returnType(),
						availLoader,
						() ->
						{
							final A_RawFunction code = function.code();
							return
								formatString(
									"Load repo module %s, in %s:%d",
									code.methodName(),
									code.module().moduleName(),
									code.startingLineNumber());
						});
					fiber.textInterface(availBuilder.textInterface);
					final long before = captureNanos();
					fiber.setSuccessAndFailureContinuations(
						ignored ->
						{
							final long after = captureNanos();
							Interpreter.current().recordTopStatementEvaluation(
								after - before,
								module,
								function.code().startingLineNumber());
							runNext.value();
						},
						fail);
					availLoader.setPhase(Phase.EXECUTING_FOR_LOAD);
					if (AvailLoader.debugLoadedStatements)
					{
						System.out.println(
							module
								+ ":" + function.code().startingLineNumber()
								+ " Running precompiled -- " + function);
					}
					runOutermostFunction(
						availBuilder.runtime, fiber, function, emptyList());
				}
				else if (availBuilder.shouldStopBuild())
				{
					module.removeFrom(
						availLoader,
						() ->
						{
							postLoad(moduleName, 0L);
							completionAction.value();
						});
				}
				else
				{
					availBuilder.runtime.addModule(module);
					final LoadedModule loadedModule = new LoadedModule(
						moduleName,
						sourceDigest,
						module,
						version,
						compilation);
					availBuilder.putLoadedModule(moduleName, loadedModule);
					postLoad(moduleName, 0L);
					completionAction.value();
				}
			});
	}

	/**
	 * Compile the specified {@linkplain ModuleDescriptor module}, store it into
	 * the {@linkplain IndexedRepositoryManager repository}, and then load it
	 * into the {@linkplain AvailRuntime Avail runtime}.
	 *
	 * <p>Note that the predecessors of this module must have already been
	 * loaded.</p>
	 *
	 * @param moduleName
	 *        The {@linkplain ResolvedModuleName resolved name} of the module
	 *        that should be loaded.
	 * @param compilationKey
	 *        The circumstances of compilation of this module.  Currently this
	 *        is just the compilation times ({@code long}s) of the module's
	 *        currently loaded predecessors, listed in the same order as the
	 *        module's {@linkplain ModuleHeader#importedModules imports}.
	 * @param completionAction
	 *        What to do after loading the module successfully or
	 *        unsuccessfully.
	 */
	private void compileModule (
		final ResolvedModuleName moduleName,
		final ModuleCompilationKey compilationKey,
		final Continuation0 completionAction)
	{
		final IndexedRepositoryManager repository = moduleName.repository();
		final ModuleArchive archive = repository.getArchive(
			moduleName.rootRelativeName());
		final byte[] digest = archive.digestForFile(moduleName);
		final ModuleVersionKey versionKey =
			new ModuleVersionKey(moduleName, digest);
		final MutableLong lastPosition = new MutableLong(0L);
		final AtomicBoolean ranOnce = new AtomicBoolean(false);
		final Continuation1NotNull<AvailCompiler> continuation =
			compiler -> compiler.parseModule(
				module ->
				{
					final boolean old = ranOnce.getAndSet(true);
					assert !old : "Completed module compilation twice!";
					final ByteArrayOutputStream stream =
						compiler.compilationContext.serializerOutputStream;
					// This is the moment of compilation.
					final long compilationTime = System.currentTimeMillis();
					final ModuleCompilation compilation =
						repository.new ModuleCompilation(
							compilationTime,
							appendCRC(stream.toByteArray()));
					archive.putCompilation(
						versionKey, compilationKey, compilation);

					// Serialize the Stacks comments.
					final ByteArrayOutputStream out =
						new ByteArrayOutputStream(5000);
					final Serializer serializer =
						new Serializer(out, module);
					// TODO MvG - Capture "/**" comments for Stacks.
//						final A_Tuple comments = fromList(
//                          module.commentTokens());
					final A_Tuple comments = emptyTuple();
					serializer.serialize(comments);
					final ModuleVersion version =
						stripNull(archive.getVersion(versionKey));
					version.putComments(appendCRC(out.toByteArray()));

					repository.commitIfStaleChanges(
						AvailBuilder.maximumStaleRepositoryMs);
					postLoad(moduleName, lastPosition.value);
					availBuilder.putLoadedModule(
						moduleName,
						new LoadedModule(
							moduleName,
							versionKey.getSourceDigest(),
							module,
							version,
							compilation));
					completionAction.value();
				},
				() ->
				{
					postLoad(moduleName, lastPosition.value);
					completionAction.value();
				});
		AvailCompiler.create(
			moduleName,
			availBuilder.textInterface,
			availBuilder.pollForAbort,
			(moduleName2, moduleSize, position) ->
			{
				assert moduleName.equals(moduleName2);
				localTracker.value(moduleName, moduleSize, position);
				globalTracker.value(
					bytesCompiled.addAndGet(position - lastPosition.value),
					globalCodeSize);
				lastPosition.value = position;
			},
			continuation,
			() ->
			{
				postLoad(moduleName, lastPosition.value);
				completionAction.value();
			},
			problemHandler);
	}

	/**
	 * Report progress related to this module.  In particular, note that the
	 * current module has advanced from its provided lastPosition to the end of
	 * the module.
	 *
	 * @param moduleName
	 *        The {@linkplain ResolvedModuleName resolved name} of the module
	 *        that just finished loading.
	 * @param lastPosition
	 *        The last local file position previously reported.
	 */
	private void postLoad (
		final ResolvedModuleName moduleName,
		final long lastPosition)
	{
		final long moduleSize = moduleName.moduleSize();
		globalTracker.value(
			bytesCompiled.addAndGet(moduleSize - lastPosition),
			globalCodeSize);
		localTracker.value(moduleName, moduleSize, moduleSize);
	}

	/**
	 * Load the modules in the {@linkplain AvailBuilder#moduleGraph}.
	 *
	 * @param afterAll
	 *        What to do after all module loading completes, whether successful
	 *        or not.
	 */
	void loadThen (final Continuation0 afterAll)
	{
		bytesCompiled.set(0L);
		final int vertexCountBefore = availBuilder.moduleGraph.vertexCount();
		availBuilder.moduleGraph.parallelVisitThen(
			this::scheduleLoadModule,
			() ->
			{
				try
				{
					assert availBuilder.moduleGraph.vertexCount()
						== vertexCountBefore;
					availBuilder.runtime.moduleNameResolver()
						.commitRepositories();
					// Parallel load has now completed or failed.
					// Clean up any modules that didn't load.  There can be
					// no loaded successors of unloaded modules, so they can
					// all be excised safely.
					availBuilder.trimGraphToLoadedModules();
				}
				finally
				{
					afterAll.value();
				}
			});
	}

	/**
	 * Load the modules in the {@linkplain AvailBuilder#moduleGraph}, blocking
	 * until all loading completes, whether successful or not.
	 */
	void load ()
	{
		final Semaphore semaphore = new Semaphore(0);
		loadThen(semaphore::release);
		semaphore.acquireUninterruptibly();
	}
}
