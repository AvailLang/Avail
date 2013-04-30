/**
 * A_Function.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import com.avail.interpreter.Interpreter;

/**
 * {@code A_Function} is an interface that specifies the operations specific to
 * functions in Avail.  It's a sub-interface of {@link A_BasicObject}, the
 * interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Function
extends A_BasicObject
{
	/**
	 * Answer the {@linkplain CompiledCodeDescriptor raw function (also known as
	 * compiled code)} on which this function is based.  The raw function holds
	 * the information that is common to all functions, and each function
	 * additionally holds zero or more captured variables and values from its
	 * lexical context.
	 *
	 * @return This function's raw function.
	 */
	A_RawFunction code ();

	/**
	 * Answer the number of lexically captured variables or constants held by
	 * this function.
	 *
	 * @return The number of outer variables captured by this function.
	 */
	int numOuterVars ();

	/**
	 * An outer variable or constant of this function has been used for the last
	 * time.  Replace it with {@linkplain NilDescriptor#nil() nil} if the
	 * function is mutable, and answer true.  If the function is immutable then
	 * something besides the {@link Interpreter} or a fiber's chain of
	 * {@linkplain ContinuationDescriptor continuations} might be referring to
	 * it, so answer false.
	 *
	 * @param index Which outer variable or constant is no longer needed.
	 * @return Whether the outer variable or constant was actually nilled.
	 */
	boolean optionallyNilOuterVar (int index);

	/**
	 * Answer this function's lexically captured variable or constant value that
	 * has the specified index.
	 *
	 * @param index Which outer variable or constant is of interest.
	 * @return The specified outer variable or constant of this function.
	 */
	AvailObject outerVarAt (int index);

	/**
	 * Set the specified captured variable/constant slot to the given variable
	 * or constant value.
	 *
	 * @param index Which variable or constant to initialize.
	 * @param value The value to write into this function.
	 */
	void outerVarAtPut (int index, AvailObject value);
}
