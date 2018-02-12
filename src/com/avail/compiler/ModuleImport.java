/*
 * ModuleImport.java
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

package com.avail.compiler;

import com.avail.builder.ModuleName;
import com.avail.descriptor.*;
import com.avail.serialization.MalformedSerialStreamException;

import static com.avail.descriptor.AtomDescriptor.objectFromBoolean;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.tuple;

/**
 * Information that a {@link ModuleHeader} uses to keep track of a module
 * import, whether from an {@linkplain ExpectedToken#EXTENDS Extends} or a
 * {@linkplain ExpectedToken#USES Uses} clause.
 */
public class ModuleImport
{
	/**
	 * The name of the module being imported.
	 */
	public final A_String moduleName;

	/**
	 * A {@linkplain SetDescriptor set} of {@linkplain StringDescriptor
	 * strings} which, when intersected with the declared version strings
	 * for the actual module being imported, must be nonempty.
	 */
	public final A_Set acceptableVersions;

	/**
	 * Whether this {@link ModuleImport} is due to a {@linkplain
	 * ExpectedToken#EXTENDS Extends} clause rather than a {@linkplain
	 * ExpectedToken#USES Uses} clause.
	 */
	public final boolean isExtension;

	/**
	 * The {@linkplain SetDescriptor set} of names ({@linkplain
	 * StringDescriptor strings}) explicitly imported through this import
	 * declaration.  If no names or renames were specified, then this is
	 * {@linkplain NilDescriptor#nil nil} instead.
	 */
	public final A_Set names;

	/**
	 * The {@linkplain MapDescriptor map} of renames ({@linkplain
	 * StringDescriptor string} → string) explicitly specified in this
	 * import declaration.  The keys are the newly introduced names and the
	 * values are the names provided by the predecessor module.  If no names
	 * or renames were specified, then this is {@linkplain
	 * NilDescriptor#nil nil} instead.
	 */
	public final A_Map renames;

	/**
	 * The {@linkplain SetDescriptor set} of names to specifically exclude
	 * from being imported from the predecessor module.
	 */
	public final A_Set excludes;

	/**
	 * Whether to include all names exported by the predecessor module that
	 * are not otherwise excluded by this import.
	 */
	public final boolean wildcard;

	/**
	 * Construct a new {@link ModuleImport}.
	 *
	 * @param moduleName
	 *            The non-resolved {@linkplain StringDescriptor name} of the
	 *            module to import.
	 * @param acceptableVersions
	 *            The {@linkplain SetDescriptor set} of version {@linkplain
	 *            StringDescriptor strings} from which to look for a match
	 *            in the actual imported module's list of compatible
	 *            versions.
	 * @param isExtension
	 *            True if these imported declarations are supposed to be
	 *            re-exported from the current module.
	 * @param names
	 *            The {@linkplain SetDescriptor set} of names ({@linkplain
	 *            StringDescriptor strings}) imported from the module.  They
	 *            will be cause atoms to be looked up within the predecessor
	 *            module, and will be re-exported verbatim if isExtension is
	 *            true.
	 * @param renames
	 *            The {@linkplain MapDescriptor map} from new names to old
	 *            names (both {@linkplain StringDescriptor strings}) that
	 *            are imported from the module.  The new names will become
	 *            new atoms in the importing module, and exported if
	 *            isExtension is true.
	 * @param excludes
	 *            The {@linkplain SetDescriptor set} of names ({@linkplain
	 *            StringDescriptor strings}) to exclude from being imported.
	 * @param wildcard
	 *            Whether to import any published names not explicitly
	 *            excluded.
	 * @throws ImportValidationException
	 *         If the specification is invalid.
	 */
	ModuleImport (
			final A_String moduleName,
			final A_Set acceptableVersions,
			final boolean isExtension,
			final A_Set names,
			final A_Map renames,
			final A_Set excludes,
			final boolean wildcard)
		throws ImportValidationException
	{
		this.moduleName = moduleName.makeShared();
		this.acceptableVersions = acceptableVersions.makeShared();
		this.isExtension = isExtension;
		this.names = names.makeShared();
		this.renames = renames.makeShared();
		this.excludes = excludes.makeShared();
		this.wildcard = wildcard;
		validate();
	}

