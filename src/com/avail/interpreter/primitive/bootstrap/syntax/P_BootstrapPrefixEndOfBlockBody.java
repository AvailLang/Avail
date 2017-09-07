/**
 * P_BootstrapPrefixEndOfBlockBody.java
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

package com.avail.interpreter.primitive.bootstrap.syntax;

import static com.avail.descriptor.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_MAP_KEY;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_STACK_KEY;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

/**
 * The {@code P_BootstrapPrefixEndOfBlockBody} primitive is used for
 * bootstrapping the {@link BlockNodeDescriptor block} syntax for defining
 * {@link FunctionDescriptor functions}.
 *
 * <p>It ensures that declarations introduced within the block body end scope
 * when the close bracket ("]") is encountered.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapPrefixEndOfBlockBody extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_BootstrapPrefixEndOfBlockBody().init(
			5, CanInline, Bootstrap);

	/** The key to the client parsing data in the fiber's environment. */
	final A_Atom clientDataKey = CLIENT_DATA_GLOBAL_KEY.atom;

	/** The key to the variable scope map in the client parsing data. */
	final A_Atom scopeMapKey = COMPILER_SCOPE_MAP_KEY.atom;

	/** The key to the tuple of scopes to pop as blocks complete parsing. */
	final A_Atom scopeStackKey = COMPILER_SCOPE_STACK_KEY.atom;

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 5;
//		final A_Phrase optionalArgumentDeclarations = args.get(0);
//		final A_Phrase optionalPrimitive = args.get(1);
//		final A_Phrase optionalLabel = args.get(2);
//		final A_Phrase statements = args.get(3);
//		final A_Phrase optionalReturnExpression = args.get(4);

		final A_Fiber fiber = interpreter.fiber();
		A_Map fiberGlobals = fiber.fiberGlobals();
		if (!fiberGlobals.hasKey(clientDataKey))
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		A_Map clientData = fiberGlobals.mapAt(clientDataKey);
		if (!clientData.hasKey(scopeMapKey))
		{
			// It looks like somebody removed all the scope information.
			return interpreter.primitiveFailure(E_INCONSISTENT_PREFIX_FUNCTION);
		}

		// Save the current scope map to a temp, pop the scope stack to replace
		// the scope map, then push the saved scope map onto the stack.  This
		// just exchanges the top of stack and current scope map.  The block
		// macro body will do its local declaration lookups in the top of stack,
		// then discard it when complete.
		final A_Map currentScopeMap = clientData.mapAt(scopeMapKey);
		A_Tuple stack = clientData.mapAt(scopeStackKey);
		final A_Map poppedScopeMap = stack.tupleAt(stack.tupleSize());
		stack = stack.tupleAtPuttingCanDestroy(
			stack.tupleSize(), currentScopeMap, true);
		clientData = clientData.mapAtPuttingCanDestroy(
			scopeMapKey, poppedScopeMap, true);
		clientData = clientData.mapAtPuttingCanDestroy(
			scopeStackKey, stack, true);
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			clientDataKey, clientData, true);
		fiber.fiberGlobals(fiberGlobals.makeShared());
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.functionType(
			TupleDescriptor.tuple(
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional arguments section. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Arguments are present. */
						TupleTypeDescriptor.oneOrMoreOf(
							/* An argument. */
							TupleTypeDescriptor.tupleTypeForTypes(
								/* Argument name, a token. */
								TOKEN.o(),
								/* Argument type. */
								InstanceMetaDescriptor.anyMeta())))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional primitive declaration. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Primitive declaration */
						TupleTypeDescriptor.tupleTypeForTypes(
							/* Primitive name. */
							TOKEN.o(),
							/* Optional failure variable declaration. */
							TupleTypeDescriptor.zeroOrOneOf(
								/* Primitive failure variable parts. */
								TupleTypeDescriptor.tupleTypeForTypes(
									/* Primitive failure variable name token */
									TOKEN.o(),
									/* Primitive failure variable type */
									InstanceMetaDescriptor.anyMeta()))))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional label declaration. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Label parts. */
						TupleTypeDescriptor.tupleTypeForTypes(
							/* Label name */
							TOKEN.o(),
							/* Optional label return type. */
							TupleTypeDescriptor.zeroOrOneOf(
								/* Label return type. */
								InstanceMetaDescriptor.topMeta())))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Statements and declarations so far. */
					TupleTypeDescriptor.zeroOrMoreOf(
						/* The "_!" mechanism wrapped each statement inside a
						 * literal phrase, so expect a phrase here instead of
						 * TOP.o().
						 */
						STATEMENT_NODE.mostGeneralType())),
				/* Optional return expression */
				LIST_NODE.create(
					TupleTypeDescriptor.zeroOrOneOf(
						PARSE_NODE.create(ANY.o())))),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.enumerationWith(
			SetDescriptor.set(
				E_LOADING_IS_OVER,
				E_INCONSISTENT_PREFIX_FUNCTION));
	}
}
