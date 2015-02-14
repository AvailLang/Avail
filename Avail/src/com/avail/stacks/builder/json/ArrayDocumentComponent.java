/**
 * ArrayDocumentComponent.java
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
 * An array of pieces of Avail method/class documentation. .e.g.<br>
 * Parameters:<br>
 * ('@param "aTuple" "tuple" A tuple') <br>
 * ('@param "predicate" "[⊥]→boolean" A function...')
 *
 * @author Rich A Arriaga &lt;rich@availlang.org&gt;
 */
public final class ArrayDocumentComponent extends DocumentComponent
{

	/**
	 * Construct a new {@link SingularDocumentComponent}.
	 *
	 * @param component The name of the documentation component.
	 * @param contents The contents of this particular component.
	 */
	public ArrayDocumentComponent (
		final String component, final DocumentComponent [] contents)
	{
		super(component);
		this.contents = contents;
	}

	/**
	 * An array of {@link DocumentComponent}
	 */
	final DocumentComponent [] contents;

	@Override
	String createMinimizedJsonLine (final boolean endInComma)
	{
		final StringBuilder sb = new StringBuilder(
			String.format("\"%s\":[", component));
		final int contentsSize = contents.length;
		for (int i = 0; i < contentsSize - 1 ; i++)
		{
			sb.append(
				contents[i].createMinimizedJsonLine(true));
		}

		sb.append(contents[contentsSize - 1].createMinimizedJsonLine(false));

		if (endInComma)
			{ return sb.append("],").toString(); }

		return sb.append(']').toString();
	}

	@Override
	String createformatedJsonLine (final boolean endInComma, final int tabLevel)
	{
		final String tabs = generateTabs(tabLevel);
		final StringBuilder sb = new StringBuilder(
			String.format("%s\"%s\" : [\n",tabs,component));
		final int contentsSize = contents.length;
		for (int i = 0; i < contentsSize - 1 ; i++)
		{
			sb.append(
				contents[i].createformatedJsonLine(
					true,
					tabLevel + 1));
		}

		sb.append(contents[contentsSize - 1]
			.createformatedJsonLine(
				true,
				tabLevel + 1));

		if (endInComma)
			{ return sb.append(String.format("\n%s],",tabs)).toString(); }

		return sb.append(String.format("\n%s]",tabs)).toString();
	}

}
