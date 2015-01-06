/**
 * StacksGlobalTag.java
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
 * A global module variable comment tag
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksGlobalTag extends AbstractStacksTag
{
	/**
	 * The name of the global module variable
	 */
	final private QuotedStacksToken globalName;

	/**
	 * The name of the global module variable
	 */
	final private QuotedStacksToken globalType;

	/**
	 * Construct a new {@link StacksGlobalTag}.
	 * @param globalName
	 * 		The name of the global module variable
	 * @param globalType
	 * 		The name of the global module variable
	 *
	 */
	public StacksGlobalTag (
		final QuotedStacksToken globalName,
		final QuotedStacksToken globalType)
	{
		this.globalName = globalName;
		this.globalType = globalType;
	}

	/**
	 * @return the globalName
	 */
	public QuotedStacksToken globalName ()
	{
		return globalName;
	}

	/**
	 * @return the globalType
	 */
	public QuotedStacksToken globalType ()
	{
		return globalType;
	}

	@Override
	public String toHTML (final HTMLFileMap htmlFileMap,
		final int hashID, final StacksErrorLog errorLog, int position)
	{
		return globalName.toHTML(htmlFileMap, hashID, errorLog);
	}

}
