/**
 * interpreter/levelTwo/L2Interpreter.java Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

package com.avail.interpreter.levelTwo;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.Primitive.Flag.SpecialReturnConstant;
import static com.avail.interpreter.Primitive.Result.*;
import static java.lang.Math.max;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.descriptor.ProcessDescriptor.ExecutionState;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.*;

/**
 * This class is used to execute level two code.  It mostly exposes the
 * machinery
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
final public class L2Interpreter
extends Interpreter
implements L2OperationDispatcher
{
	/**
	 * The {@link L2ChunkDescriptor} being executed.
	 */
	AvailObject _chunk;

	/**
	 * The L2 instruction stream as a tuple of integers.
	 */
	AvailObject _chunkWords;

	/**
	 * This chunk's register vectors.  A register vector is a tuple of integers
	 * that represent {@link #_pointers Avail object registers}.
	 */
	AvailObject _chunkVectors;

	/**
	 * The registers that hold {@link AvailObject Avail objects}.
	 */
	AvailObject[] _pointers = new AvailObject[10];

	/**
	 * The 32-bit signed integer registers.
	 */
	int[] _integers = new int[10];

	/**
	 * The double-precision floating point registers.
	 */
	double[] _doubles = new double[10];

	/**
	 * The current pointer into {@link #_chunkWords}, the level two instruction
	 * stream.
	 *
	 */
	int _offset;

	/**
	 * A reusable temporary buffer used to hold arguments during method
	 * invocations.
	 */
	List<AvailObject> _argsBuffer = new ArrayList<AvailObject>();

	/**
	 * Whether or not execution has completed.
	 */
	boolean _exitNow = false;

	/**
	 * The value returned by the outermost continuation.
	 */
	AvailObject _exitValue;

	/**
	 * The dispatcher used to simulate level one instructions via {@link
	 * #L2_doInterpretOneInstruction()}.
	 */
	L1OperationDispatcher levelOneDispatcher =
		new L1OperationDispatcher()
	{
		/**
		 * [n] - Send the message at index n in the compiledCode's literals. Pop the
		 * arguments for this message off the stack (the message itself knows how
		 * many to expect). The first argument was pushed first, and is the deepest
		 * on the stack. Use these arguments to look up the method dynamically.
		 * Before invoking the method, push the void object onto the stack. Its
		 * presence will help distinguish continuations produced by the pushLabel
		 * instruction from their senders. When the call completes (if ever) by
		 * using an implicit return instruction, it will replace this void object
		 * with the result of the call.
		 */
		@Override
		public void L1_doCall()
		{
			final AvailObject cont = pointerAt(callerRegister());
			// Look up the method implementations.  They all know numArgs.
			final AvailObject implementations =
				cont.closure().code().literalAt(getInteger());
			final AvailObject expectedReturnType =
				cont.closure().code().literalAt(getInteger());
			final AvailObject matching =
				implementations.lookupByValuesFromContinuationStackp(
					cont,
					cont.stackp());
			if (matching.equalsVoid())
			{
				//TODO: Debug
				System.out.print(cont);
				System.out.print(cont.closure());
				error("Ambiguous or invalid lookup");
				return;
			}
			if (matching.isForward())
			{
				error("Attempted to execute forward method " +
					"before it was defined.");
				return;
			}
			if (matching.isAbstract())
			{
				error("Attempted to execute an abstract method.");
				return;
			}
			final AvailObject theClosure = matching.bodyBlock();
			final AvailObject theCode = theClosure.code();
			// Call the method...
			final short nArgs = matching.bodySignature().numArgs();
			_argsBuffer.clear();
			for (int i = 1; i <= nArgs; i++)
			{
				// Reverse order - i.e., _argsBuffer.get(0) was pushed first.
				final int stackIndex = cont.stackp() + nArgs - i;
				_argsBuffer.add(cont.stackAt(stackIndex));
				cont.stackAtPut(stackIndex, VoidDescriptor.voidObject());
			}
			cont.stackp(cont.stackp() + nArgs - 1);
			cont.stackAtPut(cont.stackp(), expectedReturnType);
			// Leave the expected return type pushed on the stack.  This will be
			// used when the method returns, and it also helps distinguish label
			// continuations from call continuations.
			final short primNum = theCode.primitiveNumber();
			if (primNum != 0)
			{
				assert _chunk == L2ChunkDescriptor.chunkFromId (
						_pointers[callerRegister()].levelTwoChunkIndex());
				final Result primResult = attemptPrimitive(
					primNum,
					_argsBuffer);
				if (primResult == CONTINUATION_CHANGED)
				{
					return;
				}
				if (primResult == SUCCESS)
				{
					if (!primitiveResult.isInstanceOfSubtypeOf(
						expectedReturnType))
					{
						error("Primitive result did not agree " +
							"with expected type");
					}
					final AvailObject callerCont = _pointers[callerRegister()];
					callerCont.stackAtPut(callerCont.stackp(), primitiveResult);
					return;
				}
			}
			// Either not a primitive or else a failed primitive.
			invokeWithoutPrimitiveClosureArguments(theClosure, _argsBuffer);
		}

		/**
		 * [n] - Push the literal indexed by n in the current compiledCode.
		 */
		@Override
		public void L1_doPushLiteral ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject constant = cont.closure().code().literalAt(index);
			final int stackp = cont.stackp() - 1;
			cont.stackp(stackp);
			// We don't need to make constant beImmutable because *code objects*
			// are always immutable.
			cont.stackAtPut(stackp, constant);
		}

		/**
		 * [n] - Push the argument (actual value) or local variable (the
		 * variable itself) indexed by n. Since this is known to be the last use
		 * (nondebugger) of the argument or local, void that slot of the current
		 * continuation.
		 */
		@Override
		public void L1_doPushLastLocal ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject variable = cont.localOrArgOrStackAt(index);
			cont.localOrArgOrStackAtPut(index, VoidDescriptor.voidObject());
			final int stackp = cont.stackp() - 1;
			cont.stackp(stackp);
			cont.stackAtPut(stackp, variable);
		}

		/**
		 * [n] - Push the argument (actual value) or local variable (the
		 * variable itself) indexed by n.
		 */
		@Override
		public void L1_doPushLocal ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject variable = cont.localOrArgOrStackAt(index);
			variable.makeImmutable();
			final int stackp = cont.stackp() - 1;
			cont.stackp(stackp);
			cont.stackAtPut(stackp, variable);
		}

		/**
		 * [n] - Push the outer variable indexed by n in the current closure. If
		 * the variable is mutable, clear it (no one will know). If the variable
		 * and closure are both mutable, remove the variable from the closure by
		 * voiding it.
		 */
		@Override
		public void L1_doPushLastOuter ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject variable = cont.closure().outerVarAt(index);
			if (variable.equalsVoid())
			{
				error("Someone prematurely erased this outer var");
				return;
			}
			if (!cont.closure().optionallyNilOuterVar(index))
			{
				variable.makeImmutable();
			}
			final int stackp = cont.stackp() - 1;
			cont.stackp(stackp);
			cont.stackAtPut(stackp, variable);
		}

		/**
		 * [n,m] - Pop the top n items off the stack, and use them as outer
		 * variables in the construction of a closure based on the compiledCode
		 * that's the literal at index m of the current compiledCode.
		 */
		@Override
		public void L1_doClose ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int numCopiedVars = getInteger();
			final AvailObject codeToClose = cont.closure().code()
			.literalAt(getInteger());
			final int stackp = cont.stackp() + numCopiedVars - 1;
			cont.stackp(stackp);
			final AvailObject newClosure = ClosureDescriptor.mutable().create(
				numCopiedVars);
			newClosure.code(codeToClose);
			int stackIndex = stackp;
			for (int i = 1; i <= numCopiedVars; i++)
			{
				newClosure.outerVarAtPut(i, cont.stackAt(stackIndex));
				cont.stackAtPut(stackIndex, VoidDescriptor.voidObject());
				stackIndex--;
			}
			/*
			 * We don't assert assertObjectUnreachableIfMutable: on the popped
			 * copied vars because each copied var's new reference from the
			 * closure balances the lost reference from the wiped stack.
			 * Likewise we don't tell them makeImmutable(). The closure itself
			 * should remain mutable at this point, otherwise the copied vars
			 * would have to makeImmutable() to be referenced by an immutable
			 * closure.
			 */
			cont.stackAtPut(stackp, newClosure);
		}

		/**
		 * [n] - Pop the stack and assign this value to the local variable (not
		 * an argument) indexed by n (index 1 is first argument).
		 */
		@Override
		public void L1_doSetLocal ()
		{

			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject variable = cont.localOrArgOrStackAt(index);
			final AvailObject value = cont.stackAt(cont.stackp());
			final int stackp = cont.stackp();
			cont.stackAtPut(stackp, VoidDescriptor.voidObject());
			cont.stackp(stackp + 1);
			// The value's reference from the stack is now from the variable.
			variable.setValue(value);
		}

		/**
		 * [n] - Push the value of the local variable (not an argument) indexed
		 * by n (index 1 is first argument). If the variable itself is mutable,
		 * clear it now - nobody will know.
		 */
		@Override
		public void L1_doGetLocalClearing ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject variable = cont.localOrArgOrStackAt(index);
			final AvailObject value = variable.getValue();
			if (variable.traversed().descriptor().isMutable())
			{
				variable.clearValue();
			}
			else
			{
				value.makeImmutable();
			}
			final int stackp = cont.stackp() - 1;
			cont.stackp(stackp);
			cont.stackAtPut(stackp, value);
		}

		/**
		 * [n] - Push the outer variable indexed by n in the current closure.
		 */
		@Override
		public void L1_doPushOuter ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject variable = cont.closure().outerVarAt(index);
			if (variable.equalsVoid())
			{
				error("Someone prematurely erased this outer var");
				return;
			}
			variable.makeImmutable();
			final int stackp = cont.stackp() - 1;
			cont.stackp(stackp);
			cont.stackAtPut(stackp, variable);
		}

		/**
		 * Remove the top item from the stack.
		 */
		@Override
		public void L1_doPop ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			cont.stackAt(cont.stackp()).assertObjectUnreachableIfMutable();
			cont.stackAtPut(cont.stackp(), VoidDescriptor.voidObject());
			final int stackp = cont.stackp() + 1;
			cont.stackp(stackp);
		}

		/**
		 * [n] - Push the value of the outer variable indexed by n in the
		 * current closure. If the variable itself is mutable, clear it at this
		 * time - nobody will know.
		 */
		@Override
		public void L1_doGetOuterClearing ()
		{

			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject variable = cont.closure().outerVarAt(index);
			final AvailObject value = variable.getValue();
			if (variable.traversed().descriptor().isMutable())
			{
				variable.clearValue();
			}
			else
			{
				value.makeImmutable();
			}
			final int stackp = cont.stackp() - 1;
			cont.stackp(stackp);
			cont.stackAtPut(stackp, value);
		}

		/**
		 * [n] - Pop the stack and assign this value to the outer variable
		 * indexed by n in the current closure.
		 */
		@Override
		public void L1_doSetOuter ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject variable = cont.closure().outerVarAt(index);
			if (variable.equalsVoid())
			{
				error("Someone prematurely erased this outer var");
				return;
			}
			final int stackp = cont.stackp();
			final AvailObject value = cont.stackAt(stackp);
			cont.stackAtPut(stackp, VoidDescriptor.voidObject());
			cont.stackp(stackp + 1);
			// The value's reference from the stack is now from the variable.
			variable.setValue(value);
		}

		/**
		 * [n] - Push the value of the local variable (not an argument) indexed
		 * by n (index 1 is first argument).
		 */
		@Override
		public void L1_doGetLocal ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject variable = cont.localOrArgOrStackAt(index);
			final AvailObject value = variable.getValue();
			value.makeImmutable();
			final int stackp = cont.stackp() - 1;
			cont.stackp(stackp);
			cont.stackAtPut(stackp, value);
		}

		@Override
		public void L1_doMakeTuple ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int count = getInteger();
			final AvailObject tuple = ObjectTupleDescriptor.mutable().create(
				count);
			int stackp = cont.stackp();
			for (int i = count; i >= 1; i--)
			{
				tuple.tupleAtPut(i, cont.stackAt(stackp));
				cont.stackAtPut(stackp, VoidDescriptor.voidObject());
				stackp++;
			}
			tuple.hashOrZero(0);
			stackp--;
			cont.stackp(stackp);
			cont.stackAtPut(stackp, tuple);
		}

		/**
		 * [n] - Push the value of the outer variable indexed by n in the
		 * current closure.
		 */
		@Override
		public void L1_doGetOuter ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject variable = cont.closure().outerVarAt(index);
			final AvailObject value = variable.getValue();
			value.makeImmutable();
			final int stackp = cont.stackp() - 1;
			cont.stackp(stackp);
			cont.stackAtPut(stackp, value);
		}

		/**
		 * The extension nybblecode was encountered. Read another nybble and
		 * dispatch it as an extended instruction.
		 */
		@Override
		public void L1_doExtension ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final byte nextNybble = cont.closure().code().nybbles()
			.extractNybbleFromTupleAt(cont.pc());
			cont.pc(cont.pc() + 1);
			L1Operation.values()[nextNybble + 16].dispatch(levelOneDispatcher);
		}

		/**
		 * Build a continuation which, when restarted, will be just like
		 * restarting the current continuation.
		 */
		@Override
		public void L1Ext_doPushLabel ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final AvailObject code = cont.closure().code();
			int stackp = cont.stackp();

			// Always copy it.
			final AvailObject newContinuation = cont.copyAsMutableContinuation();

			// Fix up this new continuation. It needs to have its pc set, its
			// stackp reset, its stack area and non-argument locals cleared, and
			// its caller, closure, and args made immutable.

			// Set the new continuation's pc to the first instruction...
			newContinuation.pc(1);

			// Reset the new continuation's stack pointer...
			newContinuation.stackp(code.numArgsAndLocalsAndStack() + 1);
			for (int i = code.numArgsAndLocalsAndStack(); i >= stackp; i--)
			{
				newContinuation.stackAtPut(i, VoidDescriptor.voidObject());
			}
			final int limit = code.numArgs() + code.numLocals();
			for (int i = code.numArgs() + 1; i <= limit; i++)
			{
				newContinuation.localOrArgOrStackAtPut(
					i,
					VoidDescriptor.voidObject());
			}
			// Freeze all fields of the new object, including its caller,
			// closure, and args.
			newContinuation.makeSubobjectsImmutable();
			// ...always a fresh copy, always mutable (uniquely owned).
			assert newContinuation.caller().equalsVoid()
			|| !newContinuation.caller().descriptor().isMutable()
			: "Caller should freeze because two continuations can see it";
			assert cont.descriptor().isMutable()
			: "The CURRENT continuation can't POSSIBLY be seen by anyone!";
			stackp--;
			cont.stackAtPut(stackp, newContinuation);
			cont.stackp(stackp);
		}

		/**
		 * [n] - Push the value of the variable that's literal number n in the
		 * current compiledCode.
		 */
		@Override
		public void L1Ext_doGetLiteral ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject constant = cont.closure().code().literalAt(index);
			final AvailObject value = constant.getValue().makeImmutable();
			final int stackp = cont.stackp() - 1;
			cont.stackp(stackp);
			cont.stackAtPut(stackp, value);
		}

		/**
		 * [n] - Pop the stack and assign this value to the variable that's the
		 * literal indexed by n in the current compiledCode.
		 */
		@Override
		public void L1Ext_doSetLiteral ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final AvailObject variable = cont.closure().code().literalAt(index);
			final int stackp = cont.stackp();
			final AvailObject value = cont.stackAt(stackp);
			cont.stackAtPut(stackp, VoidDescriptor.voidObject());
			cont.stackp(stackp + 1);
			// The value's reference from the stack is now from the variable.
			variable.setValue(value);
		}

		/**
		 * [n] - Send the message at index n in the compiledCode's literals.
		 * Like the call instruction, the arguments will have been pushed on the
		 * stack in order, but unlike call, each argument's type will also have
		 * been pushed (all arguments are pushed, then all argument types).
		 * These are either the arguments' exact types, or constant types (that
		 * must be supertypes of the arguments' types), or any mixture of the
		 * two. These types will be used for method lookup, rather than the
		 * argument types. This supports a 'super'-like mechanism in the
		 * presence of multimethods. Like the call instruction, all arguments
		 * (and types) are popped, then a sentinel void object is pushed, and
		 * the looked up method is started. When the invoked method returns (via
		 * an implicit return instruction), this sentinel will be replaced by
		 * the result of the call.
		 */
		@Override
		public void L1Ext_doSuperCall ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final AvailObject code = cont.closure().code();
			final int stackp = cont.stackp();
			// Look up the method implementations.
			final AvailObject implementations = code.literalAt(getInteger());
			final AvailObject expectedReturnType = code.literalAt(getInteger());
			final AvailObject matching = implementations
			.lookupByTypesFromContinuationStackp(cont, stackp);
			if (matching.equalsVoid())
			{
				error("Ambiguous or invalid lookup");
				return;
			}
			if (matching.isForward())
			{
				error("Attempted to execute forward method " +
					"before it was defined.");
				return;
			}
			if (matching.isAbstract())
			{
				error("Attempted to execute an abstract method.");
				return;
			}
			final AvailObject theClosure = matching.bodyBlock();
			final AvailObject theCode = theClosure.traversed().code();
			// Clear the argument types off the stack...
			final short nArgs = theCode.numArgs();
			for (int i = 1; i <= nArgs; i++)
			{
				cont.stackAtPut(stackp + i - 1, VoidDescriptor.voidObject());
			}
			_argsBuffer.clear();
			final int base = stackp + nArgs + nArgs;
			for (int i = 1; i <= nArgs; i++)
			{
				_argsBuffer.add(cont.stackAt(base - i));
				cont.stackAtPut(base - i, VoidDescriptor.voidObject());
			}
			// Remove types and arguments, but then leave the expected return
			// type pushed.  This allows the return type to be verified when the
			// called method returns, and it also helps distinguish label/call
			// kinds of continuations.
			cont.stackp(base - 1);
			cont.stackAtPut(base - 1, expectedReturnType);
			final short primNum = theCode.primitiveNumber();
			if (primNum != 0)
			{
				assert _chunk == L2ChunkDescriptor
					.chunkFromId(_pointers[callerRegister()]
					                       .levelTwoChunkIndex());
				final Result primResult = attemptPrimitive(primNum, _argsBuffer);
				if (primResult == CONTINUATION_CHANGED)
				{
					return;
				}
				if (primResult == SUCCESS)
				{
					if (!primitiveResult.isInstanceOfSubtypeOf(
						expectedReturnType))
					{
						error("Primitive result did not agree " +
							"with expected type");
					}
					final AvailObject callerCont = _pointers[callerRegister()];
					callerCont.stackAtPut(callerCont.stackp(), primitiveResult);
					return;
				}
			}
			// Either not a primitive or else a failed primitive.
			invokeWithoutPrimitiveClosureArguments(theClosure, _argsBuffer);
		}

		/**
		 * [n] - Push the (n+1)st stack element's type. This is only used by the
		 * supercast mechanism to produce types for arguments not being cast.
		 * See #doSuperCall. This implies the type will be used for a lookup and
		 * then discarded. We therefore don't treat the type as acquiring a new
		 * reference from the stack, so it doesn't have to become immutable.
		 * This could be a sticky point with the garbage collector if it finds
		 * only one reference to the type, but I think it's ok still.
		 */
		@Override
		public void L1Ext_doGetType ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int index = getInteger();
			final int stackp = cont.stackp() - 1;
			final AvailObject value = cont.stackAt(stackp + index + 1);
			cont.stackp(stackp);
			cont.stackAtPut(stackp, value.type());
		}

		/**
		 * Duplicate the element at the top of the stack. Make the element
		 * immutable since there are now at least two references.
		 */
		@Override
		public void L1Ext_doDuplicate ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final int stackp = cont.stackp() - 1;
			final AvailObject value = cont.stackAt(stackp + 1);
			value.makeImmutable();
			cont.stackAtPut(stackp, value);
			cont.stackp(stackp);
		}

		/**
		 * This shouldn't happen unless the compiler is out of sync with the
		 * interpreter.
		 */
		@Override
		public  void L1Ext_doReserved ()
		{

			error("That nybblecode is not supported");
			return;
		}

		/**
		 * Return to the calling continuation with top of stack.  This isn't an
		 * actual instruction (any more), but it's implied after every block.
		 * Note that the calling continuation has automatically pre-pushed the
		 * expected return type, which after being used to check the return
		 * value should simply be replaced by this value.  This avoids
		 * manipulating the stack depth.
		 */
		@Override
		public void L1Implied_doReturn ()
		{
			final AvailObject cont = pointerAt(callerRegister());
			final AvailObject valueObject = cont.stackAt(cont.stackp());
			final AvailObject closureType = cont.closure().code().closureType();
			assert valueObject.isInstanceOfSubtypeOf(closureType.returnType())
			: "Return type from method disagrees with declaration";
			// Necessary to avoid accidental destruction.
			cont.stackAtPut(cont.stackp(), VoidDescriptor.voidObject());
			AvailObject caller = cont.caller();
			if (caller.equalsVoid())
			{
				process.executionState(ExecutionState.terminated);
				process.continuation(VoidDescriptor.voidObject());
				_exitNow = true;
				_exitValue = valueObject;
				return;
			}
			caller = caller.ensureMutable();
			final int callerStackp = caller.stackp();
			final AvailObject expectedType = caller.stackAt(callerStackp);
			if (!valueObject.isInstanceOfSubtypeOf(expectedType))
			{
				error("Return value does not agree with expected type");
			}
			caller.stackAtPut(callerStackp, valueObject);
			prepareToExecuteContinuation(caller);
		}
	};


	/**
	 * Construct a new {@link L2Interpreter}.
	 *
	 * @param runtime
	 *            An {@link AvailRuntime}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public L2Interpreter (final @NotNull AvailRuntime runtime)
	{
		super(runtime);
	}

	/**
	 * Return the current position in the L2 wordcode stream.
	 *
	 * @return The position in the L2 wordcode stream.
	 */
	int offset ()
	{
		return _offset;
	}

	/**
	 * Jump to a new position in the L2 wordcode stream.
	 *
	 * @param newOffset The new position in the L2 wordcode stream.
	 */
	void offset (final int newOffset)
	{
		// System.out.printf("[#%d] %d -> %d%n", _chunk.index(), _offset,
		// newOffset);
		_offset = newOffset;
	}

	@Override
	public void prepareToExecuteContinuation (final AvailObject continuation)
	{
		if (continuation.equalsVoid())
		{
			_chunk = VoidDescriptor.voidObject();
			_chunkWords = VoidDescriptor.voidObject();
			_chunkVectors = VoidDescriptor.voidObject();
			offset(0);
			pointerAtPut(callerRegister(), VoidDescriptor.voidObject());
			pointerAtPut(closureRegister(), VoidDescriptor.voidObject());
			return;
		}
		_chunk = L2ChunkDescriptor.chunkFromId(continuation
			.levelTwoChunkIndex());
		if (!_chunk.isValid())
		{
			// The chunk has been invalidated, but the continuation still refers
			// to it.  The garbage collector will reclaim the chunk only when
			// all such continuations have let the chunk go -- therefore, let it
			// go.  Fall back to the default level two chunk that steps over
			// nybblecodes.
			continuation.levelTwoChunkIndexOffset(
				L2ChunkDescriptor.indexOfUnoptimizedChunk(),
				L2ChunkDescriptor.offsetToPauseUnoptimizedChunk());
			_chunk = L2ChunkDescriptor.chunkFromId(
				continuation.levelTwoChunkIndex());
		}
		_chunkWords = _chunk.wordcodes();
		_chunkVectors = _chunk.vectors();
		makeRoomForChunkRegisters(_chunk, continuation.closure().code());
		pointerAtPut(callerRegister(), continuation);
		pointerAtPut(closureRegister(), continuation.closure());
		offset(continuation.levelTwoOffset());
	}

	/**
	 * Increase the number of registers if necessary to accommodate the new
	 * chunk/code.
	 *
	 * @param theChunk The {@link L2ChunkDescriptor L2Chunk} about to be
	 *                 invoked.
	 * @param theCode The code about to be invoked.
	 */
	void makeRoomForChunkRegisters (
		final AvailObject theChunk,
		final AvailObject theCode)
	{
		final int neededObjectCount = max(
			theChunk.numObjects(),
			theCode.numArgsAndLocalsAndStack()) + 3;
		if (neededObjectCount > _pointers.length)
		{
			final AvailObject[] newPointers =
				new AvailObject[neededObjectCount * 2 + 10];
			System.arraycopy(_pointers, 0, newPointers, 0, _pointers.length);
			_pointers = newPointers;
		}
		if (theChunk.numIntegers() > _integers.length)
		{
			final int[] newIntegers = new int[theChunk.numIntegers() * 2 + 10];
			System.arraycopy(_integers, 0, newIntegers, 0, _integers.length);
			_integers = newIntegers;
		}
		if (theChunk.numDoubles() > _doubles.length)
		{
			final double[] newDoubles =
				new double[theChunk.numDoubles() * 2 + 10];
			System.arraycopy(_doubles, 0, newDoubles, 0, _doubles.length);
			_doubles = newDoubles;
		}
	}

	/**
	 * Translate the code into a chunk using the specified effort. An effort of
	 * zero means produce an initial translation that decrements a counter on
	 * each invocation, re-optimizing (with more effort) if it reaches zero.
	 *
	 * @param theCode The code to translate.
	 * @param effort How much effort to put into the optimization effort.
	 * @return The (potentially new) {@link L2ChunkDescriptor L2Chunk}.
	 */
	AvailObject privateTranslateCodeOptimization (
		final AvailObject theCode,
		final int effort)
	{
		return new L2Translator().translateOptimizationFor(
			theCode,
			effort,
			this);
	}

	/**
	 * Raise an exception. Scan the stack of continuations until one is found
	 * for a closure whose code is the primitive 200. Get that continuation's
	 * second argument (a handler block of one argument), and check if that
	 * handler block will accept the exceptionValue. If not, keep looking. If it
	 * will accept it, unwind the stack so that the primitive 200 method is the
	 * top entry, and invoke the handler block with exceptionValue. If there is
	 * no suitable handler block, fail the primitive.
	 */
	@Override
	public Result searchForExceptionHandler (
		final AvailObject exceptionValue,
		final List<AvailObject> args)
	{

		AvailObject cont = _pointers[callerRegister()];
		AvailObject handler = VoidDescriptor.voidObject();
		while (!cont.equalsVoid())
		{
			if (cont.closure().code().primitiveNumber() == 200
					&& exceptionValue.isInstanceOfSubtypeOf(cont
						.localOrArgOrStackAt(2).type().argTypeAt(1)))
			{
				handler = cont.localOrArgOrStackAt(2);
				assert !handler.equalsVoid();
				_pointers[callerRegister()] = cont.ensureMutable();
				return invokeClosureArguments(handler, args);
			}
			cont = cont.caller();
		}
		return FAILURE;
	}

	/**
	 * Prepare the L2Interpreter to deal with executing the given closure. If
	 * it's a primitive, attempt it first. If it succeeds and simply returns a
	 * value, write that value into the calling continuation and return
	 * primitiveSucceeded. If the primitive succeeded by causing the
	 * continuation to change (e.g., block invocation, continuation restarting),
	 * simply answer continuationChanged. Otherwise, invoke the closure (without
	 * actually running any of its instructions yet), and answer
	 * continuationChanged.
	 */
	@Override
	public Result invokeClosureArguments (
		final AvailObject aClosure,
		final List<AvailObject> args)
	{

		final short primNum = aClosure.code().primitiveNumber();
		if (primNum != 0)
		{
			Result result;
			result = attemptPrimitive(primNum, args);
			if (result == SUCCESS)
			{
				final AvailObject cont = pointerAt(callerRegister());
				cont.stackAtPut(cont.stackp(), primitiveResult);
				return result;
			}
			if (result == CONTINUATION_CHANGED)
			{
				return result;
			}
		}
		// Either it wasn't a primitive or the primitive failed.
		invokeWithoutPrimitiveClosureArguments(aClosure, args);
		return CONTINUATION_CHANGED;
	}

	/**
	 * Prepare the L2Interpreter to deal with executing the given closure, using
	 * the given parameters.
	 */
	@Override
	public void invokeWithoutPrimitiveClosureArguments (
		final AvailObject aClosure,
		final List<AvailObject> args)
	{
		_chunk = L2ChunkDescriptor.chunkFromId(aClosure.code()
			.startingChunkIndex());
		if (!_chunk.isValid())
		{
			// The chunk is invalid, so use the default chunk and patch up
			// aClosure's code.
			_chunk = L2ChunkDescriptor.chunkFromId(L2ChunkDescriptor
				.indexOfUnoptimizedChunk());
			aClosure.code().startingChunkIndex(_chunk.index());
			aClosure.code().invocationCount(
				L2ChunkDescriptor.countdownForInvalidatedCode());
		}
		_chunk.moveToHead();
		offset(1);

		makeRoomForChunkRegisters(_chunk, aClosure.code());

		_chunkWords = _chunk.wordcodes();
		_chunkVectors = _chunk.vectors();
		pointerAtPut(closureRegister(), aClosure);
		// Transfer arguments...
		for (int i1 = aClosure.code().numArgs(); i1 >= 1; --i1)
		{
			pointerAtPut(argumentRegister(i1), args.get(i1 - 1));
		}
	}

	/**
	 * Run the given process to completion, answering the result returned by
	 * the outermost process.  We can only resume the continuation safely if it
	 * was just entering a closure, or just returning from one, or if it took an
	 * off-ramp for which there is an on-ramp.
	 *
	 * @param aProcess The process to execute.
	 * @return The final result produced by the process.
	 */
	public AvailObject run (final AvailObject aProcess)
	{
		process = aProcess;
		final AvailObject continuationTemp = aProcess.continuation();
		interruptRequestFlag = 0;
		_exitNow = false;
		prepareToExecuteContinuation(continuationTemp);

		// The caches are set up. Start dispatching nybblecodes.
		do
		{
			/**
			 * This loop is only exited by a return off the end of the outermost
			 * context, a suspend or terminate of the current process, or by an
			 * inter-nybblecode interrupt. For now there are no inter-nybblecode
			 * interrupts, but these can be added later. They will probably
			 * happen on non-primitive method invocations, returns, and backward
			 * jumps. At the time of an inter-nybblecode interrupt, the
			 * continuation must be a reflection of the current continuation,
			 * <em>not</em> the caller. That is, only the callerRegister()'s
			 * content is valid.
			 */
			final int wordCode = nextWord();
			final L2Operation operation = L2Operation.values()[wordCode];
			//System.out.printf(
			//	"[%d@%d] %s%n",
			//	_chunk.index(),
			//	_offset - 1,
			//	operation.name());
			operation.dispatch(this);
		}
		while (!_exitNow);

		return _exitValue;
	}

	@Override
	public AvailObject runClosureArguments (
		final AvailObject aClosure,
		final List<AvailObject> arguments)
	{
		final AvailObject theCode = aClosure.code();
		final short prim = theCode.primitiveNumber();
		if (prim != 0)
		{
			assert Primitive.byPrimitiveNumber(prim).hasFlag(
				SpecialReturnConstant)
			: "The outermost context can't be a primitive.";
			process.continuation(VoidDescriptor.voidObject());
			return theCode.literalAt(1);
		}
		if (theCode.numArgs() != arguments.size())
		{
			error("Closure should take " + theCode.numArgs() + " arguments");
		}

		// Safety precaution.
		aClosure.makeImmutable();
		final AvailObject outermostContinuation =
			ContinuationDescriptor.create(
				aClosure,
				VoidDescriptor.voidObject(),
				L2ChunkDescriptor.indexOfUnoptimizedChunk(),
				arguments);
		outermostContinuation.levelTwoChunkIndexOffset(
			L2ChunkDescriptor.indexOfUnoptimizedChunk(),
			L2ChunkDescriptor.offsetToContinueUnoptimizedChunk());
		process.continuation(outermostContinuation);
		prepareToExecuteContinuation(outermostContinuation);

		AvailObject result;
		do
		{
			result = jumpContinuation();
		}
		while (!process.continuation().equalsVoid());

		return result;
	}

	/**
	 * Run the current continuation within the current process.
	 *
	 * @return The result of executing the process.
	 */
	private AvailObject jumpContinuation ()
	{
		process.continuation(currentContinuation());
		return run(process);
	}

	/**
	 * Extract the next word from the level two instruction stream.
	 *
	 * @return The word.
	 */
	int nextWord ()
	{
		final int offset = offset();
		final int word = _chunkWords.tupleIntAt(offset);
		offset(offset + 1);
		return word;
	}



	@Override
	public void L2_unknownWordcode ()
	{
		error("Unknown wordcode\n");
	}

	@Override
	public void L2_doCreateSimpleContinuationIn_ ()
	{
		// Create a simple continuation using the current calling continuation,
		// closure, and arguments.
		// Place the closure in the callerRegister.

		final int destIndex = nextWord();
		final AvailObject theClosure = pointerAt(closureRegister());
		final AvailObject theCode = theClosure.code();
		final AvailObject newContinuation = ContinuationDescriptor.mutable()
			.create(theCode.numArgsAndLocalsAndStack());
		final short nArgs = theCode.numArgs();
		newContinuation.caller(pointerAt(callerRegister()));
		newContinuation.closure(theClosure);
		newContinuation.pc(1);
		newContinuation.stackp(theCode.numArgsAndLocalsAndStack() + 1);
		newContinuation.levelTwoChunkIndexOffset(_chunk.index(), offset());
		for (int i = 1; i <= nArgs; i++)
		{
			newContinuation.localOrArgOrStackAtPut(
				i,
				pointerAt(argumentRegister(i)));
		}
		for (
				int i = nArgs + 1, _end1 = theCode.numArgsAndLocalsAndStack();
				i <= _end1;
				i++)
		{
			newContinuation.localOrArgOrStackAtPut(
				i,
				VoidDescriptor.voidObject());
		}
		for (int i = 1, _end2 = theCode.numLocals(); i <= _end2; i++)
		{
			// non-argument locals
			newContinuation.localOrArgOrStackAtPut(
				nArgs + i,
				ContainerDescriptor.forOuterType(theCode
					.localTypeAt(i)));
		}
		pointerAtPut(destIndex, newContinuation);
	}

	/**
	 * Execute a single nybblecode of the current continuation, found in
	 * {@link #callerRegister() callerRegister}.
	 */
	@Override
	public void L2_doInterpretOneInstruction ()
	{
		final AvailObject continutation = pointerAt(callerRegister());
		final AvailObject closure = continutation.closure();
		final AvailObject code = closure.code();
		final AvailObject nybbles = code.nybbles();
		int pc = continutation.pc();

		// Before we extract the nybblecode, may sure that the PC hasn't passed
		// the end of the instruction sequence. If we have, then execute an
		// {@code L1Implied_doReturn}.
		if (pc > nybbles.tupleSize())
		{
			levelOneDispatcher.L1Implied_doReturn();
			return;
		}

		final int nybble = nybbles.extractNybbleFromTupleAt(pc);
		pc++;
		continutation.pc(pc);
		L1Operation.values()[nybble].dispatch(levelOneDispatcher);
	}

	@Override
	public void L2_doDecrementCounterAndReoptimizeOnZero ()
	{
		// Decrement the counter in the current code object. If it reaches zero,
		// re-optimize the current code.

		final AvailObject theClosure = pointerAt(closureRegister());
		final AvailObject theCode = theClosure.code();
		final int newCount = theCode.invocationCount() - 1;
		assert newCount >= 0;
		if (newCount != 0)
		{
			theCode.invocationCount(newCount);
		}
		else
		{
			theCode.invocationCount(L2ChunkDescriptor
				.countdownForNewlyOptimizedCode());
			final AvailObject newChunk =
				privateTranslateCodeOptimization(theCode, 3);
			assert theCode.startingChunkIndex() == newChunk.index();
			_argsBuffer.clear();
			final int nArgs = theCode.numArgs();
			for (int i = 1; i <= nArgs; i++)
			{
				_argsBuffer.add(_pointers[argumentRegister(i)]);
			}
			invokeClosureArguments(theClosure, _argsBuffer);
		}
	}

	@Override
	public void L2_doMoveFromObject_destObject_ ()
	{
		final int fromIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(fromIndex));
	}

	@Override
	public void L2_doMoveFromConstant_destObject_ ()
	{
		final int fromIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(destIndex, _chunk.literalAt(fromIndex));
	}

	@Override
	public void L2_doMoveFromOuterVariable_ofClosureObject_destObject_ ()
	{
		final int outerIndex = nextWord();
		final int fromIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(fromIndex).outerVarAt(outerIndex));
	}

	@Override
	public void L2_doCreateVariableTypeConstant_destObject_ ()
	{
		final int typeIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(
			destIndex,
			ContainerDescriptor.forOuterType(_chunk
				.literalAt(typeIndex)));
	}

	@Override
	public void L2_doGetVariable_destObject_ ()
	{
		final int getIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(getIndex).getValue().makeImmutable());
	}

	@Override
	public void L2_doGetVariableClearing_destObject_ ()
	{
		final int getIndex = nextWord();
		final int destIndex = nextWord();
		final AvailObject var = pointerAt(getIndex);
		final AvailObject value = var.getValue();
		if (var.traversed().descriptor().isMutable())
		{
			var.clearValue();
		}
		else
		{
			value.makeImmutable();
		}
		pointerAtPut(destIndex, value);
	}

	@Override
	public void L2_doSetVariable_sourceObject_ ()
	{
		final int setIndex = nextWord();
		final int sourceIndex = nextWord();
		pointerAt(setIndex).setValue(pointerAt(sourceIndex));
	}

	@Override
	public void L2_doClearVariable_ ()
	{
		@SuppressWarnings("unused")
		final int clearIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doClearVariablesVector_ ()
	{
		@SuppressWarnings("unused")
		final int variablesIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doClearObject_ ()
	{
		final int clearIndex = nextWord();
		pointerAtPut(clearIndex, VoidDescriptor.voidObject());
	}

	@Override
	public void L2_doAddIntegerConstant_destObject_ ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doAddIntegerConstant_destInteger_ifFail_ ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doAddObject_destObject_ ()
	{
		@SuppressWarnings("unused")
		final int addIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doAddInteger_destInteger_ifFail_ ()
	{
		// Note that failOffset is an absolute position in the chunk.

		final int addIndex = nextWord();
		final int destIndex = nextWord();
		final int failOffset = nextWord();
		final long add = _integers[addIndex];
		final long dest = _integers[destIndex];
		final long result = dest + add;
		final int resultInt = (int) result;
		if (result == resultInt)
		{
			_integers[destIndex] = resultInt;
		}
		else
		{
			offset(failOffset);
		}
	}

	@Override
	public void L2_doAddIntegerImmediate_destInteger_ifFail_ ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doAddModThirtyTwoBitInteger_destInteger_ ()
	{
		@SuppressWarnings("unused")
		final int bitIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doSubtractIntegerConstant_destObject_ ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doSubtractIntegerConstant_destInteger_ifFail_ ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doSubtractObject_destObject_ ()
	{
		@SuppressWarnings("unused")
		final int subtractIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doSubtractInteger_destInteger_ifFail_ ()
	{
		@SuppressWarnings("unused")
		final int subtractIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doSubtractIntegerImmediate_destInteger_ifFail_ ()
	{
		@SuppressWarnings("unused")
		final int integerImmediate = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doSubtractModThirtyTwoBitInteger_destInteger_ ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doMultiplyIntegerConstant_destObject_ ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doMultiplyIntegerConstant_destInteger_ifFail_ ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doMultiplyObject_destObject_ ()
	{
		@SuppressWarnings("unused")
		final int multiplyIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doMultiplyInteger_destInteger_ifFail_ ()
	{
		@SuppressWarnings("unused")
		final int multiplyIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doMultiplyIntegerImmediate_destInteger_ifFail_ ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doMultiplyModThirtyTwoBitInteger_destInteger_ ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doDivideObject_byIntegerConstant_destQuotientObject_destRemainderInteger_ifFail_ ()
	{
		@SuppressWarnings("unused")
		final int divideIndex = nextWord();
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int quotientIndex = nextWord();
		@SuppressWarnings("unused")
		final int remainderIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doDivideInteger_byIntegerConstant_destQuotientInteger_destRemainderInteger_ifFail_ ()
	{
		@SuppressWarnings("unused")
		final int divideIndex = nextWord();
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int quotientIndex = nextWord();
		@SuppressWarnings("unused")
		final int remainderIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doDivideInteger_byIntegerImmediate_destQuotientInteger_destRemainderInteger_ifFail_ ()
	{
		@SuppressWarnings("unused")
		final int divideIndex = nextWord();
		@SuppressWarnings("unused")
		final int integerImmediate = nextWord();
		@SuppressWarnings("unused")
		final int quotientIndex = nextWord();
		@SuppressWarnings("unused")
		final int remainderIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doDivideObject_byObject_destQuotientObject_destRemainderObject_ifZeroDivisor_ ()
	{
		@SuppressWarnings("unused")
		final int divideIndex = nextWord();
		@SuppressWarnings("unused")
		final int byIndex = nextWord();
		@SuppressWarnings("unused")
		final int quotientIndex = nextWord();
		@SuppressWarnings("unused")
		final int remainderIndex = nextWord();
		@SuppressWarnings("unused")
		final int zeroIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doDivideInteger_byInteger_destQuotientInteger_destRemainderInteger_ifFail_ifZeroDivisor_ ()
	{
		@SuppressWarnings("unused")
		final int divideIndex = nextWord();
		@SuppressWarnings("unused")
		final int byIndex = nextWord();
		@SuppressWarnings("unused")
		final int quotientIndex = nextWord();
		@SuppressWarnings("unused")
		final int remainderIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int zeroIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ ()
	{
		final int doIndex = nextWord();
		offset(doIndex);
	}

	@Override
	public void L2_doJump_ifObject_equalsObject_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_equalsConstant_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_notEqualsObject_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_notEqualsConstant_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_lessThanObject_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int thanIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_lessThanConstant_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int thanIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_lessOrEqualObject_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_lessOrEqualConstant_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_greaterThanObject_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int thanIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_greaterConstant_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int greaterIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_greaterOrEqualObject_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_greaterOrEqualConstant_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_isKindOfObject_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int ofIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_isKindOfConstant_ ()
	{
		final int doIndex = nextWord();
		final int valueIndex = nextWord();
		final int typeConstIndex = nextWord();
		final AvailObject value = pointerAt(valueIndex);
		final AvailObject type = _chunk.literalAt(typeConstIndex);
		if (value.isInstanceOfSubtypeOf(type))
		{
			offset(doIndex);
		}
	}

	@Override
	public void L2_doJump_ifObject_isNotKindOfObject_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int ofIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJump_ifObject_isNotKindOfConstant_ ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int ofIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doJumpIfInterrupt_ ()
	{
		final int ifIndex = nextWord();
		if (interruptRequestFlag != 0)
		{
			offset(ifIndex);
		}
	}

	@Override
	public void L2_doJumpIfNotInterrupt_ ()
	{
		final int ifNotIndex = nextWord();
		if (interruptRequestFlag == 0)
		{
			offset(ifNotIndex);
		}
	}

	@Override
	public void L2_doProcessInterruptNowWithContinuationObject_ ()
	{
		// The current process has been asked to pause for an inter-nybblecode
		// interrupt for
		// some reason. It has possibly executed several more wordcodes since
		// that time, to
		// place the process into a state that's consistent with naive Level One
		// execution
		// semantics. That is, a naive Level One interpreter should be able to
		// resume the
		// process later. The continuation to use can be found in
		// _pointers[continuationIndex].

		final int continuationIndex = nextWord();
		process.continuation(_pointers[continuationIndex]);
		process.interruptRequestFlag(interruptRequestFlag);
		_exitValue = VoidDescriptor.voidObject();
		_exitNow = true;
	}

	@Override
	public void L2_doCreateContinuationWithSenderObject_closureObject_pcInteger_stackpInteger_sizeImmediate_slotsVector_wordcodeOffset_destObject_ ()
	{
		final int senderIndex = nextWord();
		final int closureIndex = nextWord();
		final int pcIndex = nextWord();
		final int stackpIndex = nextWord();
		final int sizeIndex = nextWord();
		final int slotsIndex = nextWord();
		final int wordcodeOffset = nextWord();
		final int destIndex = nextWord();
		final AvailObject closure = pointerAt(closureIndex);
		final AvailObject code = closure.code();
		final AvailObject continuation = ContinuationDescriptor.mutable()
			.create(code.numArgsAndLocalsAndStack());
		continuation.caller(pointerAt(senderIndex));
		continuation.closure(closure);
		continuation.pc(pcIndex);
		continuation.stackp(
			code.numArgsAndLocalsAndStack()
			- code.maxStackDepth()
			+ stackpIndex);
		continuation.hiLevelTwoChunkLowOffset(
			(_chunk.index() << 16) + wordcodeOffset);
		final AvailObject slots = _chunkVectors.tupleAt(slotsIndex);
		for (int i = 1; i <= sizeIndex; i++)
		{
			continuation.localOrArgOrStackAtPut(
				i,
				pointerAt(slots.tupleAt(i).extractInt()));
		}
		pointerAtPut(destIndex, continuation);
	}

	@Override
	public void L2_doSetContinuationObject_slotIndexImmediate_valueObject_ ()
	{
		@SuppressWarnings("unused")
		final int continuationIndex = nextWord();
		@SuppressWarnings("unused")
		final int indexIndex = nextWord();
		@SuppressWarnings("unused")
		final int valueIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doSetContinuationObject_newPcImmediate_newStackpImmediate_ ()
	{
		@SuppressWarnings("unused")
		final int continuationIndex = nextWord();
		@SuppressWarnings("unused")
		final int pcIndex = nextWord();
		@SuppressWarnings("unused")
		final int stackpIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doExplodeContinuationObject ()
	{
		final int continuationIndex = nextWord();
		final int senderDestIndex = nextWord();
		final int closureDestIndex = nextWord();
		final int slotsDestIndex = nextWord();
		final AvailObject cont = pointerAt(continuationIndex);
		pointerAtPut(senderDestIndex, cont.caller());
		pointerAtPut(closureDestIndex, cont.closure());
		final AvailObject slotsVector = _chunkVectors.tupleAt(slotsDestIndex);
		if (!(slotsVector.tupleSize() == cont.closure().code()
				.numArgsAndLocalsAndStack()))
		{
			error("problem in doExplode...");
			return;
		}
		for (int i = 1, _end1 = slotsVector.tupleSize(); i <= _end1; i++)
		{
			pointerAtPut(
				slotsVector.tupleAt(i).extractInt(),
				cont.localOrArgOrStackAt(i));
		}
	}

	@Override
	public void L2_doSend_argumentsVector_ ()
	{
		final int selectorIndex = nextWord();
		final int argumentsIndex = nextWord();
		final AvailObject vect = _chunkVectors.tupleAt(argumentsIndex);
		_argsBuffer.clear();
		for (int i = 1; i <= vect.tupleSize(); i++)
		{
			_argsBuffer.add(_pointers[vect.tupleIntAt(i)]);
		}
		final AvailObject selector = _chunk.literalAt(selectorIndex);
		final AvailObject signatureToCall = selector
		.lookupByValuesFromArray(_argsBuffer);
		if (signatureToCall.equalsVoid())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (!signatureToCall.isMethod())
		{
			error("Attempted to call a non-implementation signature");
			return;
		}
		final AvailObject closureToCall = signatureToCall.bodyBlock();
		final AvailObject codeToCall = closureToCall.code();
		final short primNum = codeToCall.primitiveNumber();
		if (primNum != 0)
		{
			prepareToExecuteContinuation(_pointers[callerRegister()]);
			final Result primResult = attemptPrimitive(primNum, _argsBuffer);
			if (primResult == CONTINUATION_CHANGED)
			{
				return;
			}
			else if (primResult != FAILURE)
			{
				// Primitive succeeded.
				final AvailObject cont = _pointers[callerRegister()];
				assert _chunk.index() == cont.levelTwoChunkIndex();
				cont.readBarrierFault();
				assert cont.descriptor().isMutable();
				cont.stackAtPut(cont.stackp(), primitiveResult);
				return;
			}
		}
		invokeWithoutPrimitiveClosureArguments(closureToCall, _argsBuffer);
	}

	@Override
	public void L2_doGetType_destObject_ ()
	{
		final int srcIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(srcIndex).type());
	}

	@Override
	public void L2_doSuperSend_argumentsVector_argumentTypesVector_ ()
	{
		final int selectorIndex = nextWord();
		final int argumentsIndex = nextWord();
		final int typesIndex = nextWord();
		AvailObject vect = _chunkVectors.tupleAt(typesIndex);
		if (true)
		{
			_argsBuffer.clear();
			for (int i = 1; i < vect.tupleSize(); i++)
			{
				_argsBuffer.add(_pointers[vect.tupleIntAt(i)]);
			}
		}
		final AvailObject selector = _chunk.literalAt(selectorIndex);
		final AvailObject signatureToCall = selector
		.lookupByTypesFromArray(_argsBuffer);
		if (signatureToCall.equalsVoid())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (!signatureToCall.isMethod())
		{
			error("Attempted to call a non-implementation signature");
			return;
		}
		vect = _chunkVectors.tupleAt(argumentsIndex);
		_argsBuffer.clear();
		for (int i = 1; i < vect.tupleSize(); i++)
		{
			_argsBuffer.add(_pointers[vect.tupleIntAt(i)]);
		}
		final AvailObject closureToCall = signatureToCall.bodyBlock();
		final AvailObject codeToCall = closureToCall.code();
		final short primNum = codeToCall.primitiveNumber();
		if (primNum != 0)
		{
			prepareToExecuteContinuation(_pointers[callerRegister()]);
			final Result primResult = attemptPrimitive(primNum, _argsBuffer);
			if (primResult == CONTINUATION_CHANGED)
			{
				return;
			}
			else if (primResult != FAILURE)
			{
				// Primitive succeeded.
				final AvailObject cont = _pointers[callerRegister()];
				assert _chunk.index() == cont.levelTwoChunkIndex();
				cont.readBarrierFault();
				assert cont.descriptor().isMutable();
				cont.stackAtPut(cont.stackp(), primitiveResult);
				return;
			}
		}
		invokeWithoutPrimitiveClosureArguments(closureToCall, _argsBuffer);
	}

	@Override
	public void L2_doCreateTupleOfSizeImmediate_valuesVector_destObject_ ()
	{
		final int sizeIndex = nextWord();
		final int valuesIndex = nextWord();
		final int destIndex = nextWord();
		final AvailObject indices = _chunkVectors.tupleAt(valuesIndex);
		assert indices.tupleSize() == sizeIndex;
		final AvailObject tuple = ObjectTupleDescriptor.mutable().create(
			sizeIndex);
		for (int i = 1; i <= sizeIndex; i++)
		{
			tuple.tupleAtPut(i, pointerAt(indices.tupleAt(i).extractInt()));
		}
		pointerAtPut(destIndex, tuple);
	}

	@Override
	public void L2_doConcatenateTuplesVector_destObject_ ()
	{
		@SuppressWarnings("unused")
		final int subtupleIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doCreateSetOfSizeImmediate_valuesVector_destObject_ ()
	{
		@SuppressWarnings("unused")
		final int sizeIndex = nextWord();
		@SuppressWarnings("unused")
		final int valuesIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doCreateMapOfSizeImmediate_keysVector_valuesVector_destObject_ ()
	{
		@SuppressWarnings("unused")
		final int sizeIndex = nextWord();
		@SuppressWarnings("unused")
		final int keysIndex = nextWord();
		@SuppressWarnings("unused")
		final int valuesIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doCreateObjectOfSizeImmediate_keysVector_valuesVector_destObject_ ()
	{
		@SuppressWarnings("unused")
		final int sizeIndex = nextWord();
		@SuppressWarnings("unused")
		final int keysIndex = nextWord();
		@SuppressWarnings("unused")
		final int valuesIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doCreateClosureFromCodeObject_outersVector_destObject_ ()
	{
		final int codeIndex = nextWord();
		final int outersIndex = nextWord();
		final int destIndex = nextWord();
		final AvailObject outers = _chunkVectors.tupleAt(outersIndex);
		final AvailObject clos = ClosureDescriptor.mutable().create(
			outers.tupleSize());
		clos.code(_chunk.literalAt(codeIndex));
		for (int i = 1, _end1 = outers.tupleSize(); i <= _end1; i++)
		{
			clos.outerVarAtPut(i, pointerAt(outers.tupleAt(i).extractInt()));
		}
		pointerAtPut(destIndex, clos);
	}

	@Override
	public void L2_doReturnToContinuationObject_valueObject_ ()
	{
		// Return to the calling continuation with the given value.

		final int continuationIndex = nextWord();
		final int valueIndex = nextWord();
		assert continuationIndex == callerRegister();

		final AvailObject valueObject = pointerAt(valueIndex);
		AvailObject caller = pointerAt(continuationIndex);
		if (caller.equalsVoid())
		{
			process.executionState(ExecutionState.terminated);
			process.continuation(VoidDescriptor.voidObject());
			_exitValue = valueObject;
			_exitNow = true;
		}
		// Check that the return value matches the expected type which was
		// stored on the caller's stack, then replace it.
		caller = caller.ensureMutable();
		final int callerStackp = caller.stackp();
		final AvailObject expectedType = caller.stackAt(callerStackp);
		if (!valueObject.isInstanceOfSubtypeOf(expectedType))
		{
			error("Return value does not agree with expected type");
		}
		caller.stackAtPut(callerStackp, valueObject);
		prepareToExecuteContinuation(caller);
	}

	@Override
	public void L2_doExitContinuationObject_valueObject_ ()
	{
		@SuppressWarnings("unused")
		final int continuationIndex = nextWord();
		@SuppressWarnings("unused")
		final int valueIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doResumeContinuationObject_ ()
	{
		@SuppressWarnings("unused")
		final int continuationIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_doMakeImmutableObject_ ()
	{
		final int objectIndex = nextWord();
		pointerAt(objectIndex).makeImmutable();
	}

	@Override
	public void L2_doMakeSubobjectsImmutableInObject_ ()
	{
		final int objectIndex = nextWord();
		pointerAt(objectIndex).makeSubobjectsImmutable();
	}

	/**
	 * Attempt the specified primitive with the given arguments. If the
	 * primitive fails, jump to the given code offset. If it succeeds, store the
	 * result in the specified register. Note that some primitives should never
	 * be inlined. For example, block invocation assumes the callerRegister has
	 * been set up to hold the context that is calling the primitive. This is
	 * not the case for an <em>inlined</em> primitive.
	 */
	@Override
	public void L2_doAttemptPrimitive_withArguments_result_ifFail_ ()
	{
		final int primNumber = nextWord();
		final int argsVector = nextWord();
		final int resultRegister = nextWord();
		final int failureOffset = nextWord();
		final AvailObject argsVect = _chunkVectors.tupleAt(argsVector);
		_argsBuffer.clear();
		for (int i1 = 1; i1 <= argsVect.tupleSize(); i1++)
		{
			_argsBuffer.add(_pointers[argsVect.tupleAt(i1).extractInt()]);
		}
		final Result res = attemptPrimitive((short) primNumber, _argsBuffer);
		if (res == SUCCESS)
		{
			_pointers[resultRegister] = primitiveResult;
		}
		else if (res == CONTINUATION_CHANGED)
		{
			error(
				"attemptPrimitive wordcode should never set up "
				+ "a new continuation",
				primNumber);
		}
		else if (res == FAILURE)
		{
			offset(failureOffset);
		}
		else
		{
			error("Unrecognized return type from attemptPrimitive()");
		}
	}

	/**
	 * Answer the subscript of the register holding the argument with the given
	 * index (e.g., the first argument is in register 3).
	 *
	 * @param localNumber The one-based argument/local number.
	 * @return The subscript to use with {@link L2Interpreter#pointerAt(int)}.
	 */
	public int argumentRegister (final int localNumber)
	{
		// Skip the continuation and closure.
		return localNumber + 2;
	}

	/**
	 * Answer the subscript of the register holding the current context's caller
	 * context.
	 *
	 * @return The subscript to use with {@link L2Interpreter#pointerAt(int)}.
	 */
	public int callerRegister ()
	{
		// reserved
		return 1;
	}

	/**
	 * Answer the subscript of the register holding the current context's
	 * closure.
	 *
	 * @return The subscript to use with {@link L2Interpreter#pointerAt(int)}.
	 */
	public int closureRegister ()
	{
		// reserved
		return 2;
	}

	/**
	 * Answer the current continuation.
	 *
	 * @return The current continuation.
	 */
	public AvailObject currentContinuation ()
	{
		return pointerAt(callerRegister());
	}

	/**
	 * Answer an integer extracted at the current program counter. The program
	 * counter will be adjusted to skip over the integer.
	 *
	 * @return An integer extracted from the instruction stream.
	 */
	public int getInteger ()
	{

		final AvailObject cont = _pointers[callerRegister()];
		final AvailObject clos = cont.closure();
		final AvailObject cod = clos.code();
		final AvailObject nybs = cod.nybbles();
		int pc = cont.pc();
		final byte nyb = nybs.extractNybbleFromTupleAt(pc);
		pc++;
		int value = 0;
		final byte[] counts =
		{
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 4, 8
		};
		for (int count = counts[nyb]; count > 0; --count)
		{
			value = (value << 4) + nybs.extractNybbleFromTupleAt(pc);
			pc++;
		}
		final byte[] offsets =
		{
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 26, 42, 58, 0, 0
		};
		value += offsets[nyb];
		cont.pc(pc);
		return value;
	}

	/**
	 * Read from an object register.  The index is one-based in both Smalltalk
	 * (obsolete) and Java, to avoid index manipulation.  Entry [0] is unused.
	 *
	 * @param index The one-based object-register index.
	 * @return The object in the specified register.
	 */
	public AvailObject pointerAt (final int index)
	{
		return _pointers[index];
	}

	/**
	 * Write to an object register.  The index is one-based in both Smalltalk
	 * (obsolete) and Java, to avoid index manipulation.  Entry [0] is unused.
	 *
	 * @param index The one-based object-register index.
	 * @param anAvailObject The object to write to the specified register.
	 */
	public void pointerAtPut (final int index, final AvailObject anAvailObject)
	{
		_pointers[index] = anAvailObject;
	}
}
