/*
 * ErrorCodeNamesGenerator.kt
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
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.AvailErrorCode.Companion.byNumericCode
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Companion.byPrimitiveNumberOrNull
import com.avail.interpreter.Primitive.Companion.maxPrimitiveNumber
import com.avail.tools.bootstrap.Resources.errorCodeCommentKey
import com.avail.tools.bootstrap.Resources.errorCodeKey
import com.avail.tools.bootstrap.Resources.escape
import java.io.PrintWriter
import java.util.*
import com.avail.tools.bootstrap.Resources.errorCodesBaseName

/**
 * Generate a [property resource bundle][PropertyResourceBundle] that specifies
 * unbound properties for the Avail names of the
 * [primitive error codes][AvailErrorCode].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class ErrorCodeNamesGenerator
/**
 * Construct a new `ErrorCodeNamesGenerator`.
 *
 * @param locale
 *   The target [locale][Locale].
 */
(locale: Locale?) : PropertiesFileGenerator(errorCodesBaseName, locale!!)
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
	override fun generateProperties(properties: Properties, writer: PrintWriter)
	{
		val keys: MutableSet<String> = HashSet()
		for (code in AvailErrorCode.values())
		{
			if (code.nativeCode() > 0)
			{
				writer.print("# ")
				writer.print(code.nativeCode())
				writer.print(" : ")
				writer.print(code.name)
				writer.println()
				val key = errorCodeKey(code)
				keys.add(key)
				writer.print(key)
				writer.print('=')
				val errorCodeName = properties.getProperty(key)
				if (errorCodeName != null)
				{
					writer.print(escape(errorCodeName))
				}
				else if (locale.language == "en")
				{
					writer.print(
						code.name.substring(2).toLowerCase()
							.replace('_', '-'))
					writer.print(" code")
				}
				writer.println()
				val commentKey = errorCodeCommentKey(code)
				keys.add(commentKey)
				writer.print(commentKey)
				writer.print('=')
				val comment = properties.getProperty(commentKey)
				if (comment !== null)
				{
					writer.print(escape(comment))
				}
				writer.println()
			}
		}
		for (property in properties.keys)
		{
			val key = property as String
			if (!keys.contains(key))
			{
				keys.add(key)
				writer.print(key)
				writer.print('=')
				writer.println(escape(properties.getProperty(key)))
			}
		}
	}

	companion object
	{
		/**
		 * Check if all [error codes][AvailErrorCode] are reachable from
		 * [primitive][Primitive] [failure variable
		 * types][Primitive.failureVariableType].
		 *
		 * @return
		 *   `true` if all error codes are reachable, `false` otherwise.
		 */
		private fun allErrorCodesAreReachableFromPrimitives(): Boolean
		{
			// This forces initialization of Avail.
			specialObjects()
			var allErrorCodes = emptySet()
			for (code in AvailErrorCode.values())
			{
				if (!code.isCausedByInstructionFailure)
				{
					allErrorCodes = allErrorCodes.setWithElementCanDestroy(
						code.numericCode(),
						true)
				}
			}
			var reachableErrorCodes = emptySet()
			for (primitiveNumber in 1 .. maxPrimitiveNumber())
			{
				val primitive = byPrimitiveNumberOrNull(primitiveNumber)
				if (primitive != null && !primitive.hasFlag(Primitive.Flag.CannotFail))
				{
					val failureType: A_Type = primitive.failureVariableType
					if (failureType.isEnumeration)
					{
						reachableErrorCodes = reachableErrorCodes.setUnionCanDestroy(
							failureType.instances(),
							true)
					}
					else if (failureType.isSubtypeOf(mostGeneralVariableType()))
					{
						// This supports P_CatchException, which hides its error
						// codes inside a variable type.
						reachableErrorCodes = reachableErrorCodes.setUnionCanDestroy(
							failureType.readType().instances(),
							true)
					}
				}
			}
			val unreachableErrorCodes =
				allErrorCodes.setMinusCanDestroy(reachableErrorCodes, true)
			if (unreachableErrorCodes.setSize() != 0)
			{
				val unreachable =
					EnumSet.noneOf(AvailErrorCode::class.java)
				for (code in unreachableErrorCodes)
				{
					unreachable.add(byNumericCode(code.extractInt()))
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
			val languages: Array<String> =
				if (args.isNotEmpty()) args
				else arrayOf(System.getProperty("user.language"))
			if (allErrorCodesAreReachableFromPrimitives())
			{
				for (language in languages)
				{
					val generator = ErrorCodeNamesGenerator(Locale(language))
					generator.generate()
				}
			}
		}
	}
}
