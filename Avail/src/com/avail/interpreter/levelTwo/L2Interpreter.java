/**
 * interpreter/levelTwo/L2Interpreter.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.interpreter.levelTwo;

import java.util.ArrayList;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ClosureDescriptor;
import com.avail.descriptor.ContainerDescriptor;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.descriptor.L2ChunkDescriptor;
import com.avail.descriptor.ListDescriptor;
import com.avail.descriptor.ObjectTupleDescriptor;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.AvailInterpreter;
import static com.avail.descriptor.AvailObject.*;
import static com.avail.interpreter.Primitive.*;
import static java.lang.Math.*;

final public class L2Interpreter
extends AvailInterpreter
implements L2OperationDispatcher
{
	AvailObject _chunk;
	AvailObject _chunkWords;
	AvailObject _chunkVectors;
	AvailObject[] _pointers = new AvailObject[10];
	int[] _integers = new int[10];
	float[] _floats = new float[10];
	int _offset;
	List<AvailObject> _argsBuffer = new ArrayList<AvailObject>();
	boolean _exitNow = false;
	AvailObject _exitValue;

	/**
	 * Construct a new {@link L2Interpreter}.
	 *
	 * @param runtime An {@link AvailRuntime}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public L2Interpreter (final @NotNull AvailRuntime runtime)
	{
		super(runtime);
	}
	
	int offset()
	{
		return _offset;
	}
	void offset(int newOffset)
	{
		// System.out.printf("[#%d] %d -> %d%n", _chunk.index(), _offset, newOffset);
		_offset = newOffset;
	}

	@Override
	public void prepareToExecuteContinuation (AvailObject continuation)
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
		_chunk = L2ChunkDescriptor.chunkFromId (continuation.levelTwoChunkIndex());
		if (((process.executionMode() & ExecutionMode.singleStep) != 0) || !_chunk.isValid())
		{
			//  Either we're single-stepping or the chunk was invalidated, but the continuation still refers to it.
			//  The garbage collector will reclaim the chunk only when all such continuations have let the chunk
			//  go (therefore, let it go).  Fall back on single-stepping the Level One code.
			continuation.levelTwoChunkIndexOffset (
				L2ChunkDescriptor.indexOfUnoptimizedChunk(),
				L2ChunkDescriptor.offsetToPauseUnoptimizedChunk());
			_chunk = L2ChunkDescriptor.chunkFromId (continuation.levelTwoChunkIndex());
		}
		_chunkWords = _chunk.wordcodes();
		_chunkVectors = _chunk.vectors();
		makeRoomForChunkRegisters(_chunk, continuation.closure().code());
		pointerAtPut(callerRegister(), continuation);
		pointerAtPut(closureRegister(), continuation.closure());
		offset(continuation.levelTwoOffset());
	}


	// Increase the number of registers if necessary to accomodate the new chunk/code.

	void makeRoomForChunkRegisters (AvailObject theChunk, AvailObject theCode)
	{
		int neededObjectCount = max(theChunk.numObjects(), theCode.numArgsAndLocalsAndStack()) + 3;
		if (neededObjectCount > _pointers.length)
		{
			AvailObject[] newPointers = new AvailObject[neededObjectCount * 2 + 10];
			System.arraycopy(_pointers, 0, newPointers, 0, _pointers.length);
			_pointers = newPointers;
		}
		if (theChunk.numIntegers() > _integers.length)
		{
			int[] newIntegers = new int[theChunk.numIntegers() * 2 + 10];
			System.arraycopy(_integers, 0, newIntegers, 0, _integers.length);
			_integers = newIntegers;
		}
		if (theChunk.numFloats() > _floats.length)
		{
			float[] newFloats = new float[theChunk.numFloats() * 2 + 10];
			System.arraycopy(_floats, 0, newFloats, 0, _floats.length);
			_floats = newFloats;
		}
	}


	AvailObject privateTranslateCodeOptimization (
			AvailObject theCode,
			int effort)
	{
		// Translate the code into a chunk using the specified effort.  An effort of zero
		// means produce an initial translation that decrements a counter on each
		// invocation, reoptimizing (with more effort) if it reaches zero.

		return new L2Translator().translateOptimizationFor(theCode, effort, this);
	}

	@Override
	public Result searchForExceptionHandler (
			final AvailObject exceptionValue,
			final List<AvailObject> args)
	{
		//  Raise an exception.  Scan the stack of continuations until one
		//  is found for a closure whose code is the primitive 200.  Get that
		//  continuation's second argument (a handler block of one argument),
		//  and check if that handler block will accept the exceptionValue.  If
		//  not, keep looking.  If it will accept it, unwind the stack so that the
		//  primitive 200 method is the top entry, and invoke the handler block
		//  with exceptionValue.  If there is no suitable handler block, fail the
		//  primitive.

		AvailObject cont = _pointers[callerRegister()];
		AvailObject handler = VoidDescriptor.voidObject();
		while (! cont.equalsVoid())
		{
			if ((cont.closure().code().primitiveNumber() == 200) &&
				exceptionValue.isInstanceOfSubtypeOf(
					cont.localOrArgOrStackAt(2).type().argTypeAt(1)))
			{
				handler = cont.localOrArgOrStackAt(2);
				assert ! handler.equalsVoid();
				_pointers[callerRegister()] = cont.ensureMutable();
				return invokeClosureArguments (
					handler,
					args);
			}
			else
			{
				cont = cont.caller();
			}
		}
		return Result.FAILURE;
	}

	@Override
	public Result invokeClosureArguments (
			AvailObject aClosure,
			List<AvailObject> args)
	{
		//  Prepare the L2Interpreter to deal with executing the given closure.  If it's a primitive,
		//  attempt it first.  If it succeeds and simply returns a value, write that value into the
		//  calling continuation and return primitiveSucceeded.  If the primitive succeeded by
		//  causing the continuation to change (e.g., block invocation, continuation restarting),
		//  simply answer continuationChanged.  Otherwise, invoke the closure (without actually
		//  running any of its instructions yet), and answer continuationChanged.

		short primNum = aClosure.code().primitiveNumber();
		if (primNum != 0)
		{
			Result result;
			result = attemptPrimitive(primNum, args);
			if (result == Result.SUCCESS)
			{
				AvailObject cont = pointerAt(callerRegister());
				cont.stackAtPut(cont.stackp(), primitiveResult);
				return result;
			}
			if (result == Result.CONTINUATION_CHANGED)
			{
				return result;
			}
		}
		//  Either it wasn't a primitive or the primitive failed.
		invokeWithoutPrimitiveClosureArguments(aClosure, args);
		return Result.CONTINUATION_CHANGED;
	}

	@Override
	public void invokeWithoutPrimitiveClosureArguments (
			AvailObject aClosure,
			List<AvailObject> args)
	{
		//  Prepare the L2Interpreter to deal with executing the given closure, using the
		//  given parameters.

		if ((process.executionMode() & ExecutionMode.singleStep) != 0)
		{
			//  When single-stepping, never start up a chunk other than the unoptimized one.
			_chunk = L2ChunkDescriptor.chunkFromId(L2ChunkDescriptor.indexOfUnoptimizedChunk());
			// skip the decrement-and-optimize instruction
			offset(L2ChunkDescriptor.offsetToSingleStepUnoptimizedChunk());
		}
		else
		{
			_chunk = L2ChunkDescriptor.chunkFromId(aClosure.code().startingChunkIndex());
			if (!_chunk.isValid())
			{
				//  The chunk is invalid, so use the default chunk and patch up aClosure's code.
				_chunk = L2ChunkDescriptor.chunkFromId (L2ChunkDescriptor.indexOfUnoptimizedChunk());
				aClosure.code().startingChunkIndex(_chunk.index());
				aClosure.code().invocationCount(L2ChunkDescriptor.countdownForInvalidatedCode());
			}
			_chunk.moveToHead();
			offset(1);
		}

		makeRoomForChunkRegisters(_chunk, aClosure.code());

		_chunkWords = _chunk.wordcodes();
		_chunkVectors = _chunk.vectors();
		pointerAtPut(closureRegister(), aClosure);
		//  Transfer arguments...
		for (int i1 = aClosure.code().numArgs(); i1 >= 1; --i1)
		{
			pointerAtPut(argumentRegister(i1), args.get(i1 - 1));
		}
	}

	public AvailObject run (AvailObject aProcess)
	{
		// Note: we can only resume the continuation safely if it was just entering a closure, or
		// just returning from one, or if it took an offramp for which there is an onramp.
		process = aProcess;
		AvailObject continuationTemp = aProcess.continuation();

		interruptRequestFlag = InterruptRequestFlag.noInterrupt;
		_exitNow = false;
		prepareToExecuteContinuation (continuationTemp);

		if ((process.executionMode() & ExecutionMode.singleStep) != 0)
		{
			// We're single-stepping, so force the use of level one emulation.  Note that only the first step
			// of a run is allowed to execute the first nybblecode after a prepareToExecuteContinuation().
			assert _chunk.index() == L2ChunkDescriptor.indexOfUnoptimizedChunk();
			offset(L2ChunkDescriptor.offsetToContinueUnoptimizedChunk());
			process.continuation().levelTwoChunkIndexOffset(_chunk.index(), offset());
			interruptRequestFlag = InterruptRequestFlag.outOfGas;
		}

		// The caches are set up. Start dispatching nybblecodes.
		do
		{
			// This loop is only exited by a return off the end of the outermost context,
			//  a suspend or terminate of the current process, or by an interbytecode
			//  interrupt.  For now there are no interbytecode interrupts, but these can
			//  be added later. They will probably happen on nonprimitive method
			//  invocations, returns, and backward jumps.  At the time of an interbytecode
			//  interrupt the continuation must be a reflection of the current continuation,
			//  *not* the caller.  That is, only the callerRegister()'s content is valid.
			int wordCode = nextWord();
			L2Operation operation = L2Operation.values()[wordCode];
			// System.out.printf("[%d@%d] %s%n", _chunk.index(), _offset - 1, operation.name());
			operation.dispatch(this);
		}
		while (!_exitNow);

		return _exitValue;
	}

	public AvailObject runClosureArguments (
			AvailObject aClosure,
			List<AvailObject> arguments)
	{
		AvailObject theCode = aClosure.code();
		assert theCode.primitiveNumber() == 0
			: "The outermost context can't be a primitive.";
		if (theCode.numArgs() != arguments.size())
		{
			error("Closure should take " + theCode.numArgs() + " arguments");
		}

		// Safety precaution.
		aClosure.makeImmutable();
		AvailObject outermostContinuation = 
			ContinuationDescriptor.mutableDescriptor().newObjectToInvokeCallerLevelTwoChunkIndexArgs(
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

	private AvailObject jumpContinuation ()
	{
		process.continuation(currentContinuation());
		return run(process);
	}

	int nextWord ()
	{
		int word = _chunkWords.tupleIntAt(offset());
		offset(offset() + 1);
		return word;
	}


	// L2InstructionStream visitor methods

	public void L2_unknownWordcode ()
	{
		error("Unknown wordcode\n");
	}

	public void L2_doCreateSimpleContinuationIn_()
	{
		//  Create a simple continuation using the current calling continuation, closure, and arguments.
		//  Place the closure in the callerRegister.

		int destIndex = nextWord();
		final AvailObject theClosure = pointerAt(closureRegister());
		final AvailObject theCode = theClosure.code();
		final AvailObject newContinuation = AvailObject.newIndexedDescriptor(theCode.numArgsAndLocalsAndStack(), ContinuationDescriptor.mutableDescriptor());
		final short nArgs = theCode.numArgs();
		newContinuation.caller(pointerAt(callerRegister()));
		newContinuation.closure(theClosure);
		newContinuation.pc(1);
		newContinuation.stackp((newContinuation.objectSlotsCount() + 1));
		newContinuation.levelTwoChunkIndexOffset(_chunk.index(), offset());
		for (int i = 1; i <= nArgs; i++)
		{
			newContinuation.localOrArgOrStackAtPut(i, pointerAt(argumentRegister(i)));
		}
		for (int i = (nArgs + 1), _end1 = theCode.numArgsAndLocalsAndStack(); i <= _end1; i++)
		{
			newContinuation.localOrArgOrStackAtPut(i, VoidDescriptor.voidObject());
		}
		for (int i = 1, _end2 = theCode.numLocals(); i <= _end2; i++)
		{
			//  non-argument locals
			newContinuation.localOrArgOrStackAtPut((nArgs + i), ContainerDescriptor.newContainerWithOuterType(theCode.localTypeAt(i)));
		}
		pointerAtPut(destIndex, newContinuation);
	}

	/**
	 * Execute a single nybblecode of the current continuation, found in
	 * {@link #callerRegister() callerRegister}.
	 */
	public void L2_doInterpretOneInstruction ()
	{
		final AvailObject continutation = pointerAt(callerRegister());
		final AvailObject closure = continutation.closure();
		final AvailObject code = closure.code();
		final AvailObject nybbles = code.nybbles();
		int pc = continutation.pc();
		
		// Before we extract the nybblecode, may sure that the PC hasn't passed
		// the end of the instruction sequence. If we have, then execute an
		// L1_doReturn.
		if (pc > nybbles.tupleSize())
		{
			L2L1_doReturn();
			return;
		}
		
		final byte nybble = nybbles.extractNybbleFromTupleAt(pc++);
		continutation.pc(pc);
		switch (nybble)
		{
			case 0: L2L1_doCall(); break;
			case 1: L2L1_doVerifyType(); break;
			case 2:
				error("Illegal nybblecode (return is synthetic only)");
				break;
			case 3: L2L1_doPushLiteral(); break;
			case 4: L2L1_doPushLastLocal(); break;
			case 5: L2L1_doPushLocal(); break;
			case 6: L2L1_doPushLastOuter(); break;
			case 7: L2L1_doClose(); break;
			case 8: L2L1_doSetLocal(); break;
			case 9: L2L1_doGetLocalClearing(); break;
			case 10: L2L1_doPushOuter(); break;
			case 11: L2L1_doPop(); break;
			case 12: L2L1_doGetOuterClearing(); break;
			case 13: L2L1_doSetOuter(); break;
			case 14: L2L1_doGetLocal(); break;
			case 15: L2L1_doExtension(); break;
			default: error("Illegal nybblecode");
		}
	}

	public void L2_doDecrementCounterAndReoptimizeOnZero()
	{
		//  Decrement the counter in the current code object.  If it reaches zero, reoptimize the current code.

		final AvailObject theClosure = pointerAt(closureRegister());
		final AvailObject theCode = theClosure.code();
		final int newCount = (theCode.invocationCount() - 1);
		assert (newCount >= 0);
		if ((newCount != 0))
		{
			theCode.invocationCount(newCount);
		}
		else
		{
			theCode.invocationCount(L2ChunkDescriptor.countdownForNewlyOptimizedCode());
			AvailObject newChunk = privateTranslateCodeOptimization(theCode, 3);
			assert(theCode.startingChunkIndex() == newChunk.index());
			_argsBuffer.clear();
			int nArgs = theCode.numArgs();
			for (int i = 1; i <= nArgs; i++)
			{
				_argsBuffer.add(_pointers[argumentRegister(i)]);
			}
			invokeClosureArguments (
				theClosure,
				_argsBuffer);
		}
	}

	public void L2_doTranslateCode()
	{
		//  The callerRegister contains the calling continuation, and the closureRegister contains the code
		//  being invoked.  Do a naive translation of this code into Level Two.  Don't do any inlining or register
		//  coloring, but insert instrumentation that will eventually trigger a reoptimization of this code.

		AvailObject theClosure = _pointers[closureRegister()];
		AvailObject theCode = theClosure.code();
		AvailObject newChunk = privateTranslateCodeOptimization(theCode, 1);  // initial simplistic translation
		assert(theCode.startingChunkIndex() == newChunk.index());
		_argsBuffer.clear();
		int nArgs = theCode.numArgs();
		for (int i = nArgs; i > 0; --i)
		{
			_argsBuffer.add(_pointers[argumentRegister(i)]);
		}
		invokeClosureArguments (
			theClosure,
			_argsBuffer);
	}

	public void L2_doMoveFromObject_destObject_()
	{
		int fromIndex = nextWord();
		int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(fromIndex));
	}

	public void L2_doMoveFromConstant_destObject_()
	{
		int fromIndex = nextWord();
		int destIndex = nextWord();
		pointerAtPut(destIndex, _chunk.literalAt(fromIndex));
	}

	public void L2_doMoveFromOuterVariable_ofClosureObject_destObject_()
	{
		int outerIndex = nextWord();
		int fromIndex = nextWord();
		int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(fromIndex).outerVarAt(outerIndex));
	}

	public void L2_doCreateVariableTypeConstant_destObject_()
	{
		int typeIndex = nextWord();
		int destIndex = nextWord();
		pointerAtPut(destIndex, ContainerDescriptor.newContainerWithOuterType(_chunk.literalAt(typeIndex)));
	}

	public void L2_doGetVariable_destObject_()
	{
		int getIndex = nextWord();
		int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(getIndex).getValue().makeImmutable());
	}

	public void L2_doGetVariableClearing_destObject_()
	{
		int getIndex = nextWord();
		int destIndex = nextWord();
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

	public void L2_doSetVariable_sourceObject_()
	{
		int setIndex = nextWord();
		int sourceIndex = nextWord();
		pointerAt(setIndex).setValue(pointerAt(sourceIndex));
	}

	public void L2_doClearVariable_()
	{
		@SuppressWarnings("unused") int clearIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doClearVariablesVector_()
	{
		@SuppressWarnings("unused") int variablesIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doClearObject_()
	{
		int clearIndex = nextWord();
		pointerAtPut(clearIndex, VoidDescriptor.voidObject());
	}

	public void L2_doAddIntegerConstant_destObject_()
	{
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doAddIntegerConstant_destInteger_ifFail_()
	{
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doAddObject_destObject_()
	{
		@SuppressWarnings("unused") int addIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doAddInteger_destInteger_ifFail_()
	{
		//  Note that failOffset is an absolute position in the chunk.

		int addIndex = nextWord();
		int destIndex = nextWord();
		int failOffset = nextWord();
		long add = _integers[addIndex];
		long dest = _integers[destIndex];
		long result = dest + add;
		int resultInt = (int)result;
		if (result == resultInt)
		{
			_integers[destIndex] = resultInt;
		}
		else
		{
			offset(failOffset);
		}
	}

	public void L2_doAddIntegerImmediate_destInteger_ifFail_()
	{
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doAddModThirtyTwoBitInteger_destInteger_()
	{
		@SuppressWarnings("unused") int bitIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doSubtractIntegerConstant_destObject_()
	{
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doSubtractIntegerConstant_destInteger_ifFail_()
	{
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doSubtractObject_destObject_()
	{
		@SuppressWarnings("unused") int subtractIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doSubtractInteger_destInteger_ifFail_()
	{
		@SuppressWarnings("unused") int subtractIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doSubtractIntegerImmediate_destInteger_ifFail_()
	{
		@SuppressWarnings("unused") int integerImmediate = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doSubtractModThirtyTwoBitInteger_destInteger_()
	{
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doMultiplyIntegerConstant_destObject_()
	{
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doMultiplyIntegerConstant_destInteger_ifFail_()
	{
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doMultiplyObject_destObject_()
	{
		@SuppressWarnings("unused") int multiplyIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doMultiplyInteger_destInteger_ifFail_()
	{
		@SuppressWarnings("unused") int multiplyIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doMultiplyIntegerImmediate_destInteger_ifFail_()
	{
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doMultiplyModThirtyTwoBitInteger_destInteger_()
	{
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doDivideObject_byIntegerConstant_destQuotientObject_destRemainderInteger_ifFail_()
	{
		@SuppressWarnings("unused") int divideIndex = nextWord();
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int quotientIndex = nextWord();
		@SuppressWarnings("unused") int remainderIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doDivideInteger_byIntegerConstant_destQuotientInteger_destRemainderInteger_ifFail_()
	{
		@SuppressWarnings("unused") int divideIndex = nextWord();
		@SuppressWarnings("unused") int integerIndex = nextWord();
		@SuppressWarnings("unused") int quotientIndex = nextWord();
		@SuppressWarnings("unused") int remainderIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doDivideInteger_byIntegerImmediate_destQuotientInteger_destRemainderInteger_ifFail_()
	{
		@SuppressWarnings("unused") int divideIndex = nextWord();
		@SuppressWarnings("unused") int integerImmediate = nextWord();
		@SuppressWarnings("unused") int quotientIndex = nextWord();
		@SuppressWarnings("unused") int remainderIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doDivideObject_byObject_destQuotientObject_destRemainderObject_ifZeroDivisor_()
	{
		@SuppressWarnings("unused") int divideIndex = nextWord();
		@SuppressWarnings("unused") int byIndex = nextWord();
		@SuppressWarnings("unused") int quotientIndex = nextWord();
		@SuppressWarnings("unused") int remainderIndex = nextWord();
		@SuppressWarnings("unused") int zeroIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doDivideInteger_byInteger_destQuotientInteger_destRemainderInteger_ifFail_ifZeroDivisor_()
	{
		@SuppressWarnings("unused") int divideIndex = nextWord();
		@SuppressWarnings("unused") int byIndex = nextWord();
		@SuppressWarnings("unused") int quotientIndex = nextWord();
		@SuppressWarnings("unused") int remainderIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int zeroIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_()
	{
		int doIndex = nextWord();
		offset(doIndex);
	}

	public void L2_doJump_ifObject_equalsObject_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_equalsConstant_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_notEqualsObject_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_notEqualsConstant_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_lessThanObject_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int thanIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_lessThanConstant_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int thanIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_lessOrEqualObject_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_lessOrEqualConstant_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_greaterThanObject_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int thanIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_greaterConstant_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int greaterIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_greaterOrEqualObject_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_greaterOrEqualConstant_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_isKindOfObject_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int ofIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_isKindOfConstant_()
	{
		int doIndex = nextWord();
		int valueIndex = nextWord();
		int typeConstIndex = nextWord();
		final AvailObject value = pointerAt(valueIndex);
		final AvailObject type = _chunk.literalAt(typeConstIndex);
		if (value.isInstanceOfSubtypeOf(type))
		{
			offset(doIndex);
		}
	}

	public void L2_doJump_ifObject_isNotKindOfObject_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int ofIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJump_ifObject_isNotKindOfConstant_()
	{
		@SuppressWarnings("unused") int doIndex = nextWord();
		@SuppressWarnings("unused") int ifIndex = nextWord();
		@SuppressWarnings("unused") int ofIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doJumpIfInterrupt_()
	{
		int ifIndex = nextWord();
		if (interruptRequestFlag != InterruptRequestFlag.noInterrupt)
			offset(ifIndex);
	}

	public void L2_doJumpIfNotInterrupt_()
	{
		int ifNotIndex = nextWord();
		if (interruptRequestFlag == InterruptRequestFlag.noInterrupt)
			offset(ifNotIndex);
	}

	public void L2_doProcessInterruptNowWithContinuationObject_()
	{
		//  The current process has been asked to pause for an inter-nybblecode interrupt for
		//  some reason.  It has possibly executed several more wordcodes since that time, to
		//  place the process into a state that's consistent with naive Level One execution
		//  semantics.  That is, a naive Level One interpreter should be able to resume the
		//  process later.  The continuation to use can be found in _pointers[continuationIndex].

		int continuationIndex = nextWord();
		process.continuation(_pointers[continuationIndex]);
		process.interruptRequestFlag(interruptRequestFlag);
		_exitValue = VoidDescriptor.voidObject();
		_exitNow = true;
	}

	public void L2_doCreateContinuationWithSenderObject_closureObject_pcInteger_stackpInteger_sizeImmediate_slotsVector_wordcodeOffset_destObject_()
	{
		int senderIndex = nextWord();
		int closureIndex = nextWord();
		int pcIndex = nextWord();
		int stackpIndex = nextWord();
		int sizeIndex = nextWord();
		int slotsIndex = nextWord();
		int wordcodeOffset = nextWord();
		int destIndex = nextWord();
		final AvailObject clos = pointerAt(closureIndex);
		final AvailObject cod = clos.code();
		final AvailObject cont = AvailObject.newIndexedDescriptor(cod.numArgsAndLocalsAndStack(), ContinuationDescriptor.mutableDescriptor());
		cont.caller(pointerAt(senderIndex));
		cont.closure(clos);
		cont.pc(pcIndex);
		cont.stackp(((cont.objectSlotsCount() - cod.maxStackDepth()) + stackpIndex));
		cont.hiLevelTwoChunkLowOffset(((_chunk.index() << 16) + wordcodeOffset));
		final AvailObject slots = _chunkVectors.tupleAt(slotsIndex);
		for (int i = 1; i <= sizeIndex; i++)
		{
			cont.localOrArgOrStackAtPut(i, pointerAt(slots.tupleAt(i).extractInt()));
		}
		pointerAtPut(destIndex, cont);
	}

	public void L2_doSetContinuationObject_slotIndexImmediate_valueObject_()
	{
		@SuppressWarnings("unused") int continuationIndex = nextWord();
		@SuppressWarnings("unused") int indexIndex = nextWord();
		@SuppressWarnings("unused") int valueIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doSetContinuationObject_newPcImmediate_newStackpImmediate_()
	{
		@SuppressWarnings("unused") int continuationIndex = nextWord();
		@SuppressWarnings("unused") int pcIndex = nextWord();
		@SuppressWarnings("unused") int stackpIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doExplodeContinuationObject_senderDestObject_closureDestObject_slotsDestVector_()
	{
		int continuationIndex = nextWord();
		int senderDestIndex = nextWord();
		int closureDestIndex = nextWord();
		int slotsDestIndex = nextWord();
		final AvailObject cont = pointerAt(continuationIndex);
		pointerAtPut(senderDestIndex, cont.caller());
		pointerAtPut(closureDestIndex, cont.closure());
		final AvailObject slotsVector = _chunkVectors.tupleAt(slotsDestIndex);
		if (! (slotsVector.tupleSize() == cont.closure().code().numArgsAndLocalsAndStack()))
		{
			error("problem in doExplode...");
			return;
		}
		for (int i = 1, _end1 = slotsVector.tupleSize(); i <= _end1; i++)
		{
			pointerAtPut(slotsVector.tupleAt(i).extractInt(), cont.localOrArgOrStackAt(i));
		}
	}

	public void L2_doSend_argumentsVector_()
	{
		int selectorIndex = nextWord();
		int argumentsIndex = nextWord();
		final AvailObject vect = _chunkVectors.tupleAt(argumentsIndex);
		_argsBuffer.clear();
		for (int i = 1; i <= vect.tupleSize(); i++)
		{
			_argsBuffer.add(_pointers[vect.tupleIntAt(i)]);
		}
		final AvailObject selector = _chunk.literalAt(selectorIndex);
		final AvailObject signatureToCall = selector.lookupByValuesFromArray(_argsBuffer);
		if (signatureToCall.equalsVoid())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (! signatureToCall.isImplementation())
		{
			error("Attempted to call a non-implementation signature");
			return;
		}
		final AvailObject closureToCall = signatureToCall.bodyBlock();
		final AvailObject codeToCall = closureToCall.code();
		final short primNum = codeToCall.primitiveNumber();
		if ((primNum != 0))
		{
			prepareToExecuteContinuation(_pointers[callerRegister()]);
			Result primResult = attemptPrimitive(primNum, _argsBuffer);
			if (primResult == Result.CONTINUATION_CHANGED)
			{
				return;
			}
			else if (primResult != Result.FAILURE)
			{
				//  Primitive succeeded.
				AvailObject cont = _pointers [callerRegister()];
				assert(_chunk.index() == cont.levelTwoChunkIndex());
				cont.readBarrierFault();
				assert(cont.descriptor().isMutable());
				cont.stackAtPut(cont.stackp(), primitiveResult);
				return;
			}
		}
		invokeWithoutPrimitiveClosureArguments(closureToCall, _argsBuffer);
	}

	public void L2_doGetType_destObject_()
	{
		int srcIndex = nextWord();
		int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(srcIndex).type());
	}

	public void L2_doSuperSend_argumentsVector_argumentTypesVector_()
	{
		int selectorIndex = nextWord();
		int argumentsIndex = nextWord();
		int typesIndex = nextWord();
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
		final AvailObject signatureToCall = selector.lookupByTypesFromArray(_argsBuffer);
		if (signatureToCall.equalsVoid())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (! signatureToCall.isImplementation())
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
		if ((primNum != 0))
		{
			prepareToExecuteContinuation(_pointers[callerRegister()]);
			Result primResult = attemptPrimitive(primNum, _argsBuffer);
			if (primResult == Result.CONTINUATION_CHANGED)
			{
				return;
			}
			else if (primResult != Result.FAILURE)
			{
				//  Primitive succeeded.
				AvailObject cont = _pointers [callerRegister()];
				assert(_chunk.index() == cont.levelTwoChunkIndex());
				cont.readBarrierFault();
				assert(cont.descriptor().isMutable());
				cont.stackAtPut(cont.stackp(), primitiveResult);
				return;
			}
		}
		invokeWithoutPrimitiveClosureArguments(closureToCall, _argsBuffer);
	}

	public void L2_doCreateTupleOfSizeImmediate_valuesVector_destObject_()
	{
		int sizeIndex = nextWord();
		int valuesIndex = nextWord();
		int destIndex = nextWord();
		final AvailObject indices = _chunkVectors.tupleAt(valuesIndex);
		assert (indices.tupleSize() == sizeIndex);
		final AvailObject tuple = AvailObject.newIndexedDescriptor(sizeIndex, ObjectTupleDescriptor.mutableDescriptor());
		for (int i = 1; i <= sizeIndex; i++)
		{
			tuple.tupleAtPut(i, pointerAt(indices.tupleAt(i).extractInt()));
		}
		pointerAtPut(destIndex, tuple);
	}

	public void L2_doConvertTupleObject_toListObject_()
	{
		int tupleObject = nextWord();
		int destObject = nextWord();
		final AvailObject tuple = pointerAt(tupleObject);
		assert tuple.isTuple();
		final AvailObject list = AvailObject.newIndexedDescriptor(0, ListDescriptor.mutableDescriptor());
		list.tuple(tuple);
		pointerAtPut(destObject, list);
	}

	public void L2_doConcatenateTuplesVector_destObject_()
	{
		@SuppressWarnings("unused") int subtupleIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doCreateSetOfSizeImmediate_valuesVector_destObject_()
	{
		@SuppressWarnings("unused") int sizeIndex = nextWord();
		@SuppressWarnings("unused") int valuesIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doCreateMapOfSizeImmediate_keysVector_valuesVector_destObject_()
	{
		@SuppressWarnings("unused") int sizeIndex = nextWord();
		@SuppressWarnings("unused") int keysIndex = nextWord();
		@SuppressWarnings("unused") int valuesIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doCreateObjectOfSizeImmediate_keysVector_valuesVector_destObject_()
	{
		@SuppressWarnings("unused") int sizeIndex = nextWord();
		@SuppressWarnings("unused") int keysIndex = nextWord();
		@SuppressWarnings("unused") int valuesIndex = nextWord();
		@SuppressWarnings("unused") int destIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doCreateClosureFromCodeObject_outersVector_destObject_()
	{
		int codeIndex = nextWord();
		int outersIndex = nextWord();
		int destIndex = nextWord();
		final AvailObject outers = _chunkVectors.tupleAt(outersIndex);
		final AvailObject clos = AvailObject.newIndexedDescriptor(outers.tupleSize(), ClosureDescriptor.mutableDescriptor());
		clos.code(_chunk.literalAt(codeIndex));
		for (int i = 1, _end1 = outers.tupleSize(); i <= _end1; i++)
		{
			clos.outerVarAtPut(i, pointerAt(outers.tupleAt(i).extractInt()));
		}
		pointerAtPut(destIndex, clos);
	}

	public void L2_doReturnToContinuationObject_valueObject_()
	{
		//  Return to the calling continuation with the given value.

		int continuationIndex = nextWord();
		int valueIndex = nextWord();
		assert (continuationIndex == callerRegister());
		AvailObject caller = pointerAt(continuationIndex);
		final AvailObject valueObject = pointerAt(valueIndex);
		if (caller.equalsVoid())
		{
			process.executionState(ExecutionState.terminated);
			process.continuation(VoidDescriptor.voidObject());
			_exitValue = valueObject;
			_exitNow = true;
		}
		//  Store the value on the calling continuation's stack (which had void pre-pushed).
		caller = caller.ensureMutable();
		caller.stackAtPut(caller.stackp(), valueObject);
		prepareToExecuteContinuation(caller);
	}

	public void L2_doExitContinuationObject_valueObject_()
	{
		@SuppressWarnings("unused") int continuationIndex = nextWord();
		@SuppressWarnings("unused") int valueIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doResumeContinuationObject_()
	{
		@SuppressWarnings("unused") int continuationIndex = nextWord();
		error("not implemented");
		return;
	}

	public void L2_doMakeImmutableObject_()
	{
		int objectIndex = nextWord();
		pointerAt(objectIndex).makeImmutable();
	}

	public void L2_doMakeSubobjectsImmutableInObject_()
	{
		int objectIndex = nextWord();
		pointerAt(objectIndex).makeSubobjectsImmutable();
	}

	public void L2_doBreakpoint()
	{
		error("Breakpoint instruction reached");
		return;
	}

	public void L2_doAttemptPrimitive_withArguments_result_ifFail_()
	{
		//  Attempt the specified primitive with the given arguments.  If the primitive fails,
		//  jump to the given code offset.  If it succeeds, store the result in the specified
		//  register.  Note that some primitives should never be inlined.  For example, block
		//  invocation assumes the callerRegister has been set up to hold the context that
		//  is calling the primitive.  This is not the case for an *inlined* primitive.

		int primNumber = nextWord();
		int argsVector = nextWord();
		int resultRegister = nextWord();
		int failureOffset = nextWord();
		AvailObject argsVect = _chunkVectors.tupleAt(argsVector);
		_argsBuffer.clear();
		for (int i1 = 1; i1 <= argsVect.tupleSize(); i1++)
		{
			_argsBuffer.add(_pointers[argsVect.tupleAt(i1).extractInt()]);
		}
		Result res = attemptPrimitive((short)primNumber, _argsBuffer);
		if (res == Result.SUCCESS)
		{
			_pointers [resultRegister] = primitiveResult;
		}
		else if (res == Result.CONTINUATION_CHANGED)
		{
			error("attemptPrimitive wordcode should never set up a new continuation", primNumber);
		}
		else if (res == Result.FAILURE)
		{
			offset(failureOffset);
		}
		else
		{
			error("Unrecognized return type from attemptPrimitive()");
		}
	}

	public void L2L1_doCall()
	{
		//  [n] - Send the message at index n in the compiledCode's literals.  Pop the arguments for
		//  this message off the stack (the message itself knows how many to expect).  The first
		//  argument was pushed first, and is the deepest on the stack.  Use these arguments to
		//  look up the method dynamically.  Before invoking the method, push the void object
		//  onto the stack.  Its presence will help distinguish continuations produced by the
		//  pushLabel instruction from their senders.  When the call completes (if ever), it will use
		//  the return instruction, which will have the effect of replacing this void object with the
		//  result of the call.

		final AvailObject cont = pointerAt(callerRegister());
		//  Look up the method implementations.  They all know numArgs.
		final AvailObject implementations = cont.closure().code().literalAt(getInteger());
		//  index is one-based
		final AvailObject matching = implementations.lookupByValuesFromContinuationStackp(cont, cont.stackp());
		if (matching.equalsVoid())
		{
			error("Ambiguous or invalid lookup");
			return;
		}
		if (matching.isForward())
		{
			error("Attempted to execute forward method before it was defined.");
			return;
		}
		if (matching.isAbstract())
		{
			error("Attempted to execute an abstract method.");
			return;
		}
		final AvailObject theClosure = matching.bodyBlock();
		final AvailObject theCode = theClosure.code();
		//  Ok, call the method...
		final short nArgs = matching.bodySignature().numArgs();
		_argsBuffer.clear();
		for (int i = 1; i <= nArgs; i++)
		{
			// Reverse order - i.e., _argsBuffer.get(0) was pushed first."
			int stackIndex = cont.stackp() + nArgs - i;
			_argsBuffer.add(cont.stackAt(stackIndex));
			cont.stackAtPut(stackIndex, VoidDescriptor.voidObject());
		}
		cont.stackp(((cont.stackp() + nArgs) - 1));
		//  leave one (void) slot on stack to distinguish label/call continuations.
		final short primNum = theCode.primitiveNumber();
		if ((primNum != 0))
		{
			assert(_chunk == L2ChunkDescriptor.chunkFromId (_pointers[callerRegister()].levelTwoChunkIndex()));
			Result primResult = attemptPrimitive(primNum, _argsBuffer);
			if (primResult == Result.CONTINUATION_CHANGED)
			{
				return;
			}
			if (primResult == Result.SUCCESS)
			{
				AvailObject callerCont = _pointers[callerRegister()];
				callerCont.stackAtPut(callerCont.stackp(), primitiveResult);
				return;
			}
		}
		//  Either not a primitive or else a failed primitive.
		invokeWithoutPrimitiveClosureArguments(theClosure, _argsBuffer);
	}

	public void L2L1_doVerifyType()
	{
		//  [n] - Ensure the top of stack's type is a subtype of the type found at
		//  index n in the current compiledCode.  If this is not the case, raise a
		//  special runtime error or exception.

		final AvailObject cont = pointerAt(callerRegister());
		final AvailObject value = cont.stackAt(cont.stackp());
		final AvailObject literalType = cont.closure().code().literalAt(getInteger());
		if (! value.isInstanceOfSubtypeOf(literalType))
		{
			error("A method has not met its \"returns\" clause's criterion (or a supermethod's) at runtime.");
			return;
		}
	}

	public void L2L1_doReturn()
	{
		//  Return to the calling continuation with top of stack.  Must be the last instruction in block.
		//  Note that the calling continuation has automatically pre-pushed a void object as a
		//  sentinel, which should simply be replaced by this value (to avoid manipulating the stackp).

		final AvailObject cont = pointerAt(callerRegister());
		final AvailObject value = cont.stackAt(cont.stackp());
		assert value.isInstanceOfSubtypeOf(cont.closure().code().closureType().returnType()) : "Return type from method disagrees with declaration";
		cont.stackAtPut(cont.stackp(), VoidDescriptor.voidObject());
		//  Necessary to avoid accidental destruction.
		AvailObject caller = cont.caller();
		if (caller.equalsVoid())
		{
			process.executionState(ExecutionState.terminated);
			process.continuation(VoidDescriptor.voidObject());
			_exitNow = true;
			_exitValue = value;
			return;
		}
		caller = caller.ensureMutable();
		caller.stackAtPut(caller.stackp(), value);
		prepareToExecuteContinuation(caller);
	}

	public void L2L1_doPushLiteral()
	{
		//  [n] - Push the literal indexed by n in the current compiledCode.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject constant = cont.closure().code().literalAt(index);
		//  index is one-based
		int stackp;
		cont.stackp(stackp = (cont.stackp() - 1));
		//  We don't need to make constant beImmutable because *code objects* are always immutable.
		cont.stackAtPut(stackp, constant);
	}

	public void L2L1_doPushLastLocal()
	{
		//  [n] - Push the argument (actual value) or local variable (the variable itself) indexed by n.
		//  Since this is known to be the last use (nondebugger) of the argument or local, void that
		//  slot of the current continuation.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject variable = cont.localOrArgOrStackAt(index);
		//  index is one-based
		cont.localOrArgOrStackAtPut(index, VoidDescriptor.voidObject());
		cont.stackp((cont.stackp() - 1));
		cont.stackAtPut(cont.stackp(), variable);
	}

	public void L2L1_doPushLocal()
	{
		//  [n] - Push the argument (actual value) or local variable (the variable itself) indexed by n.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject variable = cont.localOrArgOrStackAt(index);
		//  index is one-based
		variable.makeImmutable();
		int stackp;
		cont.stackp(stackp = (cont.stackp() - 1));
		cont.stackAtPut(stackp, variable);
	}

	public void L2L1_doPushLastOuter()
	{
		//  [n] - Push the outer variable indexed by n in the current closure.  If the variable is
		//  mutable, clear it (no one will know).  If the variable and closure are both mutable,
		//  remove the variable from the closure by voiding it.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject variable = cont.closure().outerVarAt(index);
		if (variable.equalsVoid())
		{
			error("Someone prematurely erased this outer var");
			return;
		}
		if (! cont.closure().optionallyNilOuterVar(index))
		{
			variable.makeImmutable();
		}
		int stackp;
		cont.stackp(stackp = (cont.stackp() - 1));
		cont.stackAtPut(stackp, variable);
	}

	public void L2L1_doClose()
	{
		//  [n,m] - Pop the top n items off the stack, and use them as outer variables in the
		//  construction of a closure based on the compiledCode that's the literal at index m
		//  of the current compiledCode.

		final AvailObject cont = pointerAt(callerRegister());
		final int numCopiedVars = getInteger();
		final AvailObject theCode = cont.closure().code().literalAt(getInteger());
		int stackp;
		cont.stackp(stackp = ((cont.stackp() + numCopiedVars) - 1));
		final AvailObject newClosure = AvailObject.newIndexedDescriptor(numCopiedVars, ClosureDescriptor.mutableDescriptor());
		newClosure.code(theCode);
		int stackIndex = stackp;
		for (int i = 1; i <= numCopiedVars; i++)
		{
			newClosure.outerVarAtPut(i, cont.stackAt(stackIndex));
			cont.stackAtPut(stackIndex, VoidDescriptor.voidObject());
			--stackIndex;
		}
		//  We don't assert assertObjectUnreachableIfMutable: on the popped copied vars
		//  because each copied var's new reference from the closure balances the lost
		//  reference from the wiped stack.  Likewise we don't tell them to beImmutable.
		//  The closure itself should remain mutable at this point, otherwise the copied
		//  vars would have to beImmutable to be referenced by an immutable closure.
		cont.stackAtPut(stackp, newClosure);
	}

	public void L2L1_doSetLocal()
	{
		//  [n] - Pop the stack and assign this value to the local variable (not an argument) indexed by n (index 1 is first argument).

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject variable = cont.localOrArgOrStackAt(index);
		//  index is one-based
		final AvailObject value = cont.stackAt(cont.stackp());
		cont.stackAtPut(cont.stackp(), VoidDescriptor.voidObject());
		cont.stackp((cont.stackp() + 1));
		//  The value's reference from the stack is now from the variable.
		variable.setValue(value);
	}

	public void L2L1_doGetLocalClearing()
	{
		//  [n] - Push the value of the local variable (not an argument) indexed by n (index 1 is first argument).
		//  If the variable itself is mutable, clear it now - nobody will know.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject variable = cont.localOrArgOrStackAt(index);
		//  index is one-based
		final AvailObject value = variable.getValue();
		if (variable.traversed().descriptor().isMutable())
		{
			variable.clearValue();
		}
		else
		{
			value.makeImmutable();
		}
		int stackp;
		cont.stackp(stackp = (cont.stackp() - 1));
		cont.stackAtPut(stackp, value);
	}

	public void L2L1_doPushOuter()
	{
		//  [n] - Push the outer variable indexed by n in the current closure.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject variable = cont.closure().outerVarAt(index);
		if (variable.equalsVoid())
		{
			error("Someone prematurely erased this outer var");
			return;
		}
		variable.makeImmutable();
		int stackp;
		cont.stackp(stackp = (cont.stackp() - 1));
		cont.stackAtPut(stackp, variable);
	}

	public void L2L1_doPop()
	{
		//  Remove the top item from the stack.

		final AvailObject cont = pointerAt(callerRegister());
		cont.stackAt(cont.stackp()).assertObjectUnreachableIfMutable();
		cont.stackAtPut(cont.stackp(), VoidDescriptor.voidObject());
		cont.stackp((cont.stackp() + 1));
	}

	public void L2L1_doGetOuterClearing()
	{
		//  [n] - Push the value of the outer variable indexed by n in the current closure.
		//  If the variable itself is mutable, clear it at this time - nobody will know.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject variable = cont.closure().outerVarAt(index);
		//  index is one-based.
		final AvailObject value = variable.getValue();
		if (variable.traversed().descriptor().isMutable())
		{
			variable.clearValue();
		}
		else
		{
			value.makeImmutable();
		}
		int stackp;
		cont.stackp(stackp = (cont.stackp() - 1));
		cont.stackAtPut(stackp, value);
	}

	public void L2L1_doSetOuter()
	{
		//  [n] - Pop the stack and assign this value to the outer variable indexed by n in the current closure.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject variable = cont.closure().outerVarAt(index);
		if (variable.equalsVoid())
		{
			error("Someone prematurely erased this outer var");
			return;
		}
		int stackp;
		final AvailObject value = cont.stackAt(stackp = cont.stackp());
		cont.stackAtPut(stackp, VoidDescriptor.voidObject());
		++stackp;
		cont.stackp(stackp);
		//  The value's reference from the stack is now from the variable.
		variable.setValue(value);
	}

	public void L2L1_doGetLocal()
	{
		//  [n] - Push the value of the local variable (not an argument) indexed by n (index 1 is first argument).

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject variable = cont.localOrArgOrStackAt(index);
		//  index is one-based
		final AvailObject value = variable.getValue();
		value.makeImmutable();
		int stackp;
		cont.stackp(stackp = (cont.stackp() - 1));
		cont.stackAtPut(stackp, value);
	}

	public void L2L1_doExtension()
	{
		//  The extension nybblecode was encountered.  Read another nybble and dispatch it through ExtendedSelectors.

		final AvailObject cont = pointerAt(callerRegister());
		final byte nextNybble = cont.closure().code().nybbles().extractNybbleFromTupleAt(cont.pc());
		cont.pc((cont.pc() + 1));
		switch (nextNybble)
		{
			case 0: L2L1Ext_doGetOuter(); break;
			case 1: L2L1Ext_doMakeList(); break;
			case 2: L2L1Ext_doPushLabel(); break;
			case 3: L2L1Ext_doGetLiteral(); break;
			case 4: L2L1Ext_doSetLiteral(); break;
			case 5: L2L1Ext_doSuperCall(); break;
			case 6: L2L1Ext_doGetType(); break;
			case 7: L2L1Ext_doReserved(); break;
			case 8: L2L1Ext_doReserved(); break;
			case 9: L2L1Ext_doReserved(); break;
			case 10: L2L1Ext_doReserved(); break;
			case 11: L2L1Ext_doReserved(); break;
			case 12: L2L1Ext_doReserved(); break;
			case 13: L2L1Ext_doReserved(); break;
			case 14: L2L1Ext_doReserved(); break;
			case 15: L2L1Ext_doReserved(); break;
			default: error("Illegal nybblecode");
		}
	}

	public void L2L1Ext_doGetOuter()
	{
		//  [n] - Push the value of the outer variable indexed by n in the current closure.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject variable = cont.closure().outerVarAt(index);
		//  index is one-based.
		final AvailObject value = variable.getValue();
		value.makeImmutable();
		int stackp;
		cont.stackp(stackp = (cont.stackp() - 1));
		cont.stackAtPut(stackp, value);
	}

	public void L2L1Ext_doMakeList()
	{
		//  [n] - Make a list object from n values popped from the stack.  Push the list.

		final AvailObject cont = pointerAt(callerRegister());
		final int count = getInteger();
		final AvailObject tuple = AvailObject.newIndexedDescriptor(count, ObjectTupleDescriptor.mutableDescriptor());
		int stackp = cont.stackp();
		for (int i = count; i >= 1; i--)
		{
			tuple.tupleAtPut(i, cont.stackAt(stackp));
			cont.stackAtPut(stackp, VoidDescriptor.voidObject());
			++stackp;
		}
		tuple.hashOrZero(0);
		final AvailObject list = AvailObject.newIndexedDescriptor(0, ListDescriptor.mutableDescriptor());
		list.tuple(tuple);
		--stackp;
		cont.stackp(stackp);
		cont.stackAtPut(stackp, list);
	}

	public void L2L1Ext_doPushLabel()
	{
		//  Build a continuation which, when restarted, will be just like restarting the current continuation.

		final AvailObject cont = pointerAt(callerRegister());
		final AvailObject code = cont.closure().code();
		int stackp = cont.stackp();
		final AvailObject newContinuation = cont.copyAsMutableContinuation();
		//  Always copy it.
		//
		//  Ok, fix up this new continuation.  It needs to have its pc set, its stackp reset, its stack
		//  area and non-argument locals cleared, and its caller, closure, and args made immutable.
		//
		//  Set the new continuation's pc to the first instruction...
		newContinuation.pc(1);
		//  Reset the new continuation's stack pointer...
		newContinuation.stackp((newContinuation.objectSlotsCount() + 1));
		for (int i = stackp, _end1 = newContinuation.objectSlotsCount(); i <= _end1; i++)
		{
			newContinuation.stackAtPut(i, VoidDescriptor.voidObject());
		}
		for (int i = (code.numArgs() + 1), _end2 = (code.numArgs() + code.numLocals()); i <= _end2; i++)
		{
			newContinuation.localOrArgOrStackAtPut(i, VoidDescriptor.voidObject());
		}
		//  Freeze all fields of the new object, including its caller, closure, and args.
		newContinuation.makeSubobjectsImmutable();
		assert (newContinuation.caller().equalsVoid() || (! newContinuation.caller().descriptor().isMutable())) : "Caller should freeze because two continuations can see it";
		assert cont.descriptor().isMutable() : "The CURRENT continuation can't POSSIBLY be seen by anyone!";
		//  ...always a fresh copy, always mutable (uniquely owned).
		cont.stackAtPut(--stackp, newContinuation);
		cont.stackp(stackp);
	}

	public void L2L1Ext_doGetLiteral()
	{
		//  [n] - Push the value of the variable that's literal number n in the current compiledCode.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject constant = cont.closure().code().literalAt(index);
		//  index is one-based
		final AvailObject value = constant.getValue().makeImmutable();
		int stackp;
		cont.stackp(stackp = (cont.stackp() - 1));
		cont.stackAtPut(stackp, value);
	}

	public void L2L1Ext_doSetLiteral()
	{
		//  [n] - Pop the stack and assign this value to the variable that's the literal
		//  indexed by n in the current compiledCode.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		final AvailObject variable = cont.closure().code().literalAt(index);
		int stackp;
		final AvailObject value = cont.stackAt(stackp = cont.stackp());
		cont.stackAtPut(stackp, VoidDescriptor.voidObject());
		++stackp;
		cont.stackp(stackp);
		//  The value's reference from the stack is now from the variable.
		variable.setValue(value);
	}

	public void L2L1Ext_doSuperCall()
	{
		//  [n] - Send the message at index n in the compiledCode's literals.  Like the call instruction,
		//  the arguments will have been pushed on the stack in order, but unlike call, each argument's
		//  type will also have been pushed (all arguments are pushed, then all argument types).
		//  These are either the arguments' exact types, or constant types (that must be supertypes
		//  of the arguments' types), or any mixture of the two.  These types will be used for method
		//  lookup, rather than the argument types.  This supports a 'super'-like mechanism in the
		//  presence of multimethods.  Like the call instruction, all arguments (and types) are popped,
		//  then a sentinel void object is pushed, and the looked up method is started.  When the
		//  invoked method returns (via a return instruction), this sentinel will be replaced by the
		//  result of the call.

		final AvailObject cont = pointerAt(callerRegister());
		int stackp = cont.stackp();
		//  Look up the method implementations.
		final int litIndex = getInteger();
		final AvailObject implementations = cont.closure().code().literalAt(litIndex);
		//  index is one-based
		final AvailObject matching = implementations.lookupByTypesFromContinuationStackp(cont, stackp);
		if (matching.equalsVoid())
		{
			error("Ambiguous or invalid lookup");
			return;
		}
		if (matching.isForward())
		{
			error("Attempted to execute forward method before it was defined.");
			return;
		}
		if (matching.isAbstract())
		{
			error("Attempted to execute an abstract method.");
			return;
		}
		final AvailObject theClosure = matching.bodyBlock();
		final AvailObject theCode = theClosure.traversed().code();
		//  Clear the argument types off the stack...
		final short nArgs = theCode.numArgs();
		for (int i = 1; i <= nArgs; i++)
		{
			cont.stackAtPut(((stackp + i) - 1), VoidDescriptor.voidObject());
		}
		_argsBuffer.clear();
		int base = stackp + nArgs + nArgs;
		for (int i = 1; i <= nArgs; i++)
		{
			_argsBuffer.add(cont.stackAt(base - i));
			cont.stackAtPut(base - i, VoidDescriptor.voidObject());
		}
		//  Ok, call the method...
		stackp = (((stackp + nArgs) + nArgs) - 1);
		//  remove types and arguments, but then leave one (void) slot on stack to distinguish label/call continuations.
		cont.stackp(stackp);
		final short primNum = theCode.primitiveNumber();
		if ((primNum != 0))
		{
			assert(_chunk == L2ChunkDescriptor.chunkFromId(_pointers[callerRegister()].levelTwoChunkIndex()));
			Result primResult = attemptPrimitive(primNum, _argsBuffer);
			if (primResult == Result.CONTINUATION_CHANGED)
			{
				return;
			}
			if (primResult == Result.SUCCESS)
			{
				AvailObject callerCont = _pointers[callerRegister()];
				callerCont.stackAtPut(callerCont.stackp(), primitiveResult);
				return;
			}
		}
		//  Either not a primitive or else a failed primitive.
		invokeWithoutPrimitiveClosureArguments(theClosure, _argsBuffer);
	}

	public void L2L1Ext_doGetType()
	{
		//  [n] - Push the (n+1)st stack element's type.  This is only used by the supercast
		//  mechanism to produce types for arguments not being cast.  See #doSuperCall.
		//  This implies the type will be used for a lookup and then discarded.  We therefore
		//  don't treat the type as acquiring a new reference from the stack, so it doesn't
		//  have to become immutable.  This could be a sticky point with the garbage collector
		//  if it finds only one reference to the type, but I think it's ok still.

		final AvailObject cont = pointerAt(callerRegister());
		final int index = getInteger();
		int stackp = cont.stackp();
		final AvailObject value = cont.stackAt((stackp + index));
		cont.stackp(--stackp);
		cont.stackAtPut(stackp, value.type());
	}

	public void L2L1Ext_doReserved()
	{
		//  This shouldn't happen unless the compiler is out of sync with the interpreter.

		error("That nybblecode is not supported");
		return;
	}
	public int argumentRegister (
			final int localNumber)
	{
		//  Answer the register holding the argument with the given index (e.g., the first argument is in register 3).

		return (2 + localNumber);
	}

	public int callerRegister ()
	{
		//  Answer the register holding the current context's caller context.

		//  reserved
		return 1;
	}

	public int closureRegister ()
	{
		//  Answer the register holding the current context's closure.

		//  reserved
		return 2;
	}

	public AvailObject currentContinuation ()
	{
		return pointerAt(callerRegister());
	}

	public int getInteger ()
	{
		//  Answer an integer extracted at the current program counter.  The program
		//  counter will be adjusted to skip over the integer.

		AvailObject cont = _pointers[callerRegister()];
		AvailObject clos = cont.closure();
		AvailObject cod = clos.code();
		AvailObject nybs = cod.nybbles();
		int pc = cont.pc();
		byte nyb = nybs.extractNybbleFromTupleAt(pc);
		int value = 0;
		byte [] counts = {0,0,0,0,0,0,0,0,0,0,1,1,1,2,4,8};
		for (int count = counts[nyb]; count > 0; --count)
		{
			value = (value<<4) + nybs.extractNybbleFromTupleAt(++pc);
		}
		byte [] offsets = {0,1,2,3,4,5,6,7,8,9,10,26,42,58,0,0};
		value += offsets[nyb];
		cont.pc(pc+1);
		return value;
	}

	public AvailObject pointerAt (
			final int index)
	{
		//  The index is one-based in both Smalltalk and Java, to avoid index manipulation.
		//  In Java entry [0] is unused.  An #equivalentJava: clause is used to avoid automatic
		//  index adjustment during translation.

		return _pointers[index];
	}

	public void pointerAtPut (
			final int index, 
			final AvailObject anAvailObject)
	{
		//  The index is one-based in both Smalltalk and Java, to avoid index manipulation.
		//  In Java entry [0] is unused.  An #equivalentJava: clause is used to avoid automatic
		//  index adjustment during translation.

		_pointers[index] = anAvailObject;
	}


}

