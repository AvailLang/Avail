/**
 * ReifyStackThrowable.java
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

package com.avail.optimizer;

import java.util.ArrayList;
import java.util.List;
import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.descriptor.NilDescriptor;

/**
 * The level two execution machinery allows limited use of the Java stack during
 * ordinary execution, but when exceptional conditions arise, the Java stack is
 * unwound with a {@code ReifyStackThrowable} and converted into level one
 * continuations.  This happens when the stack gets too deep, when tricky code
 * like exceptions and backtracking happen, when taking an off-ramp to reify the
 * level one state, or when attempting to continue invalidated level two code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ReifyStackThrowable
extends Exception
{
	/** The serial version identifier. */
	private static final long serialVersionUID = 7279470906495791301L;

	/**
	 * The list of mutable level one continuations that have been reified so
	 * far.  They are added to the end during repeated throws until the
	 * interpreter catches the outermost throw and links the continuations
	 * together by making each continuation point to its predecessor in the
	 * list.
	 */
	final List<A_Continuation> continuationsNewestFirst = new ArrayList<>();

	/**
	 * Construct a new {@link ReifyStackThrowable}.
	 */
	public ReifyStackThrowable ()
	{
		// do nothing
	}

	/**
	 * Link together the mutable level one continuations that have already been
	 * pushed onto the {@link #continuationsNewestFirst} list.
	 *
	 * @param alreadyReifiedContinuation
	 *            The previously reified continuation just beyond the current
	 *            layers being reified.  Can be {@linkplain NilDescriptor#nil()}
	 *            to indicate the outermost execution frame.
	 * @return The fully assembled reified continuation.
	 */
	A_Continuation assembleContinuation (
		final A_Continuation alreadyReifiedContinuation)
	{
		A_Continuation current = alreadyReifiedContinuation;
		for (
			int index = continuationsNewestFirst.size() - 1;
			index >= 0;
			index--)
		{
			final A_Continuation next = continuationsNewestFirst.get(index);
			assert next.descriptor().isMutable();
			current = next.replacingCaller(current);
		}
		continuationsNewestFirst.clear();
		return current;
	}

	/**
	 * Push a mutable level one {@linkplain ContinuationDescriptor continuation}
	 * in such a way that anything pushed <em>after</em> this push will appear
	 * as this continuation's caller after calling {@link
	 * #assembleContinuation(A_Continuation)}.  The passed continuation must not
	 * only be mutable, but must have a {@linkplain A_Continuation#caller()
	 * caller} of {@linkplain NilDescriptor#nil() nil}.
	 *
	 * @param mutableContinuation The mutable continuation to push.
	 */
	void pushContinuation (
		final A_Continuation mutableContinuation)
	{
		assert mutableContinuation.descriptor().isMutable();
		assert mutableContinuation.caller().equalsNil();
		continuationsNewestFirst.add(mutableContinuation);
	}
}
