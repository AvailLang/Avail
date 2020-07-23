/*
 * BootstrapGenerator.kt
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
package com.avail.tools.bootstrap

import com.avail.AvailRuntime.Companion.specialObjects
import com.avail.AvailRuntimeConfiguration.activeVersions
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import com.avail.descriptor.types.TypeDescriptor
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.AvailErrorCode.Companion.byNumericCode
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Companion.byPrimitiveNumberOrNull
import com.avail.interpreter.Primitive.Companion.maxPrimitiveNumber
import com.avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import com.avail.interpreter.primitive.general.P_EmergencyExit
import com.avail.interpreter.primitive.methods.P_AddSemanticRestriction
import com.avail.interpreter.primitive.sets.P_TupleToSet
import com.avail.interpreter.primitive.types.P_CreateEnumeration
import com.avail.tools.bootstrap.Resources.errorCodeCommentKey
import com.avail.tools.bootstrap.Resources.errorCodeKey
import com.avail.tools.bootstrap.Resources.errorCodesBaseName
import com.avail.tools.bootstrap.Resources.generatedPackageName
import com.avail.tools.bootstrap.Resources.preambleBaseName
import com.avail.tools.bootstrap.Resources.primitiveCommentKey
import com.avail.tools.bootstrap.Resources.primitiveParameterNameKey
import com.avail.tools.bootstrap.Resources.primitivesBaseName
import com.avail.tools.bootstrap.Resources.sourceBaseName
import com.avail.tools.bootstrap.Resources.specialObjectCommentKey
import com.avail.tools.bootstrap.Resources.specialObjectKey
import com.avail.tools.bootstrap.Resources.specialObjectTypeKey
import com.avail.tools.bootstrap.Resources.specialObjectsBaseName
import com.avail.tools.bootstrap.Resources.stringify
import com.avail.tools.bootstrap.Resources.Key.*
import com.avail.tools.bootstrap.Resources.errorCodeExceptionKey
import com.avail.utility.UTF8ResourceBundleControl
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.text.MessageFormat
import java.util.ArrayList
import java.util.Comparator
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.ResourceBundle
import java.util.StringTokenizer

/**
 * Generate the Avail system [modules][ModuleDescriptor] that bind the
 * infallible and fallible [primitives][Primitive].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property locale
 *   The target [locale][Locale].
 *
 * @constructor
 * Construct a new `BootstrapGenerator`.
 *
 * @param locale
 *   The target [locale][Locale].
 */
class BootstrapGenerator constructor(private val locale: Locale)
{
	companion object
	{
		/** The Avail special objects.  */
		private val specialObjects: List<AvailObject> = specialObjects()

		/**
		 * A [map][Map] from the special objects to their indices.
		 */
		private val specialObjectIndexMap = mutableMapOf<A_BasicObject, Int>()

		/**
		 * Answer a textual representation of the specified version [list][List]
		 * that is satisfactory for use in an Avail [module][ModuleDescriptor]
		 * header's `check=vm` `Pragma`.
		 *
		 * @param versions
		 *   The versions.
		 * @return
		 *   The version string.
		 */
		private fun vmVersionString(versions: List<String>): String
		{
			val builder = StringBuilder()
			for (version in versions)
			{
				builder.append(version)
				builder.append(",")
			}
			val versionString = builder.toString()
			return versionString.substring(0, versionString.length - 1)
		}

		/**
		 * Answer a textual representation of the specified version [list][List]
		 * that is satisfactory for use in an Avail [module][ModuleDescriptor]
		 * header's `Versions` section.
		 *
		 * @param versions
		 *   The versions.
		 * @return
		 *   The version string.
		 */
		private fun moduleVersionString(versions: List<String>): String
		{
			val builder = StringBuilder()
			for (version in versions)
			{
				builder.append("\n\t\"")
				builder.append(version)
				builder.append("\",")
			}
			val versionString = builder.toString()
			return versionString.substring(0, versionString.length - 1)
		}

		/**
		 * Answer the selected [primitives][Primitive], the non-private,
		 * non-bootstrap ones with the specified fallibility.
		 *
		 * @param fallible
		 *   `true` if the fallible primitives should be answered, `false` if
		 *   the infallible primitives should be answered, `null` if all
		 *   primitives should be answered.
		 * @return
		 *   The selected primitives.
		 */
		private fun primitives(fallible: Boolean?): List<Primitive>
		{
			val primitives: MutableList<Primitive> = ArrayList()
			for (i in 1 .. maxPrimitiveNumber())
			{
				val primitive = byPrimitiveNumberOrNull(i)
				if (primitive !== null)
				{
					if (!primitive.hasFlag(Primitive.Flag.Private)
					    && !primitive.hasFlag(Primitive.Flag.Bootstrap)
					    && (fallible === null
					        || primitive.hasFlag(
								Primitive.Flag.CannotFail) == !fallible))
					{
						primitives.add(primitive)
					}
				}
			}
			return primitives
		}

		/**
		 * Answer the [primitive error codes][AvailErrorCode] for which Avail
		 * methods should be generated.
		 *
		 * @return
		 *   The relevant primitive error codes.
		 */
		private fun errorCodes(): List<AvailErrorCode> =
			AvailErrorCode.values().filter { it.nativeCode() > 0 }

		/**
		 * Generate all bootstrap [modules][ModuleDescriptor].
		 *
		 * @param args
		 *   The command-line arguments. The first argument is a comma-separated
		 *   list of language codes that broadly specify the [locales][Locale]
		 *   for which modules should be generated. The second argument is a
		 *   comma-separated list of Avail system versions.
		 * @throws Exception
		 *   If anything should go wrong.
		 */
		@Throws(Exception::class)
		@JvmStatic
		fun main(args: Array<String>)
		{
			val languages: MutableList<String> = ArrayList()
			if (args.isEmpty())
			{
				languages.add(System.getProperty("user.language"))
			}
			else
			{
				val tokenizer = StringTokenizer(args[0], ",")
				while (tokenizer.hasMoreTokens())
				{
					languages.add(tokenizer.nextToken())
				}
			}
			val versions = mutableListOf<String>()
			if (args.size < 2)
			{
				activeVersions().mapTo(versions) { it.asNativeString() }
			}
			else
			{
				val tokenizer = StringTokenizer(args[1], ",")
				while (tokenizer.hasMoreTokens())
				{
					versions.add(tokenizer.nextToken())
				}
			}
			for (language in languages)
			{
				val generator = BootstrapGenerator(Locale(language))
				generator.generate(versions)
			}
		}

		/* Capture the special objects. */
		init
		{
			for (i in specialObjects.indices)
			{
				val specialObject = specialObjects[i]
				if (!specialObject.equalsNil())
				{
					specialObjectIndexMap[specialObject] = i
				}
			}
		}
	}

	/**
	 * The [resource bundle][ResourceBundle] that contains file preambleBaseName
	 * information.
	 */
	val preamble: ResourceBundle

	/**
	 * The [resource bundle][ResourceBundle] that contains the Avail names of
	 * the special objects.
	 */
	private val specialObjectBundle: ResourceBundle

	/**
	 * The [resource bundle][ResourceBundle] that contains the Avail names of
	 * the [primitives][Primitive].
	 */
	private val primitiveBundle: ResourceBundle

	/**
	 * The [resource bundle][ResourceBundle] that contains the Avail names of
	 * the [primitive error codes][AvailErrorCode].
	 */
	private val errorCodeBundle: ResourceBundle

