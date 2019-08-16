/*
 * PropertiesFileGenerator.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.tools.bootstrap;

import com.avail.utility.UTF8ResourceBundleControl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

import static com.avail.tools.bootstrap.Resources.*;
import static com.avail.tools.bootstrap.Resources.Key.generatedPropertiesNotice;
import static com.avail.tools.bootstrap.Resources.Key.propertiesCopyright;
import static java.lang.String.format;
/**
 * {@code PropertiesFileGenerator} defines state and operations common to the
 * Avail properties file generators.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class PropertiesFileGenerator
{
	/** The base name of the {@linkplain ResourceBundle resource bundle}. */
	protected final String baseName;

	/** The target {@linkplain Locale locale}. */
	protected final Locale locale;

	/**
	 * The {@linkplain ResourceBundle resource bundle} that contains file
	 * preamble information.
	 */
	protected final ResourceBundle preambleBundle;

	/**
	 * Generate the preamble for the properties file. This includes the
	 * copyright and machine generation warnings.
	 *
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	protected void generatePreamble (final PrintWriter writer)
	{
		writer.println(MessageFormat.format(
			preambleBundle.getString(propertiesCopyright.name()),
			localName(baseName) + "_" + locale.getLanguage(),
			new Date()));
		writer.println(MessageFormat.format(
			preambleBundle.getString(generatedPropertiesNotice.name()),
			getClass().getName(),
			new Date()));
	}

	/**
	 * Write the names of the properties.
	 *
	 * @param properties
	 *        The existing {@linkplain Properties properties}. These should be
	 *        copied into the resultant {@linkplain ResourceBundle properties
	 *        resource bundle}.
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	protected abstract void generateProperties (
		final Properties properties,
		final PrintWriter writer);

	/**
	 * (Re)generate the target {@linkplain Properties properties} file.
	 *
	 * @throws IOException
	 *         If an exceptional situation arises while reading properties.
	 */
	public void generate () throws IOException
	{
		final File fileName = new File(format(
			"%s/%s_%s.properties",
			sourceBaseName,
			baseName.replace('.', '/'),
			locale.getLanguage()));
		System.out.println(fileName.getAbsolutePath());
		final String[] components = baseName.split("\\.");
		final File tempFileName = new File(format(
			"%s/%s_%s.propertiesTEMP",
			System.getProperty("java.io.tmpdir"),
			components[components.length - 1],
			locale.getLanguage()));
		assert fileName.getPath().endsWith(".properties");
		final Properties properties = new Properties();
		try (final FileInputStream inputStream = new FileInputStream(fileName))
		{
			try (final InputStreamReader reader =
				new InputStreamReader(inputStream, StandardCharsets.UTF_8))
			{
				properties.load(reader);
			}
		}
		catch (final FileNotFoundException e)
		{
			// Ignore. It's okay if the file doesn't already exist.
		}
		final PrintWriter writer = new PrintWriter(tempFileName, "UTF-8");
		generatePreamble(writer);
		generateProperties(properties, writer);
		writer.close();
		// Now switch the new file in.  In the rare event of failure between
		// these steps, the complete content will still be available in the
		// corresponding *.propertiesTEMP file.
		boolean worked = fileName.delete();
		if (!worked)
		{
			System.err.println(format(
				"deleting the original properties file failed: %s",
				fileName));
		}
		worked = tempFileName.renameTo(fileName);
		if (!worked)
		{
			throw new RuntimeException(format(
				"moving the temporary properties file failed: %s -> %s",
				tempFileName,
				fileName));
		}
	}

	/**
	 * Construct a new {@link PropertiesFileGenerator}.
	 *
	 * @param baseName
	 *        The base name of the {@linkplain ResourceBundle resource bundle}.
	 * @param locale
	 *        The target {@linkplain Locale locale}.
	 */
	protected PropertiesFileGenerator (
		final String baseName,
		final Locale locale)
	{
		this.baseName = baseName;
		this.locale = locale;
		this.preambleBundle = ResourceBundle.getBundle(
			preambleBaseName,
			locale,
			Resources.class.getClassLoader(),
			new UTF8ResourceBundleControl());
	}
}
