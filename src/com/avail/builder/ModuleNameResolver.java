/*
 * ModuleNameResolver.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.annotations.InnerAccess;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.utility.LRUCache;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import static com.avail.utility.Nulls.stripNull;

/**
 * A {@code ModuleNameResolver} resolves fully-qualified references to Avail
 * {@linkplain ModuleDescriptor modules} to {@linkplain File#isAbsolute()
 * absolute} {@linkplain File file references}.
 *
 * <p>Assuming that the Avail module path comprises four module roots listed in
 * the order <strong>S</strong>, <strong>P</strong>,<strong>Q</strong>,
 * <strong>R</strong>, then the following algorithm is used for resolution of a
 * fully-qualified reference <strong>/R/X/Y/Z/M</strong>:</p>
 *
 * <ol>
 * <li>Obtain the canonical name <strong>/R'/A/B/C/M'</strong> by applying an
 * existing renaming rule for <strong>/R/X/Y/Z/M</strong>.</li>
 * <li>If package <strong>/R'/A/B/C</strong> contains a module
 * <strong>M'</strong>, then capture its file reference <strong>F</strong>.</li>
 * <li>If package <strong>/R'/A/B</strong> contains a module
 * <strong>M'</strong>, then capture its file reference <strong>F</strong>.</li>
 * <li>If package <strong>/R'/A</strong> contains a module
 * <strong>M'</strong>, then capture its file reference <strong>F</strong>.</li>
 * <li>If module root <strong>/R</strong> contains a module <strong>M'</strong>,
 * then capture its file reference <strong>F</strong>.</li>
 * <li>If module root <strong>/S</strong> contains a module <strong>M'</strong>,
 * then capture its file reference <strong>F</strong>.</li>
 * <li>If module root <strong>/P</strong> contains a module <strong>M'</strong>,
 * then capture its file reference <strong>F</strong>.</li>
 * <li>If module root <strong>/Q</strong> contains a module <strong>M'</strong>,
 * then capture its file reference <strong>F</strong>.</li>
 * <li>If the resolution succeeded and <strong>F</strong> specifies a directory,
 * then replace the resolution with <strong>F/M'.avail</strong>. Verify that
 * the resolution specifies an existing regular file.</li>
 * <li>Otherwise resolution failed.</li>
 * </ol>
 *
 * <p>An instance is obtained via {@link RenamesFileParser#parse()}.</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
@ThreadSafe
public final class ModuleNameResolver
{
	/**
	 * The standard extension for Avail {@linkplain ModuleDescriptor module}
	 * source files.
	 */
	public static final String availExtension = ".avail";

	/** The {@linkplain ModuleRoots Avail module roots}. */
	final ModuleRoots moduleRoots;

	/**
	 * Answer the {@linkplain ModuleRoots Avail module roots}.
	 *
	 * @return The {@linkplain ModuleRoots Avail module roots}.
	 */
	public ModuleRoots moduleRoots ()
	{
		return moduleRoots;
	}

	/**
	 * Construct a new {@code ModuleNameResolver}.
	 *
	 * @param roots The Avail {@linkplain ModuleRoots module roots}.
	 */
	public ModuleNameResolver (final ModuleRoots roots)
	{
		this.moduleRoots = roots;
	}

	/**
	 * A {@linkplain Map map} from fully-qualified module names to their
	 * canonical names.
	 */
	private final Map<String, String> renames = new LinkedHashMap<>();

	/**
	 * Answer an immutable {@link Map} of all the module path renames.
	 *
	 * @return A non-modifiable map.
	 */
	public Map<String, String> renameRules ()
	{
		return Collections.unmodifiableMap(renames);
	}

	/**
	 * Does the resolver have a transformation rule for the specified
	 * fully-qualified module name?
	 *
	 * @param modulePath
	 *        A fully-qualified module name.
	 * @return {@code true} if there is a rule to transform the fully-qualified
	 *         module name into another one, {@code false} otherwise.
	 */
	boolean hasRenameRuleFor (final String modulePath)
	{
		return renames.containsKey(modulePath);
	}

	/**
	 * Remove all rename rules.
	 */
	public void clearRenameRules ()
	{
		renames.clear();
	}

	/**
	 * Add a rule to translate the specified fully-qualified module name.
	 *
	 * @param modulePath
	 *        A fully-qualified module name.
	 * @param substitutePath
	 *        The canonical name.
	 */
	public void addRenameRule (
		final String modulePath,
		final String substitutePath)
	{
		assert !renames.containsKey(modulePath);
		renames.put(modulePath, substitutePath);
	}

	/**
	 * Trivially translate the specified package name and local module name
	 * into a filename.
	 *
	 * @param packageName
	 *        A package name.
	 * @param localName
	 *        A local module name.
	 * @return A filename that specifies the module within the package.
	 */
	static String filenameFor (
		final String packageName,
		final String localName)
	{
		return packageName + "/" + localName + availExtension;
	}

	/**
	 * Answer the canonical name that should be used in place of the
	 * fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param qualifiedName
	 *        A fully-qualified {@linkplain ModuleName module name}.
	 * @return The canonical name that should be used in place of the
	 *         fully-qualified {@linkplain ModuleName module name}.
	 */
	ModuleName canonicalNameFor (final ModuleName qualifiedName)
	{
		final String substitute = renames.get(qualifiedName.qualifiedName());
		if (substitute != null)
		{
			return new ModuleName(substitute, true);
		}
		return qualifiedName;
	}

	/**
	 * A {@linkplain LRUCache cache} of {@linkplain ResolvedModuleName resolved
	 * module names}, keyed by fully-qualified {@linkplain ModuleName module
	 * names}.
	 */
	private final LRUCache<ModuleName, ModuleNameResolutionResult>
		resolutionCache = new LRUCache<>(10_000, 100, this::privateResolve);

	/**
	 * Clear all cached module resolutions.  Also release all file locks on
	 * repositories and close them.
	 */
	public void clearCache ()
	{
		try
		{
			resolutionCache.clear();
		}
		catch (final InterruptedException e)
		{
			// Do nothing.
		}
	}

	/**
	 * Release all external locks and handles associated with this resolver.  It
	 * must not be used again.
	 */
	public void destroy ()
	{
		moduleRoots().forEach(root -> root.repository().close());
	}

	/**
	 * Actually resolve the qualified module name.  This is @{@link InnerAccess}
	 * to ensure clients always go through the cache.
	 *
	 * @param qualifiedName
	 *        The qualified name of the module.
	 * @return A {@link ModuleNameResolutionResult} indicating the result of the
	 *         attempted resolution.
	 */
	@InnerAccess ModuleNameResolutionResult privateResolve (
		final ModuleName qualifiedName)
	{
		// Attempt to look up the fully-qualified name in the map of renaming
		// rules. Apply the rule if it exists.
		ModuleName canonicalName = canonicalNameFor(qualifiedName);

		// If the root cannot be resolved, then neither can the
		// module.
		final String enclosingRoot = canonicalName.rootName();
		@Nullable ModuleRoot root = moduleRoots.moduleRootFor(enclosingRoot);
		if (root == null)
		{
			return new ModuleNameResolutionResult(
				new UnresolvedRootException(
					null, qualifiedName.localName(), enclosingRoot));
		}

		final String[] components = canonicalName.packageName().split("/");
		assert components.length > 1;
		assert components[0].isEmpty();

		final Deque<String> nameStack = new LinkedList<>();
		nameStack.addLast("/" + enclosingRoot);
		@Nullable Deque<File> pathStack = null;

		// If the source directory is available, then build a search stack of
		// trials at ascending tiers of enclosing packages.
		@Nullable File sourceDirectory = root.sourceDirectory();
		if (sourceDirectory != null)
		{
			pathStack = new LinkedList<>();
			pathStack.addLast(sourceDirectory);
		}
		for (int index = 2; index < components.length; index++)
		{
			assert !components[index].isEmpty();
			nameStack.addLast(String.format(
				"%s/%s",
				nameStack.peekLast(),
				components[index]));
			if (sourceDirectory != null)
			{
				pathStack.addLast(new File(
					pathStack.peekLast(),
					components[index] + availExtension));
			}
		}

		// If the source directory is available, then search the file system.
		final ArrayList<ModuleName> checkedPaths = new ArrayList<>();
		@Nullable IndexedRepositoryManager repository = null;
		@Nullable File sourceFile = null;
		if (sourceDirectory != null)
		{
			assert !pathStack.isEmpty();
			// Explore the search stack from most enclosing package to least
			// enclosing.
			while (!pathStack.isEmpty())
			{
				canonicalName = new ModuleName(
					nameStack.removeLast(),
					canonicalName.localName(),
					canonicalName.isRename());
				checkedPaths.add(canonicalName);
				final File trial = new File(filenameFor(
					pathStack.removeLast().getPath(),
					canonicalName.localName()));
				if (trial.exists())
				{
					repository = root.repository();
					sourceFile = trial;
					break;
				}
			}
		}

		// If resolution failed, then one final option is available: search the
		// other roots.
		if (repository == null)
		{
			for (final String rootName : moduleRoots.rootNames())
			{
				if (!rootName.equals(enclosingRoot))
				{
					canonicalName = new ModuleName(
						String.format(
							"/%s/%s", rootName, canonicalName.localName()),
						canonicalName.isRename());
					checkedPaths.add(canonicalName);
					root = moduleRoots.moduleRootFor(rootName);
					assert root != null;
					sourceDirectory = root.sourceDirectory();
					if (sourceDirectory != null)
					{
						final File trial = new File(
							sourceDirectory,
							canonicalName.localName() + availExtension);
						if (trial.exists())
						{
							repository = root.repository();
							sourceFile = trial;
							break;
						}
					}
				}
			}
		}

		// We found a candidate.
		if (repository != null)
		{
			// If the candidate is a package, then substitute
			// the package representative.
			if (sourceFile.isDirectory())
			{
				sourceFile = new File(
					sourceFile,
					canonicalName.localName() + availExtension);
				canonicalName = new ModuleName(
					canonicalName.qualifiedName(),
					canonicalName.localName(),
					canonicalName.isRename());
				if (!sourceFile.isFile())
				{
					// Alas, the package representative did not exist.
					return new ModuleNameResolutionResult(
						new UnresolvedModuleException(
							null, qualifiedName.localName(), checkedPaths));
				}
			}
			return new ModuleNameResolutionResult(
				new ResolvedModuleName(
					canonicalName, moduleRoots(), canonicalName.isRename()));
		}

		// Resolution failed.
		return new ModuleNameResolutionResult(
			new UnresolvedModuleException(
				null, qualifiedName.localName(), checkedPaths));
	}

	/**
	 * This class was created so that, upon an UnresolvedDependencyException,
	 * the ModuleNameResolver could bundle information about the different paths
	 * checked for the missing file into the exception itself.
	 */
	@InnerAccess static final class ModuleNameResolutionResult
	{
		/** The module that was successfully resolved, or null if not found. */
		private final @Nullable ResolvedModuleName resolvedModule;

		/** An exception if the module was not found, or null if it was. */
		private final @Nullable UnresolvedDependencyException exception;

		/**
		 * Answer whether the resolution produced a {@link ResolvedModuleName},
		 * rather than an exception.
		 *
		 * @return whether the resolution was successful.
		 */
		public boolean isResolved ()
		{
			return resolvedModule != null;
		}

		/**
		 * Answer the resolvedModule, which should not be null.
		 *
		 * @return the non-null {@link ResolvedModuleName}.
		 */
		public ResolvedModuleName resolvedModule ()
		{
			return stripNull(resolvedModule);
		}

		/**
		 * Answer the exception, which should not be null.
		 *
		 * @return the non-null {@link UnresolvedDependencyException}.
		 */
		public UnresolvedDependencyException exception ()
		{
			return stripNull(exception);
		}

		/**
		 * Construct a new {@code ModuleNameResolutionResult}, upon successful
		 * resolution, with the {@linkplain ResolvedModuleName resolved module}.
		 *
		 * @param resolvedModule The module that was successfully resolved.
		 */
		ModuleNameResolutionResult (
			final ResolvedModuleName resolvedModule)
		{
			this.resolvedModule = resolvedModule;
			this.exception = null;
		}

		/**
		 * Construct a new {@code ModuleNameResolutionResult}, upon an
		 * unsuccessful resolution, with an {@linkplain
		 * UnresolvedDependencyException exception} containing the paths that
		 * did not have the missing module.
		 *
		 * @param e
		 *        The {@link UnresolvedDependencyException} that was thrown
		 *        while resolving a module.
		 */
		ModuleNameResolutionResult (
			final UnresolvedDependencyException e)
		{
			this.resolvedModule = null;
			this.exception = e;
		}
	}

	/**
	 * Resolve a fully-qualified module name (as a reference to the {@linkplain
	 * ModuleName#localName() local name} made from within the {@linkplain
	 * ModuleName#packageName() package}).
	 *
	 * @param qualifiedName
	 *        A fully-qualified {@linkplain ModuleName module name}.
	 * @param dependent
	 *        The name of the module that requires this resolution, if any.
	 * @return A {@linkplain ResolvedModuleName resolved module name} if the
	 *         resolution was successful.
	 * @throws UnresolvedDependencyException
	 *         If resolution fails.
	 */
	public ResolvedModuleName resolve (
			final ModuleName qualifiedName,
			final @Nullable ResolvedModuleName dependent)
		throws UnresolvedDependencyException
	{
		final ModuleNameResolutionResult result =
			stripNull(resolutionCache.get(qualifiedName));
		if (!result.isResolved())
		{
			// The resolution failed.
			if (dependent != null)
			{
				result.exception().setReferringModuleName(dependent);
			}
			throw result.exception();
		}
		return result.resolvedModule();
	}

	/**
	 * Commit all dirty repositories.
	 */
	public void commitRepositories ()
	{
		for (final ModuleRoot root : moduleRoots())
		{
			root.repository().commit();
		}
	}
}
