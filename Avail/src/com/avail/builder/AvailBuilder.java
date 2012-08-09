/**
 * AvailBuilder.java Copyright © 1993-2012, Mark van Gulik and Todd L Smith. All
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
import com.avail.*;
import com.avail.annotations.NotNull;
import com.avail.compiler.*;
import com.avail.compiler.AbstractAvailCompiler.ModuleHeader;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.serialization.*;
import com.avail.utility.*;

/**
 * An {@code AvailBuilder} {@linkplain AbstractAvailCompiler compiles} and
 * installs into an {@linkplain AvailRuntime Avail runtime} a target
 * {@linkplain ModuleDescriptor module} and each of its dependencies.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class AvailBuilder
{
	/**
	 * The {@linkplain AvailRuntime runtime} into which the
	 * {@linkplain AvailBuilder builder} will install the target
	 * {@linkplain ModuleDescriptor module} and its dependencies.
	 */
	private @NotNull
	final AvailRuntime runtime;

	/**
	 * The {@link Repository} of compiled modules to populate and reuse in order
	 * to accelerate builds.
	 */
	private @NotNull
	final Repository repository;

	/**
	 * The {@linkplain ModuleName canonical name} of the
	 * {@linkplain ModuleDescriptor module} that the {@linkplain AvailBuilder
	 * builder} must (recursively) load into the {@linkplain AvailRuntime
	 * runtime}.
	 */
	private final @NotNull
	ModuleName target;

	/** An {@linkplain L2Interpreter interpreter}. */
	private final @NotNull
	L2Interpreter interpreter;

	/**
	 * Construct a new {@link AvailBuilder}.
	 *
	 * @param runtime
	 *            The {@linkplain AvailRuntime runtime} into which the
	 *            {@linkplain AvailBuilder builder} will install the target
	 *            {@linkplain ModuleDescriptor module} and its dependencies.
	 * @param repository
	 *            The {@link Repository} which holds compiled modules to
	 *            accelerate builds.
	 * @param target
	 *            The {@linkplain ModuleName canonical name} of the
	 *            {@linkplain ModuleDescriptor module} that the
	 *            {@linkplain AvailBuilder builder} must (recursively) load into
	 *            the {@linkplain AvailRuntime runtime}.
	 */
	public AvailBuilder (
		final @NotNull AvailRuntime runtime,
		final @NotNull Repository repository,
		final @NotNull ModuleName target)
	{
		this.runtime = runtime;
		this.repository = repository;
		this.target = target;
		this.interpreter = new L2Interpreter(this.runtime);
	}

	/**
	 * The path maintained by the
	 * {@linkplain #traceModuleImports(ResolvedModuleName, ModuleName) tracer}
	 * to prevent recursive tracing.
	 */
	private final @NotNull
	Set<ResolvedModuleName> recursionSet = new HashSet<ResolvedModuleName>();

	/**
	 * The path maintained by the
	 * {@linkplain #traceModuleImports(ResolvedModuleName, ModuleName) tracer}
	 * to prevent recursive tracing, as a list to simplify describing a
	 * recursive chain of imports.
	 */
	private final @NotNull
	List<ResolvedModuleName> recursionList = new ArrayList<ResolvedModuleName>();

	/**
	 * The set of module names that have already been encountered and completely
	 * recursed through.
	 */
	private final @NotNull
	Set<ResolvedModuleName> completionSet = new HashSet<ResolvedModuleName>();

	/**
	 * A {@linkplain Map map} from {@linkplain ResolvedModuleName resolved
	 * module names} to their predecessors.
	 */
	private final @NotNull
	Map<ResolvedModuleName, Set<ResolvedModuleName>> predecessors = new HashMap<ResolvedModuleName, Set<ResolvedModuleName>>();

	/**
	 * A {@linkplain Map map} from {@linkplain ResolvedModuleName resolved
	 * module names} to their successors.
	 */
	private final @NotNull
	Map<ResolvedModuleName, Set<ResolvedModuleName>> successors = new HashMap<ResolvedModuleName, Set<ResolvedModuleName>>();

	/**
	 * The {@linkplain ModuleName dependencies} of the {@linkplain #target
	 * target}.
	 */
	private final @NotNull
	List<ModuleName> dependencies = new ArrayList<ModuleName>();

	/**
	 * Trace the imports of the {@linkplain ModuleDescriptor module} specified
	 * by the given {@linkplain ModuleName module name}.
	 *
	 * @param resolvedSuccessor
	 *            The resolved named of the module using or extending this
	 *            module, or null if this module is the start of the recursive
	 *            resolution (i.e., it will be the last one compiled).
	 * @param qualifiedName
	 *            A fully-qualified {@linkplain ModuleName module name}.
	 * @throws AvailCompilerException
	 *             If the {@linkplain AbstractAvailCompiler compiler} is unable
	 *             to find a module declaration for this
	 *             {@linkplain ModuleDescriptor module}.
	 * @throws RecursiveDependencyException
	 *             If the specified {@linkplain ModuleDescriptor module}
	 *             recursively depends upon itself.
	 * @throws UnresolvedDependencyException
	 *             If the specified {@linkplain ModuleDescriptor module} name
	 *             could not be resolved to a module.
	 */
	private void traceModuleImports (
		final ResolvedModuleName resolvedSuccessor,
		final @NotNull ModuleName qualifiedName) throws AvailCompilerException,
		RecursiveDependencyException, UnresolvedDependencyException
	{
		final ResolvedModuleName resolution = runtime.moduleNameResolver()
			.resolve(qualifiedName);
		if (resolution == null)
		{
			throw new UnresolvedDependencyException(
				resolvedSuccessor,
				qualifiedName.localName());
		}

		// Detect recursion into this module.
		recursionList.add(resolution);
		if (recursionSet.contains(resolution))
		{
			throw new RecursiveDependencyException(recursionList);
		}

		// Prevent recursion into this module.
		recursionSet.add(resolution);

		if (!completionSet.contains(resolution))
		{
			assert !predecessors.containsKey(resolution);
			assert !successors.containsKey(resolution);
			predecessors.put(resolution, new HashSet<ResolvedModuleName>());
			successors.put(resolution, new HashSet<ResolvedModuleName>());
		}
		if (resolvedSuccessor != null)
		{
			predecessors.get(resolvedSuccessor).add(resolution);
			successors.get(resolution).add(resolvedSuccessor);
		}

		if (!completionSet.contains(resolution))
		{
			// Build the set of names of imported modules.
			final AbstractAvailCompiler compiler = AbstractAvailCompiler
				.create(qualifiedName, interpreter, true);
			compiler.parseModuleHeader(qualifiedName);
			final ModuleHeader header = compiler.moduleHeader;
			final Set<ModuleName> importedModules = new HashSet<ModuleName>(
				header.extendedModules.size() + header.usedModules.size());
			for (final AvailObject extendedModule : header.extendedModules)
			{
				importedModules.add(resolution.asSibling(extendedModule
					.tupleAt(1).asNativeString()));
			}
			for (final AvailObject usedModule : header.usedModules)
			{
				importedModules.add(resolution.asSibling(usedModule.tupleAt(1)
					.asNativeString()));
			}

			// Recurse into each import.
			for (final ModuleName moduleName : importedModules)
			{
				try
				{
					traceModuleImports(resolution, moduleName);
				}
				catch (final RecursiveDependencyException e)
				{
					e.prependModule(resolution);
					throw e;
				}
			}

			// Prevent subsequent unnecessary visits.
			completionSet.add(resolution);
		}

		// Permit reaching (but not scanning) this module again.
		recursionSet.remove(resolution);
		recursionList.remove(recursionList.size() - 1);
	}

	/**
	 * Compute {@linkplain ModuleDescriptor module} loading order from the
	 * partial order implicitly specified by {@link #predecessors} and
	 * {@link #successors}, emptying them in the fiber.
	 */
	private void linearizeModuleImports ()
	{
		while (!predecessors.isEmpty())
		{
			for (final Map.Entry<ResolvedModuleName, Set<ResolvedModuleName>> entry : new ArrayList<Map.Entry<ResolvedModuleName, Set<ResolvedModuleName>>>(
				predecessors.entrySet()))
			{
				final ResolvedModuleName key = entry.getKey();
				if (entry.getValue().isEmpty())
				{
					dependencies.add(key);
					predecessors.remove(key);
					for (final ResolvedModuleName successor : successors
						.get(key))
					{
						predecessors.get(successor).remove(key);
					}
					successors.remove(key);
				}
			}
		}

		assert successors.isEmpty();
	}

	/**
	 * Answer the size, in bytes, of all source files that will be built.
	 *
	 * @return The size, in bytes, of all source files that will be built.
	 */
	private long globalCodeSize ()
	{
		final ModuleNameResolver resolver = runtime.moduleNameResolver();
		long globalCodeSize = 0L;
		for (final ModuleName moduleName : dependencies)
		{
			globalCodeSize += resolver.resolve(moduleName).fileReference()
				.length();
		}
		return globalCodeSize;
	}

	/**
	 * Build the {@linkplain ModuleDescriptor target} and its dependencies
	 * within a new {@linkplain AvailThread Avail thread}.
	 *
	 * @param builder
	 *            An {@linkplain AvailBuilder Avail builder}.
	 * @param localTracker
	 *            A {@linkplain Continuation4 continuation} that accepts
	 *            <ol>
	 *            <li>the {@linkplain ModuleName name} of the
	 *            {@linkplain ModuleDescriptor module} undergoing
	 *            {@linkplain AbstractAvailCompiler compilation},</li>
	 *            <li>the current line number within the current module,</li>
	 *            <li>the position of the ongoing parse (in bytes), and</li>
	 *            <li>the size of the module in bytes.</li>
	 *            </ol>
	 * @param globalTracker
	 *            A {@linkplain Continuation3 continuation} that accepts
	 *            <ol>
	 *            <li>the name of the module undergoing compilation,</li>
	 *            <li>the number of bytes globally processed, and</li>
	 *            <li>the global size (in bytes) of all modules that will be
	 *            built.</li>
	 *            </ol>
	 * @throws AvailCompilerException
	 *             If the compiler is unable to find a module declaration.
	 * @throws InterruptedException
	 *             If the builder thread is interrupted.
	 * @throws RecursiveDependencyException
	 *             If an encountered module recursively depends upon itself.
	 * @throws UnresolvedDependencyException
	 *             If a module name could not be resolved.
	 */
	public static void buildTargetInNewAvailThread (
		final @NotNull AvailBuilder builder,
		final @NotNull Continuation4<ModuleName, Long, Long, Long> localTracker,
		final @NotNull Continuation3<ModuleName, Long, Long> globalTracker)
		throws AvailCompilerException, InterruptedException,
		RecursiveDependencyException, UnresolvedDependencyException
	{
		final Mutable<Exception> killer = new Mutable<Exception>();
		final AvailThread thread = builder.runtime.newThread(new Runnable()
		{
			@Override
			public void run ()
			{
				try
				{
					builder.buildTarget(localTracker, globalTracker);
				}
				catch (final AvailCompilerException e)
				{
					killer.value = e;
				}
				catch (final RecursiveDependencyException e)
				{
					killer.value = e;
				}
				catch (final UnresolvedDependencyException e)
				{
					killer.value = e;
				}
				catch (final MalformedSerialStreamException e)
				{
					killer.value = e;
				}
			}
		});
		thread.run();
		thread.join();
		if (killer.value != null)
		{
			if (killer.value instanceof AvailCompilerException)
			{
				throw (AvailCompilerException) killer.value;
			}
			else if (killer.value instanceof RecursiveDependencyException)
			{
				throw (RecursiveDependencyException) killer.value;
			}
			else if (killer.value instanceof UnresolvedDependencyException)
			{
				throw (UnresolvedDependencyException) killer.value;
			}
		}
	}

	/**
	 * Build the {@linkplain ModuleDescriptor target} and its dependencies. Must
	 * be called from an {@linkplain AvailThread Avail thread}.
	 *
	 * @param localTracker
	 *            A {@linkplain Continuation4 continuation} that accepts
	 *            <ol>
	 *            <li>the {@linkplain ModuleName name} of the
	 *            {@linkplain ModuleDescriptor module} undergoing
	 *            {@linkplain AbstractAvailCompiler compilation},</li>
	 *            <li>the current line number within the current module,</li>
	 *            <li>the position of the ongoing parse (in bytes), and</li>
	 *            <li>the size of the module in bytes.</li>
	 *            </ol>
	 * @param globalTracker
	 *            A {@linkplain Continuation3 continuation} that accepts
	 *            <ol>
	 *            <li>the name of the module undergoing compilation,</li>
	 *            <li>the number of bytes globally processed, and</li>
	 *            <li>the global size (in bytes) of all modules that will be
	 *            built.</li>
	 *            </ol>
	 * @throws AvailCompilerException
	 *             If the compiler is unable to find a module declaration.
	 * @throws RecursiveDependencyException
	 *             If an encountered module recursively depends upon itself.
	 * @throws UnresolvedDependencyException
	 *             If a module name could not be resolved.
	 * @throws MalformedSerialStreamException
	 *             If the repository contains invalid serialized module data.
	 */
	public void buildTarget (
		final @NotNull Continuation4<ModuleName, Long, Long, Long> localTracker,
		final @NotNull Continuation3<ModuleName, Long, Long> globalTracker)
		throws AvailCompilerException, RecursiveDependencyException,
		UnresolvedDependencyException, MalformedSerialStreamException
	{
		final ModuleNameResolver resolver = runtime.moduleNameResolver();
		traceModuleImports(null, target);
		linearizeModuleImports();
		final long globalCodeSize = globalCodeSize();
		final Mutable<Long> globalPosition = new Mutable<Long>();
		globalPosition.value = 0L;
		for (final ModuleName moduleName : dependencies)
		{
			globalTracker.value(
				moduleName,
				globalPosition.value,
				globalCodeSize);
			final ResolvedModuleName resolved = resolver.resolve(moduleName);
			if (!runtime.includesModuleNamed(StringDescriptor.from(resolved
				.qualifiedName())))
			{
				final File fileReference = resolved.fileReference();
				final String filePath = fileReference.getAbsolutePath();
				final long moduleTimestamp = fileReference.lastModified();
				if (repository.hasKey(filePath, moduleTimestamp))
				{
					// Module has already been compiled since last change.
					localTracker.value(moduleName, -1L, -1L, -1L);
					final byte[] bytes = repository.get(
						filePath,
						moduleTimestamp);

					// TODO[MvG]: Remove debug.
//					System.out.format(
//						"LOADING MODULE: %s (%d compiled bytes)%n",
//						moduleName,
//						bytes.length);

					final ByteArrayInputStream inputStream =
						new ByteArrayInputStream(bytes);
					final AvailObject module = ModuleDescriptor.newModule(
						StringDescriptor.from(resolved.qualifiedName()));
					interpreter.setModule(module);
					final Deserializer deserializer = new Deserializer(
						inputStream,
						runtime);
					deserializer.currentModule(module);
					try
					{
						AvailObject tag = deserializer.deserialize();
						if (!tag.equals(
							AtomDescriptor.moduleHeaderSectionAtom()))
						{
							throw new RuntimeException(
								"Expected module header tag");
						}
						final ModuleHeader header = new ModuleHeader(resolved);
						header.deserializeHeaderFrom(deserializer);
						module.isSystemModule(header.isSystemModule);
						final String errorString = header.applyToModule(
							module,
							runtime);
						if (errorString != null)
						{
							throw new RuntimeException(errorString);
						}

						tag = deserializer.deserialize();
						if (!tag.equals(AtomDescriptor.moduleBodySectionAtom()))
						{
							throw new RuntimeException(
								"Expected module body tag");
						}

						AvailObject block;
						final List<AvailObject> noArgs =
							Collections.emptyList();
						// Run each zero-argument block.
						while ((block = deserializer.deserialize()) != null)
						{
							// System.out.println("RUNNING: " + block);
							interpreter.runFunctionArguments(block, noArgs);
						}
						runtime.addModule(module);
						module.cleanUpAfterCompile();
						interpreter.setModule(null);
					}
					catch (final Exception e)
					{
						module.removeFrom(interpreter);
						interpreter.setModule(null);
						if (e instanceof AvailCompilerException)
						{
							throw (AvailCompilerException) e;
						}
						if (e instanceof MalformedSerialStreamException)
						{
							throw (MalformedSerialStreamException) e;
						}
						throw new RuntimeException(e);
					}
				}
				else
				{
					// Actually compile the module and cache its compiled form.
					final AbstractAvailCompiler compiler = AbstractAvailCompiler
						.create(moduleName, interpreter, false);
					compiler.parseModule(
						moduleName,
						new Continuation4<ModuleName, Long, Long, Long>()
						{
							@Override
							public void value (
								final @NotNull ModuleName moduleName2,
								final @NotNull Long lineNumber,
								final @NotNull Long localPosition,
								final @NotNull Long moduleSize)
							{
								assert moduleName.equals(moduleName2);
								localTracker.value(
									moduleName,
									lineNumber,
									localPosition,
									moduleSize);
								globalTracker.value(
									moduleName,
									globalPosition.value + localPosition,
									globalCodeSize);
							}
						});
					repository.put(
						filePath,
						moduleTimestamp,
						compiler.serializerOutputStream.toByteArray());
					// TODO [MvG] - Remove serialization debug code.
//					System.err.format(
//						"%6d : %s%n",
//						compiler.serializerOutputStream.size(),
//						moduleName);
				}
			}
			globalPosition.value += resolved.fileReference().length();
		}
		assert globalPosition.value == globalCodeSize;
		globalTracker.value(target, globalPosition.value, globalCodeSize);
	}
}