	/**
	 * Produce an {@linkplain ModuleImport import} that represents an
	 * {@link ExpectedToken#EXTENDS Extend} of the provided {@linkplain
	 * A_Module module}.
	 *
	 * @param module
	 *        A module.
	 * @return The desired import.
	 */
	public static ModuleImport extend (final A_Module module)
	{
		try
		{
			final ModuleName name =
				new ModuleName(module.moduleName().asNativeString());
			return new ModuleImport(
				stringFrom(name.localName()),
				module.versions(),
				true,
				emptySet(),
				emptyMap(),
				emptySet(),
				true);
		}
		catch (final ImportValidationException e)
		{
			assert false : "This shouldn't happen";
			throw new RuntimeException(e);
		}
	}

	/**
	 * Validate the module import specification.
	 *
	 * @throws ImportValidationException
	 *         If the specification is invalid.
	 */
	private void validate () throws ImportValidationException
	{
		final A_Set renameOriginals = renames.valuesAsTuple().asSet();
		if (wildcard)
		{
			if (!names.isSubsetOf(renameOriginals))
			{
				throw new ImportValidationException(
					"wildcard import not to be specified or "
					+ "explicit positive imports only to be used to force "
					+ "inclusion of source names of renames");
			}
		}
		else
		{
			if (excludes.setSize() != 0)
			{
				throw new ImportValidationException(
					"wildcard import to be specified or "
					+ "explicit negative imports not to be specified");
			}
		}
		final A_Set redundantExclusions =
			renameOriginals.setIntersectionCanDestroy(
				excludes,
				false);
		if (redundantExclusions.setSize() != 0)
		{
			final StringBuilder builder = new StringBuilder(100);
			builder.append(
				"source names of renames not to overlap explicit "
				+ "negative imports (the redundant name");
			if (redundantExclusions.setSize() == 1)
			{
				builder.append(" is ");
			}
			else
			{
				builder.append("s are ");
			}
			boolean first = true;
			for (final A_String redundant : redundantExclusions)
			{
				if (first)
				{
					first = false;
				}
				else
				{
					builder.append(", ");
				}
				// This will quote the string.
				builder.append(redundant);
			}
			builder.append(")");
			throw new ImportValidationException(builder.toString());
		}
	}

	/**
	 * Answer a tuple suitable for serializing this import information.
	 *
	 * <p>
	 * This currently consists of exactly 7 elements:
	 * <ol>
	 * <li>The unresolved module name.</li>
	 * <li>The tuple of acceptable version strings.</li>
	 * <li>True if this is an "Extends" import, false if it's a "Uses".</li>
	 * <li>The set of names (strings) to explicitly import.</li>
	 * <li>The map from new names to old names (all strings) to explicitly
	 * import and rename.</li>
	 * <li>The set of names (strings) to explicitly exclude from
	 * importing.</li>
	 * <li>True to include all names not explicitly excluded, otherwise
	 * false</li>
	 * </ol>
	 *
	 * @see #fromSerializedTuple(A_Tuple)
	 * @return The tuple to serialize.
	 */
	A_Tuple tupleForSerialization ()
	{
		return tuple(moduleName, acceptableVersions,
			objectFromBoolean(isExtension), names, renames,
			excludes, objectFromBoolean(wildcard));
	}

	/**
	 * Convert the provided {@linkplain TupleDescriptor tuple} into a
	 * {@link ModuleImport}.  This is the reverse of the transformation
	 * provided by {@link #tupleForSerialization()}.
	 *
	 * @param serializedTuple The tuple from which to build a ModuleImport.
	 * @return The ModuleImport.
	 * @throws MalformedSerialStreamException
	 *         If the module import specification is invalid.
	 */
	public static ModuleImport fromSerializedTuple (
			final A_Tuple serializedTuple)
		throws MalformedSerialStreamException
	{
		final int tupleSize = serializedTuple.tupleSize();
		assert tupleSize == 7;
		try
		{
			return new ModuleImport(
				serializedTuple.tupleAt(1), // moduleName
				serializedTuple.tupleAt(2), // acceptableVersions
				serializedTuple.tupleAt(3).extractBoolean(), // isExtension
				serializedTuple.tupleAt(4), // names
				serializedTuple.tupleAt(5), // renames
				serializedTuple.tupleAt(6), // excludes
				serializedTuple.tupleAt(7).extractBoolean() // wildcard
			);
		}
		catch (final ImportValidationException e)
		{
			throw new MalformedSerialStreamException(e);
		}
	}
}
