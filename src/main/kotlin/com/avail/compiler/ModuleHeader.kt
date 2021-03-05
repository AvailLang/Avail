/*
 * ModuleHeader.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
import com.avail.builder.ResolvedModuleName
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.module.A_Module.Companion.applyModuleHeader
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tokens.LiteralTokenDescriptor
import com.avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import com.avail.descriptor.tokens.TokenDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor.Companion.toList
import com.avail.interpreter.execution.AvailLoader
import com.avail.serialization.Deserializer
import com.avail.serialization.MalformedSerialStreamException
import com.avail.serialization.Serializer

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
 *   The [resolved&#32;name][ResolvedModuleName] of the module.
 */
class ModuleHeader constructor(val moduleName: ResolvedModuleName)
{
	/**
	 * The versions for which the module undergoing compilation guarantees
	 * support.
	 */
	val versions = mutableListOf<A_String>()

	/**
	 * The [module&#32;imports][ModuleImport] imported by the module undergoing
	 * compilation.  This includes both modules being extended and modules being
	 * simply used.
	 */
	val importedModules = mutableListOf<ModuleImport>()

	/**
	 * The [names][StringDescriptor] defined and exported by the
	 * [module][ModuleDescriptor] undergoing compilation.
	 */
	val exportedNames = mutableSetOf<A_String>()

	/**
	 * The [names][StringDescriptor] of [methods][MethodDescriptor] that are
	 * [module][ModuleDescriptor] entry points.
	 */
	val entryPoints = mutableListOf<A_String>()

	/**
	 * The [pragma&#32;tokens][TokenDescriptor], which are always string
	 * [literals][LiteralTokenDescriptor].
	 */
	val pragmas = mutableListOf<A_Token>()

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
	val importedModuleNames: List<String> get () =
		importedModules.map { it.moduleName.asNativeString() }

	/**
	 * A [List] of [String]s which name entry points defined in this module
	 * header.
	 */
	val entryPointNames: List<String> get () =
		entryPoints.map { it.asNativeString() }

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
		serializer.serialize(tupleFromList(exportedNames.toList()))
		serializer.serialize(tupleFromList(entryPoints))
		serializer.serialize(tupleFromList(pragmas))
		serializer.serialize(fromInt(startOfBodyPosition))
		serializer.serialize(fromInt(startOfBodyLineNumber))
	}

	/**
	 * The information about the imported modules as a [tuple][A_Tuple] of
	 * tuples suitable for serialization.
	 */
	private val tuplesForSerializingModuleImports: A_Tuple get () =
		tupleFromList(importedModules.map { it.tupleForSerialization })

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
			serializedTuple: A_Tuple): List<ModuleImport> =
		serializedTuple.map { ModuleImport.fromSerializedTuple(it) }

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
	 * Update the given [AvailLoader]'s module to correspond with information
	 * that has been accumulated in this [ModuleHeader].
	 *
	 * @param loader
	 *   The current [AvailLoader] for this [A_Module].
	 * @return
	 *   An error message [String] if there was a problem, or `null` if no
	 *   problems were encountered.
	 */
	fun applyToModule(loader: AvailLoader): String?
	{
		return loader.module().applyModuleHeader(loader, this)
	}
}
