/*
 * ModuleHeader.kt
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

import com.avail.AvailRuntime
import com.avail.builder.ModuleName
import com.avail.builder.ResolvedModuleName
import com.avail.builder.UnresolvedDependencyException
import com.avail.compiler.splitter.MessageSplitter
import com.avail.descriptor.A_Module
import com.avail.descriptor.AvailObject
import com.avail.descriptor.ModuleDescriptor
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.MESSAGE_BUNDLE_KEY
import com.avail.descriptor.atoms.AtomWithPropertiesDescriptor.createAtomWithProperties
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.MessageBundleDescriptor.newBundle
import com.avail.descriptor.numbers.IntegerDescriptor.fromInt
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor.emptySet
import com.avail.descriptor.sets.SetDescriptor.setFromCollection
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tokens.LiteralTokenDescriptor.literalToken
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList
import com.avail.descriptor.tuples.StringDescriptor.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor.toList
import com.avail.exceptions.MalformedMessageException
import com.avail.serialization.Deserializer
import com.avail.serialization.MalformedSerialStreamException
import com.avail.serialization.Serializer
import java.util.*

/**
 * A module's header information.
 *
 * @property moduleName
 *   The [ModuleName] of the module undergoing compilation.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `ModuleHeader`.
 *
 * @param moduleName
 *   The [resolved name][ResolvedModuleName] of the module.
 */
class ModuleHeader constructor(val moduleName: ResolvedModuleName)
{
	/**
	 * The versions for which the module undergoing compilation guarantees
	 * support.
	 */
	val versions: MutableList<A_String> = ArrayList()

	/**
	 * The [module imports][ModuleImport] imported by the module undergoing
	 * compilation.  This includes both modules being extended and modules being
	 * simply used.
	 */
	val importedModules: MutableList<ModuleImport> = ArrayList()

	/**
	 * The [names][StringDescriptor] defined and exported by the
	 * [module][ModuleDescriptor] undergoing compilation.
	 */
	val exportedNames: MutableSet<A_String> = LinkedHashSet()

	/**
	 * The [names][StringDescriptor] of [methods][MethodDescriptor] that are
	 * [module][ModuleDescriptor] entry points.
	 */
	val entryPoints: MutableList<A_String> = ArrayList()

	/**
	 * The [pragma tokens][TokenDescriptor], which are always string
	 * [literals][LiteralTokenDescriptor].
	 */
	val pragmas: MutableList<A_Token> = ArrayList()

	/**
	 * The position in the file where the body starts (right after the "body"
	 * token).
	 */
	var startOfBodyPosition: Int = 0

	/**
	 * The line number in the file where the body starts (on the same line as
	 * the "body" token).
	 */
	var startOfBodyLineNumber: Int = 0

	/**
	 * The list of local module [names][String] imported by this module header,
	 * in the order they appear in the `Uses` and `Extends` clauses.
	 */
	val importedModuleNames: List<String>
		get()
		{
			val localNames = ArrayList<String>(importedModules.size)
			for (moduleImport in importedModules)
			{
				localNames.add(moduleImport.moduleName.asNativeString())
			}
			return localNames
		}

	/**
	 * A [List] of [String]s which name entry points defined in this module
	 * header.
	 */
	val entryPointNames: List<String>
		get()
		{
			val javaStrings = ArrayList<String>(entryPoints.size)
			for (entryPoint in entryPoints)
			{
				javaStrings.add(entryPoint.asNativeString())
			}
			return javaStrings
		}

	/**
	 * Output the module header.
	 *
	 * @param serializer
	 *   The serializer on which to write the header information.
	 */
	fun serializeHeaderOn(serializer: Serializer)
	{
		serializer.serialize(stringFrom(moduleName.qualifiedName))
		serializer.serialize(tupleFromList(versions))
		serializer.serialize(tuplesForSerializingModuleImports)
		serializer.serialize(tupleFromList(ArrayList(exportedNames)))
		serializer.serialize(tupleFromList(entryPoints))
		serializer.serialize(tupleFromList(pragmas))
		serializer.serialize(fromInt(startOfBodyPosition))
		serializer.serialize(fromInt(startOfBodyLineNumber))
	}