	/**
	 * Answer the name of the specified error code.
	 *
	 * @param numericCode
	 *   The error code.
	 * @return
	 *   The localized name of the error code.
	 */
	private fun errorCodeName(numericCode: A_Number): String
	{
		val code = byNumericCode(
			numericCode.extractInt()) ?: error(String.format(
			"no %s for %s", AvailErrorCode::class.java.simpleName, numericCode))
		return errorCodeBundle.getString(errorCodeKey(code))
	}

	/**
	 * Answer the name of the exception associated with the specified error
	 * code.
	 *
	 * @param numericCode
	 *   The error code.
	 * @return
	 *   The localized name of the error code.
	 */
	private fun exceptionName(numericCode: A_Number): String
	{
		val code = byNumericCode(
			numericCode.extractInt()) ?: error(String.format(
			"no %s for %s", AvailErrorCode::class.java.simpleName, numericCode))
		return errorCodeBundle.getString(errorCodeExceptionKey(code))
	}

	/**
	 * Answer the correct [file name][File] for the [module][ModuleDescriptor]
	 * specified by the [key][Resources.Key].
	 *
	 * @param key
	 *   The module name key.
	 * @return
	 *   The file name.
	 */
	private fun moduleFileName(key: Resources.Key): File
	{
		return File(String.format(
			"%s/%s/%s/%s.avail/%s.avail",
			sourceBaseName,
			generatedPackageName.replace('.', '/'),
			locale.language,
			preamble.getString(representativeModuleName.name),
			preamble.getString(key.name)))
	}

	/**
	 * Generate the preamble for the pragma-containing module.
	 *
	 * @param versions
	 *   The [list][List] of version strings supported by the module.
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generateOriginModulePreamble(
		versions: List<String>,
		writer: PrintWriter)
	{
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name),
			preamble.getString(originModuleName.name),
			Date()))
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name),
			BootstrapGenerator::class.java.name,
			Date()))
		writer.println(MessageFormat.format(
			preamble.getString(originModuleHeader.name),
			preamble.getString(originModuleName.name),
			moduleVersionString(versions),
			vmVersionString(versions),
			preamble.getString(bootstrapDefiningMethod.name),
			preamble.getString(bootstrapSpecialObject.name),
			preamble.getString(
				bootstrapDefineSpecialObjectMacro.name),
			preamble.getString(bootstrapMacroNames.name),
			preamble.getString(bootstrapMacros.name)))
	}

	/**
	 * A [map][Map] from localized names to Avail special objects.
	 */
	private val specialObjectsByName = mutableMapOf<String, AvailObject>()

	/**
	 * A [map][Map] from Avail special objects to localized names.
	 */
	private val namesBySpecialObject = mutableMapOf<A_BasicObject, String>()

	/**
	 * Answer the name of the specified special object.
	 *
	 * @param specialObject
	 *   A special object.
	 * @return
	 *   The localized name of the special object.
	 */
	private fun specialObjectName(specialObject: A_BasicObject): String
	{
		return namesBySpecialObject[specialObject]
		       ?: error("no special object for $specialObject")
	}

	/**
	 * Answer a textual representation of the special objects that is
	 * satisfactory for use in an Avail [module][ModuleDescriptor] header.
	 *
	 * @return
	 *   The "Names" string.
	 */
	private fun specialObjectsNamesString(): String
	{
		val names: MutableList<String> =
			specialObjectsByName.keys.toMutableList()
		names.sort()
		val builder = StringBuilder()
		for (name in names)
		{
			val specialObject: A_BasicObject? = specialObjectsByName[name]
			builder.append("\n\t")
			builder.append(String.format(
				"/* %3d */", specialObjectIndexMap[specialObject]))
			builder.append(" \"")
			builder.append(name)
			builder.append("\",")
		}
		val namesString = builder.toString()
		return namesString.substring(0, namesString.length - 1)
	}

	/**
	 * Generate the preamble for the special object linking module.
	 *
	 * @param versions
	 *   The [list][List] of version strings supported by the module.
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generateSpecialObjectModulePreamble(
		versions: List<String>,
		writer: PrintWriter)
	{
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name),
			preamble.getString(specialObjectsModuleName.name),
			Date()))
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name),
			BootstrapGenerator::class.java.name,
			Date()))
		writer.println(MessageFormat.format(
			preamble.getString(generalModuleHeader.name),
			preamble.getString(specialObjectsModuleName.name),
			moduleVersionString(versions), String.format(
			"%n\t\"%s\"",
			preamble.getString(originModuleName.name)),
			"",
			specialObjectsNamesString()))
	}

	/**
	 * Generate the body of the special object linking
	 * [module][ModuleDescriptor].
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generateSpecialObjectModuleBody(writer: PrintWriter)
	{
		// Emit the special object methods.
		for (i in specialObjects.indices)
		{
			if (!specialObjects[i].equalsNil())
			{
				val notAlphaKey = specialObjectKey(i)
				if (!specialObjectBundle.containsKey(notAlphaKey)
				    || specialObjectBundle.getString(notAlphaKey).isEmpty())
				{
					System.err.println("missing key/value: $notAlphaKey")
					continue
				}
				val methodName = specialObjectBundle.getString(notAlphaKey)
				val typeKey = specialObjectTypeKey(i)
				val commentKey = specialObjectCommentKey(i)
				if (specialObjectBundle.containsKey(commentKey))
				{
					val commentTemplate =
						specialObjectBundle.getString(commentKey)
					var type = specialObjectBundle.getString(typeKey)
					if (type.isEmpty())
					{
						type = methodName
					}
					writer.print(MessageFormat.format(
						commentTemplate, methodName, type))
				}
				val use = MessageFormat.format(
					preamble.getString(specialObjectUse.name), i)
				writer.println(MessageFormat.format(
					preamble.getString(
						definingSpecialObjectUse.name),
					stringify(methodName),
					use))
				writer.println()
			}
		}
	}

	/**
	 * A [map][Map] from localized names to Avail [primitives][Primitive].
	 */
	private val primitiveNameMap = mutableMapOf<String, MutableSet<Primitive>>()

	/**
	 * Answer a textual representation of the specified [primitive][Primitive]
	 * names [list][List] that is satisfactory for use in an Avail
	 * [module][ModuleDescriptor] header.
	 *
	 * @param primitives
	 *   The primitives.
	 * @return
	 *   The "Names" string.
	 */
	private fun primitivesNamesString(primitives: List<Primitive>): String
	{
		val wanted: Set<Primitive> = HashSet(primitives)
		val names = primitiveNameMap.keys.toMutableList()
		names.sort()
		val builder = StringBuilder()
		for (name in names)
		{
			val set = primitiveNameMap[name]!!.toMutableSet()
			set.retainAll(wanted)
			if (set.isNotEmpty())
			{
				builder.append("\n\t\"")
				builder.append(name)
				builder.append("\",")
			}
		}
		val namesString = builder.toString()
		return namesString.substring(0, namesString.length - 1)
	}

