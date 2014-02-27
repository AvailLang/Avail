/**
 * StacksRaisesTag.java
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

import java.util.List;

/**
 * The "@raises" tag in an Avail comment indicates an exception that is thrown
 * by the method.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksRaisesTag extends AbstractStacksTag
{
	/**
	 * The description of the exception.
	 */
	final private List<AbstractStacksToken> exceptionDescription;

	/**
	 * The name of the exception.
	 */
	final private QuotedStacksToken exceptionName;

	/**
	 * Construct a new {@link StacksRaisesTag}.
	 *
	 * @param tag
	 * 		The Avail comment tag
	 * @param exceptionName
	 * 		The name of the exception.
	 * @param exceptionDescription
	 */
	public StacksRaisesTag (
		final KeywordStacksToken tag,
		final QuotedStacksToken exceptionName,
		final List<AbstractStacksToken> exceptionDescription)
	{
		super(tag);
		this.exceptionDescription = exceptionDescription;
		this.exceptionName = exceptionName;
	}

	/**
	 * @return the exceptionDescription
	 */
	public List<AbstractStacksToken> exceptionDescription ()
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

}
