/*
 * ModuleImport.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.compiler

import com.avail.builder.ModuleName
import com.avail.compiler.ModuleImport.Companion.fromSerializedTuple
import com.avail.descriptor.A_Module
import com.avail.descriptor.NilDescriptor
import com.avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import com.avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor.emptySet
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromArray
import com.avail.descriptor.tuples.StringDescriptor.stringFrom
import com.avail.serialization.MalformedSerialStreamException

/**
 * Information that a [ModuleHeader] uses to keep track of a module
 * import, whether from an Extends clause or a Uses clause, as specified by the
 * [SpecialMethodAtom.MODULE_HEADER].
 *
 * @property isExtension
 *   Whether this [ModuleImport] is due to an Extends clause rather than a
 *   `Uses` clause, as indicated by [module
 *   header][SpecialMethodAtom.MODULE_HEADER].
 * @property wildcard
 *   Whether to include all names exported by the predecessor module that are
 *   not otherwise excluded by this import.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [ModuleImport].
 *
 * @param moduleName
 *   The non-resolved [name][StringDescriptor] of the module to import.
 * @param acceptableVersions
 *   The [set][SetDescriptor] of version [strings][String] from which to look
 *   for a match in the actual imported module's list of compatible versions.
 * @param isExtension
 *   `true` if these imported declarations are supposed to be re-exported from
 *   the current module.
 * @param names
 *   The [set][SetDescriptor] of names ([strings][String]) imported from the
 *   module.  They will be cause atoms to be looked up within the predecessor
 *   module, and will be re-exported verbatim if `isExtension` is `true`.
 * @param renames
 *   The [map][MapDescriptor] from new names to old names (both
 *   [strings][StringDescriptor]) that are imported from the module.  The new
 *   names will become new atoms in the importing module, and exported if
 *   `isExtension` is `true`.
 * @param excludes
 *   The [set][SetDescriptor] of names ([strings][String]) to exclude from being
 *   imported.
 * @param wildcard
 *   Whether to import any published names not explicitly excluded.
 * @throws ImportValidationException
 *   If the specification is invalid.
 */