	/**
	 * Generate the preamble for the specified [primitive][Primitive] module.
	 *
	 * @param fallible
	 *   `true` to indicate the fallible primitives module, `false` to indicate
	 *   the infallible primitives module, `null` to indicate the introductory
	 *   primitives module.
	 * @param versions
	 *   The [list][List] of version strings supported by the module.
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generatePrimitiveModulePreamble(
		fallible: Boolean?,
		versions: List<String>,
		writer: PrintWriter)
	{
		val key: Resources.Key =
			if (fallible === null)
			{
				primitivesModuleName
			}
			else
			{
				if (fallible) falliblePrimitivesModuleName
				else infalliblePrimitivesModuleName
			}
		// Write the copyright.
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name),
			preamble.getString(key.name),
			Date()))
		// Write the generated module notice.
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name),
			BootstrapGenerator::class.java.name,
			Date()))
		// Write the header.
		val uses = StringBuilder()
		uses.append("\n\t\"")
		uses.append(preamble.getString(originModuleName.name))
		uses.append('"')
		if (fallible !== null)
		{
			if (java.lang.Boolean.TRUE == fallible)
			{
				uses.append(",\n\t\"")
				uses.append(preamble.getString(
					errorCodesModuleName.name))
				uses.append("\"")
			}
			uses.append(",\n\t\"")
			uses.append(preamble.getString(
				specialObjectsModuleName.name))
			uses.append("\",\n\t\"")
			uses.append(preamble.getString(
				primitivesModuleName.name))
			uses.append("\" =\n\t(")
			uses.append(primitivesNamesString(
				primitives(fallible)).replace("\t", "\t\t"))
			uses.append("\n\t)")
		}
		val names = StringBuilder()
		if (fallible === null)
		{
			names.append(primitivesNamesString(primitives(null)))
		}
		else if (java.lang.Boolean.TRUE == fallible)
		{
			names.append("\n\t")
			names.append(stringify(preamble.getString(
				primitiveFailureFunctionGetterMethod.name)))
			names.append(",\n\t")
			names.append(stringify(preamble.getString(
				primitiveFailureFunctionSetterMethod.name)))
		}
		writer.println(MessageFormat.format(
			preamble.getString(generalModuleHeader.name),
			preamble.getString(key.name),
			moduleVersionString(versions),
			"",
			uses.toString(),
			names.toString()))
	}

	/**
	 * Answer the method parameter declarations for the specified
	 * [primitive][Primitive].
	 *
	 * @param primitive
	 *   A primitive.
	 * @param forSemanticRestriction
	 *   `true` if the parameters should be shifted out one type level for use
	 *   by a semantic restriction, `false` otherwise.
	 * @return
	 *   The textual representation of the primitive method's parameters
	 *   (indent=1).
	 */
	private fun primitiveMethodParameterDeclarations(
		primitive: Primitive,
		forSemanticRestriction: Boolean): String
	{
		val functionType = primitive.blockTypeRestriction()
		val parameterTypes = functionType.argsTupleType()
		val parameterCount = parameterTypes.sizeRange()
		assert(parameterCount.lowerBound().equals(
			parameterCount.upperBound())) {
			String.format(
				"Expected %s to have a fixed parameter count",
				primitive.javaClass.simpleName)
		}
		val builder = StringBuilder()
		var i = 1
		val end = parameterCount.lowerBound().extractInt()
		while (i <= end)
		{
			val argNameKey = primitiveParameterNameKey(primitive, i)
			val argName: String
			argName = if (primitiveBundle.containsKey(argNameKey))
			{
				val localized = primitiveBundle.getString(argNameKey)
				if (localized.isNotEmpty()) localized
				else preamble.getString(parameterPrefix.name) + i
			}
			else
			{
				preamble.getString(parameterPrefix.name) + i
			}
			val type = parameterTypes.typeAtIndex(i)
			val paramType =
				if (forSemanticRestriction) instanceMeta(type)
				else type
			val typeName = specialObjectName(paramType)
			builder.append('\t')
			builder.append(argName)
			builder.append(" : ")
			builder.append(typeName)
			if (i != end)
			{
				builder.append(',')
			}
			builder.append('\n')
			i++
		}
		return builder.toString()
	}

	/**
	 * Answer the method statements for the specified [primitive][Primitive].
	 *
	 * @param primitive
	 *   A primitive.
	 * @return
	 *   The textual representation of the primitive's statements (indent=1).
	 */
	private fun primitiveMethodStatements(
		primitive: Primitive): String
	{
		val builder = StringBuilder()
		builder.append('\t')
		builder.append(preamble.getString(primitiveKeyword.name))
		builder.append(' ')
		builder.append(primitive.fieldName())
		if (!primitive.hasFlag(Primitive.Flag.CannotFail))
		{
			builder.append(" (")
			builder.append(
				preamble.getString(
					primitiveFailureVariableName.name))
			builder.append(" : ")
			val varType: A_Type = primitive.failureVariableType
			if (varType.isEnumeration)
			{
				if (varType.isSubtypeOf(naturalNumbers))
				{
					builder.append("{")
					val instances = varType.instances()
					val codes: MutableList<A_Number> = ArrayList()
					for (instance in instances)
					{
						codes.add(instance)
					}
					codes.sortWith(Comparator { o1: A_Number, o2: A_Number ->
						o1.extractInt().compareTo(o2.extractInt())
					})
					for (code in codes)
					{
						val errorCodeName = errorCodeName(code)
						builder.append("\n\t\t")
						builder.append(errorCodeName)
						builder.append(',')
					}
					// Discard the trailing comma.
					builder.setLength(builder.length - 1)
					builder.append("}ᵀ")
				}
				else
				{
					builder.append(specialObjectName(
						TypeDescriptor.Types.ANY.o
					))
				}
			}
			else
			{
				builder.append(specialObjectName(varType))
			}
			builder.append(')')
		}
		builder.append(";\n")
		if (!primitive.hasFlag(Primitive.Flag.CannotFail))
		{
			builder.append('\t')
			if (primitive.hasFlag(Primitive.Flag.CatchException))
			{
				val argNameKey = primitiveParameterNameKey(
					primitive, 1)
				val argName: String
				argName = if (primitiveBundle.containsKey(argNameKey))
				{
					val localized = primitiveBundle.getString(argNameKey)
					if (localized.isNotEmpty())
					{
						localized
					}
					else
					{
						preamble.getString(
							parameterPrefix.name) + 1
					}
				}
				else
				{
					preamble.getString(parameterPrefix.name) + 1
				}
				builder.append(MessageFormat.format(
					preamble.getString(
						invokePrimitiveFailureFunctionMethodUse.name),
					argName,
					namesBySpecialObject[emptyTuple]))
			}
			else
			{
				builder.append(MessageFormat.format(
					preamble.getString(
						invokePrimitiveFailureFunctionMethodUse.name),
					preamble.getString(
						primitiveFailureFunctionName.name),
					preamble.getString(
						primitiveFailureVariableName.name)))
			}
			builder.append("\n")
		}
		return builder.toString()
	}

	/**
	 * Answer a block that contains the specified (already formatted) parameter
	 * declarations and (already formatted) statements.
	 *
	 * @param declarations
	 *   The parameter declarations.
	 * @param statements
	 *   The block's statements.
	 * @param returnType
	 *   The return type, or `null` if the return type should not be explicit.
	 * @return
	 *   A textual representation of the block (indent=0).
	 */
	private fun block(
		declarations: String,
		statements: String,
		returnType: A_BasicObject?): String
	{
		val builder = StringBuilder()
		builder.append("\n[\n")
		builder.append(declarations)
		if (declarations.isNotEmpty())
		{
			builder.append("|\n")
		}
		builder.append(statements)
		builder.append(']')
		if (returnType !== null)
		{
			builder.append(" : ")
			builder.append(specialObjectName(returnType))
		}
		return builder.toString()
	}

