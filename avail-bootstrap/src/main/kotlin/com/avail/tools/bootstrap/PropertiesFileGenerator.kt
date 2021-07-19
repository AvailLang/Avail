/*
 * PropertiesFileGenerator.kt
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

import com.avail.tools.bootstrap.BootstrapGenerator.Companion.checkedFormat
import com.avail.tools.bootstrap.Resources.localName
import com.avail.tools.bootstrap.Resources.preambleBaseName
import com.avail.tools.bootstrap.Resources.sourceBaseName
import com.avail.utility.UTF8ResourceBundleControl
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.ResourceBundle

/**
 * `PropertiesFileGenerator` defines state and operations common to the Avail
 * properties file generators.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property baseName
 *   The base name of the [resource bundle][ResourceBundle].
 * @property locale
 *   The target [locale][Locale].
 *
 * @constructor
 * Construct a new [{@link ]PropertiesFileGenerator].
 *
 * @param baseName
 *   The base name of the [resource bundle][ResourceBundle].
 * @param locale
 *   The target [locale][Locale].
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class PropertiesFileGenerator protected constructor(
	protected val baseName: String,
	protected val locale: Locale)
{
	/**
	 * The [resource bundle][ResourceBundle] that contains file preamble
	 * information.
	 */
	protected val preambleBundle: ResourceBundle = ResourceBundle.getBundle(
		preambleBaseName,
		locale,
		Resources::class.java.classLoader,
		UTF8ResourceBundleControl())

	/**
	 * Generate the preamble for the properties file. This includes the
	 * copyright and machine generation warnings.
	 *
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	protected fun generatePreamble(writer: PrintWriter) = with(writer) {
		println(
			checkedFormat(
				preambleBundle.getString(
					Resources.Key.propertiesCopyright.name),
				localName(baseName) + "_" + locale.language,
				Date())
		)
		println(
			checkedFormat(
				preambleBundle.getString(
					Resources.Key.generatedPropertiesNotice.name),
				javaClass.name,
				Date()))
	}

	/**
	 * Write the names of the properties.
	 *
	 * @param properties
	 *   The existing [properties][Properties]. These should be copied into the
	 *   resultant [properties resource bundle][ResourceBundle].
	 * @param writer
	 *   The [output stream][PrintWriter].
	 */
	protected abstract fun generateProperties(
		properties: Properties,
		writer: PrintWriter)

	/**
	 * (Re)generate the target [properties][Properties] file.
	 *
	 * @throws IOException
	 *   If an exceptional situation arises while reading properties.
	 */
	@Throws(IOException::class)
	fun generate()
	{
		val fileName = File(String.format(
			"%s/%s_%s.properties",
			sourceBaseName,
			baseName.replace('.', '/'),
			locale.language))
		println(fileName.absolutePath)
		val components = baseName.split("\\.".toRegex()).toTypedArray()
		val tempFileName = File(String.format(
			"%s/%s_%s.propertiesTEMP",
			System.getProperty("java.io.tmpdir"),
			components[components.size - 1],
			locale.language))
		assert(fileName.path.endsWith(".properties"))
		val properties = Properties()
		try
		{
			FileInputStream(fileName).use { inputStream ->
				InputStreamReader(inputStream, StandardCharsets.UTF_8)
					.use { properties.load(it) }
			}
		}
		catch (e: FileNotFoundException)
		{
			// Ignore. It's okay if the file doesn't already exist.
		}
		PrintWriter(tempFileName, StandardCharsets.UTF_8.name()).use { writer ->
			generatePreamble(writer)
			generateProperties(properties, writer)
		}
		// Now switch the new file in.  In the rare event of failure between
		// these steps, the complete content will still be available in the
		// corresponding *.propertiesTEMP file.
		var worked = fileName.delete()
		if (!worked)
		{
			System.err.println(
				"deleting the original properties file failed: $fileName")
		}
		worked = tempFileName.renameTo(fileName)
		if (!worked)
		{
			throw RuntimeException(
				"moving the temporary properties file failed: " +
					"$tempFileName -> $fileName")
		}
	}
}
