/*
 * P_SetImplicitObserveFunction.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.hooks

import avail.AvailRuntime.HookType.IMPLICIT_OBSERVE
import avail.descriptor.fiber.FiberDescriptor.TraceFlag
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.functions.FunctionDescriptor.Companion.createWithOuters1
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.descriptor.variables.VariableDescriptor
import avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelOne.L1InstructionWriter
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.primitive.Primitive1
import avail.utility.cast

/**
 * **Primitive:** Set the [function][FunctionDescriptor] to invoke whenever a
 * [variable][VariableDescriptor] with
 * [write&#32;reactors][VariableAccessReactor] is written when [write
 * tracing][TraceFlag.TRACE_VARIABLE_WRITES] is not enabled.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_SetImplicitObserveFunction : Primitive1(
	CannotFail,
	CanInline,
	HasSideEffect,
	WritesToHiddenGlobalState)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val function = arg1
		// Produce a wrapper that will invoke the supplied function, and then
		// specially resume the calling continuation (which won't be correctly
		// set up for a return).
		val wrapper = createWithOuters1(rawFunction, function.cast())
		// Now set the wrapper as the implicit observe function.
		runtime[IMPLICIT_OBSERVE] = wrapper
		return nil
	}

	/** The [A_RawFunction] that wraps the supplied observe function. */
	private val rawFunction = createRawFunction()

	/**
	 * Create an [A_RawFunction] which has an outer that'll be supplied during
	 * function closure.  The outer is a user-supplied function which is itself
	 * given a function and a tuple of arguments to apply, after which this
	 * generated function will resume the continuation that was interrupted to
	 * invoke this primitive.
	 *
	 * @return The one-outer, two-argument raw function.
	 */
	private fun createRawFunction(): A_RawFunction
	{
		val code = with(L1InstructionWriter(nil, 0, nil)) {
			val outerIndex = createOuter(IMPLICIT_OBSERVE.functionType)
			argumentTypes(mostGeneralFunctionType, mostGeneralTupleType)
			returnType = bottom
			returnTypeIfPrimitiveFails = bottom
			write(0, L1Operation.L1_doPushOuter, outerIndex)
			write(0, L1Operation.L1_doPushLocal, 1)
			write(0, L1Operation.L1_doPushLocal, 2)
			write(0, L1Operation.L1_doMakeTuple, 2)
			write(
				0,
				L1Operation.L1_doCall,
				addLiteral(SpecialMethodAtom.APPLY.bundle),
				addLiteral(TOP()))
			write(0, L1Operation.L1_doPop)
			write(0, L1Operation.L1Ext_doPushLabel)
			write(
				0,
				L1Operation.L1_doCall,
				addLiteral(SpecialMethodAtom.CONTINUATION_CALLER.bundle),
				addLiteral(variableTypeFor(mostGeneralContinuationType)))
			write(
				0,
				L1Operation.L1_doCall,
				addLiteral(SpecialMethodAtom.GET_VARIABLE.bundle),
				addLiteral(mostGeneralContinuationType))
			write(
				0,
				L1Operation.L1_doCall,
				addLiteral(SpecialMethodAtom.RESUME_CONTINUATION.bundle),
				addLiteral(bottom))
			compiledCode()
		}
		code.methodName = stringFrom("«implicit observe function wrapper»")
		return code
	}

	/**
	 * The function outers could contain an escaped variable that becomes
	 * shared.
	 */
	override fun mightMakeEscapedVariableShared(
		argumentTypes: List<A_Type>
	): Boolean = true

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(IMPLICIT_OBSERVE.functionType), TOP())
}