	/**
	 * Answer a comment for the specified [primitive][Primitive].
	 *
	 * @param primitive
	 *   A primitive.
	 * @return
	 *   A textual representation of the comment (indent=0).
	 */
	private fun primitiveComment(
		primitive: Primitive): String
	{
		val builder = StringBuilder()
		val commentKey = primitiveCommentKey(primitive)
		if (primitiveBundle.containsKey(commentKey))
		{
			// Compute the number of template arguments.
			val primitiveArgCount = primitive.argCount
			val templateArgCount = 2 + (primitiveArgCount shl 1) +
               when
               {
                   primitive.hasFlag(Primitive.Flag.CannotFail) -> 0
                   primitive.failureVariableType.isEnumeration ->
	                   primitive.failureVariableType
		                   .instanceCount().extractInt()
                   else -> 1
               }
			val formatArgs = arrayOfNulls<Any>(templateArgCount)
			// The method name goes into the first slot…
			formatArgs[0] = primitiveBundle.getString(
				primitive.javaClass.simpleName)
			// …then come the parameter names, followed by their types…
			val paramsType = primitive.blockTypeRestriction().argsTupleType()
			for (i in 1 .. primitiveArgCount)
			{
				val argNameKey = primitiveParameterNameKey(primitive, i)
				val argName: String
				argName = if (primitiveBundle.containsKey(argNameKey))
				{
					val localized = primitiveBundle.getString(argNameKey)
					if (localized.isNotEmpty())
					{
						localized
					}
					else
					{
						preamble.getString(parameterPrefix.name) + i
					}
				}
				else
				{
					preamble.getString(parameterPrefix.name) + i
				}
				formatArgs[i] = argName
				formatArgs[i + primitiveArgCount] = paramsType.typeAtIndex(i)
			}
			// …then the return type…
			formatArgs[(primitiveArgCount shl 1) + 1] =
				primitive.blockTypeRestriction().returnType()
			// …then the exceptions.
			if (!primitive.hasFlag(Primitive.Flag.CannotFail))
			{
				var raiseIndex = (primitiveArgCount shl 1) + 2
				val varType: A_Type = primitive.failureVariableType
				if (varType.isEnumeration)
				{
					if (varType.isSubtypeOf(naturalNumbers))
					{
						val instances = varType.instances()
						val codes: MutableList<A_Number> = ArrayList()
						for (instance in instances)
						{
							codes.add(instance)
						}
						codes.sortWith(Comparator { o1: A_Number, o2: A_Number ->
							o1.extractInt().compareTo(o2.extractInt())
						})
						for (code in codes)
						{
							formatArgs[raiseIndex++] = exceptionName(code)
						}
					}
					else
					{
						formatArgs[raiseIndex] =
							specialObjectName(TypeDescriptor.Types.ANY.o)
					}
				}
				else
				{
					formatArgs[raiseIndex] = specialObjectName(varType)
				}
			}
			// Check if the string uses single-quotes incorrectly.  They should
			// only be used for quoting brace-brackets, and should be doubled
			// for all other uses.
			val messagePattern = primitiveBundle.getString(commentKey)
			var inQuotes = false
			var sawBraces = false
			var isEmpty = true
			for (element in messagePattern)
			{
				when (element)
				{
					'\'' ->
					{
						if (inQuotes)
						{
							if (!sawBraces && !isEmpty)
							{
								System.err.format(
									"Malformed primitive comment (%s) – "
									+ "Single-quoted section was not empty "
									+ "but did not contain any brace "
									+ "brackets ('{' or '}').%n",
									commentKey)
							}
						}
						inQuotes = !inQuotes
						sawBraces = false
						isEmpty = true
					}
					'{', '}' ->
					{
						sawBraces = true
						isEmpty = false
					}
					else -> isEmpty = false
				}
			}
			if (inQuotes)
			{
				System.err.format(
					"Malformed primitive comment (%s) – contains unclosed "
						+ "single-quote character%n",
					commentKey)
			}
			builder.append(MessageFormat.format(
				messagePattern,
				*formatArgs))
		}
		return builder.toString()
	}

