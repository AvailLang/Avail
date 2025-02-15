/*
 * BootstrapGenerator.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.tools.bootstrap

import avail.AvailRuntimeConfiguration.activeVersions
import avail.SpecialObject
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.Companion.byNumericCode
import avail.interpreter.Primitive
import avail.interpreter.Primitive.PrimitiveHolder.Companion.holdersByName
import avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import avail.interpreter.primitive.general.P_EmergencyExit
import avail.interpreter.primitive.hooks.P_GetPrimitiveFailureFunction
import avail.interpreter.primitive.hooks.P_InstallPrimitiveFailureFunction
import avail.interpreter.primitive.methods.P_AddSemanticRestriction
import avail.interpreter.primitive.sets.P_TupleToSet
import avail.interpreter.primitive.types.P_CreateEnumeration
import avail.tools.bootstrap.Resources.Key
import avail.tools.bootstrap.Resources.Key.availCopyright
import avail.tools.bootstrap.Resources.Key.availModuleName
import avail.tools.bootstrap.Resources.Key.bootstrapDefineSpecialObjectMacro
import avail.tools.bootstrap.Resources.Key.bootstrapDefiningMethod
import avail.tools.bootstrap.Resources.Key.bootstrapMacroNames
import avail.tools.bootstrap.Resources.Key.bootstrapMacros
import avail.tools.bootstrap.Resources.Key.bootstrapSpecialObject
import avail.tools.bootstrap.Resources.Key.definingMethodUse
import avail.tools.bootstrap.Resources.Key.definingSpecialObjectUse
import avail.tools.bootstrap.Resources.Key.errorCodesModuleName
import avail.tools.bootstrap.Resources.Key.falliblePrimitivesModuleName
import avail.tools.bootstrap.Resources.Key.generalModuleHeader
import avail.tools.bootstrap.Resources.Key.generatedModuleNotice
import avail.tools.bootstrap.Resources.Key.infalliblePrimitivesModuleName
import avail.tools.bootstrap.Resources.Key.invokePrimitiveFailureFunctionMethod
import avail.tools.bootstrap.Resources.Key.invokePrimitiveFailureFunctionMethodUse
import avail.tools.bootstrap.Resources.Key.originModuleHeader
import avail.tools.bootstrap.Resources.Key.originModuleName
import avail.tools.bootstrap.Resources.Key.parameterPrefix
import avail.tools.bootstrap.Resources.Key.primitiveCommonTestPackageName
import avail.tools.bootstrap.Resources.Key.primitiveCommonTestPackageRepresentativeHeader
import avail.tools.bootstrap.Resources.Key.primitiveCoverageTestCaseFailed
import avail.tools.bootstrap.Resources.Key.primitiveCoverageTestCaseFailedSpecial
import avail.tools.bootstrap.Resources.Key.primitiveCoverageTestCaseOk
import avail.tools.bootstrap.Resources.Key.primitiveCoverageTestModuleHeader
import avail.tools.bootstrap.Resources.Key.primitiveCoverageTestModuleName
import avail.tools.bootstrap.Resources.Key.primitiveCoverageTestPackageName
import avail.tools.bootstrap.Resources.Key.primitiveCoverageTestPackageRepresentativeHeader
import avail.tools.bootstrap.Resources.Key.primitiveCoverageTestSubPackageRepresentativeHeader
import avail.tools.bootstrap.Resources.Key.primitiveFailureCrashName
import avail.tools.bootstrap.Resources.Key.primitiveFailureCrashNameUse
import avail.tools.bootstrap.Resources.Key.primitiveFailureFunctionGetterMethod
import avail.tools.bootstrap.Resources.Key.primitiveFailureFunctionSetterMethod
import avail.tools.bootstrap.Resources.Key.primitiveFailureMethod
import avail.tools.bootstrap.Resources.Key.primitiveFailureMethodUse
import avail.tools.bootstrap.Resources.Key.primitiveFailureVariableName
import avail.tools.bootstrap.Resources.Key.primitiveKeyword
import avail.tools.bootstrap.Resources.Key.primitiveSemanticRestriction
import avail.tools.bootstrap.Resources.Key.primitiveSemanticRestrictionUse
import avail.tools.bootstrap.Resources.Key.primitiveTestSuiteImplementation
import avail.tools.bootstrap.Resources.Key.primitiveTestSuiteName
import avail.tools.bootstrap.Resources.Key.primitivesModuleName
import avail.tools.bootstrap.Resources.Key.representativeModuleName
import avail.tools.bootstrap.Resources.Key.specialObjectUse
import avail.tools.bootstrap.Resources.Key.specialObjectsModuleName
import avail.tools.bootstrap.Resources.errorCodeCommentKey
import avail.tools.bootstrap.Resources.errorCodeExceptionKey
import avail.tools.bootstrap.Resources.errorCodeKey
import avail.tools.bootstrap.Resources.errorCodesBaseName
import avail.tools.bootstrap.Resources.generatedPackageName
import avail.tools.bootstrap.Resources.preambleBaseName
import avail.tools.bootstrap.Resources.primitiveCommentKey
import avail.tools.bootstrap.Resources.primitiveParameterNameKey
import avail.tools.bootstrap.Resources.primitivesBaseName
import avail.tools.bootstrap.Resources.sourceBaseName
import avail.tools.bootstrap.Resources.specialObjectCommentKey
import avail.tools.bootstrap.Resources.specialObjectKey
import avail.tools.bootstrap.Resources.specialObjectTypeKey
import avail.tools.bootstrap.Resources.specialObjectsBaseName
import avail.tools.bootstrap.Resources.stringify
import avail.utility.Strings.increaseIndentation
import avail.utility.UTF8ResourceBundleControl
import avail.utility.notNullAnd
import avail.utility.t
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.text.DecimalFormat
import java.text.MessageFormat
import java.util.Date
import java.util.Locale
import java.util.MissingFormatArgumentException
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
		/** An object that controls reading of the resource bundles. */
		val control = UTF8ResourceBundleControl()

		/**
		 * A checked version of MessageFormat, that ensures each supplied argument
		 * gets plugged into the resulting string at least once.
		 */
		@Throws(MissingFormatArgumentException::class)
		fun checkedFormat(
			locale: Locale,
			pattern: String,
			vararg arguments: Any?
		): String
		{
			val format = MessageFormat(pattern, locale)
			val clone = format.clone() as MessageFormat
			val fakeFormats = Array(arguments.size) { DecimalFormat() }
			clone.formatsByArgumentIndex = fakeFormats
			val captured = clone.formatsByArgumentIndex
			if (!captured.contentEquals(fakeFormats))
			{
				val uncaptured = fakeFormats
					.indices
					.filter { i -> i >= captured.size || captured[i] == null }
					.filter {i -> arguments[i] != null }
				if (uncaptured.isNotEmpty())
				{
					System.err.println(
						String.format(
							"Pattern omits reference to arguments: %s. "
								+ "Pattern =\n\t%s\nArgs = %s",
							uncaptured,
							pattern.trim().replace("\n", "\n\t"),
							arguments.toList()))
				}
			}
			return format.format(arguments)
		}

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
			return versions.joinToString(",")
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
			return versions.joinToString(",") { "\n\t\"$it\"" }
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
		private fun primitives(fallible: Boolean?): List<Primitive> =
			holdersByName.flatMap { (_, holder) ->
				holder.primitive.run {
					when
					{
						hasFlag(Primitive.Flag.Private) -> emptyList()
						hasFlag(Primitive.Flag.Bootstrap) -> emptyList()
						fallible.notNullAnd {
							equals(hasFlag(Primitive.Flag.CannotFail))
						} -> emptyList()
						else -> listOf(this@run)
					}
				}
			}

		/**
		 * Answer the [primitive error codes][AvailErrorCode] for which Avail
		 * methods should be generated.
		 *
		 * @return
		 *   The relevant primitive error codes.
		 */
		private fun errorCodes(): List<AvailErrorCode> =
			AvailErrorCode.entries.filter { it.nativeCode() > 0 }

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
			val languages = mutableListOf<String>()
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
				activeVersions.mapTo(versions) { it }
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
				val generator = BootstrapGenerator(Locale.of(language))
				generator.generate(versions)
			}
		}

		/* Capture the special objects. */
		init
		{
			for (entry in SpecialObject.entries)
			{
				val specialObject = entry.value
				if (specialObject.notNil)
				{
					specialObjectIndexMap[specialObject] = entry.ordinal
				}
			}
		}
	}

	/**
	 * The [ResourceAccess] protecting the [ResourceBundle] that contains
	 * preamble resources.
	 */
	private val preamble = object : ResourceAccess<Key>(
		preambleBaseName, locale, Key::name) { }

	/**
	 * The [ResourceAccess] protecting the [ResourceBundle] that contains
	 * special object resources.
	 */
	private val specialObjectBundle = object : ResourceAccess<SpecialObject>(
		specialObjectsBaseName, locale, ::specialObjectKey) { }

	/**
	 * The [ResourceAccess] protecting the [ResourceBundle] that contains
	 * special object resources, but providing access to the comment entries.
	 */
	private val specialObjectCommentBundle =
		object : ResourceAccess<SpecialObject>(
			specialObjectBundle.bundle, ::specialObjectCommentKey) { }

	/**
	 * The [ResourceAccess] protecting the [ResourceBundle] that contains
	 * special object resources, but providing access to the type entries.
	 */
	private val specialObjectTypeBundle =
		object : ResourceAccess<SpecialObject>(
			specialObjectBundle.bundle, ::specialObjectTypeKey) { }

	/**
	 * The [ResourceAccess] protecting the [ResourceBundle] that contains the
	 * Avail names of the [Primitive]s.
	 */
	private val primitiveBundle = object : ResourceAccess<Primitive>(
		primitivesBaseName, locale, Primitive::simpleName) { }

	/**
	 * The [ResourceAccess] protecting the [ResourceBundle] that contains the
	 * Avail [Primitive] comments.
	 */
	private val primitiveCommentBundle = object : ResourceAccess<Primitive>(
		primitiveBundle.bundle, ::primitiveCommentKey) { }

	/**
	 * The [ResourceAccess] protecting the [ResourceBundle] that contains the
	 * Avail names of the [Primitive]s' parameters.
	 */
	private val primitiveParameterNameBundle =
		object : ResourceAccess<Pair<Primitive, Int>>(
			primitiveBundle.bundle,
			{ (prim, arg) -> primitiveParameterNameKey(prim, arg) }
		) { }

	/**
	 * The [ResourceAccess] protecting the [ResourceBundle] that contains the
	 * Avail names of the [primitive error codes][AvailErrorCode].
	 */
	private val errorCodeBundle = object : ResourceAccess<AvailErrorCode>(
		errorCodesBaseName, locale, ::errorCodeKey) { }

	/**
	 * The [ResourceAccess] protecting the [ResourceBundle] that contains the
	 * comments for the [primitive error codes][AvailErrorCode].
	 */
	private val errorCommentBundle = object : ResourceAccess<AvailErrorCode>(
		errorCodeBundle.bundle, ::errorCodeCommentKey) { }

	/**
	 * The [ResourceAccess] protecting the [ResourceBundle] that contains the
	 * exception names for the [primitive error codes][AvailErrorCode].
	 */
	private val errorCodeExceptionBundle =
		object : ResourceAccess<AvailErrorCode>(
			errorCodeBundle.bundle, ::errorCodeExceptionKey) { }

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
		val code = byNumericCode(numericCode.extractInt)
		code ?: error(String.format(
			"no %s for %s", AvailErrorCode::class.java.simpleName, numericCode))
		return errorCodeBundle[code]
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
		val code = byNumericCode(numericCode.extractInt)
		code ?: error(String.format(
			"no %s for %s", AvailErrorCode::class.java.simpleName, numericCode))
		return errorCodeBundle[code]
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
	private fun moduleFileName(key: Key): File
	{
		return File(String.format(
			"%s/%s/%s/%s.avail/%s.avail",
			sourceBaseName,
			generatedPackageName.replace('.', '/'),
			locale.language,
			preamble[representativeModuleName],
			preamble[key]))
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
		writer.println(checkedFormat(
			preamble[availCopyright],
			preamble[originModuleName],
			Date()))
		writer.println(checkedFormat(
			preamble[generatedModuleNotice],
			BootstrapGenerator::class.java.name,
			Date()))
		writer.println(checkedFormat(
			preamble[originModuleHeader],
			preamble[originModuleName],
			moduleVersionString(versions),
			vmVersionString(versions),
			preamble[bootstrapDefiningMethod],
			preamble[bootstrapSpecialObject],
			preamble[bootstrapDefineSpecialObjectMacro],
			preamble[bootstrapMacroNames],
			preamble[bootstrapMacros]))
	}

	/**
	 * A checked version of MessageFormat, that ensures each supplied argument
	 * gets plugged into the resulting string at least once.
	 */
	@Throws(MissingFormatArgumentException::class)
	fun checkedFormat(
		pattern: String,
		vararg arguments: Any?
	): String = checkedFormat(locale, pattern, *arguments)

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
		return namesBySpecialObject[specialObject] ?: error(
			"no special object for $specialObject")
	}

	/**
	 * Answer a textual representation of the special objects that is
	 * satisfactory for use in an Avail [module][ModuleDescriptor] header.
	 *
	 * @return
	 *   The "Names" string.
	 */
	private fun specialObjectsNamesString() =
		specialObjectsByName.entries.sortedBy { it.key }
			.joinToString(",") { (name, specialObject) ->
				val index = specialObjectIndexMap[specialObject]
				"\n\t/* %3d */ \"%s\"".format(index, name)
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
		writer.println(checkedFormat(
			preamble[availCopyright],
			preamble[specialObjectsModuleName],
			Date()))
		writer.println(checkedFormat(
			preamble[generatedModuleNotice],
			BootstrapGenerator::class.java.name,
			Date()))
		writer.println(checkedFormat(
			preamble[generalModuleHeader],
			preamble[specialObjectsModuleName],
			moduleVersionString(versions),
			"%n\t\"%s\"".format(preamble[originModuleName]),
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
		for (entry in SpecialObject.entries)
		{
			if (!entry.value.notNil) continue
			if (entry !in specialObjectBundle)
			{
				System.err.println(
					"missing key/value: " +
						specialObjectBundle.extractKey(entry))
				continue
			}
			val methodName = specialObjectBundle[entry]
			if (entry in specialObjectCommentBundle)
			{
				val commentTemplate = specialObjectCommentBundle[entry]
				val type = specialObjectTypeBundle[entry]
				if (type.isEmpty())
				{
					writer.print(checkedFormat(commentTemplate, methodName))
				}
				else
				{
					writer.print(
						checkedFormat(commentTemplate, methodName, type))
				}
			}
			val use = checkedFormat(
				preamble[specialObjectUse],
				entry.ordinal)
			writer.println(
				checkedFormat(
					preamble[definingSpecialObjectUse],
					stringify(methodName),
					use))
			writer.println()
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
	 * @param fallible
	 *   Whether the primitives that we are to include are the ones that are
	 *   fallible (`true`), infallible (`false`), or both (`null`).
	 * @return
	 *   The "Names" string, indented once.
	 */
	private fun primitivesNamesString(fallible: Boolean?): String
	{
		val wanted = primitives(fallible).toSet()
		return primitiveNameMap.entries
			.filter { (_, prims) -> prims.intersect(wanted).isNotEmpty() }
			.map(Map.Entry<String, *>::key)
			.sorted()
			.joinToString(",") { "\n\t\"$it\"" }
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
		val key: Key = when (fallible)
		{
			true -> falliblePrimitivesModuleName
			false -> infalliblePrimitivesModuleName
			null -> primitivesModuleName
		}
		// Write the copyright.
		writer.println(checkedFormat(
			preamble[availCopyright],
			preamble[key],
			Date()))
		// Write the generated module notice.
		writer.println(checkedFormat(
			preamble[generatedModuleNotice],
			BootstrapGenerator::class.java.name,
			Date()))
		// Write the header.
		val uses = buildString {
			append("\n\t\"")
			append(preamble[originModuleName])
			append('"')
			if (fallible !== null)
			{
				if (java.lang.Boolean.TRUE == fallible)
				{
					append(",\n\t\"")
					append(preamble[errorCodesModuleName])
					append("\"")
				}
				append(",\n\t\"")
				append(preamble[specialObjectsModuleName])
				append("\",\n\t\"")
				append(preamble[primitivesModuleName])
				append("\" =\n\t(")
				append(
					increaseIndentation(primitivesNamesString(fallible), 1))
				append("\n\t)")
			}
		}
		val names = when
		{
			fallible === null -> primitivesNamesString(null)
			java.lang.Boolean.TRUE == fallible ->
				listOf(
					primitiveFailureFunctionSetterMethod,
					primitiveFailureFunctionGetterMethod,
					primitiveFailureMethod
				).joinToString(",") { "\n\t${stringify(preamble[it])}" }
			else -> ""
		}
		writer.println(checkedFormat(
			preamble[generalModuleHeader],
			preamble[key],
			moduleVersionString(versions),
			"",
			uses,
			names))
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
		val parameterTypes = functionType.argsTupleType
		val parameterCount = parameterTypes.sizeRange
		assert(parameterCount.lowerBound.equals(parameterCount.upperBound)) {
			String.format(
				"Expected %s to have a fixed parameter count",
				primitive.simpleName)
		}
		return buildString {
			val end = parameterCount.lowerBound.extractInt
			for (i in 1..end)
			{
				val argName =
					if ((primitive to i) in primitiveParameterNameBundle)
					{
						primitiveParameterNameBundle[primitive to i]
					}
					else
					{
						preamble[parameterPrefix] + i
					}
				val type = parameterTypes.typeAtIndex(i)
				val paramType =
					if (forSemanticRestriction) instanceMeta(type)
					else type
				val typeName = specialObjectName(paramType)
				append('\t')
				append(argName)
				append(" : ")
				append(typeName)
				if (i != end)
				{
					append(',')
				}
				append('\n')
			}
		}
	}

	/**
	 * Answer the method statements for the specified [primitive][Primitive].
	 *
	 * @param primitive
	 *   A primitive.
	 * @return
	 *   The textual representation of the primitive's statements (indent=1).
	 */
	private fun primitiveMethodStatements(primitive: Primitive) = buildList {
		val canFail = !primitive.hasFlag(Primitive.Flag.CannotFail)
		add(
			buildString {
				append("${preamble[primitiveKeyword]} ${primitive.name}")
				if (canFail)
				{
					append(" (")
					append(preamble[primitiveFailureVariableName])
					append(" : ")
					val varType: A_Type = primitive.failureVariableType
					if (varType.isEnumeration)
					{
						if (varType.isSubtypeOf(naturalNumbers))
						{
							varType.instances
								.sortedBy { it.extractInt }
								.joinTo(
									this@buildString, ",", "{", "}ᵀ"
								) { "\n\t\t${errorCodeName(it)}" }
						}
						else
						{
							append(specialObjectName(Types.ANY()))
						}
					}
					else
					{
						append(specialObjectName(varType))
					}
					append(")")
				}
				append(";")
			})
		if (canFail)
		{
			add(
				if (primitive.hasFlag(Primitive.Flag.CatchException))
				{
					val argName =
						primitiveParameterNameBundle.getOr(primitive to 1) {
							preamble[parameterPrefix] + 1
						}
					checkedFormat(
						preamble[invokePrimitiveFailureFunctionMethodUse],
						argName,
						namesBySpecialObject[emptyTuple])
				}
				else
				{
					checkedFormat(
						preamble[primitiveFailureMethodUse],
						preamble[primitiveFailureVariableName])
				})
		}
	}

	/**
	 * Answer a block that contains the specified (already formatted) parameter
	 * declarations and (already formatted) statements.
	 *
	 * @param declarations
	 *   The parameter declarations.
	 * @param statements
	 *   The block's statements as strings.  This may include a leading
	 *   primitive declaration as a quasi-statement.
	 * @param returnType
	 *   The return type, or `null` if the return type should not be explicit.
	 * @return
	 *   A textual representation of the block (indent=0).
	 */
	private fun block(
		declarations: String,
		statements: List<String>,
		returnType: A_BasicObject?
	) = buildString {
		append("\n[\n")
		append(declarations)
		if (declarations.isNotEmpty())
		{
			append("|\n")
		}
		statements.forEach {
			append("\t$it\n")
		}
		append(']')
		if (returnType !== null)
		{
			append(" : ")
			append(specialObjectName(returnType))
		}
	}

	/**
	 * Answer a comment for the specified [primitive][Primitive].
	 *
	 * @param primitive
	 *   A primitive.
	 * @return
	 *   A textual representation of the comment (indent=0).
	 */
	private fun primitiveComment(primitive: Primitive) = buildString {
		if (primitive in primitiveCommentBundle)
		{
			// Compute the number of template arguments.
			val primitiveArgCount = primitive.argCount
			val templateArgCount = 2 + (primitiveArgCount shl 1) +
				when
				{
					primitive.hasFlag(Primitive.Flag.CannotFail) -> 0
					primitive.failureVariableType.isEnumeration ->
						primitive.failureVariableType.instanceCount.extractInt
					else -> 1
				}
			val formatArgs = arrayOfNulls<Any>(templateArgCount)
			// The method name goes into the first slot…
			formatArgs[0] = primitiveBundle[primitive]
			// …then come the parameter names, followed by their types…
			val paramsType = primitive.blockTypeRestriction().argsTupleType
			for (i in 1..primitiveArgCount)
			{
				val argName =
					primitiveParameterNameBundle.getOr(primitive to i) {
						preamble[parameterPrefix] + i
					}
				formatArgs[i] = argName
				formatArgs[i + primitiveArgCount] = paramsType.typeAtIndex(i)
			}
			// …then the return type…
			formatArgs[(primitiveArgCount shl 1) + 1] =
				primitive.blockTypeRestriction().returnType
			// …then the exceptions.
			if (!primitive.hasFlag(Primitive.Flag.CannotFail))
			{
				var raiseIndex = (primitiveArgCount shl 1) + 2
				val varType: A_Type = primitive.failureVariableType
				if (varType.isEnumeration)
				{
					if (varType.isSubtypeOf(naturalNumbers))
					{
						val instances = varType.instances
						for (code in instances.sortedBy { it.extractInt })
						{
							formatArgs[raiseIndex++] = exceptionName(code)
						}
					}
					else
					{
						formatArgs[raiseIndex] = specialObjectName(Types.ANY())
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
			val messagePattern = primitiveCommentBundle[primitive]
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
									primitiveCommentBundle
										.extractKey(primitive))
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
					primitiveCommentBundle.extractKey(primitive))
			}
			append(checkedFormat(messagePattern, *formatArgs))
		}
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
		writer: PrintWriter
	) = with(writer) {
		print(checkedFormat(
			preamble[definingMethodUse],
			stringify(name),
			block))
		println(';')
		println()
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
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			listOf(
				"${preamble[primitiveKeyword]} ${primitive.name};"
			),
			primitive.blockTypeRestriction().returnType)
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
		val primitive = P_CreateEnumeration
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			listOf(
				"${preamble[primitiveKeyword]} ${primitive.name};"
			),
			primitive.blockTypeRestriction().returnType)
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
	private fun generateCrashMethod(writer: PrintWriter)
	{
		val primitive = P_EmergencyExit
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			listOf(
				"${preamble[primitiveKeyword]} ${primitive.name};"
			),
			primitive.blockTypeRestriction().returnType)
		generateMethod(
			preamble[primitiveFailureCrashName],
			block,
			writer)
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
		val failureArg = preamble[primitiveFailureVariableName]
		val failureType = specialObjectName(Types.ANY())
		val block = block(
			"\t$failureArg : $failureType\n",
			listOf(
				checkedFormat(
					preamble[invokePrimitiveFailureFunctionMethodUse],
					preamble[primitiveFailureFunctionGetterMethod],
					failureArg)
			),
			bottom)
		generateMethod(
			preamble[primitiveFailureMethod],
			block,
			writer)
	}

	/**
	 * Generate the [primitive][Primitive] failure function getter.
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generatePrimitiveFailureFunctionGetter(writer: PrintWriter)
	{
		val primitive = P_GetPrimitiveFailureFunction
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			listOf(
				"${preamble[primitiveKeyword]} ${primitive.name};"
			),
			primitive.blockTypeRestriction().returnType)
		generateMethod(
			preamble[primitiveFailureFunctionGetterMethod],
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
		val primitive = P_InstallPrimitiveFailureFunction
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			listOf(
				"${preamble[primitiveKeyword]} ${primitive.name};"
			),
			primitive.blockTypeRestriction().returnType)
		generateMethod(
			preamble[primitiveFailureFunctionSetterMethod],
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
		//TODO Invoke primitiveFailureMethod
		val primitive = P_InvokeWithTuple
		val primKeyword = preamble[primitiveKeyword]
		val prim = primitive.name
		val failName = preamble[primitiveFailureVariableName]
		val failType = specialObjectName(primitive.failureVariableType)
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			listOf(
				"$primKeyword $prim ($failName : $failType);",
				checkedFormat(
					preamble[primitiveFailureCrashNameUse],
					preamble[primitiveFailureVariableName])
			),
			TOP())
		generateMethod(
			preamble[invokePrimitiveFailureFunctionMethod],
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
		var block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			listOf(
				"${preamble[primitiveKeyword]} ${primitive.name} (" +
					"${preamble[primitiveFailureVariableName]} : " +
					"${specialObjectName(naturalNumbers)});",
				checkedFormat(
					preamble[primitiveFailureCrashNameUse],
					preamble[primitiveFailureVariableName])
			),
			TOP())
		generateMethod(
			preamble[primitiveSemanticRestriction],
			block,
			writer)

		block = block(
			primitiveMethodParameterDeclarations(P_InvokeWithTuple, true),
			listOf(specialObjectName(bottom)),
			null)
		writer.append(checkedFormat(
			preamble[primitiveSemanticRestrictionUse],
			stringify(preamble[invokePrimitiveFailureFunctionMethod]),
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
		if (primitive !in primitiveBundle)
		{
			System.err.println(
				"missing key/value: ${primitiveBundle.extractKey(primitive)}")
			return
		}
		val comment = primitiveComment(primitive)
		val block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			primitiveMethodStatements(primitive),
			primitive.blockTypeRestriction().returnType)
		writer.print(comment)
		generateMethod(primitiveBundle[primitive], block, writer)
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
		fallible: Boolean,
		writer: PrintWriter)
	{
		if (fallible)
		{
			// Generate access to the hook that holds the primitive failure
			// function.
			generatePrimitiveToSetMethod(writer)
			generatePrimitiveEnumMethod(writer)
			generateCrashMethod(writer)
			generatePrimitiveFailureFunctionGetter(writer)
			generatePrimitiveFailureFunctionSetter(writer)
			generateInvokePrimitiveFailureFunctionMethod(writer)
			generatePrivateSemanticRestrictionMethod(writer)
			generatePrimitiveFailureMethod(writer)
		}

		// Generate the primitive methods.
		for (primitive in primitives(fallible))
		{
			if (!primitive.hasFlag(Primitive.Flag.Private)
				&& !primitive.hasFlag(Primitive.Flag.Bootstrap))
			{
				generatePrimitiveMethod(primitive, writer)
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
	private fun errorCodesNamesString() = errorCodesByName.entries
		.sortedBy { it.key }
		.joinToString(",") { (name, code) ->
			"\n\t/* %3d */ \"%s\"".format(code.nativeCode(), name)
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
		writer: PrintWriter
	) = with(writer) {
		println(checkedFormat(
			preamble[availCopyright],
			preamble[errorCodesModuleName],
			Date()))
		println(checkedFormat(
			preamble[generatedModuleNotice],
			BootstrapGenerator::class.java.name,
			Date()))
		val uses = buildString {
			append("\n\t\"")
			append(preamble[originModuleName])
			append('"')
		}
		println(checkedFormat(
			preamble[generalModuleHeader],
			preamble[errorCodesModuleName],
			moduleVersionString(versions),
			"",
			uses,
			errorCodesNamesString()))
	}

	/**
	 * Generate the body for the error codes [module][ModuleDescriptor].
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	private fun generateErrorCodesModuleBody(writer: PrintWriter)
	{
		for (code in errorCodes())
		{
			if (code !in errorCodeBundle)
			{
				System.err.println(
					"missing key/value: ${errorCodeBundle.extractKey(code)}")
				continue
			}
			if (code in errorCommentBundle)
			{
				writer.print(errorCommentBundle[code])
			}
			writer.println(checkedFormat(
				preamble[definingMethodUse],
				stringify(errorCodeBundle[code]),
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
		writer: PrintWriter
	) = with(writer) {
		println(checkedFormat(
			preamble[availCopyright],
			preamble[representativeModuleName],
			Date()))
		println(checkedFormat(
			preamble[generatedModuleNotice],
			BootstrapGenerator::class.java.name,
			Date()))
		val keys = arrayOf(
			originModuleName,
			specialObjectsModuleName,
			errorCodesModuleName,
			primitivesModuleName,
			infalliblePrimitivesModuleName,
			falliblePrimitivesModuleName)
		val extendedString = buildString {
			keys.forEach { key ->
				append("\n\t\"")
				append(preamble[key])
				append("\",")
			}
			setLength(length - 1)
		}
		writer.println(checkedFormat(
			preamble[generalModuleHeader],
			preamble[representativeModuleName],
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
	private fun generateOriginModule(versions: List<String>)
	{
		val fileName = moduleFileName(originModuleName)
		assert(fileName.path.endsWith(".avail"))
		PrintWriter(fileName, UTF_8.name()).use {
			generateOriginModulePreamble(versions, it)
		}
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
		PrintWriter(fileName, UTF_8.name()).use {
			generateSpecialObjectModulePreamble(versions, it)
			generateSpecialObjectModuleBody(it)
		}
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
		val key: Key = if (fallible === null)
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
		PrintWriter(fileName, UTF_8.name()).use {
			generatePrimitiveModulePreamble(fallible, versions, it)
			if (fallible != null) generatePrimitiveModuleBody(fallible, it)
		}
	}

	/**
	 * Generate the [module][ModuleDescriptor] that binds the primitive error
	 * [codes][AvailErrorCode] to Avail names.
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
		PrintWriter(fileName, UTF_8.name()).use {
			generateErrorCodesModulePreamble(versions, it)
			generateErrorCodesModuleBody(it)
		}
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
		PrintWriter(fileName, UTF_8.name()).use {
			generateRepresentativeModulePreamble(versions, it)
		}
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
	private fun primitiveCoverageTestModuleName(primitive: Primitive) =
		checkedFormat(
			preamble[primitiveCoverageTestModuleName],
			primitive.simpleName.substring(2))

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
	) = File(String.format(
		"%s/%s/%s/%s.avail/%s.avail/%s.avail",
		sourceBaseName,
		generatedPackageName.replace('.', '/'),
		locale.language,
		preamble[primitiveCoverageTestPackageName],
		testPackage.name,
		primitiveCoverageTestModuleName(primitive)))

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
		val moduleName = preamble[primitiveCommonTestPackageName]
		val fileName = File(String.format(
			"%s/%s.avail",
			targetDirectory,
			moduleName))
		PrintWriter(fileName, UTF_8.name()).use { writer ->
			writer.println(
				checkedFormat(
					preamble[availCopyright],
					moduleName,
					Date()))
			writer.println(
				checkedFormat(
					preamble[generatedModuleNotice],
					BootstrapGenerator::class.java.name,
					Date()))
			writer.println(
				checkedFormat(
					preamble[primitiveCommonTestPackageRepresentativeHeader],
					moduleName,
					versionString,
					names))
			writer.println(body)
		}
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
		val packageName = preamble[primitiveCoverageTestPackageName]
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
		val (
			versionString,
			primitiveCommonNames,
			primitiveCommonImplementation,
			testPackageMap
		) = PrintWriter(fileName, UTF_8.name()).use { writer ->
			writer.println(
				checkedFormat(
					preamble[availCopyright],
					packageName,
					Date()))
			writer.println(
				checkedFormat(
					preamble[generatedModuleNotice],
					BootstrapGenerator::class.java.name,
					Date()))
			val used = StringBuilder()
				.append("\n\t\"")
				.append(preamble[availModuleName])
				.append("\",")
			val extendsPrimitiveCommon = StringBuilder()
			val testPackageMap = mutableMapOf<String, TestPackage>()
			primitives(null).forEach { primitive ->
				val primitivePackage = primitive.javaClass.getPackage().name
				testPackageMap.computeIfAbsent(primitivePackage) {
					TestPackage(primitivePackage)
				}.add(primitive)
			}
			val testPackages = testPackageMap.values.sortedBy { it.name }
			val primitiveCommonNames = StringBuilder()
			val primitiveCommonImplementation = StringBuilder()
			testPackages.forEach { testPackage ->
				used.append("\n\t\"")
				used.append(testPackage.name)
				used.append("\",")

				extendsPrimitiveCommon.append("\n\t\t\"")
				extendsPrimitiveCommon.append(testPackage.testSuiteName)
				extendsPrimitiveCommon.append("\",")

				primitiveCommonNames.append("\n\t\"")
				primitiveCommonNames.append(testPackage.testSuiteName)
				primitiveCommonNames.append("\",")

				primitiveCommonImplementation.append(
					testPackage.testSuiteCreationCode)
				primitiveCommonImplementation.append("\n")
			}
			used.setLength(used.length - 1)
			primitiveCommonNames.setLength(primitiveCommonNames.length - 1)
			primitiveCommonImplementation.setLength(
				primitiveCommonImplementation.length - 2)

			val versionString = moduleVersionString(versions)
			writer.println(
				checkedFormat(
					preamble[primitiveCoverageTestPackageRepresentativeHeader],
					preamble[primitiveCoverageTestPackageName],
					versionString,
					used.toString(),
					preamble[primitiveCommonTestPackageName],
					extendsPrimitiveCommon.toString()))
			t(
				versionString,
				primitiveCommonNames.toString(),
				primitiveCommonImplementation.toString(),
				testPackageMap)
		}
		generatePrimitiveTestCommonModule(
			targetDirectory,
			versionString,
			primitiveCommonNames,
			primitiveCommonImplementation)
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
			val primitiveName = primitive.simpleName.substring(2)
			@Suppress("MapGetWithNotNullAssertionOperator")
			val testPackage =
				testPackageMap[primitive.javaClass.getPackage().name]!!
			val moduleName = primitiveCoverageTestModuleName(primitive)
			val fileName =
				primitiveCoverageTestModuleFileName(primitive, testPackage)
			PrintWriter(fileName, UTF_8.name()).use { writer ->
				writer.println(
					checkedFormat(
						preamble[availCopyright],
						moduleName,
						Date()))
				writer.println(
					checkedFormat(
						preamble[primitiveCoverageTestModuleHeader],
						moduleName,
						moduleVersionString(versions),
						preamble[primitiveCommonTestPackageName]))
				writer.println()
				writer.println(
					checkedFormat(
						preamble[primitiveCoverageTestCaseOk],
						primitiveName,
						testPackage.testSuiteName))
				if (!primitive.hasFlag(Primitive.Flag.CannotFail))
				{
					val varType = primitive.failureVariableType
					if (varType.isEnumeration)
					{
						if (varType.isSubtypeOf(naturalNumbers))
						{
							varType.instances
								.map { i -> byNumericCode(i.extractInt)!! }
								.sortedBy { i -> i.code }
								.forEach { code ->
									val exceptionName =
										errorCodeExceptionBundle[code]
									writer.println(
										checkedFormat(
											preamble[
												primitiveCoverageTestCaseFailed],
											primitiveName,
											exceptionName,
											testPackage.testSuiteName))
								}
						}
						else
						{
							writer.println(
								checkedFormat(
									preamble[
										primitiveCoverageTestCaseFailedSpecial],
									primitiveName))
						}
					}
					else
					{
						writer.println(
							checkedFormat(
								preamble[
									primitiveCoverageTestCaseFailedSpecial],
								primitiveName))
					}
				}
			}
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
			preamble[primitiveCoverageTestPackageName]))
		packageName.mkdir()
		val testPackageMap =
			generatePrimitiveCoverageTestRepresentativeModule(versions)
		testPackageMap.values.forEach { testPackage ->
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
			preamble[representativeModuleName]))
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
		// Map localized names to the special objects.
		for (entry in SpecialObject.entries)
		{
			if (entry.value.notNil)
			{
				val value = specialObjectBundle[entry]
				if (value.isNotEmpty())
				{
					specialObjectsByName[value] = entry.value
					namesBySpecialObject[entry.value] = value
				}
			}
		}

		// Map localized names to the primitives.
		primitives(null).forEach { primitive ->
			val value = primitiveBundle[primitive]
			if (value.isNotEmpty())
			{
				primitiveNameMap.computeIfAbsent(value) { mutableSetOf() }
					.add(primitive)
			}
		}

		// Map localized names to the primitive error codes.
		errorCodes().forEach { code ->
			val value = errorCodeBundle[code]
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
	 *
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
			val packageName = preamble[primitiveCoverageTestPackageName]
			val fileName = File(String.format(
				"%s/%s/%s/%s.avail/%s.avail/%s.avail",
				sourceBaseName,
				generatedPackageName.replace('.', '/'),
				locale.language,
				packageName,
				name,
				name))
			PrintWriter(fileName, "UTF-8").use { writer ->
				writer.println(
					checkedFormat(
						preamble[availCopyright],
						name,
						Date()))
				writer.println(
					checkedFormat(
						preamble[generatedModuleNotice],
						BootstrapGenerator::class.java.name,
						Date()))
				val usedString = buildString {
					append("\n\t\"")
					append(preamble[availModuleName])
					append("\",")
					usesModuleNames.sorted().forEach { usesModule ->
						append("\n\t\"")
						append(usesModule)
						append("\",")
					}
					setLength(length - 1)
				}
				writer.println(
					checkedFormat(
						preamble[
							primitiveCoverageTestSubPackageRepresentativeHeader],
						name,
						moduleVersionString(versions),
						usedString))
			}
		}

		init
		{
			val packagePath = primitivePackage.split(".")
			assert(packagePath.size > 2)
			val basePackageName = packagePath[packagePath.size - 1]
			this.name = checkedFormat(
				preamble[primitiveCoverageTestModuleName],
				basePackageName.replaceFirstChar(Char::titlecase))
			val packageName = File(String.format(
				"%s/%s/%s/%s.avail/%s.avail",
				sourceBaseName,
				generatedPackageName.replace('.', '/'),
				locale.language,
				preamble[primitiveCoverageTestPackageName],
				this.name))
			packageName.mkdir()
			this.testSuiteName = checkedFormat(
				preamble[primitiveTestSuiteName],
				basePackageName)
			this.testSuiteCreationCode = checkedFormat(
				preamble[primitiveTestSuiteImplementation],
				basePackageName,
				this.testSuiteName)
		}
	}
}
