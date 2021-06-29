/*
 * PrimitiveNamesGenerator.kt
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
package com.avail.tools.bootstrap

import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.instances
import com.avail.interpreter.Primitive
import com.avail.tools.bootstrap.Resources.escape
import com.avail.tools.bootstrap.Resources.primitiveCommentKey
import com.avail.tools.bootstrap.Resources.primitiveParameterNameKey
import com.avail.tools.bootstrap.Resources.primitivesBaseName
import java.io.PrintWriter
import java.text.MessageFormat
import java.util.Locale
import java.util.Properties
import java.util.PropertyResourceBundle
import java.util.ResourceBundle

/**
 * Generate a [property resource bundle][PropertyResourceBundle] that specifies
 * unbound properties for the Avail names of the [primitives][Primitive].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [PrimitiveNamesGenerator].
 *
 * @param locale
 *   The target [locale][Locale].
 */
class PrimitiveNamesGenerator constructor(locale: Locale)
	: PropertiesFileGenerator(primitivesBaseName, locale)
{
	/**
	 * Write the names of the properties, whose unspecified values should be
	 * the Avail names of the corresponding [primitives][Primitive].
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
		Primitive.holdersByName.forEach { (_, holder) ->
			val primitive = holder.primitive
			if (!primitive.hasFlag(Primitive.Flag.Private))
			{
				// Write a comment that gives the primitive number and its
				// arity.
				keys.add(primitive.javaClass.simpleName)
				format(
					"# %s : _=%d%n",
					primitive.name,
					primitive.argCount)
				// Write the primitive key and any name already associated with
				// it.
				print(primitive.javaClass.simpleName)
				print('=')
				val primitiveName = properties.getProperty(
					primitive.javaClass.simpleName)
				if (primitiveName !== null)
				{
					print(escape(primitiveName))
				}
				println()
				// Write each of the parameter keys and their previously
				// associated values.
				for (i in 1 .. primitive.argCount)
				{
					val argNameKey = primitiveParameterNameKey(primitive, i)
					keys.add(argNameKey)
					print(argNameKey)
					print('=')
					val argName = properties.getProperty(argNameKey)
					if (argName !== null)
					{
						print(escape(argName))
					}
					println()
				}
				// Write out the comment.
				val commentKey = primitiveCommentKey(primitive)
				keys.add(commentKey)
				print(commentKey)
				print('=')
				val comment = properties.getProperty(commentKey)
				if (comment !== null && comment.isNotEmpty())
				{
					print(escape(comment))
				}
				else
				{
					// Initialize the count of template parameters that occur in
					// the final value to 1, to account for the @method tag of
					// methodCommentTemplate.
					var templateParameters = 1
					val commentTemplate =
						preambleBundle.getString(
							Resources.Key.methodCommentTemplate.name)
					val parameters: String
					val argCount = primitive.argCount
					if (argCount > 0)
					{
						val parameterTemplate = preambleBundle.getString(
							Resources.Key.methodCommentParameterTemplate.name)
						val builder = StringBuilder(500)
						for (i in 0 until primitive.argCount)
						{
							builder.append(MessageFormat.format(
								parameterTemplate,
								"{$templateParameters}",
								"{${templateParameters + argCount}}"))
							templateParameters++
						}
						templateParameters += argCount
						parameters = builder.toString()
					}
					else
					{
						parameters = ""
					}
					// The return contributes one argument to the final
					// template.
					val returnsTemplate = preambleBundle.getString(
						Resources.Key.methodCommentReturnsTemplate.name)
					val returns = MessageFormat.format(
						returnsTemplate, "{$templateParameters}")
					templateParameters++
					// If the primitive failure type is an enumeration, then
					// exceptions contribute one argument to the final template
					// for each value. Otherwise, it just contributes one
					// argument. But if the primitive cannot fail, then no
					// arguments are contributed.
					val raises: String
					raises = if (!primitive.hasFlag(Primitive.Flag.CannotFail))
					{
						val raisesTemplate = preambleBundle.getString(
							Resources.Key.methodCommentRaisesTemplate.name)
						val failureType: A_Type = primitive.failureVariableType
						if (failureType.isEnumeration)
						{
							buildString {
								failureType.instances.forEach { _ ->
									append(
										MessageFormat.format(
											raisesTemplate,
											"{$templateParameters}"
										)
									)
									templateParameters++
								}
							}
						}
						else
						{
							MessageFormat.format(
								raisesTemplate, "{$templateParameters}")
						}
					}
					else
					{
						""
					}
					print(escape(MessageFormat.format(
						commentTemplate, parameters, returns, raises)))
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
			languages.forEach { language ->
				PrimitiveNamesGenerator(Locale(language)).generate()
			}
		}
	}
}
