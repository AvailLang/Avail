/**
 * DocumentComponent.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.stacks.builder.json;

/**
 * A piece of Avail method/class documentation. .e.g.
 * '@method "take from_until_"'
 *
 * @author Richard A Arriaga &lt;rich@availlang.org&gt;
 */
public abstract class DocumentComponent
{

	/**
	 *
	 * Construct a new {@link DocumentComponent}.
	 *
	 * @param component The name of the documentation component.
	 */
	public DocumentComponent (final String component)
	{
		this.component = component;
	}

	/**
	 * The name of the documentation component. e.g. "type".  This acts as the
	 * JSON property in a <a href="http://www.json.org/">JSON</a> file.
	 */
	final String component;

	/**
	 * Convert component and its contents into a
	 * <a href="http://www.json.org/">JSON</a> name/value pair.  The output is
	 * minimized to remove all readable formatting.
	 *
	 * @param endInComma boolean to determine if line will end in a comma ','
	 * @return the String JSON component.
	 */
	abstract String createMinimizedJsonLine (boolean endInComma);

	/**
	 * Convert component and its contents into a
	 * <a href="http://www.json.org/">JSON</a> name/value pair.  The output is
	 * formatted be more human readable.
	 *
	 * @param endInComma endInComma boolean to determine if line will end in a
	 * 	comma ','
	 * @param tabLevel number of tabs in to add to formated text.
	 * @return the String JSON component.
	 */
	abstract String createformatedJsonLine (boolean endInComma, int tabLevel);

	/**
	 * Create a string solely comprised of tabs.  This is used in the
	 * formatting of the JSON file.
	 *
	 * @param tabCount Number of tabs string should contain
	 * @return
	 */
	public static String generateTabs (final int tabCount)
	{
		final StringBuilder tabs = new StringBuilder();
		for (int i = 1; i <= tabCount ; i++)
		{
			tabs.append('\t');
		}
		return tabs.toString();
	}

}
