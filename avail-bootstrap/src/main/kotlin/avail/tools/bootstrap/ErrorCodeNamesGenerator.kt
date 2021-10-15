/*
 * ErrorCodeNamesGenerator.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import avail.AvailRuntime
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.sets.A_Set.Companion.setMinusCanDestroy
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.Companion.byNumericCode
import avail.interpreter.Primitive
import avail.tools.bootstrap.Resources.errorCodeCommentKey
import avail.tools.bootstrap.Resources.errorCodeExceptionKey
import avail.tools.bootstrap.Resources.errorCodeKey
import avail.tools.bootstrap.Resources.errorCodesBaseName
import avail.tools.bootstrap.Resources.escape
import java.io.PrintWriter
import java.util.EnumSet
import java.util.Locale
import java.util.Properties
import java.util.PropertyResourceBundle
import java.util.ResourceBundle

/**
 * Generate a [property resource bundle][PropertyResourceBundle] that specifies
 * unbound properties for the Avail names of the
 * [primitive error codes][AvailErrorCode].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `ErrorCodeNamesGenerator`.
 *
 * @param locale
 *   The target [locale][Locale].
 */
class ErrorCodeNamesGenerator (locale: Locale?)
	: PropertiesFileGenerator(errorCodesBaseName, locale!!)
{
	/**
	 * Write the names of the properties, whose unspecified values should be the
	 * Avail names of the corresponding [primitive error codes][AvailErrorCode].
	 *
	 * @param properties
	 *   The existing [properties][Properties]. These should be copied into the
	 *   resultant [properties resource bundle][ResourceBundle].
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	override fun generateProperties(
		properties: Properties,
		writer: PrintWriter
	) = with(writer) {
		val keys = mutableSetOf<String>()
		AvailErrorCode.values().forEach { code ->
			if (code.nativeCode() > 0)
			{
				print("# ")
				print(code.nativeCode())
				print(" : ")
				print(code.name)
				println()
				val key = errorCodeKey(code)
				keys.add(key)
				print(key)
				print('=')
				val errorCodeName = properties.getProperty(key)
				if (errorCodeName !== null)
				{
					print(escape(errorCodeName))
				}
				else if (locale.language == "en")
				{
					print(code.name.substring(2).lowercase().replace('_', '-'))
					print(" code")
				}
				println()
				val exceptionKey = errorCodeExceptionKey(code)
				keys.add(exceptionKey)
				print(exceptionKey)
				print('=')
				val exception = properties.getProperty(exceptionKey)
				if (exception !== null)
				{
					print(escape(exception))
				}
				else if (locale.language == "en")
				{
					print(code.name.substring(2).lowercase().replace('_', '-'))
					print(" exception")
				}
				println()
				val commentKey = errorCodeCommentKey(code)
				keys.add(commentKey)
				print(commentKey)
				print('=')
				val comment = properties.getProperty(commentKey)
				if (comment !== null)
				{
					print(escape(comment))
				}
				println()
			}
		}
		properties.keys.forEach { property ->
			val key = property as String
			if (!keys.contains(key))
			{
				keys.add(key)
				print(key)
				print('=')
				println(escape(properties.getProperty(key)))
			}
		}
	}

	companion object
	{
		/**
		 * Check if all [error][AvailErrorCode] codes are reachable from
		 * [primitive][Primitive] [failure variable
		 * types][Primitive.failureVariableType].
		 *
		 * @return
		 *   `true` if all error codes are reachable, `false` otherwise.
		 */
		private fun allErrorCodesAreReachableFromPrimitives(): Boolean
		{
			// This forces initialization of Avail.
			AvailRuntime
			var allErrorCodes = emptySet
			AvailErrorCode.values().forEach { code ->
				if (!code.isCausedByInstructionFailure)
				{
					allErrorCodes = allErrorCodes.setWithElementCanDestroy(
						code.numericCode(),
						true)
				}
			}
			var reachableErrorCodes = emptySet
			Primitive.holdersByName.forEach { (_, holder) ->
				val primitive = holder.primitive
				if (!primitive.hasFlag(Primitive.Flag.CannotFail))
				{
					val failureType: A_Type = primitive.failureVariableType
					if (failureType.isEnumeration)
					{
						reachableErrorCodes =
							reachableErrorCodes.setUnionCanDestroy(
								failureType.instances, true)
					}
					else if (failureType.isSubtypeOf(mostGeneralVariableType))
					{
						// This supports P_CatchException, which hides its error
						// codes inside a variable type.
						reachableErrorCodes =
							reachableErrorCodes.setUnionCanDestroy(
								failureType.readType.instances, true)
					}
				}
			}
			val unreachableErrorCodes =
				allErrorCodes.setMinusCanDestroy(reachableErrorCodes, true)
			if (unreachableErrorCodes.setSize != 0)
			{
				val unreachable = EnumSet.noneOf(AvailErrorCode::class.java)
				unreachableErrorCodes.forEach { code ->
					unreachable.add(byNumericCode(code.extractInt))
				}
				System.err.printf(
					"some error codes are unreachable: %s%n",
					unreachable)
				return false
			}
			return true
		}

		/**
		 * Generate the specified [resource bundles][ResourceBundle].
		 *
		 * @param args
		 *   The command-line arguments, an array of language codes that broadly
		 *   specify the [locales][Locale] for which resource bundles should be
		 *   generated.
		 * @throws Exception
		 *   If anything should go wrong.
		 */
		@Throws(Exception::class)
		@JvmStatic
		fun main(args: Array<String>)
		{
			val languages =
				if (args.isNotEmpty()) args
				else arrayOf(System.getProperty("user.language"))
			if (allErrorCodesAreReachableFromPrimitives())
			{
				languages.forEach { language ->
					ErrorCodeNamesGenerator(Locale(language)).generate()
				}
			}
		}
	}
}
