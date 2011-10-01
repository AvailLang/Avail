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
import static com.avail.interpreter.Primitive.Result.*;
import static java.lang.Math.max;
import java.util.*;
import java.util.logging.Level;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.descriptor.ProcessDescriptor.ExecutionState;
import static com.avail.exceptions.AvailErrorCode.*;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.*;

/**
 * This class is used to execute {@link L2ChunkDescriptor level two code}, which
 * is a translation of the level one nybblecodes found in {@link
 * CompiledCodeDescriptor compiled code}.
 *
 * <p>
 * Level one nybblecodes are designed to be compact and very simple, but not
 * particularly efficiently executable.  Level two is designed for a clean model
 * for optimization, including:
 * <ul>
 * <li>primitive folding.</li>
 * <li>register coloring/allocation.</li>
 * <li>inlining.</li>
 * <li>common sub-expression elimination.</li>
 * <li>side-effect analysis.</li>
 * <li>object escape analysis.</li>
 * <li>a variant of keyhole optimization that involves building the loosest
 * possible level two instruction dependency graph, then "pulling" eligible
 * instruction sequences that are profitably rewritten.</li>
 * <li>further translation to native code – although the current plan is to
 * generate Java bytecodes to leverage the enormous amount of effort that went
 * into the bytecode verifier, concurrency semantics, and HotSpot's low-level
 * optimizations.</li>
 * </ul>
 * As of 2011.05.09, only the first of these optimizations has been implemented,
 * although a translation into Smalltalk blocks was implemented experimentally
 * by Mark van Gulik in the mid-1990s.
 * </p>
 *
 * <p>
 * To accomplish these goals, the stack-oriented architecture of level one maps
 * onto a register transfer language for level two.  At runtime the idealized
 * {@linkplain L2Interpreter interpreter} has an arbitrarily large bank of
 * pointer registers (that point to {@linkplain AvailObject Avail objects}),
 * plus a separate bank for {@code int}s (unboxed 32-bit signed integers), and
 * a similar bank yet for {@code double}s (unboxed double-precision floating
 * point numbers).  Ideally these will map to machine registers, but more likely
 * they will spill into physical arrays of the appropriate type.  Register
 * spilling is a well studied art, and essentially a solved problem.  Better
 * yet, the Java HotSpot optimizer should be able to do at least as good a job
 * as we can, so we should be able to just generate Java bytecodes and leave it
 * at that.
 * </p>
 *
 * <p>
 * One of the less intuitive aspects of the level one / level two mapping is how
 * to handle the call stack.  The level one view is of a chain of continuations,
 * but level two doesn't even have a stack!  We bridge this disconnect by
 * reserving a register to hold the level one continuation of the
 * <em>caller</em> of the current method.  This is at least vaguely analogous to
 * the way that high level languages typically implement their calling
 * conventions using stack frames and such.
 * </p>
 *
 * <p>
 * However, our target is not assembly language (nor something that purports to
 * operate at that level in some platform-neutral way).  Instead, our target
 * language, level two, is designed for representing and performing
 * optimization.  With this in mind, the level two instruction set includes an
 * instruction that constructs a new continuation from a list of registers.  A
 * corresponding instruction "explodes" a continuation into registers reserved
 * as part of the calling convention (strictly enforced).  During transition
 * from caller to callee (and vice-versa), the only registers that hold usable
 * state are the "architectural" registers – those that hold the state of a
 * continuation being constructed or deconstructed.  This sounds brutally
 * inefficient, but time will tell.  Also, I have devised and implemented
 * mechanisms to allow deeper inlining than would normally be possible in a
 * traditional system, the explicit construction and deconstruction of
 * continuations being one such mechanism.
 * </p>
 *
 * <p>
 * Note that unlike languages like C and C++, optimizations below level one are
 * always transparent – other than observations about performance and memory
 * use.  Also note that this was a design constraint for Avail as far back as
 * 1993, after <span style="font-variant: small-caps;">Self</span>, but before
 * its technological successor Java.  The way in which this is accomplished (or
 * will be more fully accomplished) in Avail is by allowing the generated level
 * two code itself to define how to maintain the "accurate fiction" of a level
 * one interpreter.  If a method is inlined ten layers deep inside an outer
 * method, a non-inlined call from that inner method requires ten layers of
 * continuations to be constructed prior to the call (to accurately maintain the
 * fiction that it was always simply interpreting level one nybblecodes).  There
 * are ways to avoid or at least postpone this phase transition, but I don't
 * have any solid plans for introducing such a mechanism any time soon.
 * </p>
 *
 * <p>
 * Finally, note that the Avail control structures are defined in terms of
 * multimethod dispatch and continuation resumption.  As of 2011.05.09 they
 * are also <em>implemented</em> that way, but a goal is to perform object
 * escape analysis in such a way that it deeply favors chasing continuations.
 * If successful, a continuation resumption can basically be rewritten as a
 * jump, leading to a more traditional control flow in the typical case, which
 * should be much easier to further optimize (say with SSA) than code which
 * literally passes and resumes continuations.  In those cases that the
 * continuation actually escapes (say, if the continuations are used for
 * backtracking) then it can't dissolve into a simple jump – but it will still
 * execute correctly, just not as quickly.
 * </p>
 *
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
	private AvailObject chunk;

	/**
	 * Return the currently executing {@linkplain L2ChunkDescriptor level two
	 * chunk}.
	 *
	 * @return
	 *            The {@linkplain L2ChunkDescriptor level two chunk} that is
	 *            currently being executed.
	 */
	@InnerAccess AvailObject chunk ()
	{
		return chunk;
	}

	/**
	 * Start executing a new chunk.  The {@linkplain #offset} at which to
	 * execute must be set separately.
	 *
	 * <p>
	 * Note that the {@linkplain CompiledCodeDescriptor compiled code} is passed
	 * in because the {@linkplain L2ChunkDescriptor#unoptimizedChunk()
	 * default chunk} doesn't inherently know how many registers it needs
	 * – the answer depends on the level one compiled code being executed.
	 * </p>
	 *
	 * @param chunk
	 *            The {@linkplain L2ChunkDescriptor level two chunk} to start
	 *            executing.
	 * @param code
	 *            The {@linkplain CompiledCodeDescriptor compiled code} on whose
	 *            behalf to start executing the chunk.
	 */
	private void setChunk (
		final @NotNull AvailObject chunk,
		final @NotNull AvailObject code)
	{
		this.chunk = chunk;
		chunkWords = chunk.wordcodes();
		chunkVectors = chunk.vectors();
		makeRoomForChunkRegisters(chunk, code);
		L2ChunkDescriptor.moveToHead(chunk);

		if (logger.isLoggable(Level.FINER))
		{
			logger.finer(String.format(
				"executing new chunk (%d)", chunk.index()));
		}
	}

	/**
	 * The L2 instruction stream as a tuple of integers.
	 */
	private AvailObject chunkWords;

	/**
	 * This chunk's register vectors.  A register vector is a tuple of integers
	 * that represent {@link #pointers Avail object registers}.
	 */
	private AvailObject chunkVectors;

	/**
	 * The registers that hold {@link AvailObject Avail objects}.
	 */
	private AvailObject[] pointers = new AvailObject[10];

	/**
	 * The 32-bit signed integer registers.
	 */
	private int[] integers = new int[10];

	/**
	 * The double-precision floating point registers.
	 */
	private double[] doubles = new double[10];

	/**
	 * The current pointer into {@link #chunkWords}, the level two instruction
	 * stream.
	 */
	private int offset;

	/**
	 * A reusable temporary buffer used to hold arguments during method
	 * invocations.
	 */
	final List<AvailObject> argsBuffer = new ArrayList<AvailObject>();

	/**
	 * Whether or not execution has completed.
	 */
	private boolean exitNow = false;

	/**
	 * The value returned by the outermost continuation.
	 */
	private AvailObject exitValue;

	/**
	 * The dispatcher used to simulate level one instructions via {@link
	 * #L2_doInterpretOneInstructionAndBranchBackIfNoInterrupt()}.
	 */
	final L1OperationDispatcher levelOneDispatcher = new L1OperationDispatcher()
	{
		/**
		 * Push a value onto the current virtualized continuation's stack (which
		 * is just some consecutively-numbered pointer registers and an integer
		 * register that maintains the position).
		 *
		 * @param value The value to push on the virtualized stack.
		 */
		private final void push (@NotNull final AvailObject value)
		{
			int stackp = integerAt(stackpRegister());
			stackp--;
			assert stackp >= argumentRegister(1);
			pointerAtPut(stackp, value);
			integerAtPut(stackpRegister(), stackp);
		}

		/**
		 * Pop a value off the current virtualized continuation's stack (which
		 * is just some consecutively-numbered pointer registers and an integer
		 * register that maintains the position).
		 *
		 * @return The value popped off the virtualized stack.
		 */
		private final @NotNull AvailObject pop ()
		{
			final int stackp = integerAt(stackpRegister());
			assert stackp <= argumentRegister(
				pointerAt(functionRegister()).code().numArgsAndLocalsAndStack());
			final AvailObject popped = pointerAt(stackp);
			// Clear the stack slot
			pointerAtPut(stackp, NullDescriptor.nullObject());
			integerAtPut(stackpRegister(), stackp + 1);
			return popped;
		}

		/**
		 * Extract the specified literal from the current function's code.
		 *
		 * @param literalIndex
		 *            The index of the literal to look up in the current
		 *            function's code.
		 * @return
		 *            The literal extracted from the specified literal slot of
		 *            the code.
		 */
		private final @NotNull AvailObject literalAt (final int literalIndex)
		{
			final AvailObject function = pointerAt(functionRegister());
			final AvailObject code = function.code();
			return code.literalAt(literalIndex);
		}


		/**
		 * [n] - Send the message at index n in the compiledCode's literals. Pop
		 * the arguments for this message off the stack (the message itself
		 * knows how many to expect). The first argument was pushed first, and
		 * is the deepest on the stack. Use these arguments to look up the
		 * method dynamically. Before invoking the method, push the {@linkplain
		 * NullDescriptor#nullObject() null object} onto the stack. Its presence
		 * will help distinguish continuations produced by the pushLabel
		 * instruction from their senders. When the call completes (if ever) by
		 * using an implicit return instruction, it will replace this null object
		 * with the result of the call.
		 */
		@Override
		public void L1_doCall()
		{
			final AvailObject implementations = literalAt(getInteger());
			final AvailObject expectedReturnType = literalAt(getInteger());
			final int numArgs = implementations.numArgs();
			argsBuffer.clear();
			for (int i = numArgs; i >= 1; i--)
			{
				argsBuffer.add(0, pop());
			}
			final AvailObject matching =
				implementations.lookupByValuesFromList(argsBuffer);
			if (matching.equalsNull())
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
			// Leave the expected return type pushed on the stack.  This will be
			// used when the method returns, and it also helps distinguish label
			// continuations from call continuations.
			push(expectedReturnType);

			// Call the method...
			reifyContinuation();
			invokePossiblePrimitiveWithReifiedCaller(matching.bodyBlock());
		}

		/**
		 * [n] - Push the literal indexed by n in the current compiledCode.
		 */
		@Override
		public void L1_doPushLiteral ()
		{
			final int literalIndex = getInteger();
			final AvailObject constant = literalAt(literalIndex);
			// We don't need to make constant beImmutable because *code objects*
			// are always immutable.
			push(constant);
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
			final int localIndex = argumentRegister(getInteger());
			final AvailObject local = pointerAt(localIndex);
			pointerAtPut(localIndex, NullDescriptor.nullObject());
			push(local);
		}

		/**
		 * [n] - Push the argument (actual value) or local variable (the
		 * variable itself) indexed by n.
		 */
		@Override
		public void L1_doPushLocal ()
		{
			final int localIndex = argumentRegister(getInteger());
			final AvailObject local = pointerAt(localIndex);
			local.makeImmutable();
			push(local);
		}

		/**
		 * [n] - Push the outer variable indexed by n in the current function. If
		 * the variable is mutable, clear it (no one will know). If the variable
		 * and function are both mutable, remove the variable from the function by
		 * voiding it.
		 */
		@Override
		public void L1_doPushLastOuter ()
		{
			final AvailObject function = pointerAt(functionRegister());
			final int outerIndex = getInteger();
			final AvailObject outer = function.outerVarAt(outerIndex);
			if (outer.equalsNull())
			{
				error("Someone prematurely erased this outer var");
				return;
			}
			if (!function.optionallyNilOuterVar(outerIndex))
			{
				outer.makeImmutable();
			}
			push(outer);
		}

		/**
		 * [n,m] - Pop the top n items off the stack, and use them as outer
		 * variables in the construction of a function based on the compiledCode
		 * that's the literal at index m of the current compiledCode.
		 */
		@Override
		public void L1_doClose ()
		{
			final int numCopiedVars = getInteger();
			final int literalIndexOfCode = getInteger();
			final AvailObject codeToClose = literalAt(literalIndexOfCode);
			final AvailObject newFunction = FunctionDescriptor.mutable().create(
				numCopiedVars);
			newFunction.code(codeToClose);
			for (int i = numCopiedVars; i >= 1; i--)
			{
				final AvailObject value = pop();
				assert !value.equalsNull();
				newFunction.outerVarAtPut(i, value);
			}
			/*
			 * We don't assert assertObjectUnreachableIfMutable: on the popped
			 * outer variables because each outer variable's new reference from
			 * the function balances the lost reference from the wiped stack.
			 * Likewise we don't tell them makeImmutable(). The function itself
			 * should remain mutable at this point, otherwise the outer
			 * variables would have to makeImmutable() to be referenced by an
			 * immutable function.
			 */
			push(newFunction);
		}

		/**
		 * [n] - Pop the stack and assign this value to the local variable (not
		 * an argument) indexed by n (index 1 is first argument).
		 */
		@Override
		public void L1_doSetLocal ()
		{
			final int localIndex = argumentRegister(getInteger());
			final AvailObject localVariable = pointerAt(localIndex);
			final AvailObject value = pop();
			// The value's reference from the stack is now from the variable.
			localVariable.setValue(value);
		}

		/**
		 * [n] - Push the value of the local variable (not an argument) indexed
		 * by n (index 1 is first argument). If the variable itself is mutable,
		 * clear it now - nobody will know.
		 */
		@Override
		public void L1_doGetLocalClearing ()
		{
			final int localIndex = argumentRegister(getInteger());
			final AvailObject localVariable = pointerAt(localIndex);
			final AvailObject value = localVariable.getValue();
			if (localVariable.traversed().descriptor().isMutable())
			{
				localVariable.clearValue();
			}
			else
			{
				value.makeImmutable();
			}
			push(value);
		}

		/**
		 * [n] - Push the outer variable indexed by n in the current function.
		 */
		@Override
		public void L1_doPushOuter ()
		{
			final AvailObject function = pointerAt(functionRegister());
			final int outerIndex = getInteger();
			final AvailObject outer = function.outerVarAt(outerIndex);
			if (outer.equalsNull())
			{
				error("Someone prematurely erased this outer var");
				return;
			}
			outer.makeImmutable();
			push(outer);
		}

		/**
		 * [] - Remove the top item from the stack.
		 */
		@Override
		public void L1_doPop ()
		{
			pop();
		}

		/**
		 * [n] - Push the value of the outer variable indexed by n in the
		 * current function. If the variable itself is mutable, clear it at this
		 * time - nobody will know.
		 */
		@Override
		public void L1_doGetOuterClearing ()
		{
			final AvailObject function = pointerAt(functionRegister());
			final int outerIndex = getInteger();
			final AvailObject outerVariable = function.outerVarAt(outerIndex);
			final AvailObject value = outerVariable.getValue();
			if (outerVariable.traversed().descriptor().isMutable())
			{
				outerVariable.clearValue();
			}
			else
			{
				value.makeImmutable();
			}
			push(value);
		}

		/**
		 * [n] - Pop the stack and assign this value to the outer variable
		 * indexed by n in the current function.
		 */
		@Override
		public void L1_doSetOuter ()
		{
			final AvailObject function = pointerAt(functionRegister());
			final int outerIndex = getInteger();
			final AvailObject outerVariable = function.outerVarAt(outerIndex);
			if (outerVariable.equalsNull())
			{
				error("Someone prematurely erased this outer var");
				return;
			}
			final AvailObject newValue = pop();
			// The value's reference from the stack is now from the variable.
			outerVariable.setValue(newValue);
		}

		/**
		 * [n] - Push the value of the local variable (not an argument) indexed
		 * by n (index 1 is first argument).
		 */
		@Override
		public void L1_doGetLocal ()
		{
			final int localIndex = argumentRegister(getInteger());
			final AvailObject localVariable = pointerAt(localIndex);
			final AvailObject value = localVariable.getValue();
			value.makeImmutable();
			push(value);
		}

		/**
		 * [n] - Pop n values off the stack to make a tuple.
		 */
		@Override
		public void L1_doMakeTuple ()
		{
			final int count = getInteger();
			final AvailObject tuple = ObjectTupleDescriptor.mutable().create(
				count);
			for (int i = count; i >= 1; i--)
			{
				tuple.tupleAtPut(i, pop());
			}
			tuple.hashOrZero(0);
			push(tuple);
		}

		/**
		 * [n] - Push the value of the outer variable indexed by n in the
		 * current function.
		 */
		@Override
		public void L1_doGetOuter ()
		{
			final AvailObject function = pointerAt(functionRegister());
			final int outerIndex = getInteger();
			final AvailObject outerVariable = function.outerVarAt(outerIndex);
			final AvailObject outer = outerVariable.getValue();
			if (outer.equalsNull())
			{
				error("Someone prematurely erased this outer var");
				return;
			}
			outer.makeImmutable();
			push(outer);
		}

		/**
		 * The extension nybblecode was encountered. Read another nybble and
		 * dispatch it as an extended instruction.
		 */
		@Override
		public void L1_doExtension ()
		{
			final AvailObject function = pointerAt(functionRegister());
			final AvailObject code = function.code();
			final AvailObject nybbles = code.nybbles();
			int pc = integerAt(pcRegister());
			final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
			pc++;
			integerAtPut(pcRegister(), pc);
			L1Operation.values()[nybble + 16].dispatch(levelOneDispatcher);
		}

		/**
		 * Build a continuation which, when restarted, will be just like
		 * restarting the current continuation.
		 */
		@Override
		public void L1Ext_doPushLabel ()
		{
			final AvailObject function = pointerAt(functionRegister());
			final AvailObject code = function.code();
			final int numArgs = code.numArgs();
			final List<AvailObject> args = new ArrayList<AvailObject>(numArgs);
			for (int i = 1; i <= numArgs; i++)
			{
				args.add(pointerAt(argumentRegister(i)));
			}
			final int numLocals = code.numLocals();
			final List<AvailObject> locals =
				new ArrayList<AvailObject>(numLocals);
			for (int i = 1; i <= numLocals; i++)
			{
				locals.add(pointerAt(argumentRegister(numArgs + i)));
			}
			final AvailObject newContinuation = ContinuationDescriptor.create(
				function,
				pointerAt(callerRegister()),
				chunk(),
				args,
				locals);
			// Freeze all fields of the new object, including its caller,
			// function, and args.
			newContinuation.makeSubobjectsImmutable();
			// ...always a fresh copy, always mutable (uniquely owned).
			assert newContinuation.caller().equalsNull()
				|| !newContinuation.caller().descriptor().isMutable()
			: "Caller should freeze because two continuations can see it";
			push(newContinuation);
		}

		/**
		 * [n] - Push the value of the variable that's literal number n in the
		 * current compiledCode.
		 */
		@Override
		public void L1Ext_doGetLiteral ()
		{
			final int literalIndex = getInteger();
			final AvailObject literalVariable = literalAt(literalIndex);
			// We don't need to make constant beImmutable because *code objects*
			// are always immutable.
			final AvailObject value = literalVariable.getValue();
			value.makeImmutable();
			push(value);
		}

		/**
		 * [n] - Pop the stack and assign this value to the variable that's the
		 * literal indexed by n in the current compiledCode.
		 */
		@Override
		public void L1Ext_doSetLiteral ()
		{
			final int literalIndex = getInteger();
			final AvailObject literalVariable = literalAt(literalIndex);
			final AvailObject value = pop();
			// The value's reference from the stack is now from the variable.
			literalVariable.setValue(value);
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
		 * (and types) are popped, then a sentinel null object is pushed, and
		 * the looked up method is started. When the invoked method returns (via
		 * an implicit return instruction), this sentinel will be replaced by
		 * the result of the call.
		 */
		@Override
		public void L1Ext_doSuperCall ()
		{
			final AvailObject implementations = literalAt(getInteger());
			final AvailObject expectedReturnType = literalAt(getInteger());
			final int numArgs = implementations.numArgs();
			// Pop the argument types (the types by which to do a lookup)...
			argsBuffer.clear();
			for (int i = numArgs; i >= 1; i--)
			{
				argsBuffer.add(0, pop());
			}
			final AvailObject matching =
				implementations.lookupByTypesFromList(argsBuffer);
			if (matching.equalsNull())
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
			// Pop the arguments themselves...
			argsBuffer.clear();
			for (int i = numArgs; i >= 1; i--)
			{
				argsBuffer.add(0, pop());
			}
			// Leave the expected return type pushed on the stack.  This will be
			// used when the method returns, and it also helps distinguish label
			// continuations from call continuations.
			push(expectedReturnType);

			// Call the method...
			reifyContinuation();
			invokePossiblePrimitiveWithReifiedCaller(matching.bodyBlock());
		}

		/**
		 * [n] - Push the nth stack element's type. This is only used by the
		 * supercast mechanism to produce types for arguments not being cast.
		 * See #doSuperCall. This implies the type will be used for a lookup and
		 * then discarded. We therefore don't treat the type as acquiring a new
		 * reference from the stack, so it doesn't have to become immutable.
		 * This could be a sticky point with the garbage collector if it finds
		 * only one reference to the type, but I think it will still work.
		 *
		 * <p>
		 * Strike that.  The level one state has to have a consistent reference
		 * count, so we have to make the object immutable in case the type has
		 * to refer to it.
		 * </p>
		 */
		@Override
		public void L1Ext_doGetType ()
		{
			final int depth = getInteger();
			final int deepStackp = integerAt(stackpRegister() + depth);
			final AvailObject value = pointerAt(deepStackp);
			value.makeImmutable();
			push(value.kind());
		}

		/**
		 * Duplicate the element at the top of the stack. Make the element
		 * immutable since there are now at least two references.
		 */
		@Override
		public void L1Ext_doDuplicate ()
		{
			final int stackp = integerAt(stackpRegister());
			final AvailObject value = pointerAt(stackp);
			value.makeImmutable();
			push(value);
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
		 * Note that the calling continuation has automatically pushed the
		 * expected return type, which after being used to check the return
		 * value should simply be replaced by this value.  This avoids
		 * manipulating the stack depth.
		 */
		@Override
		public void L1Implied_doReturn ()
		{
			final AvailObject caller = pointerAt(callerRegister());
			final AvailObject value = pop();
			final AvailObject function = pointerAt(functionRegister());
			assert value.isInstanceOf(
					function.code().functionType().returnType())
				: "Return type from method disagrees with declaration";
			if (caller.equalsNull())
			{
				exitProcessWith(value);
				return;
			}
			prepareToExecuteContinuation(caller);
			final AvailObject expectedType = pop();
			if (!value.isInstanceOf(expectedType))
			{
				error(String.format(
					"Return value (%s) does not agree with expected type (%s)",
					value,
					expectedType));
			}
			push(value);
		}
	};


	@Override
	public void exitProcessWith (final AvailObject finalObject)
	{
		process.executionState(ExecutionState.TERMINATED);
		process.continuation(NullDescriptor.nullObject());
		exitNow = true;
		exitValue = finalObject;
		pointerAtPut(callerRegister(), null);
		pointerAtPut(functionRegister(), null);
	}


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
	private int offset ()
	{
		return offset;
	}

	/**
	 * Jump to a new position in the L2 wordcode stream.
	 *
	 * @param newOffset The new position in the L2 wordcode stream.
	 */
	private void offset (final int newOffset)
	{
		// System.out.printf("[#%d] %d -> %d%n", _chunk.index(), _offset,
		// newOffset);
		offset = newOffset;
	}

	/**
	 * Answer the subscript of the integer register reserved for holding the
	 * current (virtualized) continuation's {@linkplain
	 * ContinuationDescriptor#o_Pc(AvailObject) pc} (program counter).
	 *
	 * @return The subscript to use with {@link L2Interpreter#integerAt(int)}.
	 */
	final public int pcRegister ()
	{
		// reserved
		return 1;
	}


	/**
	 * Answer the subscript of the integer register reserved for holding the
	 * current (virtualized) continuation's {@linkplain
	 * ContinuationDescriptor#o_Stackp(AvailObject) stackp} (stack pointer).
	 * While in this register, the value refers to the exact pointer register
	 * number rather than the value that would be stored in a continuation's
	 * stackp slot, so adjustments must be made during reification and
	 * explosion of continuations.
	 *
	 * @return The subscript to use with {@link L2Interpreter#pointerAt(int)}.
	 */
	final public int stackpRegister ()
	{
		// reserved
		return 2;
	}


	/**
	 * Answer the subscript of the register holding the argument with the given
	 * index (e.g., the first argument is in register 3).
	 *
	 * @param localNumber The one-based argument/local number.
	 * @return The subscript to use with {@link L2Interpreter#pointerAt(int)}.
	 */
	final public int argumentRegister (final int localNumber)
	{
		// Skip the continuation and function.
		return localNumber + 2;
	}


	/**
	 * Answer the current continuation.
	 *
	 * @return The current continuation.
	 */
	final public AvailObject currentContinuation ()
	{
		return pointerAt(callerRegister());
	}


	/**
	 * Answer the subscript of the register holding the current context's caller
	 * context.
	 *
	 * @return The subscript to use with {@link L2Interpreter#pointerAt(int)}.
	 */
	final public int callerRegister ()
	{
		// reserved
		return 1;
	}


	/**
	 * Answer the subscript of the register holding the current context's
	 * function.
	 *
	 * @return The subscript to use with {@link L2Interpreter#pointerAt(int)}.
	 */
	final public int functionRegister ()
	{
		// reserved
		return 2;
	}


	/**
	 * Answer an integer extracted at the current program counter. The program
	 * counter will be adjusted to skip over the integer.
	 *
	 * @return An integer extracted from the instruction stream.
	 */
	public int getInteger ()
	{
		final AvailObject function = pointerAt(functionRegister());
		final AvailObject code = function.code();
		final AvailObject nybbles = code.nybbles();
		int pc = integerAt(pcRegister());
		final byte firstNybble = nybbles.extractNybbleFromTupleAt(pc);
		pc++;
		int value = 0;
		final byte[] counts =
		{
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 4, 8
		};
		for (int count = counts[firstNybble]; count > 0; count--, pc++)
		{
			value = (value << 4) + nybbles.extractNybbleFromTupleAt(pc);
		}
		final byte[] offsets =
		{
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 26, 42, 58, 0, 0
		};
		value += offsets[firstNybble];
		integerAtPut(pcRegister(), pc);
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
		assert index > 0;
		assert pointers[index] != null;
		return pointers[index];
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
		assert index > 0;
		assert index < 3 || anAvailObject != null;
		pointers[index] = anAvailObject;
	}


	/**
	 * Read from an integer register.  The index is one-based.  Entry [0] is
	 * unused.
	 *
	 * @param index The one-based integer-register index.
	 * @return The int in the specified register.
	 */
	public int integerAt (final int index)
	{
		assert index > 0;
		return integers[index];
	}


	/**
	 * Write to an integer register.  The index is one-based.  Entry [0] is
	 * unused.
	 *
	 * @param index The one-based integer-register index.
	 * @param value The value to write to the register.
	 */
	public void integerAtPut (final int index, final int value)
	{
		assert index > 0;
		integers[index] = value;
	}


	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Construct the continuation from the L2 registers that hold the
	 * L1-architectural state.  Write the resulting continuation into the
	 * {@linkplain L2Interpreter#callerRegister() caller register}.
	 * </p>
	 */
	@Override
	public void reifyContinuation ()
	{
		final AvailObject function = pointerAt(functionRegister());
		final AvailObject code = function.code();
		final AvailObject continuation =
			ContinuationDescriptor.mutable().create(
				code.numArgsAndLocalsAndStack());
		continuation.caller(pointerAt(callerRegister()));
		continuation.function(function);
		continuation.pc(integerAt(pcRegister()));
		continuation.stackp(
			integerAt(stackpRegister()) + 1 - argumentRegister(1));
		continuation.levelTwoChunkOffset(chunk(), offset());
		for (int i = code.numArgsAndLocalsAndStack(); i >= 1; i--)
		{
			continuation.argOrLocalOrStackAtPut(
				i,
				pointerAt(argumentRegister(i)));
		}
		pointerAtPut(callerRegister(), continuation);
	}

	@Override
	public void prepareToExecuteContinuation (final AvailObject continuation)
	{
		if (continuation.equalsNull())
		{
			chunk = NullDescriptor.nullObject();
			chunkWords = NullDescriptor.nullObject();
			chunkVectors = NullDescriptor.nullObject();
			offset(0);
			pointerAtPut(callerRegister(), NullDescriptor.nullObject());
			pointerAtPut(functionRegister(), NullDescriptor.nullObject());
			return;
		}
		AvailObject chunkToInvoke = continuation.levelTwoChunk();
		if (!chunkToInvoke.isValid())
		{
			// The chunk has been invalidated, but the continuation still refers
			// to it.  The garbage collector will reclaim the chunk only when
			// all such continuations have let the chunk go.  Therefore, let it
			// go.  Fall back to the default level two chunk that steps over
			// nybblecodes.
			chunkToInvoke = L2ChunkDescriptor.unoptimizedChunk();
			continuation.levelTwoChunkOffset(
				chunkToInvoke,
				L2ChunkDescriptor.offsetToContinueUnoptimizedChunk());
		}
		setChunk(chunkToInvoke, continuation.function().code());
		offset(continuation.levelTwoOffset());

		integerAtPut(pcRegister(), continuation.pc());
		integerAtPut(stackpRegister(), argumentRegister(continuation.stackp()));
		pointerAtPut(callerRegister(), continuation.caller());
		pointerAtPut(functionRegister(), continuation.function());
		final int slots = continuation.numArgsAndLocalsAndStack();
		for (int i = 1; i <= slots; i++)
		{
			pointerAtPut(argumentRegister(i), continuation.stackAt(i));
		}
	}

	/**
	 * Increase the number of registers if necessary to accommodate the new
	 * chunk/code.
	 *
	 * @param theChunk The {@link L2ChunkDescriptor L2Chunk} about to be
	 *                 invoked.
	 * @param theCode The code about to be invoked.
	 */
	private void makeRoomForChunkRegisters (
		final AvailObject theChunk,
		final AvailObject theCode)
	{
		final int neededObjectCount = max(
			theChunk.numObjects(),
			theCode.numArgsAndLocalsAndStack()) + 3;
		if (neededObjectCount > pointers.length)
		{
			final AvailObject[] newPointers =
				new AvailObject[neededObjectCount * 2 + 10];
			System.arraycopy(pointers, 0, newPointers, 0, pointers.length);
			pointers = newPointers;
		}
		if (theChunk.numIntegers() > integers.length)
		{
			final int[] newIntegers = new int[theChunk.numIntegers() * 2 + 10];
			System.arraycopy(integers, 0, newIntegers, 0, integers.length);
			integers = newIntegers;
		}
		if (theChunk.numDoubles() > doubles.length)
		{
			final double[] newDoubles =
				new double[theChunk.numDoubles() * 2 + 10];
			System.arraycopy(doubles, 0, newDoubles, 0, doubles.length);
			doubles = newDoubles;
		}
	}

	/**
	 * Translate the code into a chunk using the specified effort. An effort of
	 * zero means produce an initial translation that decrements a counter on
	 * each invocation, re-optimizing (with more effort) if it reaches zero.
	 * The code is updated to refer to the new chunk.
	 *
	 * @param theCode The code to translate.
	 * @param effort How much effort to put into the optimization effort.
	 */
	private void privateTranslateCodeOptimization (
		final AvailObject theCode,
		final int effort)
	{
		new L2Translator().translateOptimizationFor(
			theCode,
			effort,
			this);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Raise an exception. Scan the stack of continuations until one is found
	 * for a function whose code specifies {@linkplain
	 * Primitive#prim200_CatchException primitive 200}.
	 * Get that continuation's second argument (a handler block of one
	 * argument), and check if that handler block will accept the
	 * exceptionValue. If not, keep looking. If it will accept it, unwind the
	 * stack so that the primitive 200 method is the top entry, and invoke the
	 * handler block with exceptionValue. If there is no suitable handler block,
	 * fail the primitive.
	 * </p>
	 */
	@Override
	public Result searchForExceptionHandler (
		final List<AvailObject> arguments)
	{
		assert arguments.size() == 1;

		final AvailObject exceptionValue = arguments.get(0);
		AvailObject continuation = pointerAt(callerRegister());
		while (!continuation.equalsNull())
		{
			if (continuation.function().code().primitiveNumber() == 200)
			{
				final AvailObject handler = continuation.argOrLocalOrStackAt(2);
				if (exceptionValue.isInstanceOf(
					handler.kind().argsTupleType().typeAtIndex(1)))
				{
					assert !handler.equalsNull();
					prepareToExecuteContinuation(continuation);
					return invokeFunctionArguments(
						handler,
						Collections.singletonList(exceptionValue));
				}
			}
			continuation = continuation.caller();
		}
		return primitiveFailure(E_UNHANDLED_EXCEPTION);
	}

	@Override
	public Result invokeFunctionArguments (
		final AvailObject aFunction,
		final List<AvailObject> args)
	{
		final AvailObject code = aFunction.code();
		assert code.numArgs() == args.size();
		final int primNum = code.primitiveNumber();
		if (primNum != 0)
		{
			final Result result = attemptPrimitive(primNum, code, args);
			if (result == SUCCESS)
			{
				return result;
			}
			if (result == CONTINUATION_CHANGED)
			{
				return result;
			}
		}
		// Either it wasn't a primitive or the primitive failed.
		if (!pointerAt(functionRegister()).equalsNull())
		{
			integerAtPut(pcRegister(), 1);
			integerAtPut(
				stackpRegister(),
				argumentRegister(code.numArgsAndLocalsAndStack() + 1));
			// reifyContinuation();
		}
		invokeWithoutPrimitiveFunctionArguments(aFunction, args);
		return CONTINUATION_CHANGED;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Prepare the L2Interpreter to deal with executing the given function, using
	 * the given arguments.  Also set up the new function's locals.  Assume the
	 * current context has already been reified.  If the function is a primitive,
	 * then it was already attempted and must have failed, so the failure value
	 * must be in {@link #primitiveResult}.  The (Java) caller will deal with
	 * that.
	 * </p>
	 */
	@Override
	public void invokeWithoutPrimitiveFunctionArguments (
		final AvailObject aFunction,
		final List<AvailObject> args)
	{
		final AvailObject code = aFunction.code();
		AvailObject chunkToInvoke = code.startingChunk();
		if (!chunkToInvoke.isValid())
		{
			// The chunk is invalid, so use the default chunk and patch up
			// aFunction's code.
			chunkToInvoke = L2ChunkDescriptor.unoptimizedChunk();
			code.startingChunk(chunkToInvoke);
			code.invocationCount(
				L2ChunkDescriptor.countdownForInvalidatedCode());
		}
		setChunk(chunkToInvoke, code);
		offset(1);

		pointerAtPut(functionRegister(), aFunction);
		// Transfer arguments...
		final int numArgs = code.numArgs();
		final int numLocals = code.numLocals();
		final int numStackSlots = code.numArgsAndLocalsAndStack()
			- numLocals
			- numArgs;
		int dest = argumentRegister(1);
		for (int i = 1; i <= numArgs; i++)
		{
			pointerAtPut(dest, args.get(i - 1));
			dest++;
		}
		// Create locals...
		for (int i = 1; i <= numLocals; i++)
		{
			pointerAtPut(
				dest,
				ContainerDescriptor.forOuterType(code.localTypeAt(i)));
			dest++;
		}
		// Void the stack slots (by filling them with the null object)...
		for (int i = 1; i <= numStackSlots; i++)
		{
			pointerAtPut(dest, NullDescriptor.nullObject());
			dest++;
		}
	}

	/**
	 * Start or complete execution of the specified function.  The function is
	 * permitted to be primitive.  The current continuation must already have
	 * been reified.
	 *
	 * @param theFunction
	 *            The function to invoke.
	 */
	void invokePossiblePrimitiveWithReifiedCaller (
		final AvailObject theFunction)
	{
		final AvailObject theCode = theFunction.code();
		final int primNum = theCode.primitiveNumber();
		if (primNum != 0)
		{
			final Result primResult = attemptPrimitive(
				primNum,
				theCode,
				argsBuffer);
			switch (primResult)
			{
				case CONTINUATION_CHANGED:
				case SUSPENDED:
					return;
				case SUCCESS:
					final AvailObject caller = pointerAt(callerRegister());
					prepareToExecuteContinuation(caller);
					final int callerStackpIndex =
						argumentRegister(caller.stackp());
					final AvailObject expectedType =
						pointerAt(callerStackpIndex);
					if (!primitiveResult.isInstanceOf(expectedType))
					{
						// TODO: [MvG] Remove after debugging.
						primitiveResult.isInstanceOf(expectedType);
						error(String.format(
							"Return value (%s) does not agree with "
							+ "expected type (%s)",
							primitiveResult,
							expectedType));
					}
					pointerAtPut(callerStackpIndex, primitiveResult);
					return;
				case FAILURE:
					invokeWithoutPrimitiveFunctionArguments(
						theFunction,
						argsBuffer);
					final int failureVariableIndex =
						argumentRegister(theCode.numArgs() + 1);
					final AvailObject failureVariable =
						pointerAt(failureVariableIndex);
					failureVariable.setValue(primitiveResult);
					return;
			}
		}
		invokeWithoutPrimitiveFunctionArguments(
			theFunction,
			argsBuffer);
	}

	/**
	 * Run the interpreter until the outermost function returns, answering the
	 * result it returns.  Assume the interpreter has already been set up to run
	 * something other than a (successful) primitive function at the outermost
	 * level.
	 *
	 * @return The final result produced by outermost function.
	 */
	private AvailObject run ()
	{
		interruptRequestFlag = 0;
		exitNow = false;
		while (!exitNow)
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
			if (logger.isLoggable(Level.FINEST))
			{
				final StringBuilder stackString = new StringBuilder();
				AvailObject chain = pointerAt(callerRegister());
				while (!chain.equalsNull())
				{
					stackString.insert(0, "->");
					stackString.insert(0, argumentRegister(chain.stackp()));
					chain = chain.caller();
				}

				logger.finest(String.format(
					"executing %s (pc = %d) [stack: %s]",
					operation,
					offset - 1,
					stackString));
			}

			operation.dispatch(this);
		}
		return exitValue;
	}

	@Override
	public AvailObject runFunctionArguments (
		final AvailObject function,
		final List<AvailObject> arguments)
	{
		pointerAtPut(callerRegister(), NullDescriptor.nullObject());
		pointerAtPut(functionRegister(), NullDescriptor.nullObject());
		// Keep the process's current continuation clear during execution.
		process.continuation(NullDescriptor.nullObject());

		final Result result = invokeFunctionArguments(function, arguments);
		if (result == SUCCESS)
		{
			// Outermost call was a primitive invocation.
			return primitiveResult;
		}
		return run();
	}

	/**
	 * Extract the next word from the level two instruction stream.
	 *
	 * @return The word.
	 */
	private int nextWord ()
	{
		final int theOffset = offset();
		final int word = chunkWords.tupleIntAt(theOffset);
		offset(theOffset + 1);
		return word;
	}



	@Override
	public void L2_unknownWordcode ()
	{
		error("Unknown wordcode\n");
	}

	@Override
	public void L2_doPrepareNewFrame ()
	{
		// A new function has been set up for execution.  Its L1-architectural
		// slots have already been initialized, except for the pc and stackp.
		// Note that the stack pointer must be adjusted to point to the actual
		// register just above the last slot reserved for continuation frame
		// data.
		final AvailObject function = pointerAt(functionRegister());
		final AvailObject code = function.code();
		integerAtPut(pcRegister(), 1);
		integerAtPut(
			stackpRegister(),
			argumentRegister(code.numArgsAndLocalsAndStack() + 1));
	}

	/**
	 * Execute a single nybblecode of the current continuation, found in
	 * {@link #callerRegister() callerRegister}.  If no interrupt is indicated,
	 * move the L2 {@link #offset()} back to the same instruction (which always
	 * occupies a single word, so the address is implicit).
	 */
	@Override
	public void L2_doInterpretOneInstructionAndBranchBackIfNoInterrupt ()
	{
		final AvailObject function = pointerAt(functionRegister());
		final AvailObject code = function.code();
		final AvailObject nybbles = code.nybbles();
		final int pc = integerAt(pcRegister());

		if (interruptRequestFlag == 0)
		{
			// Branch back to this (operandless) instruction by default.
			offset(offset() - 1);
		}

		// Before we extract the nybblecode, make sure that the PC hasn't passed
		// the end of the instruction sequence. If we have, then execute an
		// L1Implied_doReturn.
		if (pc > nybbles.tupleSize())
		{
			assert pc == nybbles.tupleSize() + 1;
			if (logger.isLoggable(Level.FINEST))
			{
				logger.finest(String.format(
					"simulating %s (pc = %d)",
					L1Operation.L1Implied_Return,
					pc));
			}
			levelOneDispatcher.L1Implied_doReturn();
			return;
		}
		final int nybble = nybbles.extractNybbleFromTupleAt(pc);
		integerAtPut(pcRegister(), pc + 1);

		final L1Operation operation = L1Operation.values()[nybble];
		if (logger.isLoggable(Level.FINEST))
		{
			logger.finest(String.format(
				"simulating %s (pc = %d)",
				operation,
				pc));
		}
		operation.dispatch(levelOneDispatcher);
	}

	@Override
	public void L2_doDecrementCounterAndReoptimizeOnZero ()
	{
		// Decrement the counter in the current code object. If it reaches zero,
		// re-optimize the current code.

		final AvailObject theFunction = pointerAt(functionRegister());
		final AvailObject theCode = theFunction.code();
		final int newCount = theCode.invocationCount() - 1;
		assert newCount >= 0;
		if (newCount != 0)
		{
			theCode.invocationCount(newCount);
		}
		else
		{
			theCode.invocationCount(
				L2ChunkDescriptor.countdownForNewlyOptimizedCode());
			privateTranslateCodeOptimization(theCode, 3);
			argsBuffer.clear();
			final int nArgs = theCode.numArgs();
			for (int i = 1; i <= nArgs; i++)
			{
				argsBuffer.add(pointerAt(argumentRegister(i)));
			}
			invokeFunctionArguments(theFunction, argsBuffer);
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
		pointerAtPut(destIndex, chunk().literalAt(fromIndex));
	}

	@Override
	public void L2_doMoveFromOuterVariable_ofFunctionObject_destObject_ ()
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
			ContainerDescriptor.forOuterType(chunk().literalAt(typeIndex)));
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
		pointerAtPut(clearIndex, NullDescriptor.nullObject());
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
		final long add = integers[addIndex];
		final long dest = integers[destIndex];
		final long result = dest + add;
		final int resultInt = (int) result;
		if (result == resultInt)
		{
			integers[destIndex] = resultInt;
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
		final AvailObject type = chunk().literalAt(typeConstIndex);
		if (value.isInstanceOf(type))
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
		// interrupt for some reason. It has possibly executed several more
		// wordcodes since that time, to place the process into a state that's
		// consistent with naive Level One execution semantics. That is, a naive
		// Level One interpreter should be able to resume the process later. The
		// continuation to use can be found in pointerAt(continuationIndex).
		final int continuationIndex = nextWord();
		process.continuation(pointerAt(continuationIndex));
		process.interruptRequestFlag(interruptRequestFlag);
		exitValue = NullDescriptor.nullObject();
		exitNow = true;
	}

	@Override
	public void L2_doCreateContinuationSender_function_pc_stackp_size_slots_offset_dest_ ()
	{
		final int senderIndex = nextWord();
		final int functionIndex = nextWord();
		final int pcIndex = nextWord();
		final int stackpIndex = nextWord();
		final int sizeIndex = nextWord();
		final int slotsIndex = nextWord();
		final int wordcodeOffset = nextWord();
		final int destIndex = nextWord();
		final AvailObject function = pointerAt(functionIndex);
		final AvailObject code = function.code();
		final AvailObject continuation =
			ContinuationDescriptor.mutable().create(
				code.numArgsAndLocalsAndStack());
		continuation.caller(pointerAt(senderIndex));
		continuation.function(function);
		continuation.pc(pcIndex);
		continuation.stackp(
			code.numArgsAndLocalsAndStack()
			- code.maxStackDepth()
			+ stackpIndex);
		continuation.levelTwoChunkOffset(
			chunk(),
			wordcodeOffset);
		final AvailObject slots = chunkVectors.tupleAt(slotsIndex);
		for (int i = 1; i <= sizeIndex; i++)
		{
			continuation.argOrLocalOrStackAtPut(
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
		final int functionDestIndex = nextWord();
		final int slotsDestIndex = nextWord();
		final AvailObject cont = pointerAt(continuationIndex);
		pointerAtPut(senderDestIndex, cont.caller());
		pointerAtPut(functionDestIndex, cont.function());
		final AvailObject slotsVector = chunkVectors.tupleAt(slotsDestIndex);
		if (slotsVector.tupleSize()
			!= cont.function().code().numArgsAndLocalsAndStack())
		{
			error("problem in doExplode...");
			return;
		}
		for (int i = 1, end = slotsVector.tupleSize(); i <= end; i++)
		{
			pointerAtPut(
				slotsVector.tupleAt(i).extractInt(),
				cont.argOrLocalOrStackAt(i));
		}
	}

	@Override
	public void L2_doSend_argumentsVector_ ()
	{
		// Assume the current continuation is already reified.
		final int selectorIndex = nextWord();
		final int argumentsIndex = nextWord();
		final AvailObject vect = chunkVectors.tupleAt(argumentsIndex);
		argsBuffer.clear();
		for (int i = 1; i <= vect.tupleSize(); i++)
		{
			argsBuffer.add(pointerAt(vect.tupleIntAt(i)));
		}
		final AvailObject selector = chunk().literalAt(selectorIndex);
		final AvailObject signatureToCall =
			selector.lookupByValuesFromList(argsBuffer);
		if (signatureToCall.equalsNull())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (!signatureToCall.isMethod())
		{
			error("Attempted to call a non-implementation signature");
			return;
		}
		invokePossiblePrimitiveWithReifiedCaller(
			signatureToCall.bodyBlock());
	}

	@Override
	public void L2_doSendAfterFailedPrimitive_argumentsVector_ ()
	{
		// The continuation is required to have already been reified.
		final int selectorIndex = nextWord();
		final int argumentsIndex = nextWord();
		final int failureValueIndex = nextWord();
		final AvailObject vect = chunkVectors.tupleAt(argumentsIndex);
		argsBuffer.clear();
		for (int i = 1; i <= vect.tupleSize(); i++)
		{
			argsBuffer.add(pointerAt(vect.tupleIntAt(i)));
		}
		final AvailObject selector = chunk().literalAt(selectorIndex);
		final AvailObject signatureToCall =
			selector.lookupByValuesFromList(argsBuffer);
		if (signatureToCall.equalsNull())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (!signatureToCall.isMethod())
		{
			error("Attempted to call a non-implementation signature");
			return;
		}
		final AvailObject functionToCall = signatureToCall.bodyBlock();
		final AvailObject codeToCall = functionToCall.code();
		final int primNum = codeToCall.primitiveNumber();
		assert primNum != 0;
		assert !Primitive.byPrimitiveNumber(primNum).hasFlag(Flag.CannotFail);
		invokeWithoutPrimitiveFunctionArguments(
			functionToCall,
			argsBuffer);
		// Now write the primitive failure value into the first local.
		final int failureVariableIndex =
			argumentRegister(vect.tupleSize() + 1);
		final AvailObject failureVariable = pointerAt(failureVariableIndex);
		final AvailObject failureValue = pointerAt(failureValueIndex);
		failureVariable.setValue(failureValue);
	}

	@Override
	public void L2_doGetType_destObject_ ()
	{
		final int srcIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(srcIndex).kind());
	}

	@Override
	public void L2_doSuperSend_argumentsVector_argumentTypesVector_ ()
	{
		final int selectorIndex = nextWord();
		final int argumentsIndex = nextWord();
		final int typesIndex = nextWord();
		AvailObject vect = chunkVectors.tupleAt(typesIndex);
		if (true)
		{
			argsBuffer.clear();
			for (int i = 1; i < vect.tupleSize(); i++)
			{
				argsBuffer.add(pointerAt(vect.tupleIntAt(i)));
			}
		}
		final AvailObject selector = chunk().literalAt(selectorIndex);
		final AvailObject signatureToCall = selector
		.lookupByTypesFromList(argsBuffer);
		if (signatureToCall.equalsNull())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (!signatureToCall.isMethod())
		{
			error("Attempted to call a non-implementation signature");
			return;
		}
		vect = chunkVectors.tupleAt(argumentsIndex);
		argsBuffer.clear();
		for (int i = 1; i < vect.tupleSize(); i++)
		{
			argsBuffer.add(pointerAt(vect.tupleIntAt(i)));
		}
		final AvailObject functionToCall = signatureToCall.bodyBlock();
		final AvailObject codeToCall = functionToCall.code();
		final int primNum = codeToCall.primitiveNumber();
		if (primNum != 0)
		{
			prepareToExecuteContinuation(pointerAt(callerRegister()));
			final Result primResult = attemptPrimitive(
				primNum,
				codeToCall,
				argsBuffer);
			if (primResult == CONTINUATION_CHANGED)
			{
				return;
			}
			else if (primResult != FAILURE)
			{
				// Primitive succeeded.
				final AvailObject cont = pointerAt(callerRegister());
				assert chunk() == cont.levelTwoChunk();
				cont.readBarrierFault();
				assert cont.descriptor().isMutable();
				cont.stackAtPut(cont.stackp(), primitiveResult);
				return;
			}
		}
		invokeWithoutPrimitiveFunctionArguments(functionToCall, argsBuffer);
	}

	@Override
	public void L2_doCreateTupleOfSizeImmediate_valuesVector_destObject_ ()
	{
		final int sizeIndex = nextWord();
		final int valuesIndex = nextWord();
		final int destIndex = nextWord();
		final AvailObject indices = chunkVectors.tupleAt(valuesIndex);
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
	public void L2_doCreateFunctionFromCodeObject_outersVector_destObject_ ()
	{
		final int codeIndex = nextWord();
		final int outersIndex = nextWord();
		final int destIndex = nextWord();
		final AvailObject outers = chunkVectors.tupleAt(outersIndex);
		final AvailObject clos = FunctionDescriptor.mutable().create(
			outers.tupleSize());
		clos.code(chunk().literalAt(codeIndex));
		for (int i = 1, end = outers.tupleSize(); i <= end; i++)
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
		final AvailObject caller = pointerAt(continuationIndex);
		if (caller.equalsNull())
		{
			exitProcessWith(valueObject);
			return;
		}
		prepareToExecuteContinuation(caller);
		final int callerStackpIndex = argumentRegister(caller.stackp());
		final AvailObject expectedType = pointerAt(callerStackpIndex);
		if (!valueObject.isInstanceOf(expectedType))
		{

			error("Return value does not agree with expected type");
		}
		pointerAtPut(callerStackpIndex, valueObject);
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
	public void L2_doAttemptPrimitive_withArguments_result_failure_ifFail_ ()
	{
		final int primNumber = nextWord();
		final int argsVector = nextWord();
		final int resultRegister = nextWord();
		final int failureValueRegister = nextWord();
		final int failureOffset = nextWord();
		final AvailObject argsVect = chunkVectors.tupleAt(argsVector);
		argsBuffer.clear();
		for (int i1 = 1; i1 <= argsVect.tupleSize(); i1++)
		{
			argsBuffer.add(pointerAt(argsVect.tupleAt(i1).extractInt()));
		}
		// Only primitive 340 needs the compiledCode argument, and it's always
		// folded.
		final Result res = attemptPrimitive(
			primNumber,
			null,
			argsBuffer);
		if (res == SUCCESS)
		{
			pointerAtPut(resultRegister, primitiveResult);
		}
		else if (res == FAILURE)
		{
			pointerAtPut(failureValueRegister, primitiveResult);
			offset(failureOffset);
		}
		else if (res == CONTINUATION_CHANGED)
		{
			error(
				"attemptPrimitive wordcode should never set up "
				+ "a new continuation",
				primNumber);
		}
		else
		{
			error("Unrecognized return type from attemptPrimitive()");
		}
	}

	/**
	 * Run the specified no-fail primitive with the given arguments, writing the
	 * result into the specified destination.
	 */
	@Override
	public void L2_doNoFailPrimitive_withArguments_result_ ()
	{
		final int primNumber = nextWord();
		final int argsVector = nextWord();
		final int resultRegister = nextWord();
		final AvailObject argsVect = chunkVectors.tupleAt(argsVector);
		argsBuffer.clear();
		for (int i1 = 1; i1 <= argsVect.tupleSize(); i1++)
		{
			argsBuffer.add(pointerAt(argsVect.tupleAt(i1).extractInt()));
		}
		// Only primitive 340 needs the compiledCode argument, and it's always
		// folded.
		final Result res = attemptPrimitive(
			primNumber,
			null,
			argsBuffer);
		assert res == SUCCESS;
		pointerAtPut(resultRegister, primitiveResult);
	}
}
