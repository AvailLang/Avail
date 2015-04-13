/**
 * StacksRaisesTag.java
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

package com.avail.stacks;

/**
 * The "@raises" tag in an Avail comment indicates an exception that is thrown
 * by the method.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksRaisesTag extends AbstractStacksTag
{
	/**
	 * The name of the exception.
	 */
	final private QuotedStacksToken exceptionName;

	/**
	 * The description of the exception.
	 */
	final private StacksDescription exceptionDescription;

	/**
	 * Construct a new {@link StacksRaisesTag}.
	 *
	 * @param exceptionName
	 * 		The name of the exception.
	 * @param exceptionDescription
	 */
	public StacksRaisesTag (
		final QuotedStacksToken exceptionName,
		final StacksDescription exceptionDescription)
	{
		this.exceptionDescription = exceptionDescription;
		this.exceptionName = exceptionName;
	}

	/**
	 * @return the exceptionDescription
	 */
	public StacksDescription exceptionDescription ()
	{
		return exceptionDescription;
	}

	/**
	 * @return the exceptionName
	 */
	public QuotedStacksToken exceptionName ()
	{
		return exceptionName;
	}

	@Override
	public String toHTML (final LinkingFileMap htmlFileMap,
		final int hashID, final StacksErrorLog errorLog, int position)
	{
		final StringBuilder exceptionBuilder = new StringBuilder();
		if (htmlFileMap.internalLinks().containsKey(exceptionName.lexeme()))
		{
			exceptionBuilder.append("<a ng-click=\"myParent().changeLinkValue('")
				.append(htmlFileMap.internalLinks().get(exceptionName.lexeme()))
				.append("')\" href=\"")
				.append(htmlFileMap.internalLinks().get(exceptionName.lexeme()))
				.append("\">")
				.append(exceptionName.toHTML(htmlFileMap, hashID, errorLog))
				.append("</a>");
		}
		else
		{
			exceptionBuilder
				.append(exceptionName.toHTML(htmlFileMap, hashID, errorLog));
		}

		final StringBuilder stringBuilder = new StringBuilder()
			.append(tabs(4) + "<tr "
				+ HTMLBuilder.tagClass(HTMLClass.classMethodParameters)
				+ ">\n")
			.append(tabs(5) + "<td "
				+ HTMLBuilder
					.tagClass(HTMLClass.classStacks, HTMLClass.classICode)
				+ ">")
			.append(exceptionBuilder)
			.append("</td>\n")
			.append(tabs(5) + "<td "
				+ HTMLBuilder
					.tagClass(HTMLClass.classStacks, HTMLClass.classIDesc)
				+ ">\n")
			.append(tabs(6) + exceptionDescription.toHTML(htmlFileMap, hashID,
				errorLog))
			.append("\n" + tabs(5) + "</td>\n")
			.append(tabs(4) + "</tr>\n");
		return stringBuilder.toString();
	}

}
