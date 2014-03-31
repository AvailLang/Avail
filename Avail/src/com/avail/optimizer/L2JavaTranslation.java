/**
 * L2JavaTranslation.java
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

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.AvailObject;

/**
 * This class represents a translation of a level two continuation into
 * equivalent Java code.  Its execution should be the equivalent of interpreting
 * the level two code, which is itself an optimized version of the corresponding
 * level one raw function.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class L2JavaTranslation
{
	/**
	 * Start running the Java code produced from the level two code.  If all
	 * proceeds optimally, the result of the call will be returned.  If anything
	 * prevents this from happening (unusual continuation manipulation, soft
	 * stack limit being reached, interrupt requested), then a {@link
	 * ReifyStackThrowable} will be thrown, accumulating a list of level one
	 * continuations that represent, in aggregate, the current fiber's reified
	 * state.
	 *
	 * If the chunk behind this Java translation is still valid upon attempting
	 * to {@link #resume(A_Continuation)} a continuation, the continuation will
	 * indicate the level two instruction position at which to continue
	 * executing.  A switch statement with fall-throughs can be used to
	 * eliminate redundant portions of the code due to the effective plurality
	 * of entry points.
	 *
	 * @param args The arguments to the function
	 * @return
	 * @throws ReifyStackThrowable
	 */
	abstract AvailObject start (AvailObject... args)
	throws ReifyStackThrowable;

	/**
	 * Continue running the specified continuation.
	 *
	 * @param thisContinuation
	 * @return
	 * @throws ReifyStackThrowable
	 */
	abstract AvailObject resume (A_Continuation thisContinuation)
	throws ReifyStackThrowable;

}
