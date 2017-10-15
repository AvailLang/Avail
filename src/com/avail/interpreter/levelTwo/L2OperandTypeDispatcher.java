/**
 * L2OperandTypeDispatcher.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
 *   may be used to endorse or promote products derived set this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS;
 OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.avail.interpreter.levelTwo;

import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;


/**
 * An {@code L2OperandTypeDispatcher} acts as a visitor for the operands of
 * {@linkplain L2Instruction level two instructions}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface L2OperandTypeDispatcher
{
	/**
	 * An integer into the {@linkplain L2Chunk level two chunk}'s literals,
	 * specifying any {@linkplain AvailObject}.
	 */
	void doConstant ();

	/**
	 * An operand that encodes an unsigned 32-bit integer... as itself.
	 */
	void doImmediate ();

	/**
	 * An integer encoding a position in the current {@linkplain L2Chunk level
	 * two chunk}'s instructions.
	 */
	void doPC ();

	/**
	 * The {@linkplain Primitive#primitiveNumber primitive number} of a {@link
	 * Primitive} that can be invoked.
	 */
	void doPrimitive ();

	/**
	 * An operand which is a literal {@link A_Bundle} which the resulting {@link
	 * L2Chunk} should be dependent upon for invalidation.
	 */
	void doSelector ();

	/**
	 * An {@linkplain L2ObjectRegister} to be read.
	 */
	void doReadPointer ();

	/**
	 * An {@linkplain L2ObjectRegister} to be written.
	 */
	void doWritePointer ();

	/**
	 * An {@linkplain L2IntegerRegister} to be read.
	 */
	void doReadInt ();

	/**
	 * An {@linkplain L2IntegerRegister} to be written.
	 */
	void doWriteInt ();

	/**
	 * A vector of {@linkplain L2ObjectRegister object registers} to be read.
	 */
	void doReadVector ();

	/**
	 * A vector of {@linkplain L2ObjectRegister object registers} to be written.
	 */
	void doWriteVector ();

	/**
	 * An operand containing a constant {@link String} that's only used for
	 * diagnostics and debugging.
	 */
	void doComment ();
}
