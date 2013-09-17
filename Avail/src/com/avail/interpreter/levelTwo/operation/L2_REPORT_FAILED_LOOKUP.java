/**
 * L2_REPORT_FAILED_LOOKUP.java
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
package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.ArrayList;
import java.util.List;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.DefinitionDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

/**
 * A method lookup failed, producing either zero or more than one most-specific
 * matching {@linkplain DefinitionDescriptor definition}.  Report the problem.
 */
public class L2_REPORT_FAILED_LOOKUP extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_REPORT_FAILED_LOOKUP().init(
			SELECTOR.is("method bundle"),
			READ_VECTOR.is("arguments"),
			CONSTANT.is("matching definitions"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final A_Bundle bundle = instruction.bundleAt(0);
		final List<L2ObjectRegister> arguments =
			instruction.readVectorRegisterAt(1).registers();
		final A_Set definitions = instruction.constantAt(2);

		// TODO[MvG]: Invoke the lookup failure handler function.
		final List<A_BasicObject> argumentValues =
			new ArrayList<>(arguments.size());
		for (final L2ObjectRegister argument : arguments)
		{
			argumentValues.add(argument.in(interpreter));
		}
		final A_Tuple argumentsTuple = TupleDescriptor.fromList(argumentValues);
		error(
			"lookup of method %s produced %d most-specific definitions"
			+ "for arguments: %s",
			bundle.message(),
			definitions.setSize(),
			argumentsTuple);
	}

	@Override
	public void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets)
	{
		// A lookup failure should invoke the Avail lookup failure handler,
		// so it doesn't continue running the current continuation.
		assert registerSets.size() == 0;
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		return false;
	}
}
