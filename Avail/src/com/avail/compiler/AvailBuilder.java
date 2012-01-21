/**
 * AvailBuilder.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.compiler;

import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
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
	private final @NotNull AvailRuntime runtime;

	/**
	 * The {@linkplain ModuleName canonical name} of the
	 * {@linkplain ModuleDescriptor module} that the {@linkplain AvailBuilder
	 * builder} must (recursively) load into the {@linkplain AvailRuntime
	 * runtime}.
	 */
	private final @NotNull ModuleName target;

	/** An {@linkplain L2Interpreter interpreter}. */
	private final @NotNull L2Interpreter interpreter;

	/**
	 * Construct a new {@link AvailBuilder}.
	 *
	 * @param runtime
	 *            The {@linkplain AvailRuntime runtime} into which the
	 *            {@linkplain AvailBuilder builder} will install the target
	 *            {@linkplain ModuleDescriptor module} and its dependencies.
	 * @param target
	 *            The {@linkplain ModuleName canonical name} of the
	 *            {@linkplain ModuleDescriptor module} that the
	 *            {@linkplain AvailBuilder builder} must (recursively) load into
	 *            the {@linkplain AvailRuntime runtime}.
	 */
	public AvailBuilder (
			final @NotNull AvailRuntime runtime,
			final @NotNull ModuleName target)
	{
		this.runtime = runtime;
		this.target = target;
		this.interpreter = new L2Interpreter(this.runtime);
	}

	/**
	 * The path maintained by the {@linkplain #traceModuleImports(
	 * ResolvedModuleName, ModuleName) tracer} to prevent recursive tracing.
	 */
	private final @NotNull Set<ResolvedModuleName> recursionSet =
		new HashSet<ResolvedModuleName>();

	/**
	 * The set of module names that have already been encountered and completely
	 * recursed through.
	 */
	private final @NotNull Set<ResolvedModuleName> completionSet =
		new HashSet<ResolvedModuleName>();

	/**
	 * A {@linkplain Map map} from {@linkplain ResolvedModuleName resolved
	 * module names} to their predecessors.
	 */
	private final @NotNull Map<ResolvedModuleName, Set<ResolvedModuleName>>
		predecessors =
			new HashMap<ResolvedModuleName, Set<ResolvedModuleName>>();

	/**
	 * A {@linkplain Map map} from {@linkplain ResolvedModuleName resolved
	 * module names} to their successors.
	 */
	private final @NotNull Map<ResolvedModuleName, Set<ResolvedModuleName>>
		successors =
			new HashMap<ResolvedModuleName, Set<ResolvedModuleName>>();

	/**
	 * The {@linkplain ModuleName dependencies} of the {@linkplain #target
	 * target}.
	 */
	private final @NotNull List<ModuleName> dependencies =
		new ArrayList<ModuleName>();

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
	 *            If the {@linkplain AbstractAvailCompiler compiler} is unable
	 *            to process a module declaration for this
	 *            {@linkplain ModuleDescriptor module}.
	 * @throws RecursiveDependencyException
	 *            If the specified {@linkplain ModuleDescriptor module}
	 *            recursively depends upon itself.
	 * @throws UnresolvedDependencyException
	 *            If the specified {@linkplain ModuleDescriptor module} name
	 *            could not be resolved to a module.
	 */
	private void traceModuleImports (
		final ResolvedModuleName resolvedSuccessor,
		final @NotNull ModuleName qualifiedName)
	throws
		AvailCompilerException,
		RecursiveDependencyException,
		UnresolvedDependencyException
	{
		final ResolvedModuleName resolution =
			runtime.moduleNameResolver().resolve(qualifiedName);
		if (resolution == null)
		{
			throw new UnresolvedDependencyException(
				resolvedSuccessor,
				qualifiedName.localName());
		}

		// Detect recursion into this module.
		if (recursionSet.contains(resolution))
		{
			throw new RecursiveDependencyException(resolution);
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
			final AbstractAvailCompiler compiler = AbstractAvailCompiler.create(
				qualifiedName,
				interpreter,
				true);
			compiler.parseModuleHeader(qualifiedName);
			final Set<ModuleName> importedModules = new HashSet<ModuleName>(
				compiler.extendedModules.size() + compiler.usedModules.size());
			for (final AvailObject extendedModule : compiler.extendedModules)
			{
				importedModules.add(resolution.asSibling(
					extendedModule.tupleAt(1).asNativeString()));
			}
			for (final AvailObject usedModule : compiler.usedModules)
			{
				importedModules.add(resolution.asSibling(
					usedModule.tupleAt(1).asNativeString()));
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
	}

	/**
	 * Compute {@linkplain ModuleDescriptor module} loading order from the
	 * partial order implicitly specified by {@link #predecessors} and
	 * {@link #successors}, emptying them in the process.
	 */
	private void linearizeModuleImports ()
	{
		while (!predecessors.isEmpty())
		{
			for (final Map.Entry<ResolvedModuleName, Set<ResolvedModuleName>>
					entry
				: new ArrayList<
						Map.Entry<ResolvedModuleName, Set<ResolvedModuleName>>>(
					predecessors.entrySet()))
			{
				final ResolvedModuleName key = entry.getKey();
				if (entry.getValue().isEmpty())
				{
					dependencies.add(key);
					predecessors.remove(key);
					for (final ResolvedModuleName successor
						: successors.get(key))
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
	 * Build the {@linkplain ModuleDescriptor target} and its dependencies.
	 *
	 * @param localTracker
	 *            A {@linkplain Continuation3 continuation} that accepts
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
	 *            <li>the {@linkplain ModuleName name} of the
	 *            {@linkplain ModuleDescriptor module} undergoing
	 *            {@linkplain AbstractAvailCompiler compilation},</li>
	 *            <li>the number of bytes globally processed, and</li>
	 *            <li>the global size (in bytes) of all modules that will be
	 *            built.</li>
	 *            </ol>
	 * @throws AvailCompilerException
	 *             If the {@linkplain AbstractAvailCompiler compiler} is unable
	 *             to process a module declaration.
	 * @throws RecursiveDependencyException
	 *             If an encountered {@linkplain ModuleDescriptor module}
	 *             recursively depends upon itself.
	 * @throws UnresolvedDependencyException
	 *             If a module name could not be resolved.
	 */
	public void buildTarget (
		final @NotNull Continuation4<ModuleName, Long, Long, Long> localTracker,
		final @NotNull Continuation3<ModuleName, Long, Long> globalTracker)
	throws
		AvailCompilerException,
		RecursiveDependencyException,
		UnresolvedDependencyException
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
			if (!runtime.includesModuleNamed(
				StringDescriptor.from(resolved.qualifiedName())))
			{
				final AbstractAvailCompiler compiler =
					AbstractAvailCompiler.create(
						moduleName,
						interpreter,
						false);
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
			}
			globalPosition.value += resolved.fileReference().length();
		}
		assert globalPosition.value == globalCodeSize;
		globalTracker.value(target, globalPosition.value, globalCodeSize);
	}
}
