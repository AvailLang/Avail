/**
 * ModuleRoots.java
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.ModuleDescriptor;

/**
 * {@code ModuleRoots} encapsulates the Avail {@linkplain ModuleDescriptor
 * module} path in both composed and decomposed forms. The Avail module path
 * specifies bindings between <em>logical root names</em> and {@linkplain
 * File#isAbsolute() absolute} {@linkplain File pathnames} of directories
 * containing Avail modules. A logical root name should typically belong to a
 * vendor of Avail modules, ergo a domain name or registered trademark suffices
 * nicely.
 *
 * <p>The format of an Avail module path is described by the following
 * simple grammar:</p>
 *
 * <pre>
 * modulePath ::= binding ++ ";" ;
 * binding ::= root "=" directory ;
 * root ::= [^=;]+ ;
 * directory ::= [^;]+ ;
 * </pre>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@ThreadSafe
public final class ModuleRoots
{
	/** The Avail {@linkplain ModuleDescriptor module} path. */
	private final String modulePath;

	/**
	 * Answer the Avail {@linkplain ModuleDescriptor module} path.
	 *
	 * @return The Avail {@linkplain ModuleDescriptor module} path.
	 */
	@ThreadSafe
	public String modulePath ()
	{
		return modulePath;
	}

	/**
	 * A {@linkplain Map map} from logical root names to {@linkplain
	 * File#isAbsolute() absolute} pathnames of {@linkplain File directories}
	 * (containing Avail {@linkplain ModuleDescriptor modules}).
	 */
	private final Map<String, File> rootMap =
		new LinkedHashMap<String, File>();

	/**
	 * Parse the Avail {@linkplain ModuleDescriptor module} path into a
	 * {@linkplain Map map} of logical root names to {@linkplain
	 * File#isAbsolute() absolute} pathnames of {@linkplain File directories}.
	 *
	 * @throws IllegalArgumentException
	 *         If any component of the Avail {@linkplain ModuleDescriptor
	 *         module} path is not the absolution pathname of a directory.
	 */
	private void parseAvailModulePath ()
		throws IllegalArgumentException
	{
		for (final String component : modulePath.split(";"))
		{
			if (!component.isEmpty())
			{
				final String[] binding = component.split("=");
				if (binding.length != 2)
				{
					throw new IllegalArgumentException(
						"An Avail module path component must be a logical root "
						+ "name, then an equals (=), then the absolute "
						+ "pathname of a directory containing Avail modules.");
				}

				final String rootName = binding[0];
				final File fileName = new File(binding[1]);
				if (!fileName.isAbsolute() || !fileName.isDirectory())
				{
					throw new IllegalArgumentException(
						"An Avail module path component ("
						+ fileName.getPath()
						+ ") did not specify the absolute pathname of a "
						+ "directory.");
				}

				rootMap.put(rootName, fileName);
			}
		}
	}

	/**
	 * Answer the logical root names in the order that they are specified in
	 * the Avail {@linkplain ModuleDescriptor module} path.
	 *
	 * @return The logical root names.
	 */
	@ThreadSafe
	public Set<String> rootNames ()
	{
		return Collections.unmodifiableSet(rootMap.keySet());
	}

	/**
	 * Answer the {@linkplain File root directory} bound to the specified
	 * logical root name.
	 *
	 * @param rootName A logical root name, typically something owned by a
	 *                 vendor of Avail {@linkplain ModuleDescriptor
	 *                 modules}.
	 * @return The {@linkplain File root directory} bound to the specified
	 *         logical root name, or {@code null} if no such binding exists.
	 */
	@ThreadSafe
	public File rootDirectoryFor (final String rootName)
	{
		return rootMap.get(rootName);
	}

	/**
	 * Construct a new {@link ModuleRoots} from the specified Avail {@linkplain
	 * ModuleDescriptor module} path.
	 *
	 * @param modulePath
	 *        An Avail {@linkplain ModuleDescriptor module} path.
	 * @throws IllegalArgumentException
	 *         If the Avail {@linkplain ModuleDescriptor module} path is
	 *         malformed.
	 */
	@ThreadSafe
	public ModuleRoots (final String modulePath)
	{
		this.modulePath = modulePath;
		parseAvailModulePath();
	}
}
