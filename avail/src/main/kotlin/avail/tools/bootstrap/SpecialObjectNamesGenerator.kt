/*
 * SpecialObjectNamesGenerator.kt
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

import avail.AvailRuntime.Companion.specialObjects
import avail.descriptor.representation.A_BasicObject
import avail.tools.bootstrap.BootstrapGenerator.Companion.checkedFormat
import avail.tools.bootstrap.Resources.escape
import avail.tools.bootstrap.Resources.specialObjectCommentKey
import avail.tools.bootstrap.Resources.specialObjectKey
import avail.tools.bootstrap.Resources.specialObjectTypeKey
import avail.tools.bootstrap.Resources.specialObjectsBaseName
import java.io.PrintWriter
import java.util.Locale
import java.util.Properties
import java.util.PropertyResourceBundle
import java.util.ResourceBundle

/**
 * Generate a [property resource bundle][PropertyResourceBundle] that specifies
 * unbound properties for the Avail names of the special objects.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [SpecialObjectNamesGenerator].
 *
 * @param locale
 *   The target [locale][Locale].
 */
internal class SpecialObjectNamesGenerator constructor(locale: Locale)
	: PropertiesFileGenerator(specialObjectsBaseName, locale)
{
	/**
	 * Write the names of the properties, whose unspecified values should be
	 * the Avail names of the corresponding special objects.
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
		specialObjects.indices.forEach { i ->
			if (specialObjects[i].notNil)
			{
				val specialObject: A_BasicObject = specialObjects[i]
				// Write a primitive descriptive of the special object as a
				// comment, to assist a human translator.
				val text = specialObject.toString().replace("\n", "\n#")
				print("# ")
				print(text)
				println()
				// Write the method name of the special object.
				val key = specialObjectKey(i)
				keys.add(key)
				print(key)
				print('=')
				val specialObjectName = properties.getProperty(key)
				if (specialObjectName !== null)
				{
					print(escape(specialObjectName))
				}
				println()
				// Write the preferred alias that Stacks should indicate.
				val typeKey = specialObjectTypeKey(i)
				keys.add(typeKey)
				print(typeKey)
				print('=')
				val type = properties.getProperty(typeKey, "")
				print(escape(type))
				println()
				// Write the Stacks comment.
				val commentKey = specialObjectCommentKey(i)
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
					val commentTemplate = preambleBundle.getString(
						Resources.Key.specialObjectCommentTemplate.name)
					val template: String = if (specialObject.isType)
					{
						Resources.Key.specialObjectCommentTypeTemplate.name
					}
					else
					{
						Resources.Key.specialObjectCommentValueTemplate.name
					}
					print(
						escape(
							checkedFormat(
								commentTemplate,
								preambleBundle.getString(template))))
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
				SpecialObjectNamesGenerator(Locale(language)).generate()
			}
		}
	}
}
