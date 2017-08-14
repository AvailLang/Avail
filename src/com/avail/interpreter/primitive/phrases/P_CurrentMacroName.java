/**
 * P_CurrentMacroName.java
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
package com.avail.interpreter.primitive.phrases;

import static com.avail.descriptor.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.MACRO_BUNDLE_KEY;
import static com.avail.descriptor.FiberDescriptor.GeneralFlag.*;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Answer the {@link A_Atom} for which a send node
 * is being macro-evaluated in the current fiber.  Fail if macro evaluation is
 * not happening in this fiber.
 */
public final class P_CurrentMacroName
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_CurrentMacroName().init(
			0, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 0;
		if (!interpreter.fiber().generalFlag(IS_EVALUATING_MACRO))
		{
			return interpreter.primitiveFailure(E_NOT_EVALUATING_MACRO);
		}
		final @Nullable AvailLoader loader = interpreter.fiber().availLoader();
		assert loader != null
			: "Macro expansion shouldn't be possible after loading";
		final A_Map fiberGlobals = interpreter.fiber().fiberGlobals();
		final A_Map clientData = fiberGlobals.mapAt(
			CLIENT_DATA_GLOBAL_KEY.atom);
		final A_Bundle currentMacroBundle = clientData.mapAt(
			MACRO_BUNDLE_KEY.atom);
		return interpreter.primitiveSuccess(currentMacroBundle.message());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.empty(),
			ATOM.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_NOT_EVALUATING_MACRO));
	}
}
