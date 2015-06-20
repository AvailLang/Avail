/**
 * P_409_BootstrapPrefixStartOfBlock.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.bootstrap;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * The {@code P_409_BootstrapPrefixStartOfBlock} primitive is triggered at the
 * start of parsing a block.  It pushes the current scope onto the scope stack
 * so that it can be popped again by the {@link P_404_BootstrapBlockMacro} when
 * the block parsing completes.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_409_BootstrapPrefixStartOfBlock
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_409_BootstrapPrefixStartOfBlock().init(
			0, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 0;

		final AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		final A_Atom clientDataGlobalKey = AtomDescriptor.clientDataGlobalKey();
		final A_Atom compilerScopeMapKey = AtomDescriptor.compilerScopeMapKey();
		final A_Atom compilerScopeStackKey =
			AtomDescriptor.compilerScopeStackKey();
		final A_Fiber fiber = interpreter.fiber();
		A_Map fiberGlobals = fiber.fiberGlobals();
		A_Map clientData = fiberGlobals.mapAt(clientDataGlobalKey);
		final A_Map bindings = clientData.mapAt(compilerScopeMapKey);
		A_Tuple stack = clientData.hasKey(compilerScopeStackKey)
			? clientData.mapAt(compilerScopeStackKey)
			: TupleDescriptor.empty();
		stack = stack.appendCanDestroy(bindings, false);
		clientData = clientData.mapAtPuttingCanDestroy(
			compilerScopeStackKey, stack, true);
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			clientDataGlobalKey, clientData, true);
		fiber.fiberGlobals(fiberGlobals.makeShared());
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(),
			TOP.o());
	}
}
