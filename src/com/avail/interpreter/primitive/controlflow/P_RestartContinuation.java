/*
 * P_RestartContinuation.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.controlflow;

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operation.L2_RESTART_CONTINUATION;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationTypeDescriptor.mostGeneralContinuationType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED;

/**
 * <strong>Primitive:</strong> Restart the given {@linkplain
 * ContinuationDescriptor continuation}. Make sure it's a label-like
 * continuation rather than a call-like, because a call-like continuation
 * requires a value to be stored on its stack in order to resume it,
 * something this primitive does not do.
 */
public final class P_RestartContinuation
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_RestartContinuation().init(
			1,
			CannotFail,
			CanInline,
			CanSwitchContinuations,
			AlwaysSwitchesContinuation);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Continuation originalCon = interpreter.argument(0);

		final A_RawFunction code = originalCon.function().code();
		//TODO MvG - This should be a primitive failure.
		assert originalCon.stackp() == code.numSlots() + 1
			: "Continuation should have been a label- rather than "
			+ "call-continuation";
		assert originalCon.pc() == 0
			: "Continuation should have been a label- rather than "
			+ "call-continuation";

		// Move the (original) arguments from the continuation into
		// interpreter.argsBuffer.
		final int numArgs = code.numArgs();
		interpreter.argsBuffer.clear();
		for (int i = 1; i <= numArgs; i++)
		{
			interpreter.argsBuffer.add(originalCon.argOrLocalOrStackAt(i));
		}
		// The restart entry point expects the interpreter's reifiedContinuation
		// to be the label continuation's *caller*.
		interpreter.reifiedContinuation = originalCon.caller();
		interpreter.function = originalCon.function();
		interpreter.chunk = code.startingChunk();
		interpreter.offset = 0;
		interpreter.returnNow = false;
		interpreter.latestResult(null);
		return CONTINUATION_CHANGED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralContinuationType()),
			bottom());
	}

	@Override
	public boolean tryToGenerateSpecialPrimitiveInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final A_RawFunction rawFunction,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator,
		final CallSiteHelper callSiteHelper)
	{
		// A restart works with every continuation that is created by a label.
		// First, pop out of the Java stack frames back into the outer L2 run
		// loop (which saves/restores the current frame and continues at the
		// next L2 instruction).
		translator.addInstruction(
			L2_RESTART_CONTINUATION.instance,
			arguments.get(0));
		assert !translator.generator.currentlyReachable();
//		translator.startBlock(
//			translator.createBasicBlock(
//				"unreachable after L2_RESTART_CONTINUATION"));
		return true;
	}
}
