/**
 * L2_GET_INVALID_MESSAGE_SEND_FUNCTION.java
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

package com.avail.interpreter.levelTwo.operation;

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;
import com.avail.AvailRuntime;
import com.avail.descriptor.AbstractEnumerationTypeDescriptor;
import com.avail.descriptor.BottomTypeDescriptor;
import com.avail.descriptor.FunctionTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

/**
 * Store the {@linkplain AvailRuntime#invalidMessageSendFunction() invalid
 * message send function} into the supplied {@linkplain L2IntegerRegister
 * integer register}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_GET_INVALID_MESSAGE_SEND_FUNCTION
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_GET_INVALID_MESSAGE_SEND_FUNCTION().init(
			WRITE_POINTER.is("invalid message send function"));


	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ObjectRegister destination =
			instruction.writeObjectRegisterAt(0);
		destination.set(
			interpreter.runtime().invalidMessageSendFunction(),
			interpreter);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ObjectRegister destination =
			instruction.writeObjectRegisterAt(0);
		registerSet.typeAtPut(
			destination,
			FunctionTypeDescriptor.create(
				TupleDescriptor.from(
					AbstractEnumerationTypeDescriptor.withInstances(
						TupleDescriptor.from(
								E_NO_METHOD.numericCode(),
								E_NO_METHOD_DEFINITION.numericCode(),
								E_AMBIGUOUS_METHOD_DEFINITION.numericCode(),
								E_FORWARD_METHOD_DEFINITION.numericCode(),
								E_ABSTRACT_METHOD_DEFINITION.numericCode())
							.asSet())),
				BottomTypeDescriptor.bottom()),
			instruction);
	}
}