class ModuleImport
@Throws(ImportValidationException::class) internal constructor(
	moduleName: A_String,
	acceptableVersions: A_Set,
	val isExtension: Boolean,
	names: A_Set,
	renames: A_Map,
	excludes: A_Set,
	val wildcard: Boolean)
{
	/** The name of the module being imported. */
	val moduleName: A_String

	/**
	 * A [set][SetDescriptor] of [strings][StringDescriptor] which, when
	 * intersected with the declared version strings for the actual module being
	 * imported, must be nonempty.
	 */
	val acceptableVersions: A_Set

	/**
	 * The [set][SetDescriptor] of names ([strings][String]) explicitly imported
	 * through this import declaration.  If no names or renames were specified,
	 * then this is [nil][NilDescriptor.nil] instead.
	 */
	val names: A_Set

	/**
	 * The [map][MapDescriptor] of renames ([string][String] → string)
	 * explicitly specified in this import declaration.  The keys are the newly
	 * introduced names and the values are the names provided by the predecessor
	 * module.  If no names or renames were specified, then this is
	 * [nil][NilDescriptor.nil] instead.
	 */
	val renames: A_Map

	/**
	 * The [set][SetDescriptor] of names to specifically exclude from being
	 * imported from the predecessor module.
	 */
	val excludes: A_Set

	init
	{
		this.moduleName = moduleName.makeShared()
		this.acceptableVersions = acceptableVersions.makeShared()
		this.names = names.makeShared()
		this.renames = renames.makeShared()
		this.excludes = excludes.makeShared()
		validate()
	}

	/**
	 * Validate the module import specification.
	 *
	 * @throws ImportValidationException
	 *   If the specification is invalid.
	 */
	@Throws(ImportValidationException::class)
	private fun validate()
	{
		val renameOriginals = renames.valuesAsTuple().asSet()
		if (wildcard)
		{
			if (!names.isSubsetOf(renameOriginals))
			{
				throw ImportValidationException(
					"wildcard import not to be specified or "
					+ "explicit positive imports only to be used to force "
					+ "inclusion of source names of renames")
			}
		}
		else
		{
			if (excludes.setSize() != 0)
			{
				throw ImportValidationException(
					"wildcard import to be specified or "
					+ "explicit negative imports not to be specified")
			}
		}
		val redundantExclusions = renameOriginals.setIntersectionCanDestroy(
			excludes,
			false)
		if (redundantExclusions.setSize() != 0)
		{
			val builder = StringBuilder(100)
			builder.append(
				"source names of renames not to overlap explicit "
				+ "negative imports (the redundant name")
			if (redundantExclusions.setSize() == 1)
			{
				builder.append(" is ")
			}
			else
			{
				builder.append("s are ")
			}
			var first = true
			for (redundant in redundantExclusions)
			{
				if (first)
				{
					first = false
				}
				else
				{
					builder.append(", ")
				}
				// This will quote the string.
				builder.append(redundant)
			}
			builder.append(')')
			throw ImportValidationException(builder.toString())
		}
	}

	/**
	 * Answer a tuple suitable for serializing this import information.
	 *
	 * This currently consists of exactly 7 elements:
	 *
	 *  1. The unresolved module name.
	 *  2. The tuple of acceptable version strings.
	 *  3. `true` if this is an `Extends` import, false if it's a `Uses`.
	 *  4. The set of names (strings) to explicitly import.
	 *  5. The map from new names to old names (all strings) to explicitly
	 *     import and rename.
	 *  6. The set of names (strings) to explicitly exclude from importing.
	 *  7. `true` to include all names not explicitly excluded, otherwise
	 *     `false`.
	 *
	 * @see [fromSerializedTuple]
	 * @return
	 *   The tuple to serialize.
	 */
	internal val tupleForSerialization
		get() = tupleFromArray(
			moduleName,
			acceptableVersions,
			objectFromBoolean(isExtension),
			names,
			renames,
			excludes,
			objectFromBoolean(wildcard))

	companion object
	{
		/**
		 * Produce a `ModuleImport` that represents an extension of the
		 * provided [A_Module].
		 *
		 * @param module
		 *   A module.
		 * @return
		 *   The desired import.
		 */
		fun extend(module: A_Module): ModuleImport
		{
			try
			{
				val name = ModuleName(module.moduleName().asNativeString())
				return ModuleImport(
					stringFrom(name.localName),
					module.versions(),
					true,
					emptySet(),
					emptyMap(),
					emptySet(),
					true)
			}
			catch (e: ImportValidationException)
			{
				assert(false) { "This shouldn't happen" }
				throw RuntimeException(e)
			}
		}

		/**
		 * Convert the provided [tuple][TupleDescriptor] into a `ModuleImport`.
		 * This is the reverse of the transformation provided by
		 * [tupleForSerialization].
		 *
		 * @param serializedTuple
		 *   The tuple from which to build a `ModuleImport`.
		 * @return
		 *   The `ModuleImport`.
		 * @throws MalformedSerialStreamException
		 *   If the module import specification is invalid.
		 */
		@Throws(MalformedSerialStreamException::class)
		fun fromSerializedTuple(serializedTuple: A_Tuple): ModuleImport
		{
			val tupleSize = serializedTuple.tupleSize()
			assert(tupleSize == 7)
			try
			{
				return ModuleImport(
					serializedTuple.tupleAt(1), // moduleName
					serializedTuple.tupleAt(2), // acceptableVersions
					serializedTuple.tupleAt(3).extractBoolean(), // isExtension
					serializedTuple.tupleAt(4), // names
					serializedTuple.tupleAt(5), // renames
					serializedTuple.tupleAt(6), // excludes
					serializedTuple.tupleAt(7).extractBoolean() // wildcard
				)
			}
			catch (e: ImportValidationException)
			{
				throw MalformedSerialStreamException(e)
			}
		}
	}
}
