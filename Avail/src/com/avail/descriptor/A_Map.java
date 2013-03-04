/**
 * A_Map.java
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
 * {@code A_Tuple} is an interface that specifies the tuple-specific operations
 * that an {@link AvailObject} must implement.  It's a sub-interface of {@link
 * A_BasicObject}, the interface that defines the behavior that all AvailObjects are
 * required to support.
 *
 * <p>The purpose for A_BasicObject and its sub-interfaces is to allow sincere type
 * annotations about the basic kinds of objects that support or may be passed as
 * arguments to various operations.  The VM is free to always declare objects as
 * AvailObject, but in cases where it's clear that a particular object must
 * always be a tuple, a declaration of A_Tuple ensures that only the basic
 * object capabilities plus tuple-like capabilities are to be allowed.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Map
extends A_BasicObject
{
	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject mapAt (A_BasicObject keyObject);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map mapAtPuttingCanDestroy (
		A_BasicObject keyObject,
		A_BasicObject newValueObject,
		boolean canDestroy);

	/**
	 * Dispatch to the descriptor.
	 */
	int mapSize ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map mapWithoutKeyCanDestroy (
		A_BasicObject keyObject,
		boolean canDestroy);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean hasKey (A_BasicObject keyObject);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple valuesAsTuple ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Set keysAsSet ();
}
