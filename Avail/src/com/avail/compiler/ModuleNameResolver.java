/**
 * com.avail.compiler/RenameRules.java
 * Copyright (c) 2010, Mark van Gulik.
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

import java.io.File;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import com.avail.annotations.NotNull;
import com.avail.descriptor.ModuleDescriptor;

/**
 * A {@code ModuleNameResolver} resolves unqualified references to Avail
 * {@linkplain ModuleDescriptor modules} to {@linkplain File#isAbsolute()
 * absolute} {@linkplain File file references}.
 * 
 * <p>Assuming that the Avail module path comprises four module roots listed in
 * the order <strong>S</strong>, <strong>P</strong>,<strong>Q</strong>,
 * <strong>R</strong>, then the following algorithm is used for resolution of an
 * unqualified reference <strong>M</strong> from within a fully-qualified module
 * group <strong>/R/A/B/C</strong>:</p>
 * 
 * <ol>
 * <li>If a module renaming rule exists for <strong>/R/A/B/C/M</strong>, then
 * apply it and answer the resultant file reference <strong>M'</strong>.</li>
 * <li>If module group <strong>/R/A/B/C</strong> contains a module
 * <strong>M</strong>, then capture its file reference <string>F</strong>.</li>
 * <li>If module group <strong>/R/A/B</strong> contains a module
 * <strong>M</strong>, then capture its file reference <string>F</strong>.</li>
 * <li>If module group <strong>/R/A</strong> contains a module
 * <strong>M</strong>, then capture its file reference <string>F</strong>.</li>
 * <li>If module root <strong>/R</strong> contains a module <strong>M</strong>,
 * then capture its file reference <strong>F</strong>.</li>
 * <li>If module root <strong>/S</strong> contains a module <strong>M</strong>,
 * then capture its file reference <strong>F</strong>.</li>
 * <li>If module root <strong>/P</strong> contains a module <strong>M</strong>,
 * then capture its file reference <strong>F</strong>.</li>
 * <li>If module root <strong>/Q</strong> contains a module <strong>M</strong>,
 * then capture its file reference <strong>F</strong>.</li>
 * <li>If the resolution succeeded and <strong>F</strong> specifies a directory,
 * then replace the resolution with <strong>F/Main.avail</strong>. Verify that
 * the resolution specifies an existing regular file.</li>   
 * <li>Otherwise resolution failed.</li>
 * </ol>
 *
 * <p>An instance is obtained via {@link RenamesFileParser#parse()}.</p>
 * 
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class ModuleNameResolver
{
	/**
	 * The local module name of the specially designated module group
	 * representative.
	 */
	static final @NotNull String moduleGroupRepresentative = "Main.avail";
	
	/** The {@linkplain ModuleRoots Avail module roots}. */
	private final @NotNull ModuleRoots moduleRoots;
	
	/**
	 * Answer the {@linkplain ModuleRoots Avail module roots}.
	 * 
	 * @return The {@linkplain ModuleRoots Avail module roots}.
	 */
	public @NotNull ModuleRoots moduleRoots ()
	{
		return moduleRoots;
	}
	
	/**
	 * Construct a new {@link ModuleNameResolver}.
	 * 
	 * @param roots The Avail {@linkplain ModuleRoots module roots}.
	 */
	ModuleNameResolver (final @NotNull ModuleRoots roots)
	{
		this.moduleRoots = roots;
	}
	
	/**
	 * A {@linkplain Map map} from logical {@linkplain ModuleDescriptor
	 * module} paths to absolute {@linkplain File file references}.
	 */
	private final @NotNull Map<String, File> renames =
		new HashMap<String, File>();
	
	/**
	 * Does the {@linkplain ModuleNameResolver resolver} have a transformation
	 * rule for the specified logical {@linkplain ModuleDescriptor module}
	 * path? 
	 * 
	 * @param modulePath
	 *        The logical {@linkplain ModuleDescriptor module} path that
	 *        should be queried.
	 * @return {@code true} if there is a rule to transform the logical
	 *         {@linkplain ModuleDescriptor module} path into an
	 *         {@linkplain File#isAbsolute() absolute} {@linkplain File file
	 *         reference}, {@code false} otherwise.
	 */
	boolean hasRenameRuleFor (final @NotNull String modulePath)
	{
		return renames.containsKey(modulePath);
	}
	
	/**
	 * Add a rule to translate the specified logical {@linkplain
	 * ModuleDescriptor module} path into the specified {@linkplain
	 * File#isAbsolute() absolute} {@linkplain File file reference}.
	 * 
	 * @param modulePath
	 *        The logical {@linkplain ModuleDescriptor module} path that
	 *        should be translated.
	 * @param fileReference
	 *        An {@linkplain File#isAbsolute() absolute} {@linkplain File file
	 *        reference}.
	 */
	void addRenameRule (
		final @NotNull String modulePath,
		final @NotNull File fileReference)
	{
		assert !renames.containsKey(modulePath);
		renames.put(modulePath, fileReference);
	}
	
	/**
	 * Trivially translate the specified module group name and local module name
	 * into a filename.
	 * 
	 * @param moduleGroup A module group name.
	 * @param localName A local module name.
	 * @return A filename that specifies the module within the module group.
	 */
	private @NotNull String translate (
		final @NotNull String moduleGroup,
		final @NotNull String localName)
	{
		return moduleGroup + "/" + localName + ".avail";
	}
	
	/**
	 * Resolve a reference to the specified unqualified module name made from
	 * within the given module group to an {@linkplain File#isAbsolute()
	 * absolute} {@linkplain File file reference}.
	 * 
	 * @param moduleGroup
	 *        The fully-qualified name of the module group from within which the
	 *        reference is being made.
	 * @param localName
	 *        An unqualified module name.
	 * @return An {@linkplain File#isAbsolute() absolute} {@linkplain File file
	 *         reference}, or {@code null} if the module reference could not be
	 *         resolved.
	 */
	public File resolve (
		final @NotNull String moduleGroup,
		final @NotNull String localName)
	{
		assert localName.indexOf('/') == -1;
		
		// First attempt to lookup the canonical module name in the map of
		// renaming rules. Apply the rule if it exists.
		final String modulePath = moduleGroup + "/" + localName;
		final File rename = renames.get(modulePath);
		if (rename != null)
		{
			return rename;
		}
		
		File resolution = null;
		
		// Really resolve the local name. Start by splitting the module
		// group into its components.
		final String[] components = moduleGroup.split("/");
		assert components.length > 1;
		assert components[0].isEmpty();
		
		// Build a search stack of trials at ascending tiers of enclosing
		// module groups.
		final Deque<File> searchStack = new LinkedList<File>();
		final String enclosingRoot = components[1];
		searchStack.push(moduleRoots.rootDirectoryFor(enclosingRoot));
		for (int index = 2; index < components.length; index++)
		{
			searchStack.push(new File(
				searchStack.peekFirst(),
				components[index] + ".avail"));
		}
		
		// Explore the search stack from most enclosing module group to
		// least enclosing.
		while (!searchStack.isEmpty())
		{
			final File trial = new File(translate(
				searchStack.pop().getPath(), localName));
			if (trial.exists())
			{
				resolution = trial;
				break;
			}
		}
		
		// If resolution failed, then one final option is available: search
		// the other root directories.
		if (resolution == null)
		{
			for (final String root : moduleRoots.rootNames())
			{
				if (!root.equals(enclosingRoot))
				{
					final File trial = new File(
						moduleRoots.rootDirectoryFor(root),
						localName + ".avail");
					if (trial.exists())
					{
						resolution = trial;
						break;
					}
				}
			}
		}
		
		if (resolution != null)
		{
			// We found a candidate. If it is a module group, then substitute
			// the module group representative.
			if (resolution.isDirectory())
			{
				resolution = new File(resolution, moduleGroupRepresentative);
				if (!resolution.isFile())
				{
					// Alas, the module group representative did not exist.
					return null;
				}
			}
		}
		
		// Answer the (possibly null) resolution.
		return resolution;
	}
}