	/**
	 * Generate a method from the specified name and block.
	 *
	 * @param name
	 *   The (already localized) method name.
	 * @param block
	 *   The textual block (indent=0).
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generateMethod(
		name: String,
		block: String,
		writer: PrintWriter)
	{
		writer.print(MessageFormat.format(
			preamble.getString(definingMethodUse.name),
			stringify(name),
			block))
		writer.println(';')
		writer.println()
	}

	/**
	 * Generate the bootstrap [primitive][Primitive] tuple-to-set converter.
	 * This will be used to provide precise failure variable types.
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generatePrimitiveToSetMethod(writer: PrintWriter)
	{
		val primitive: Primitive = P_TupleToSet
		val statements = StringBuilder()
		statements.append('\t')
		statements.append(preamble.getString(primitiveKeyword.name))
		statements.append(' ')
		statements.append(primitive.fieldName())
		statements.append(";\n")
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			statements.toString(),
			primitive.blockTypeRestriction().returnType())
		generateMethod("{«_‡,»}", block, writer)
	}

	/**
	 * Generate the bootstrap [primitive][Primitive] enumeration method. This
	 * will be used to provide precise failure variable types.
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generatePrimitiveEnumMethod(writer: PrintWriter)
	{
		val primitive: Primitive = P_CreateEnumeration
		val statements = StringBuilder()
		statements.append('\t')
		statements.append(preamble.getString(
			primitiveKeyword.name))
		statements.append(' ')
		statements.append(primitive.fieldName())
		statements.append(";\n")
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			statements.toString(),
			primitive.blockTypeRestriction().returnType())
		generateMethod("_ᵀ", block, writer)
	}

	/**
	 * Generate the bootstrap [primitive][Primitive] failure method. This will
	 * be invoked if any primitive fails during the compilation of the bootstrap
	 * modules.
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generatePrimitiveFailureMethod(writer: PrintWriter)
	{
		val primitive: Primitive = P_EmergencyExit
		val statements = StringBuilder()
		statements.append('\t')
		statements.append(preamble.getString(
			primitiveKeyword.name))
		statements.append(' ')
		statements.append(primitive.fieldName())
		statements.append(";\n")
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			statements.toString(),
			primitive.blockTypeRestriction().returnType())
		generateMethod(
			preamble.getString(primitiveFailureMethod.name),
			block,
			writer)
	}

	/**
	 * Generate the [primitive][Primitive] failure function.
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generatePrimitiveFailureFunction(writer: PrintWriter)
	{
		val functionType: A_BasicObject =
			functionType(tuple(naturalNumbers), bottom())
		writer.print(
			preamble.getString(primitiveFailureFunctionName.name))
		writer.print(" : ")
		writer.print(specialObjectName(functionType))
		writer.println(" :=")
		writer.println("\t[")
		writer.print("\t\t")
		writer.print(preamble.getString(parameterPrefix.name))
		writer.print(1)
		writer.print(" : ")
		writer.println(specialObjectName(TypeDescriptor.Types.ANY.o))
		writer.println("\t|")
		writer.print("\t\t")
		writer.print(MessageFormat.format(
			preamble.getString(primitiveFailureMethodUse.name),
			preamble.getString(parameterPrefix.name) + 1))
		writer.println("")
		writer.print("\t] : ")
		writer.print(specialObjectName(bottom()))
		writer.println(';')
		writer.println()
	}

	/**
	 * Generate the [primitive][Primitive] failure function getter.
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generatePrimitiveFailureFunctionGetter(
		writer: PrintWriter)
	{
		val statements = StringBuilder()
		statements.append('\t')
		statements.append(
			preamble.getString(primitiveFailureFunctionName.name))
		statements.append("\n")
		val block = block(
			"",
			statements.toString(),
			functionType(tuple(naturalNumbers), bottom()))
		generateMethod(
			preamble.getString(
				primitiveFailureFunctionGetterMethod.name),
			block,
			writer)
	}

	/**
	 * Generate the [primitive][Primitive] failure function setter.
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generatePrimitiveFailureFunctionSetter(writer: PrintWriter)
	{
		val argName = preamble.getString(parameterPrefix.name) + 1
		val declarations = StringBuilder()
		declarations.append('\t')
		declarations.append(argName)
		declarations.append(" : ")
		val functionType: A_BasicObject =
			functionType(tuple(naturalNumbers), bottom())
		declarations.append(specialObjectName(functionType))
		declarations.append('\n')
		val statements = StringBuilder()
		statements.append('\t')
		statements.append(
			preamble.getString(primitiveFailureFunctionName.name))
		statements.append(" := ")
		statements.append(argName)
		statements.append(";\n")
		val block = block(
			declarations.toString(),
			statements.toString(),
			TypeDescriptor.Types.TOP.o
		)
		generateMethod(
			preamble.getString(
				primitiveFailureFunctionSetterMethod.name),
			block,
			writer)
	}

	/**
	 * Generate the bootstrap function application method that the exported
	 * [primitives][Primitive] use to invoke the primitive failure function.
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generateInvokePrimitiveFailureFunctionMethod(
		writer: PrintWriter)
	{
		val primitive: Primitive = P_InvokeWithTuple
		val statements = StringBuilder()
		statements.append('\t')
		statements.append(preamble.getString(primitiveKeyword.name))
		statements.append(' ')
		statements.append(primitive.fieldName())
		statements.append(" (")
		statements.append(
			preamble.getString(primitiveFailureVariableName.name))
		statements.append(" : ")
		statements.append(specialObjectName(primitive.failureVariableType))
		statements.append(')')
		statements.append(";\n")
		statements.append('\t')
		statements.append(MessageFormat.format(
			preamble.getString(primitiveFailureMethodUse.name),
			preamble.getString(primitiveFailureVariableName.name)))
		statements.append("\n")
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			statements.toString(),
			TypeDescriptor.Types.TOP.o
		)
		generateMethod(
			preamble.getString(
				invokePrimitiveFailureFunctionMethod.name),
			block,
			writer)
	}

	/**
	 * Generate the bootstrap semantic restriction application method that the
	 * bootstrap code uses to provide type-safe usage of the bootstrap function
	 * application method. Also generate the actual application of the semantic
	 * restriction.
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generatePrivateSemanticRestrictionMethod(
		writer: PrintWriter)
	{
		val primitive: Primitive = P_AddSemanticRestriction
		var statements = StringBuilder()
		statements.append('\t')
		statements.append(preamble.getString(primitiveKeyword.name))
		statements.append(' ')
		statements.append(primitive.fieldName())
		statements.append(" (")
		statements.append(
			preamble.getString(primitiveFailureVariableName.name))
		statements.append(" : ")
		statements.append(
			specialObjectName(naturalNumbers))
		statements.append(')')
		statements.append(";\n")
		statements.append('\t')
		statements.append(MessageFormat.format(
			preamble.getString(primitiveFailureMethodUse.name),
			preamble.getString(
				primitiveFailureVariableName.name)))
		statements.append("\n")
		var block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			statements.toString(),
			TypeDescriptor.Types.TOP.o
		)
		generateMethod(
			preamble.getString(primitiveSemanticRestriction.name),
			block,
			writer)
		statements = StringBuilder()
		statements.append('\t')
		statements.append(specialObjectName(bottom()))
		statements.append("\n")
		block = block(
			primitiveMethodParameterDeclarations(
				P_InvokeWithTuple,
				true),
			statements.toString(),
			null)
		writer.append(MessageFormat.format(
			preamble.getString(
				primitiveSemanticRestrictionUse.name),
			stringify(preamble.getString(
				invokePrimitiveFailureFunctionMethod.name)),
			block))
		writer.println(";\n")
	}

	/**
	 * Generate a linkage method for the specified [primitive][Primitive].
	 *
	 * @param primitive
	 *   A primitive.
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generatePrimitiveMethod(
		primitive: Primitive,
		writer: PrintWriter)
	{
		val name = primitive.javaClass.simpleName
		if (!primitiveBundle.containsKey(name)
		    || primitiveBundle.getString(name).isEmpty())
		{
			System.err.println("missing key/value: $name")
			return
		}
		val comment = primitiveComment(primitive)
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			primitiveMethodStatements(primitive),
			primitive.blockTypeRestriction().returnType())
		writer.print(comment)
		generateMethod(primitiveBundle.getString(name), block, writer)
	}

	/**
	 * Generate the body of the specified [primitive][Primitive] module.
	 *
	 * @param fallible
	 *   `true` to indicate the fallible primitives module, `false` to indicate
	 *   the infallible primitives module.
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generatePrimitiveModuleBody(
		fallible: Boolean?,
		writer: PrintWriter)
	{
		// Generate the module variable that holds the primitive failure
		// function.
		if (java.lang.Boolean.TRUE == fallible)
		{
			generatePrimitiveToSetMethod(writer)
			generatePrimitiveEnumMethod(writer)
			generatePrimitiveFailureMethod(writer)
			generatePrimitiveFailureFunction(writer)
			generatePrimitiveFailureFunctionGetter(writer)
			generatePrimitiveFailureFunctionSetter(writer)
			generateInvokePrimitiveFailureFunctionMethod(writer)
			generatePrivateSemanticRestrictionMethod(writer)
		}

		// Generate the primitive methods.
		if (fallible !== null)
		{
			val primitives = primitives(fallible)
			for (primitive in primitives)
			{
				if (!primitive.hasFlag(Primitive.Flag.Private)
				    && !primitive.hasFlag(Primitive.Flag.Bootstrap))
				{
					generatePrimitiveMethod(primitive, writer)
				}
			}
		}
	}

	/**
	 * A [map][Map] from localized names to [primitive error
	 * codes][AvailErrorCode].
	 */
	private val errorCodesByName = mutableMapOf<String, AvailErrorCode>()

	/**
	 * Answer a textual representation of the
	 * [primitive error codes][AvailErrorCode] that is satisfactory for use in
	 * an Avail [module][ModuleDescriptor] header.
	 *
	 * @return
	 *   The "Names" string.
	 */
	private fun errorCodesNamesString(): String
	{
		val names = errorCodesByName.keys.toMutableList()
		names.sort()
		val builder = StringBuilder()
		for (name in names)
		{
			val code = errorCodesByName[name]
			builder.append("\n\t")
			builder.append(String.format("/* %3d */", code!!.nativeCode()))
			builder.append(" \"")
			builder.append(name)
			builder.append("\",")
		}
		val namesString = builder.toString()
		return namesString.substring(0, namesString.length - 1)
	}