	/**
	 * The information about the imported modules as a [tuple][A_Tuple] of
	 * tuples suitable for serialization.
	 */
	private val tuplesForSerializingModuleImports: A_Tuple
		get()
		{
			val list = ArrayList<A_Tuple>()
			for (moduleImport in importedModules)
			{
				list.add(moduleImport.tupleForSerialization)
			}
			return tupleFromList(list)
		}

	/**
	 * Convert the information encoded in a tuple into a [List] of
	 * [ModuleImport]s.
	 *
	 * @param serializedTuple
	 *   An encoding of a list of ModuleImports.
	 * @return
	 *   The list of ModuleImports.
	 * @throws MalformedSerialStreamException
	 *   If the module import specification is invalid.
	 */
	@Throws(MalformedSerialStreamException::class)
	private fun moduleImportsFromTuple(
		serializedTuple: A_Tuple): List<ModuleImport>
	{
		val list = ArrayList<ModuleImport>()
		for (importTuple in serializedTuple)
		{
			list.add(ModuleImport.fromSerializedTuple(importTuple))
		}
		return list
	}

	/**
	 * Extract the module's header information from the [Deserializer].
	 *
	 * @param deserializer
	 *   The source of the header information.
	 * @throws MalformedSerialStreamException
	 *   If malformed.
	 */
	@Throws(MalformedSerialStreamException::class)
	fun deserializeHeaderFrom(deserializer: Deserializer)
	{
		val name = deserializer.deserialize()!!
		if (name.asNativeString() != moduleName.qualifiedName)
		{
			throw RuntimeException(
				"Incorrect module name.  Expected: "
				+ "${moduleName.qualifiedName} but found $name")
		}
		val theVersions = deserializer.deserialize()!!
		versions.clear()
		versions.addAll(toList(theVersions))
		val theExtended = deserializer.deserialize()!!
		importedModules.clear()
		importedModules.addAll(moduleImportsFromTuple(theExtended))
		val theExported = deserializer.deserialize()!!
		exportedNames.clear()
		exportedNames.addAll(toList(theExported))
		val theEntryPoints = deserializer.deserialize()!!
		entryPoints.clear()
		entryPoints.addAll(toList(theEntryPoints))
		val thePragmas = deserializer.deserialize()!!
		pragmas.clear()
		// Synthesize fake tokens for the pragma strings.
		for (pragmaString in thePragmas)
		{
			pragmas.add(
				literalToken(
					pragmaString,
					0,
					0,
					pragmaString))
		}
		val positionInteger = deserializer.deserialize()!!
		startOfBodyPosition = positionInteger.extractInt()
		val lineNumberInteger = deserializer.deserialize()!!
		startOfBodyLineNumber = lineNumberInteger.extractInt()
	}

