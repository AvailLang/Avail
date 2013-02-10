/**
 * P_249_SimpleMacroDeclaration.java
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
package com.avail.interpreter.primitive;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.PARSE_NODE;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import static com.avail.interpreter.Primitive.Result.*;
import java.util.List;
import com.avail.*;
import com.avail.descriptor.*;
import com.avail.exceptions.*;
import com.avail.interpreter.*;
import com.avail.utility.Continuation0;

/**
 * <strong>Primitive 249:</strong> Simple macro definition.
 */
public class P_249_SimpleMacroDeclaration
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_249_SimpleMacroDeclaration().init(2, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final AvailObject string = args.get(0);
		final AvailObject function = args.get(1);
		final AvailObject blockType = function.kind();
		final AvailObject tupleType = blockType.argsTupleType();
		final AvailObject fiber = FiberDescriptor.current();
		final AvailLoader loader = fiber.availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		for (int i = function.code().numArgs(); i >= 1; i--)
		{
			if (!tupleType.typeAtIndex(i).isSubtypeOf(
				PARSE_NODE.mostGeneralType()))
			{
				//TODO[MvG] Get rid of this requirement.
//				**** No longer true.  For example, an ellipsis should produce
//				**** a token, and a statically-evaluated expression (_†) should
//				**** produce a value (typically a type), not a phrase.
//				return interpreter.primitiveFailure(
//					E_MACRO_ARGUMENT_MUST_BE_A_PARSE_NODE);
			}
		}

		interpreter.primitiveSuspend();
		AvailRuntime.current().whenLevelOneSafeDo(
			AvailTask.forUnboundFiber(
				fiber,
				new Continuation0()
				{
					@Override
					public void value ()
					{
						Result state;
						AvailObject result;
						try
						{
							loader.addMacroBody(
								loader.lookupName(string),
								function);
							function.code().setMethodName(
								StringDescriptor.from(
									String.format("Macro body of %s", string)));
							state = SUCCESS;
							result = NilDescriptor.nil();
						}
						catch (
							final AmbiguousNameException|SignatureException e)
						{
							state = FAILURE;
							result = e.errorValue();
						}
						Interpreter.resumeFromPrimitive(
							AvailRuntime.current(),
							fiber,
							state,
							result);
					}
				}));
		return FIBER_SUSPENDED;
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.stringTupleType(),
				FunctionTypeDescriptor.forReturnType(
					PARSE_NODE.mostGeneralType())),
			TOP.o());
	}
}