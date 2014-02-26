/**
 * StacksParameterTag.java
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
 * The contents of an Avail comment "@param" tag
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksParameterTag extends AbstractStacksTag
{
	/**
	 * The type of the parameter
	 */
	final private QuotedStacksToken paramType;

	/**
	 * The description of the parameter.
	 */
	final private List<AbstractStacksToken> paramDescription;

	/**
	 * The name of the parameter variable.
	 */
	final private QuotedStacksToken paramName;

	/**
	 * Construct a new {@link StacksParameterTag}.
	 *
	 * @param tag
	 * 		The Avail comment tag
	 * @param paramType
	 * 		The type of the parameter
	 * @param paramDescription
	 * 		The description of the parameter.
	 * @param paramName
	 * 		The name of the parameter variable.
	 */
	public StacksParameterTag (final KeywordStacksToken tag,
		final QuotedStacksToken paramType,
		final List<AbstractStacksToken> paramDescription,
		final QuotedStacksToken paramName)
	{
		super(tag);
		this.paramType = paramType;
		this.paramDescription = paramDescription;
		this.paramName = paramName;
	}

	/**
	 * @return the returnDescription
	 */
	public List<AbstractStacksToken> paramDescription ()
	{
		return paramDescription;
	}

	/**
	 * @return the returnType
	 */
	public QuotedStacksToken paramType ()
	{
		return paramType;
	}

	/**
	 * @return the paramName
	 */
	public QuotedStacksToken paramName ()
	{
		return paramName;
	}
}
