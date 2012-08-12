/**
 * ModuleName.java
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

package com.avail.builder;

import com.avail.annotations.*;
import com.avail.descriptor.ModuleDescriptor;

/**
 * A {@code ModuleName} represents the canonical name of an Avail {@linkplain
 * ModuleDescriptor module}. A canonical name is specified relative to an
 * Avail {@linkplain ModuleRoots module root} and has the form
 * <strong>/R/X/Y/Z</strong>, where <strong>R</strong> is a module root on the
 * Avail module path, <strong>X</strong> is a package within
 * <strong>R</strong>, <strong>Y</strong> is a package within
 * <strong>X</strong>, and <strong>Z</strong> is a module or package within
 * </strong>Y</strong>.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class ModuleName
{
	/** The fully-qualified module name. */
	private final String qualifiedName;

	/**
	 * Answer the fully-qualified module name.
	 *
	 * @return The fully-qualified module name.
	 */
	public String qualifiedName ()
	{
		return qualifiedName;
	}

	/** The logical root name of the {@linkplain ModuleName module name}. */
	private final String moduleRoot;

	/**
	 * Answer the logical root name of the {@linkplain ModuleName module name}.
	 *
	 * @return the moduleRoot
	 *         The logical root name of the {@linkplain ModuleName module name}.
	 */
	public String moduleRoot ()
	{
		return moduleRoot;
	}

	/**
	 * The fully-qualified package name of the {@linkplain ModuleName module
	 * name}.
	 */
	private final String packageName;

	/**
	 * Answer the fully-qualified package name of the {@linkplain ModuleName
	 * module name}.
	 *
	 * @return The fully-qualified package name of the {@linkplain ModuleName
	 *         module name}.
	 */
	public String packageName ()
	{
		return packageName;
	}

	/**
	 * The local name of the {@linkplain ModuleDescriptor module} referenced by
	 * this {@linkplain ModuleName module name}.
	 */
	private final String localName;

	/**
	 * Answer the local name of the {@linkplain ModuleDescriptor module}
	 * referenced by this {@linkplain ModuleName module name}.
	 *
	 * @return The local name of the {@linkplain ModuleDescriptor module}
	 *         referenced by this {@linkplain ModuleName module name}.
	 */
	public String localName ()
	{
		return localName;
	}

	/**
	 * Construct a new {@link ModuleName} from the specified fully-qualified
	 * module name.
	 *
	 * @param qualifiedName A fully-qualified module name.
	 * @throws IllegalArgumentException
	 *         If the argument was malformed.
	 */
	public ModuleName (final String qualifiedName)
		throws IllegalArgumentException
	{
		this.qualifiedName = qualifiedName;

		final String[] components = qualifiedName.split("/");
		if (components.length < 3 || !components[0].isEmpty())
		{
			throw new IllegalArgumentException(
				"invalid fully-qualified module name (" + qualifiedName + ")");
		}

		// Handle the easy ones first.
		moduleRoot = components[1];
		localName  = components[components.length - 1];

		// Now determine the package.
		final StringBuilder builder = new StringBuilder(50);
		for (int index = 1; index < components.length - 1; index++)
		{
			builder.append('/');
			builder.append(components[index]);
		}
		packageName = builder.toString();
	}

	/**
	 * Construct a new {@link ModuleName} from the specified canonical module
	 * group name and local name.
	 *
	 * @param packageName A canonical package name.
	 * @param localName A local module name.
	 * @throws IllegalArgumentException
	 *         If the argument was malformed.
	 */
	public ModuleName (
			final String packageName,
			final String localName)
		throws IllegalArgumentException
	{
		this(packageName + "/" + localName);
	}

	@Override
	public boolean equals (final @Nullable Object obj)
	{
		if (obj instanceof ModuleName)
		{
			return qualifiedName.equals(((ModuleName) obj).qualifiedName);
		}

		return false;
	}

	@Override
	public int hashCode ()
	{
		// The magic number is a prime.
		return 345533 * qualifiedName.hashCode();
	}

	@Override
	public String toString ()
	{
		return qualifiedName;
	}
}
