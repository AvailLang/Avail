/**
 * StacksCodeTag.java
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

import com.avail.utility.json.JSONWriter;

/**
 * The Avail comment "@code" tag. This is used for code like syntax styles.
 *
 * THIS IS LIKELY NOT USED AS IT IS A TAG IN DESCRIPTION TEXT
 * DEPRICATED -- DELETE ME
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksCodeTag extends AbstractStacksTag
{
	/**
	 * The text that is intended to be styled as code.  Can either be quoted
	 * text or numerical value.  Multiple tokens should be quoted.
	 */
	final private AbstractStacksToken codeStyledText;

	/**
	 * Construct a new {@link StacksCodeTag}.
	 *
	 * @param codeStyledText
	 * 		The text that is intended to be styled as code.  Can either be
	 * 		quoted text or numerical value.  Multiple tokens should be quoted.
	 */
	public StacksCodeTag (
		final AbstractStacksToken codeStyledText)
	{
		this.codeStyledText = codeStyledText;
	}

	/**
	 * @return the codeStyledText
	 */
	public AbstractStacksToken codeStyledText ()
	{
		return codeStyledText;
	}

	@Override
	public void toJSON (
		final LinkingFileMap linkingFileMap,
		final int hashID,
		final StacksErrorLog errorLog,
		final int position,
		final JSONWriter jsonWriter)
	{
		//DO NOTHING
	}

	@Override
	public String toString ()
	{
		return this.getClass().getSimpleName();
	}
}
