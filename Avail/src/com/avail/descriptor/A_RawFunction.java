/**
 * A_RawFunction.java
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

package com.avail.descriptor;

import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.utility.evaluation.*;

/**
 * {@code A_RawFunction} is an interface that specifies the operations specific
 * to raw functions in Avail.  It's a sub-interface of {@link A_BasicObject},
 * the interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_RawFunction
extends A_BasicObject
{
	public void countdownToReoptimize(int value);

	public long totalInvocations ();

	public AvailObject literalAt (int index);

	public A_Type functionType ();

	public void decrementCountdownToReoptimize (Continuation0 continuation);

	public A_Tuple nybbles ();

	public A_Type localTypeAt (int index);

	public A_Type outerTypeAt (int index);

	public void setStartingChunkAndReoptimizationCountdown (
		L2Chunk chunk,
		long countdown);

	public int maxStackDepth ();

	public int numArgs ();

	/**
	 * Also defined in {@link A_Continuation}.
	 *
	 * @return
	 */
	public int numArgsAndLocalsAndStack ();

	public int numLiterals ();

	public int numLocals ();

	public int numOuters ();

	public int primitiveNumber();

	public L2Chunk startingChunk ();

	public void tallyInvocation ();

	/**
	 * Also defined in {@link A_Phrase} for block nodes.
	 *
	 * @return
	 */
	public int startingLineNumber ();

	public A_Module module ();

	public void setMethodName (A_String methodName);

	public A_String methodName ();
}