	/**
	 * Generate the preamble for the error codes [module][ModuleDescriptor].
	 *
	 * @param versions
	 *   The [list][List] of version strings supported by the module.
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generateErrorCodesModulePreamble(
		versions: List<String>,
		writer: PrintWriter)
	{
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name),
			preamble.getString(errorCodesModuleName.name),
			Date()))
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name),
			BootstrapGenerator::class.java.name,
			Date()))
		val uses = StringBuilder()
		uses.append("\n\t\"")
		uses.append(preamble.getString(originModuleName.name))
		uses.append('"')
		writer.println(MessageFormat.format(
			preamble.getString(generalModuleHeader.name),
			preamble.getString(errorCodesModuleName.name),
			moduleVersionString(versions),
			"",
			uses.toString(),
			errorCodesNamesString()))
	}

	/**
	 * Generate the body for the error codes [module][ModuleDescriptor].
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generateErrorCodesModuleBody(
		writer: PrintWriter)
	{
		for (code in errorCodes())
		{
			val key = errorCodeKey(code)
			if (!errorCodeBundle.containsKey(key)
			    || errorCodeBundle.getString(key).isEmpty())
			{
				System.err.println("missing key/value: $key")
				continue
			}
			val commentKey = errorCodeCommentKey(code)
			if (errorCodeBundle.containsKey(commentKey))
			{
				writer.print(errorCodeBundle.getString(commentKey))
			}
			writer.println(MessageFormat.format(
				preamble.getString(definingMethodUse.name),
				stringify(
					errorCodeBundle.getString(key)),
					"\n[\n\t${code.nativeCode()}\n];\n"))
		}
	}

	/**
	 * Generate the preamble for the representative [module][ModuleDescriptor].
	 *
	 * @param versions
	 *   The [list][List] of version strings supported by the module.
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generateRepresentativeModulePreamble(
		versions: List<String>,
		writer: PrintWriter)
	{
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name),
			preamble.getString(representativeModuleName.name),
			Date()))
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name),
			BootstrapGenerator::class.java.name,
			Date()))
		val keys = arrayOf(
			originModuleName,
			specialObjectsModuleName,
			errorCodesModuleName,
			primitivesModuleName,
			infalliblePrimitivesModuleName,
			falliblePrimitivesModuleName)
		val extended = StringBuilder()
		for (key in keys)
		{
			extended.append("\n\t\"")
			extended.append(preamble.getString(key.name))
			extended.append("\",")
		}
		var extendedString = extended.toString()
		extendedString = extendedString.substring(
			0, extendedString.length - 1)
		writer.println(MessageFormat.format(
			preamble.getString(generalModuleHeader.name),
			preamble.getString(representativeModuleName.name),
			moduleVersionString(versions),
			extendedString,
			"",
			""))
	}

	/**
	 * Generate the [module][ModuleDescriptor] that contains the pragmas.
	 *
	 * @param versions
	 *   The supported versions.
	 * @throws IOException
	 *   If the source module could not be written.
	 */
	@Throws(IOException::class)
	private fun generateOriginModule(
		versions: List<String>)
	{
		val fileName = moduleFileName(originModuleName)
		assert(fileName.path.endsWith(".avail"))
		val writer = PrintWriter(fileName, "UTF-8")
		generateOriginModulePreamble(versions, writer)
		writer.close()
	}

	/**
	 * Generate the [module][ModuleDescriptor] that binds the special objects to
	 * Avail names.
	 *
	 * @param versions
	 *   The supported versions.
	 * @throws IOException
	 *   If the source module could not be written.
	 */
	@Throws(IOException::class)
	private fun generateSpecialObjectsModule(versions: List<String>)
	{
		val fileName = moduleFileName(specialObjectsModuleName)
		assert(fileName.path.endsWith(".avail"))
		val writer = PrintWriter(fileName, "UTF-8")
		generateSpecialObjectModulePreamble(versions, writer)
		generateSpecialObjectModuleBody(writer)
		writer.close()
	}

	/**
	 * Generate the specified primitive [module][ModuleDescriptor].
	 *
	 * @param fallible
	 *   `true` to indicate the fallible primitives module, `false` to indicate
	 *   the infallible primitives module, `null` to indicate the introductory
	 *   primitives module.
	 * @param versions
	 *   The [list][List] of version strings supported by the module.
	 * @throws IOException
	 *   If the source module could not be written.
	 */
	@Throws(IOException::class)
	private fun generatePrimitiveModule(
		fallible: Boolean?,
		versions: List<String>)
	{
		val key: Resources.Key = if (fallible === null)
		{
			primitivesModuleName
		}
		else
		{
			if (fallible) falliblePrimitivesModuleName
			else infalliblePrimitivesModuleName
		}
		val fileName = moduleFileName(key)
		assert(fileName.path.endsWith(".avail"))
		val writer = PrintWriter(fileName, "UTF-8")
		generatePrimitiveModulePreamble(fallible, versions, writer)
		generatePrimitiveModuleBody(fallible, writer)
		writer.close()
	}

	/**
	 * Generate the [module][ModuleDescriptor] that binds the
	 * [primitive error codes][AvailErrorCode] to Avail names.
	 *
	 * @param versions
	 *   The supported versions.
	 * @throws IOException
	 *   If the source module could not be written.
	 */
	@Throws(IOException::class)
	private fun generateErrorCodesModule(versions: List<String>)
	{
		val fileName = moduleFileName(errorCodesModuleName)
		assert(fileName.path.endsWith(".avail"))
		val writer = PrintWriter(fileName, "UTF-8")
		generateErrorCodesModulePreamble(versions, writer)
		generateErrorCodesModuleBody(writer)
		writer.close()
	}

	/**
	 * Generate the [module][ModuleDescriptor] that represents the bootstrap
	 * package.
	 *
	 * @param versions
	 *   The supported versions.
	 * @throws IOException
	 *   If the source module could not be written.
	 */
	@Throws(IOException::class)
	private fun generateRepresentativeModule(versions: List<String>)
	{
		val fileName = moduleFileName(representativeModuleName)
		assert(fileName.path.endsWith(".avail"))
		val writer = PrintWriter(fileName, "UTF-8")
		generateRepresentativeModulePreamble(versions, writer)
		writer.close()
	}

	/**
	 * Answer the correct [module][ModuleDescriptor] name for the
	 * [primitive][Primitive] test coverage module specified by the provided
	 * primitive.
	 *
	 * @param primitive
	 *   The primitive.
	 * @return
	 *   The module name.
	 */
	private fun primitiveCoverageTestModuleName(primitive: Primitive): String =
		MessageFormat.format(
			preamble.getString(
				primitiveCoverageTestModuleName.name),
			primitive.javaClass.simpleName.substring(2))

	/**
	 * Answer the correct [file name][File] for the [primitive][Primitive] test
	 * coverage [module][ModuleDescriptor] specified by the provided primitive.
	 *
	 * @param primitive
	 *   The primitive.
	 * @return
	 *   The file name.
	 */
	private fun primitiveCoverageTestModuleFileName(
		primitive: Primitive,
		testPackage: TestPackage
	): File
	{
		return File(String.format(
			"%s/%s/%s/%s.avail/%s.avail/%s.avail",
			sourceBaseName,
			generatedPackageName.replace('.', '/'),
			locale.language,
			preamble.getString(primitiveCoverageTestPackageName.name),
			testPackage.name,
			primitiveCoverageTestModuleName(primitive)))
	}

