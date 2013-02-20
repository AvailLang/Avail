/**
 * ModuleNameResolver.java
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

import java.io.File;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import com.avail.annotations.*;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.utility.*;

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
 * existing renaming rule for <strong>/R/X/Y/Z/M</strong>.
 * <li>If package <strong>/R'/A/B/C</strong> contains a module
 * <strong>M'</strong>, then capture its file reference <string>F</strong>.</li>
 * <li>If package <strong>/R'/A/B</strong> contains a module
 * <strong>M'</strong>, then capture its file reference <string>F</strong>.</li>
 * <li>If package <strong>/R'/A</strong> contains a module
 * <strong>M'</strong>, then capture its file reference <string>F</strong>.</li>
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
	 * Construct a new {@link ModuleNameResolver}.
	 *
	 * @param roots The Avail {@linkplain ModuleRoots module roots}.
	 */
	ModuleNameResolver (final ModuleRoots roots)
	{
		this.moduleRoots = roots;
	}

	/**
	 * A {@linkplain Map map} from fully-qualified module names to their
	 * canonical names.
	 */
	private final Map<String, String> renames = new HashMap<String, String>();

	/**
	 * Does the {@linkplain ModuleNameResolver resolver} have a transformation
	 * rule for the specified fully-qualified module name?
	 *
	 * @param modulePath A fully-qualified module name.
	 * @return {@code true} if there is a rule to transform the fully-qualified
	 *         module name into another one, {@code false} otherwise.
	 */
	boolean hasRenameRuleFor (final String modulePath)
	{
		return renames.containsKey(modulePath);
	}

	/**
	 * Add a rule to translate the specified fully-qualified module name.
	 *
	 * @param modulePath A fully-qualified module name.
	 * @param substitutePath The canonical name.
	 */
	void addRenameRule (
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
	 * @param packageName A package name.
	 * @param localName A local module name.
	 * @return A filename that specifies the module within the package.
	 */
	String filenameFor (
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
			return new ModuleName(substitute);
		}

		return qualifiedName;
	}

	/**
	 * A {@linkplain LRUCache cache} of {@linkplain ResolvedModuleName resolved
	 * module names}, keyed by fully-qualified {@linkplain ModuleName module
	 * names}.
	 */
	private final LRUCache<ModuleName, ResolvedModuleName> resolutionCache =
		new LRUCache<ModuleName, ResolvedModuleName>(
			100,
			100,
			new Transformer1<ModuleName, ResolvedModuleName>()
			{
				@Override
				public @Nullable ResolvedModuleName value (
					final @Nullable ModuleName qualifiedName)
				{
					assert qualifiedName != null;
					File resolution = null;

					// First attempt to lookup the fully-qualified name in the
					// map of renaming rules. Apply the rule if it exists.
					ModuleName canonicalName = canonicalNameFor(qualifiedName);

					// Really resolve the local name. Start by splitting the
					// module group into its components.
					final String[] components =
						canonicalName.packageName().split("/");
					assert components.length > 1;
					assert components[0].isEmpty();

					// Build a search stack of trials at ascending tiers of
					// enclosing packages.
					final Deque<File> searchStack = new LinkedList<File>();
					final Deque<String> canonicalNames =
						new LinkedList<String>();
					final String enclosingRoot = canonicalName.moduleRoot();
					canonicalNames.push("/" + enclosingRoot);
					searchStack.push(
						moduleRoots.rootDirectoryFor(enclosingRoot));
					for (int index = 2; index < components.length; index++)
					{
						assert !components[index].isEmpty();
						canonicalNames.push(String.format(
							"%s/%s",
							canonicalNames.peekFirst(),
							components[index]));
						searchStack.push(new File(
							searchStack.peekFirst(),
							components[index] + availExtension));
					}

					// Explore the search stack from most enclosing package to
					// least enclosing.
					while (!searchStack.isEmpty())
					{
						canonicalName = new ModuleName(
							canonicalNames.pop(), canonicalName.localName());
						final File trial = new File(filenameFor(
							searchStack.pop().getPath(),
							canonicalName.localName()));
						if (trial.exists())
						{
							resolution = trial;
							break;
						}
					}

					// If resolution failed, then one final option is available:
					// search the other root directories.
					if (resolution == null)
					{
						for (final String root : moduleRoots.rootNames())
						{
							if (!root.equals(enclosingRoot))
							{
								canonicalName = new ModuleName(String.format(
									"/%s/%s", root, canonicalName.localName()));
								final File trial = new File(
									moduleRoots.rootDirectoryFor(root),
									canonicalName.localName() + availExtension);
								if (trial.exists())
								{
									resolution = trial;
									break;
								}
							}
						}
					}

					// We found a candidate.
					if (resolution != null)
					{
						// If the candidate is a package, then substitute the
						// package representative.
						final boolean isPackage = resolution.isDirectory();
						if (isPackage)
						{
							resolution = new File(
								resolution,
								canonicalName.localName() + availExtension);
							if (!resolution.isFile())
							{
								// Alas, the package representative did not
								// exist.
								return null;
							}
						}
						return new ResolvedModuleName(
							canonicalName, isPackage, resolution);
					}

					// Resolution failed.
					return null;
				}
			});

	/**
	 * Resolve a fully-qualified module name (as a reference to the {@linkplain
	 * ModuleName#localName() local name} made from within the {@linkplain
	 * ModuleName#packageName() package}).
	 *
	 * @param qualifiedName
	 *        A fully-qualified {@linkplain ModuleName module name}.
	 * @return A {@linkplain ResolvedModuleName resolved module name}, or {@code
	 *         null} if resolution failed.
	 */
	public @Nullable ResolvedModuleName resolve (final ModuleName qualifiedName)
	{
		return resolutionCache.get(qualifiedName);
	}
}