	/**
	 * Update the given module to correspond with information that has been
	 * accumulated in this `ModuleHeader`.
	 *
	 * @param module
	 *   The module to update.
	 * @param runtime
	 *   The current [AvailRuntime].
	 * @return
	 *   An error message [String] if there was a problem, or `null` if no
	 *   problems were encountered.
	 */
	fun applyToModule(module: A_Module, runtime: AvailRuntime): String?
	{
		val resolver = runtime.moduleNameResolver()
		module.versions(setFromCollection(versions))

		for (name in exportedNames)
		{
			assert(name.isString)
			val trueName = createAtomWithProperties(name, module)
			module.introduceNewName(trueName)
			module.addImportedName(trueName)
		}

		for (moduleImport in importedModules)
		{
			val ref: ResolvedModuleName
			try
			{
				ref = resolver.resolve(
					moduleName.asSibling(
						moduleImport.moduleName.asNativeString()))
			}
			catch (e: UnresolvedDependencyException)
			{
				assert(false) { "This never happens" }
				throw RuntimeException(e)
			}

			val availRef = stringFrom(ref.qualifiedName)
			if (!runtime.includesModuleNamed(availRef))
			{
				return ("module \"" + ref.qualifiedName
						+ "\" to be loaded already")
			}

			val mod = runtime.moduleAt(availRef)
			val reqVersions = moduleImport.acceptableVersions
			if (reqVersions.setSize() > 0)
			{
				val modVersions = mod.versions()
				if (!modVersions.setIntersects(reqVersions))
				{
					return (
						"version compatibility; module "
						+ "\"${ref.localName}\" guarantees versions "
						+ "$modVersions but the current module requires "
						+ "$reqVersions")
				}
			}
			module.addAncestors(mod.allAncestors())

			// Figure out which strings to make available.
			var stringsToImport: A_Set
			val importedNamesMultimap = mod.importedNames()
			if (moduleImport.wildcard)
			{
				val renameSourceNames =
					moduleImport.renames.valuesAsTuple().asSet()
				stringsToImport = importedNamesMultimap.keysAsSet()
				stringsToImport = stringsToImport.setMinusCanDestroy(
					renameSourceNames, true)
				stringsToImport = stringsToImport.setUnionCanDestroy(
					moduleImport.names, true)
				stringsToImport = stringsToImport.setMinusCanDestroy(
					moduleImport.excludes, true)
			}
			else
			{
				stringsToImport = moduleImport.names
			}

			// Look up the strings to get existing atoms.  Don't complain
			// about ambiguity, just export all that match.
			var atomsToImport = emptySet()
			for (string in stringsToImport)
			{
				if (!importedNamesMultimap.hasKey(string))
				{
					return (
						"module \"${ref.qualifiedName}\" to export $string")
				}
				atomsToImport = atomsToImport.setUnionCanDestroy(
					importedNamesMultimap.mapAt(string), true)
			}

			// Perform renames.
			for (entry in moduleImport.renames.mapIterable())
			{
				val newString = entry.key()
				val oldString = entry.value()
				// Find the old atom.
				if (!importedNamesMultimap.hasKey(oldString))
				{
					return (
						"module \"${ref.qualifiedName}\" to export "
						+ "$oldString for renaming to $newString")
				}
				val oldCandidates = importedNamesMultimap.mapAt(oldString)
				if (oldCandidates.setSize() != 1)
				{
					return (
						"module \"${ref.qualifiedName}\" to export a "
						+ "unique name $oldString for renaming to $newString")
				}
				val oldAtom = oldCandidates.iterator().next()
				// Find or create the new atom.
				val newAtom: A_Atom
				val newNames = module.newNames()
				if (newNames.hasKey(newString))
				{
					// Use it.  It must have been declared in the
					// "Names" clause.
					newAtom = module.newNames().mapAt(newString)
				}
				else
				{
					// Create it.
					newAtom = createAtomWithProperties(newString, module)
					module.introduceNewName(newAtom)
				}
				// Now tie the bundles together.
				assert(newAtom.bundleOrNil().equalsNil())
				val newBundle: A_Bundle
				try
				{
					val oldBundle = oldAtom.bundleOrCreate()
					val method = oldBundle.bundleMethod()
					newBundle = newBundle(
						newAtom, method, MessageSplitter(newString))
				}
				catch (e: MalformedMessageException)
				{
					return (
						"well-formed signature for $newString, a rename of "
						+ "$oldString from \"${ref.qualifiedName}\"")
				}

				newAtom.setAtomProperty(
					MESSAGE_BUNDLE_KEY.atom, newBundle)
				atomsToImport = atomsToImport.setWithElementCanDestroy(
					newAtom, true)
			}

			// Actually make the atoms available in this module.
			if (moduleImport.isExtension)
			{
				module.addImportedNames(atomsToImport)
			}
			else
			{
				module.addPrivateNames(atomsToImport)
			}
		}

		for (name in entryPoints)
		{
			assert(name.isString)
			try
			{
				val trueNames = module.trueNamesForStringName(name)
				val size = trueNames.setSize()
				val trueName: AvailObject
				when (size)
				{
					0 ->
					{
						trueName = createAtomWithProperties(name, module)
						module.addPrivateName(trueName)
					}
					1 ->
					{
						// Just validate the name.
						MessageSplitter(name)
						trueName = trueNames.iterator().next()
					}
					else -> return (
						"entry point \"${name.asNativeString()}\" to be "
						+ "unambiguous")
				}
				module.addEntryPoint(name, trueName)
			}
			catch (e: MalformedMessageException)
			{
				return (
					"entry point \"${name.asNativeString()}\" to be a "
					+ "valid name")
			}

		}

		return null
	}
}
