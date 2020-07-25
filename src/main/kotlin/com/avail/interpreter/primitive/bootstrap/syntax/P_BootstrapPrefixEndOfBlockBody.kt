/*
 * P_BootstrapPrefixEndOfBlockBody.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.bootstrap.syntax

import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_MAP_KEY
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_STACK_KEY
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.phrases.BlockPhraseDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.TOKEN
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_INCONSISTENT_PREFIX_FUNCTION
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * The `P_BootstrapPrefixEndOfBlockBody` primitive is used for bootstrapping the
 * [block][BlockPhraseDescriptor] syntax for defining
 * [functions][FunctionDescriptor].
 *
 * It ensures that declarations introduced within the block body end scope when
 * the close bracket ("]") is encountered.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapPrefixEndOfBlockBody : Primitive(5, CanInline, Bootstrap)
{
	/** The key to the client parsing data in the fiber's environment.  */
	private val clientDataKey = CLIENT_DATA_GLOBAL_KEY.atom

	/** The key to the variable scope map in the client parsing data.  */
	private val scopeMapKey = COMPILER_SCOPE_MAP_KEY.atom

	/** The key to the tuple of scopes to pop as blocks complete parsing.  */
	private val scopeStackKey = COMPILER_SCOPE_STACK_KEY.atom

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(5)
		//	val optionalArgumentDeclarations : A_Phrase = interpreter.argument(0);
		//	val optionalPrimitive : A_Phrase = interpreter.argument(1);
		//	val A_Phrase optionalLabel : A_Phrase = interpreter.argument(2);
		//	val statements : A_Phrase= interpreter.argument(3);
		//	val optionalReturnExpression : A_Phrase = interpreter.argument(4);

		val fiber = interpreter.fiber()
		var fiberGlobals = fiber.fiberGlobals()
		if (!fiberGlobals.hasKey(clientDataKey))
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		}
		var clientData: A_Map = fiberGlobals.mapAt(clientDataKey)
		if (!clientData.hasKey(scopeMapKey))
		{
			// It looks like somebody removed all the scope information.
			return interpreter.primitiveFailure(E_INCONSISTENT_PREFIX_FUNCTION)
		}

		// Save the current scope map to a temp, pop the scope stack to replace
		// the scope map, then push the saved scope map onto the stack.  This
		// just exchanges the top of stack and current scope map.  The block
		// macro body will do its local declaration lookups in the top of stack,
		// then discard it when complete.
		val currentScopeMap = clientData.mapAt(scopeMapKey)
		var stack: A_Tuple = clientData.mapAt(scopeStackKey)
		val poppedScopeMap = stack.tupleAt(stack.tupleSize())
		stack =
			stack.tupleAtPuttingCanDestroy(
				stack.tupleSize(), currentScopeMap, true)
		clientData =
			clientData.mapAtPuttingCanDestroy(
				scopeMapKey, poppedScopeMap, true)
		clientData =
			clientData.mapAtPuttingCanDestroy(
				scopeStackKey, stack, true)
		fiberGlobals =
			fiberGlobals.mapAtPuttingCanDestroy(
				clientDataKey, clientData, true)
		fiber.setFiberGlobals(fiberGlobals.makeShared())
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional arguments section. */
					zeroOrOneOf(
						/* Arguments are present. */
						oneOrMoreOf(
							/* An argument. */
							tupleTypeForTypes(
								/* Argument name, a token. */
								TOKEN.o(),
								/* Argument type. */
								anyMeta())))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional primitive declaration. */
					zeroOrOneOf(
						/* Primitive declaration */
						tupleTypeForTypes(
							/* Primitive name. */
							TOKEN.o(),
							/* Optional failure variable declaration. */
							zeroOrOneOf(
								/* Primitive failure variable parts. */
								tupleTypeForTypes(
									/* Primitive failure variable name token */
									TOKEN.o(),
									/* Primitive failure variable type */
									anyMeta()))))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional label declaration. */
					zeroOrOneOf(
						/* Label parts. */
						tupleTypeForTypes(
							/* Label name */
							TOKEN.o(),
							/* Optional label return type. */
							zeroOrOneOf(
								/* Label return type. */
								topMeta())))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Statements and declarations so far. */
					zeroOrMoreOf(
						/* The "_!" mechanism wrapped each statement inside a
						 * literal phrase, so expect a phrase here instead of
						 * TOP.o().
						 */
						STATEMENT_PHRASE.mostGeneralType())),
				/* Optional return expression */
				LIST_PHRASE.create(
					zeroOrOneOf(
						PARSE_PHRASE.create(ANY.o())))),
			TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_LOADING_IS_OVER, E_INCONSISTENT_PREFIX_FUNCTION))
}
