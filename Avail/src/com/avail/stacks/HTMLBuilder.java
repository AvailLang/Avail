/**
 * HTMLBuilder.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.stacks;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import com.avail.utility.IO;

/**
 * A class used to build HTML content from Stacks comments.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class HTMLBuilder
{
	/**
	 * The string contents of the properties file in question.
	 */
	final private String properties;

	/**
	 * The starting tab count
	 */
	final private int startingTabCount;

	/**
	 * The starting tab count
	 */
	private int currentTabCount;

	/**
	 * Increment the current tab count
	 */
	private void incrementCurrentTabCount()
	{
		currentTabCount++;
	}

	/**
	 * Increment the current tab count
	 */
	private void decrementCurrentTabCount()
	{
		currentTabCount--;
	}

	/**
	 * Construct a new {@link HTMLBuilder}.
	 *
	 * @param implementationProperties
	 * 		The file path location of the HTML properties used to generate
	 * 		the bulk of the inner html of the implementations.
	 * @param startingTabCount
	 * 		The number of tabs in to start with.
	 */
	public HTMLBuilder(final Path implementationProperties,
		final int startingTabCount)
	{
		this.properties =
			HTMLBuilder.getOuterHTMLTemplate(implementationProperties);

		this.startingTabCount = startingTabCount;
		this.currentTabCount = startingTabCount;
	}

	/**
	 * @param classString
	 * 		the html tag class for styling
	 * @param content
	 * 		the content between the tags
	 * @param tabCount
	 * 		the tabs
	 * @return
	 */
	public static String divClass(
		final String classString, final String content, final int tabCount)
	{
		final StringBuilder stringBuilder = new StringBuilder();

		return stringBuilder.toString();
	}

	/**
	 * @param numberOfTabs
	 * 		the number of tabs to insert into the string.
	 * @return
	 * 		a String consisting of the number of tabs requested in
	 * 		in numberOfTabs.
	 */
	private String tabs(final int numberOfTabs)
	{
		final StringBuilder stringBuilder = new StringBuilder();
		for (int i = 1; i <= numberOfTabs; i++)
		{
			stringBuilder.append('\t');
		}
		return stringBuilder.toString();
	}

	/**
	 * Add a class statement with the listed classes.
	 * @param classes
	 * 		The classes to add to the tag
	 * @return
	 */
	public static String tagClass(final String ... classes)
	{
		final StringBuilder stringBuilder = new StringBuilder()
			.append("class=\"");
		final int argumentCount = classes.length;
		for (int i = 0;  i < argumentCount - 1; i++)
		{
			stringBuilder.append(classes[i]).append(" ");
		}
		return stringBuilder
			.append(classes[argumentCount - 1])
			.append("\"")
			.toString();
	}

	/**
	 * Obtain a template file and return a string of that template
	 * @param templateFilePath
	 * 		The template file to obtain
	 * @return
	 * 		The string contents of that file.
	 */
	public static String getOuterHTMLTemplate (final Path templateFilePath)
	{
		try
		{
			final FileInputStream templateFile =
				new FileInputStream(templateFilePath.toString());
			final FileChannel channel =
				templateFile.getChannel();

			final ByteBuffer buf =
				ByteBuffer.allocate((int) channel.size());

			channel.read(buf);

			IO.close(channel);
			IO.close(templateFile);

			return new String(buf.array(), "UTF-8");
		}
		catch (final IOException e)
		{
			e.printStackTrace();
		}
		return null;
	}
}