	/**
	 * Generate the package representative for the [primitive][Primitive]
	 * coverage test cases.
	 *
	 * @param targetDirectory
	 *   The directory the file will be written to.
	 * @param versionString
	 *   The module-insertion-ready supported versions.
	 * @param names
	 *   The exported Names section of the module.
	 * @param body
	 *   The body of the [Resources.Key.primitiveCommonTestPackageName] module.
	 * @throws IOException
	 *   If any module could not be written.
	 */
	@Throws(IOException::class)
	private fun generatePrimitiveTestCommonModule(
		targetDirectory: String,
		versionString: String,
		names: String,
		body: String)
	{
		val moduleName = preamble.getString(
			primitiveCommonTestPackageName.name)
		val fileName = File(String.format(
			"%s/%s.avail",
			targetDirectory,
			moduleName))
		val writer = PrintWriter(fileName, "UTF-8")
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name),
			moduleName,
			Date()))
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name),
			BootstrapGenerator::class.java.name,
			Date()))

		writer.println(MessageFormat.format(
			preamble.getString(
				primitiveCommonTestPackageRepresentativeHeader.name),
			moduleName,
			versionString,
			names))
		writer.println(body)
		writer.close()
	}

	/**
	 * Generate the package representative for the [primitive][Primitive]
	 * coverage test cases.
	 *
	 * @param versions
	 *   The supported versions.
	 * @return
	 *   The Map from the [primitive][Primitive] package to the corresponding
	 *   [TestPackage].
	 * @throws IOException
	 *   If any module could not be written.
	 */
	@Throws(IOException::class)
	private fun generatePrimitiveCoverageTestRepresentativeModule(
		versions: List<String>): Map<String, TestPackage>
	{
		val packageName = preamble.getString(
			primitiveCoverageTestPackageName.name)
		val targetDirectory = String.format(
			"%s/%s/%s/%s.avail",
			sourceBaseName,
			generatedPackageName.replace('.', '/'),
			locale.language,
			packageName)
		val fileName = File(String.format(
			"%s/%s.avail",
			targetDirectory,
			packageName))
		val writer = PrintWriter(fileName, "UTF-8")
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name),
			packageName,
			Date()))
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name),
			BootstrapGenerator::class.java.name,
			Date()))
		val used = StringBuilder()
		used.append("\n\t\"")
		used.append(preamble.getString(availModuleName.name))
		used.append("\",")
		val extendsPrimitiveCommon = StringBuilder()
		val testPackageMap = mutableMapOf<String, TestPackage>()
		for (primitive in primitives(null))
		{
			val primitivePackage =
				primitive.javaClass.getPackage().name
			testPackageMap.computeIfAbsent(primitivePackage) {
				TestPackage(primitivePackage)
			}.add(primitive)
		}
		val testPackages = testPackageMap.values.toMutableList()
		testPackages.sortBy { it.name }
		val primitiveCommonNames = StringBuilder()
		val primitiveCommonImplementation = StringBuilder()
		for (testPackage in testPackages)
		{
			used.append("\n\t\"")
			used.append(testPackage.name)
			used.append("\",")

			extendsPrimitiveCommon.append("\n\t\t\"")
			extendsPrimitiveCommon.append(testPackage.testSuiteName)
			extendsPrimitiveCommon.append("\",")

			primitiveCommonNames.append("\n\t\"")
			primitiveCommonNames.append(testPackage.testSuiteName)
			primitiveCommonNames.append("\",")

			primitiveCommonImplementation.append(testPackage.testSuiteCreationCode)
			primitiveCommonImplementation.append("\n")
		}
		var usedString = used.toString()
		usedString = usedString.substring(0, usedString.length - 1)

		var extendsPrimitiveCommonString = extendsPrimitiveCommon.toString()
		extendsPrimitiveCommonString =
			extendsPrimitiveCommonString.substring(
				0, extendsPrimitiveCommonString.length - 1)

		val versionString = moduleVersionString(versions)
		writer.println(MessageFormat.format(
			preamble.getString(
				primitiveCoverageTestPackageRepresentativeHeader.name),
			preamble.getString(primitiveCoverageTestPackageName.name),
			versionString,
			usedString,
			preamble.getString(primitiveCommonTestPackageName.name),
			extendsPrimitiveCommonString))
		writer.close()
		generatePrimitiveTestCommonModule(
			targetDirectory,
			versionString,
			primitiveCommonNames.substring(
				0, primitiveCommonNames.length - 1),
			primitiveCommonImplementation.toString().substring(
				0, primitiveCommonImplementation.length - 2))
		return testPackageMap
	}

	/**
	 * Generate each module that covers [primitive][Primitive] use cases.
	 *
	 * @param versions
	 *   The supported versions.
	 * @param testPackageMap
	 *   The Map from the [primitive][Primitive] package to the corresponding
	 *   [TestPackage].
	 * @throws IOException
	 *   If any module could not be written.
	 */
	@Throws(IOException::class)
	private fun generatePrimitiveCoverageTestModules(
		versions: List<String>,
		testPackageMap: Map<String, TestPackage>)
	{
		for (primitive in primitives(null))
		{
			val primitiveName = primitive.javaClass.simpleName.substring(2)
			@Suppress("MapGetWithNotNullAssertionOperator")
			val testPackage =
				testPackageMap[primitive.javaClass.getPackage().name]!!
			val moduleName = primitiveCoverageTestModuleName(primitive)
			val fileName =
				primitiveCoverageTestModuleFileName(primitive, testPackage)
			val writer = PrintWriter(fileName, "UTF-8")
			writer.println(MessageFormat.format(
				preamble.getString(availCopyright.name),
				moduleName,
				Date()))
			writer.println(MessageFormat.format(
				preamble.getString(primitiveCoverageTestModuleHeader.name),
				moduleName,
				moduleVersionString(versions),
				preamble.getString(primitiveCommonTestPackageName.name)))
			writer.println()
			writer.println(MessageFormat.format(
				preamble.getString(primitiveCoverageTestCaseOk.name),
				primitiveName,
				testPackage.testSuiteName))
			if (!primitive.hasFlag(Primitive.Flag.CannotFail))
			{
				val varType = primitive.failureVariableType
				if (varType.isEnumeration)
				{
					if (varType.isSubtypeOf(naturalNumbers))
					{
						val instances = varType.instances()
						val codes = mutableListOf<AvailErrorCode>()
						for (instance in instances)
						{
							codes.add(byNumericCode(instance.extractInt())!!)
						}
						codes.sortBy { it.code }
						for (code in codes)
						{
							val exceptionKey = errorCodeExceptionKey(code)
							val exceptionName =
								errorCodeBundle.getString(exceptionKey)
							writer.println(MessageFormat.format(
								preamble.getString(
									primitiveCoverageTestCaseFailed.name),
								primitiveName,
								exceptionName,
								testPackage.testSuiteName))
						}
					}
					else
					{
						writer.println(MessageFormat.format(
							preamble.getString(
								primitiveCoverageTestCaseFailedSpecial.name),
							primitiveName))
					}
				}
				else
				{
					writer.println(MessageFormat.format(
						preamble.getString(
							primitiveCoverageTestCaseFailedSpecial.name),
						primitiveName))
				}
			}
			writer.close()
		}
	}
	
	/**
	 * Generate the package that comprises the generated [primitive][Primitive]
	 * test cases.
	 *
	 * @param versions
	 *   The supported versions.
	 * @throws IOException
	 *   If an I/O error occurred while trying to write the module.
	 */
	@Throws(IOException::class)
	private fun generatePrimitiveCoverageTestPackage(versions: List<String>)
	{
		val packageName = File(String.format(
			"%s/%s/%s/%s.avail",
			sourceBaseName,
			generatedPackageName.replace('.', '/'),
			locale.language,
			preamble.getString(
				primitiveCoverageTestPackageName.name)))
		packageName.mkdir()
		val testPackageMap =
			generatePrimitiveCoverageTestRepresentativeModule(versions)
		for (testPackage in testPackageMap.values)
		{
			testPackage.generatePackageRepresentativeModule(versions)
		}
		generatePrimitiveCoverageTestModules(versions, testPackageMap)
	}

	/**
	 * Generate the target Avail source [modules][ModuleDescriptor].
	 *
	 * @param versions
	 *   The supported versions.
	 * @throws IOException
	 *   If any of the source modules could not be written.
	 */
	@Throws(IOException::class)
	fun generate(versions: List<String>)
	{
		val languagePath = File(String.format(
			"%s/%s/%s",
			sourceBaseName,
			generatedPackageName.replace('.', '/'),
			locale.language))
		languagePath.mkdir()
		val packageName = File(String.format(
			"%s/%s/%s/%s.avail",
			sourceBaseName,
			generatedPackageName.replace('.', '/'),
			locale.language,
			preamble.getString(representativeModuleName.name)))
		packageName.mkdir()
		generateOriginModule(versions)
		generateSpecialObjectsModule(versions)
		generatePrimitiveModule(null, versions)
		generatePrimitiveModule(false, versions)
		generatePrimitiveModule(true, versions)
		generateErrorCodesModule(versions)
		generateRepresentativeModule(versions)
		generatePrimitiveCoverageTestPackage(versions)
	}

	init
	{
		val control = UTF8ResourceBundleControl()
		preamble = ResourceBundle.getBundle(
			preambleBaseName,
			locale,
			BootstrapGenerator::class.java.classLoader,
			control)
		specialObjectBundle = ResourceBundle.getBundle(
			specialObjectsBaseName,
			locale,
			BootstrapGenerator::class.java.classLoader,
			control)
		primitiveBundle = ResourceBundle.getBundle(
			primitivesBaseName,
			locale,
			BootstrapGenerator::class.java.classLoader,
			control)
		errorCodeBundle = ResourceBundle.getBundle(
			errorCodesBaseName,
			locale,
			BootstrapGenerator::class.java.classLoader,
			control)

		// Map localized names to the special objects.
		for (i in specialObjects.indices)
		{
			val specialObject = specialObjects[i]
			if (!specialObject.equalsNil())
			{
				val key = specialObjectKey(i)
				val value = specialObjectBundle.getString(key)
				if (value.isNotEmpty())
				{
					specialObjectsByName[value] = specialObject
					namesBySpecialObject[specialObject] = value
				}
			}
		}

		// Map localized names to the primitives.
		for (primitive in primitives(null))
		{
			val value = primitiveBundle.getString(
				primitive.javaClass.simpleName)
			if (value.isNotEmpty())
			{
				val set = primitiveNameMap.computeIfAbsent(value)
					{ mutableSetOf() }
				set.add(primitive)
			}
		}

		// Map localized names to the primitive error codes.
		for (code in errorCodes())
		{
			val value = errorCodeBundle.getString(errorCodeKey(code))
			if (value.isNotEmpty())
			{
				errorCodesByName[value] = code
			}
		}
	}

	/**
	 * `TestPackage` groups [primitive][Primitive] test sub packages with the
	 * names of the primitive test modules contained in them.
	 *
	 * @constructor
	 * Construct a new [TestPackage].
	 *
	 * @param primitivePackage
	 *   The [Primitive] used to extract the package information.
	 */
	private inner class TestPackage constructor(primitivePackage: String)
	{
		/**
		 * The [module][ModuleDescriptor] name of this [TestPackage].
		 */
		val name: String

		/**
		 * The name of the test suite used by this [TestPackage].
		 */
		val testSuiteName: String

		/**
		 * The Avail code that creates the [test suite][testSuiteName] used by
		 * this [TestPackage].
		 */
		val testSuiteCreationCode: String

		/**
		 * The [set][Set] of [module][ModuleDescriptor] names of the modules
		 * included in this [TestPackage]
		 */
		val usesModuleNames = mutableSetOf<String>()

		/**
		 * Add the provided [Primitive] to the [usesModuleNames].
		 *
		 * @param primitive
		 *   The `Primitive` to add.
		 */
		fun add (primitive: Primitive)
		{
			this.usesModuleNames.add(primitiveCoverageTestModuleName(primitive))
		}

		/**
		 * Generate the subpackage representative for the [primitive][Primitive]
		 * coverage test cases.
		 *
		 * @param versions
		 *   The supported versions.
		 * @throws IOException
		 *   If any module could not be written.
		 */
		@Throws(IOException::class)
		fun generatePackageRepresentativeModule(versions: List<String>)
		{
			val packageName = preamble.getString(
				primitiveCoverageTestPackageName.name)
			val fileName = File(String.format(
				"%s/%s/%s/%s.avail/%s.avail/%s.avail",
				sourceBaseName,
				generatedPackageName.replace('.', '/'),
				locale.language,
				packageName,
				name,
				name))
			val writer = PrintWriter(fileName, "UTF-8")
			writer.println(MessageFormat.format(
				preamble.getString(availCopyright.name),
				name,
				Date()))
			writer.println(MessageFormat.format(
				preamble.getString(generatedModuleNotice.name),
				BootstrapGenerator::class.java.name,
				Date()))
			val usesModules = usesModuleNames.toMutableList().let {
				it.sortBy { module -> module  }
				it
			}
			val used = StringBuilder()
			used.append("\n\t\"")
			used.append(preamble.getString(availModuleName.name))
			used.append("\",")
			for (usesModule in usesModules)
			{
				used.append("\n\t\"")
				used.append(usesModule)
				used.append("\",")
			}
			var usedString = used.toString()
			usedString = usedString.substring(0, usedString.length - 1)
			writer.println(MessageFormat.format(
				preamble.getString(primitiveCoverageTestSubPackageRepresentativeHeader.name),
				name,
				moduleVersionString(versions),
				usedString))
			writer.close()
		}

		init
		{
			val packagePath = primitivePackage.split(".")
			assert(packagePath.size > 2)
			val basePackageName = packagePath[packagePath.size - 1]
			this.name = MessageFormat.format(
				preamble.getString(primitiveCoverageTestModuleName.name),
				basePackageName.capitalize())
			val packageName = File(String.format(
				"%s/%s/%s/%s.avail/%s.avail",
				sourceBaseName,
				generatedPackageName.replace('.', '/'),
				locale.language,
				preamble.getString(primitiveCoverageTestPackageName.name),
				this.name))
			packageName.mkdir()
			this.testSuiteName = MessageFormat.format(
				preamble.getString(primitiveTestSuiteName.name),
				basePackageName)
			this.testSuiteCreationCode = MessageFormat.format(
				preamble.getString(primitiveTestSuiteImplementation.name),
				basePackageName,
				this.testSuiteName)
		}
	}
}
