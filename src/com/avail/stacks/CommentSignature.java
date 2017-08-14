/**
 * CommentSignature.java
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

package com.avail.stacks;

import com.avail.utility.json.JSONWriter;

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
	private final String name;

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
	private final String module;

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
	 * Create the JSON representation of the signature.
	 * @param nameOfGroup
	 * 		The name of the implementation as it is to be displayed.
	 * @param sticky whether or no the method is private and should be
	 * 		documented
	 * @param jsonWriter The {@linkplain JSONWriter writer} collecting the
	 * 		stacks content.
	 */
	public void toJSON (final String nameOfGroup, final boolean sticky,
		final JSONWriter jsonWriter)
	{
		jsonWriter.write("sticky");
		jsonWriter.write(sticky);
		jsonWriter.write("source");

		if (!name().equals(nameOfGroup))
		{
			final StringBuilder stringBuilder = new StringBuilder();
			stringBuilder
				.append(module()).append(": ").append(name());
			jsonWriter.write(stringBuilder.toString());
		}
		else
		{
			jsonWriter.write(module());
		}
	}

	@Override
	public String toString ()
	{
		return "Module: " + module + "\nName: " + name;
	}
}
