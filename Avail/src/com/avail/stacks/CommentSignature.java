/**
 * CommentSignature.java
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
 * The defining characteristic of a comment as it pertains to the
 * implementation it describes.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class CommentSignature
{

	/**
	 * The name of the class/method the comment describes.
	 */
	final private String name;

	/**
	 * @return the implementation name.
	 */
	public String name()
	{
		return name;
	}

	/**
	 *  The module this implementation appears in.
	 */
	final private String module;

	/**
	 * @return the implementation module location.
	 */
	public String module()
	{
		return module;
	}

	/**
	 * Construct a new {@link CommentSignature}.
	 * @param name
	 * 		The name of the class/method the comment describes.
	 * @param module
	 * 		The module this implementation appears in.
	 *
	 */
	public CommentSignature (
		final String name,
		final String module)
	{
		this.name = name;
		this.module = module;
	}

	/**
	 * Create the HTML representation of the signature.
	 * @return
	 */
	public String toHTML ()
	{
		final StringBuilder stringBuilder = new StringBuilder()
			.append(tabs(2) + "<div "
				+ HTMLBuilder.tagClass(HTMLClass.classModuleLocation)
				+">")
			.append(module).append(": <strong>")
			.append(name).append("</strong></div>\n");
		return stringBuilder.toString();
	}

	/**
	 * @param numberOfTabs
	 * 		the number of tabs to insert into the string.
	 * @return
	 * 		a String consisting of the number of tabs requested in
	 * 		in numberOfTabs.
	 */
	public String tabs(final int numberOfTabs)
	{
		final StringBuilder stringBuilder = new StringBuilder();
		for (int i = 1; i <= numberOfTabs; i++)
		{
			stringBuilder.append("\t");
		}
		return stringBuilder.toString();
	}
}
