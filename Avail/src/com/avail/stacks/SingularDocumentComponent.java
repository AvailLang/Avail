/**
 * SingularDocumentComponent.java
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



/**
 * A singular piece of Avail method/class documentation. .e.g.
 * '@method "take from_until_"'
 *
 * @author Richard A Arriaga &lt;rich@availlang.org&gt;
 */
public final class SingularDocumentComponent extends DocumentComponent
{

	/**
	 * Construct a new {@link SingularDocumentComponent}.
	 *
	 * @param component The name of the documentation component.
	 * @param contents The contents of this particular component.
	 */
	public SingularDocumentComponent (
		final String component, final String contents)
	{
		super(component);
		this.contents = contents;
	}

	/**
	 * The contents of the component.  e.g "<any…|1..>"
	 */
	final String contents;


	@Override
	String createMinimizedJsonLine (final boolean endInComma)
	{
		if (endInComma)
			{return String.format("\"%s\":\"%s\",", component,contents);}
		return String.format("\"%s\":\"%s\"", component,contents);
	}

	@Override
	String createformatedJsonLine (final boolean endInComma, final int tabLevel)
	{
		if (endInComma)
			{return String.format("%s\"%s\" : \"%s\",\n",
				generateTabs(tabLevel),component,contents);}
		return String.format("%s\"%s\" : \"%s\"\n",
			generateTabs(tabLevel),component,contents);
	}
}
