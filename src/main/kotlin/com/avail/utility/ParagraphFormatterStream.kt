/*
 * ParagraphFormatterStream.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.utility;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * ParagraphFormatterStream wraps an Appendable with a ParagraphFormatter, so
 * that Strings can automatically be formatted by the ParagraphFormatter before
 * being appended to the output stream.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public class ParagraphFormatterStream
implements Appendable
{
	/** The text formatter that formats text prior to its appendage. */
	private final ParagraphFormatter formatter;

	/** The appender */
	private final Appendable appendable;

	/**
	 * Construct a new {@link ParagraphFormatterStream}.
	 *
	 * @param formatter The text formatter.
	 * @param appendable The Appendable that receives output text.
	 */
	public ParagraphFormatterStream (final ParagraphFormatter formatter,
		final Appendable appendable)
	{
		this.formatter = formatter;
		this.appendable = appendable;
	}

	@Override
	public Appendable append (final char c)
		throws IOException
	{
		String str = String.valueOf(c);
		str = formatter.format(str);
		return appendable.append(str);
	}

	@Override
	public Appendable append (final @Nullable CharSequence csq)
		throws IOException
	{
		String str = String.valueOf(csq);
		str = formatter.format(str);
		return appendable.append(str);
	}

	@Override
	public Appendable append (
			final @Nullable CharSequence csq,
			final int start,
			final int end)
		throws IOException
	{
		String str = String.valueOf(csq);
		str = str.substring(start, end);
		str = formatter.format(str);
		return appendable.append(str);
	}
}
