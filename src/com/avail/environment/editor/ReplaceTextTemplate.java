/*
 * ReplaceTextTemplate.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.environment.editor;

import com.avail.environment.AvailWorkbench;
import com.avail.environment.editor.utility.PrefixTrie;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * A {@code ReplaceTextTemplate} is a holder of replacement text for a given
 * key word.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class ReplaceTextTemplate
{
	/**
	 * The {@link PrefixTrie} that contains the templates.
	 */
	private final PrefixTrie<String> prefixTrie = new PrefixTrie<>();

	/**
	 * Answer the {@link PrefixTrie} that contains the templates.
	 *
	 * @return A {@code PrefixTrie}.
	 */
	public PrefixTrie<String> prefixTrie ()
	{
		return prefixTrie;
	}

	/**
	 * The {@link Properties} where the templates are stored.
	 */
	private final Properties properties = new Properties();

	/**
	 * The name of the {@link Properties} {@link File} where the templates are
	 * stored.
	 */
	private static final String propertiesFileName =
		AvailWorkbench.resourcePrefix + "template.properties";

	/**
	 * Populate the {@link ReplaceTextTemplate#prefixTrie} from the {@link
	 * Properties} file referenced by {@link #propertiesFileName}.
	 */
	public void initializeTemplatesFromPropertiesFile ()
	{
		try
		{
			final InputStream inputStream = ReplaceTextTemplate.class
				.getResourceAsStream(propertiesFileName);
			if (inputStream != null)
			{
				properties.load(inputStream);
			}
			populatePrefixTrie();
		}
		catch (final IOException e)
		{
			System.err.println(
				"Failed To Load Template Properties File; File Not Found");
		}
	}

	/**
	 * Populate the {@link ReplaceTextTemplate#prefixTrie} from {@link
	 * #properties}.
	 */
	private void populatePrefixTrie ()
	{
		properties.keySet().parallelStream().forEach((k ->
		{
			final String key = (String) k;
			prefixTrie.addWord(key, properties.getProperty(key));
		}));
	}

	/**
	 * Write the properties file to disk.
	 */
	public void savePropertiesToFile ()
	{
		try
		{
			final OutputStream output = new FileOutputStream(propertiesFileName);
			properties.store(output, "Inline code replace templates");
		}
		catch (final IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Add a template to the {@link ReplaceTextTemplate}.
	 *
	 * @param key
	 *        The key value to appear in the drop down.
	 * @param value
	 *        The replacement text.
	 * @return {@code true} if added; {@code false} otherwise.
	 */
	public boolean addTemplate (
		final String key,
		final String value)
	{
		if (properties.containsKey(key))
		{
			return false;
		}
		properties.setProperty(key, value);
		prefixTrie.addWord(key, value);
		return true;
	}

	/**
	 * Edit the template in {@link ReplaceTextTemplate}.
	 *
	 * @param key
	 *        The key value to change the template for.
	 * @param value
	 *        The new replacement text.
	 * @return {@code true} if changed; {@code false} otherwise.
	 */
	public boolean editTemplate (
		final String key,
		final String value)
	{
		if (!properties.containsKey(key))
		{
			return false;
		}
		properties.setProperty(key, value);
		populatePrefixTrie();
		return true;
	}

	/**
	 * Remove the indicated template from {@link ReplaceTextTemplate}.
	 *
	 * @param key
	 *        The property name to remove.
	 */
	public void removeTemplate (
		final String key)
	{
		if (properties.remove(key) != null)
		{
			populatePrefixTrie();
		}
	}
}
