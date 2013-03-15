/**
 * A_Variable.java
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

/**
 * {@code A_Variable} is an interface that specifies the behavior specific to
 * Avail {@linkplain VariableDescriptor variables} that an {@link AvailObject}
 * must implement.  It's a sub-interface of {@link A_BasicObject}, the interface
 * that defines the behavior that all AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Variable
extends A_BasicObject
{

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject value ();

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject getValue ();

	/**
	 * Dispatch to the descriptor.
	 */
	void setValue (A_BasicObject newValue);

	/**
	 * Dispatch to the descriptor.
	 */
	void setValueNoCheck (AvailObject newValue);

	/**
	 * @param newValue
	 * @return
	 */
	AvailObject getAndSetValue (AvailObject newValue);

	/**
	 * @param reference
	 * @param newValue
	 * @return
	 */
	boolean compareAndSwapValues (AvailObject reference, AvailObject newValue);

	/**
	 * @param addend
	 * @return
	 */
	A_Number fetchAndAddValue (A_Number addend);

	/**
	 * Dispatch to the descriptor.
	 */
	void clearValue ();

}