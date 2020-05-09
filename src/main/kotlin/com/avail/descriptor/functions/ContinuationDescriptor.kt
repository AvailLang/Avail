/*
 * ContinuationDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *    list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.functions

import com.avail.AvailRuntime
import com.avail.annotations.AvailMethod
import com.avail.annotations.EnumField
import com.avail.annotations.HideFieldInDebugger
import com.avail.descriptor.A_Fiber
import com.avail.descriptor.Descriptor
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import com.avail.descriptor.functions.ContinuationDescriptor.IntegerSlots.Companion.LEVEL_TWO_OFFSET
import com.avail.descriptor.functions.ContinuationDescriptor.IntegerSlots.Companion.PROGRAM_COUNTER
import com.avail.descriptor.functions.ContinuationDescriptor.IntegerSlots.Companion.STACK_POINTER
import com.avail.descriptor.functions.ContinuationDescriptor.IntegerSlots.LEVEL_TWO_OFFSET_AND_HASH
import com.avail.descriptor.functions.ContinuationDescriptor.ObjectSlots.*
import com.avail.descriptor.functions.ContinuationRegisterDumpDescriptor.Companion.createRegisterDump
import com.avail.descriptor.representation.*
import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.bottom
import com.avail.descriptor.types.ContinuationTypeDescriptor.continuationTypeForFunctionType
import com.avail.descriptor.types.TypeTag
import com.avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.execution.Interpreter.Companion.stringifyThen
import com.avail.interpreter.levelOne.L1Operation
import com.avail.interpreter.levelTwo.L1InstructionStepper
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.primitive.continuations.P_ContinuationStackData
import com.avail.interpreter.primitive.controlflow.P_CatchException
import com.avail.interpreter.primitive.controlflow.P_ExitContinuationWithResultIf
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation
import com.avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import com.avail.io.TextInterface
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.instanceMethod
import com.avail.optimizer.jvm.CheckedMethod.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.Casts.cast
import java.util.*

/**
 * A [continuation][ContinuationDescriptor] acts as an immutable execution
 * stack.  A running [fiber][FiberDescriptor] conceptually operates by
 * repeatedly replacing its continuation with a new one (i.e., one derived from
 * the previous state by nybblecode execution rules), performing necessary
 * side-effects as it does so.
 *
 * A continuation can be [exited][P_ExitContinuationWithResultIf], which causes
 * the current fiber's continuation to be replaced by the specified
 * continuation's caller. A return value is supplied to this caller.  A
 * continuation can also be [restarted][P_RestartContinuationWithArguments],
 * either with a specified tuple of arguments or with the
 * [original&#32;arguments][P_RestartContinuation].
 *
 * @constructor
 *
 * @param mutability
 *   The {@linkplain Mutability mutability} of the new descriptor.
 *
 * @author Mark van Gulik&lt;mark@availlang.org&gt;
 */
class ContinuationDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability,
	TypeTag.CONTINUATION_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * A composite field containing the [PROGRAM_COUNTER] and
		 * [STACK_POINTER].
		 */
		PROGRAM_COUNTER_AND_STACK_POINTER,

		/**
		 * A composite field containing the [LEVEL_TWO_OFFSET], and the cached
		 * hash of this object.
		 */
		LEVEL_TWO_OFFSET_AND_HASH;

		companion object {
			/**
			 * The index into the current continuation's [FUNCTION]'s compiled
			 * code's tuple of nybblecodes at which execution will next occur.
			 */
			@JvmField
			@EnumField(
				describedBy = EnumField.Converter::class,
				lookupMethodName = "decimal")
			val PROGRAM_COUNTER =
				BitField(PROGRAM_COUNTER_AND_STACK_POINTER, 32, 32)

			/**
			 * An index into this continuation's [frame&#32;slots][FRAME_AT_].
			 * It grows from the top + 1 (empty stack), and at its deepest it
			 * just abuts the last local variable.
			 */
			@JvmField
			@EnumField(
				describedBy = EnumField.Converter::class,
				lookupMethodName = "decimal")
			val STACK_POINTER =
				BitField(PROGRAM_COUNTER_AND_STACK_POINTER, 0, 32)

			/**
			 * The Level Two [instruction][L2Chunk.instructions] index at
			 * which to resume.
			 */
			@JvmField
			@EnumField(
				describedBy = EnumField.Converter::class,
				lookupMethodName = "decimal")
			val LEVEL_TWO_OFFSET = BitField(LEVEL_TWO_OFFSET_AND_HASH, 32, 32)

			/**
			 * Either zero or the hash of this [A_Continuation].
			 */
			@JvmField
			@HideFieldInDebugger
			val HASH_OR_ZERO = BitField(LEVEL_TWO_OFFSET_AND_HASH, 0, 32)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The continuation that invoked this one, or [nil] for the outermost
		 * continuation. When a continuation is not directly created by an
		 * [L1Operation.L1Ext_doPushLabel], it will have a type pushed on it.
		 * This type is checked against any value that the callee attempts to
		 * return to it. This supports link-time type strengthening at call
		 * sites.
		 */
		CALLER,

		/**
		 * The [function][FunctionDescriptor] being executed via this
		 * continuation.
		 */
		FUNCTION,

		/**
		 * The [L2Chunk] which can be resumed directly by the [Interpreter] to
		 * effect continued execution.
		 */
		LEVEL_TWO_CHUNK,

		/**
		 * An instance of [ContinuationRegisterDumpDescriptor], which holds a
		 * collection of [AvailObject] and [Long] values. These values are
		 * stored in the continuation for an [L2Chunk] to use as it wishes, but
		 * it's simply ignored when a chunk becomes invalid, since the
		 * [L2Chunk.unoptimizedChunk] and its [L1InstructionStepper] always rely
		 * solely on the pure L1 state.
		 *
		 *
		 * This slot can be [NilDescriptor.nil] if it's not needed.
		 */
		LEVEL_TWO_REGISTER_DUMP,

		/**
		 * The slots allocated for locals, arguments, and stack entries.  The
		 * arguments are first, then the locals, and finally the stack entries
		 * (growing downwards from the top).  At its deepest, the stack slots
		 * will abut the last local.
		 */
		FRAME_AT_
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === LEVEL_TWO_OFFSET_AND_HASH
		|| e === LEVEL_TWO_CHUNK

	/**
	 * Set both my level one program counter and level one stack pointer.
	 */
	@AvailMethod
	override fun o_AdjustPcAndStackp(
		self: AvailObject,
		pc: Int,
		stackp: Int
	) {
		assert(isMutable)
		self.setSlot(PROGRAM_COUNTER, pc)
		self.setSlot(STACK_POINTER, stackp)
	}

	@AvailMethod
	override fun o_FrameAt(self: AvailObject, subscript: Int) =
		self.slot(FRAME_AT_, subscript)

	@AvailMethod
	override fun o_FrameAtPut(
		self: AvailObject,
		subscript: Int,
		value: AvailObject
	): AvailObject {
		self.setSlot(FRAME_AT_, subscript, value)
		return self
	}

	@AvailMethod
	override fun o_Caller(self: AvailObject) = self.slot(CALLER)

	override fun o_CurrentLineNumber(
		self: AvailObject
	): Int {
		val code = self.function().code()
		val encodedDeltas = code.lineNumberEncodedDeltas()
		val instructionDecoder = L1InstructionDecoder()
		code.setUpInstructionDecoder(instructionDecoder)
		val thisPc = self.pc()
		instructionDecoder.pc(1)
		var lineNumber = code.startingLineNumber()
		var instructionCounter = 1
		while (!instructionDecoder.atEnd()
			&& instructionDecoder.pc() < thisPc)
		{
			val encodedDelta = encodedDeltas.tupleIntAt(instructionCounter++)
			val decodedDelta =
				if (encodedDelta and 1 == 0) encodedDelta shr 1
				else -(encodedDelta shr 1)
			lineNumber += decodedDelta
			// Now skip one nybblecode instruction.
			val op = instructionDecoder.getOperation()
			for (i in op.operandTypes.size - 1 downTo 0) {
				instructionDecoder.getOperand()
			}
		}
		return lineNumber
	}

	/**
	 * If immutable, copy the object as mutable, otherwise answer the original
	 * mutable continuation.  This is used by the [Interpreter] to ensure it is
	 * always executing a mutable continuation and is therefore always able to
	 * directly modify it.
	 */
	@AvailMethod
	override fun o_EnsureMutable(self: AvailObject): A_Continuation =
		if (isMutable) self else newLike(mutable, self, 0, 0)

	@AvailMethod
	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		another.equalsContinuation(self)

	@AvailMethod
	override fun o_EqualsContinuation(
		self: AvailObject,
		aContinuation: A_Continuation
	): Boolean = when {
		self.sameAddressAs(aContinuation) -> true
		!self.caller().equals(aContinuation.caller()) -> false
		!self.function().equals(aContinuation.function()) -> false
		self.pc() != aContinuation.pc() -> false
		self.stackp() != aContinuation.stackp() -> false
		else -> (1..self.numSlots()).all {
			self.frameAt(it).equals(aContinuation.frameAt(it))
		}
	}

	@AvailMethod
	override fun o_Function(self: AvailObject) = self.slot(FUNCTION)

	@AvailMethod
	override fun o_Hash(self: AvailObject): Int {
		// Hashing a continuation isn't expected to be common, but it's
		// sufficiently expensive that we need to cache the hash value in the
		// rare case that we do need it.
		var hash = self.slot(IntegerSlots.HASH_OR_ZERO)
		if (hash == 0) {
			val caller = cast(self.caller().traversed())
			var callerHash = 0
			if (!caller.equalsNil()
				&& caller.slot(IntegerSlots.HASH_OR_ZERO) == 0) {
				// The caller isn't hashed yet either.  Iteratively hash the
				// call chain bottom-up to avoid potentially deep recursion.
				val chain: Deque<AvailObject> = ArrayDeque()
				var ancestor = caller
				do {
					chain.addFirst(ancestor)
					ancestor = ancestor.caller().traversed()
				} while (!ancestor.equalsNil()
					&& ancestor.slot(IntegerSlots.HASH_OR_ZERO) == 0)
				for (c in chain) {
					callerHash = c.hashCode()
				}
			}
			hash = 0x0593599A xor callerHash
			hash += self.function().hash()
			hash *= AvailObject.multiplier
			hash += self.pc()
			hash *= AvailObject.multiplier
			hash = hash xor self.stackp()
			for (i in self.numSlots() downTo 1) {
				hash *= AvailObject.multiplier
				hash += -0x23cb5228 xor self.frameAt(i).hash()
			}
			if (hash == 0) {
				// Using this substitute for 0 is not strictly necessary, but
				// there's always a tiny chance that some pattern of components
				// tends to produce zero.  May as well play this one safely.
				hash = 0x4693F664
			}
			self.setSlot(IntegerSlots.HASH_OR_ZERO, hash)
		}
		return hash
	}

	@AvailMethod
	override fun o_Kind(self: AvailObject): A_Type =
		continuationTypeForFunctionType(self.function().kind())

	/**
	 * Set both my chunk index and the offset into it.
	 */
	@AvailMethod
	override fun o_LevelTwoChunkOffset(
		self: AvailObject,
		chunk: L2Chunk,
		offset: Int
	) {
		if (isShared) {
			synchronized(self) {
				self.setSlot(LEVEL_TWO_CHUNK, chunk.chunkPojo)
				self.setSlot(LEVEL_TWO_OFFSET, offset)
			}
		} else {
			self.setSlot(LEVEL_TWO_CHUNK, chunk.chunkPojo)
			self.setSlot(LEVEL_TWO_OFFSET, offset)
		}
	}

	@AvailMethod
	override fun o_LevelTwoChunk(self: AvailObject): L2Chunk {
		val chunk: L2Chunk =
			self.mutableSlot(LEVEL_TWO_CHUNK).javaObjectNotNull()
		if (chunk != L2Chunk.unoptimizedChunk && chunk.isValid) {
			L2Chunk.Generation.usedChunk(chunk)
		}
		return chunk
	}

	@AvailMethod
	override fun o_LevelTwoOffset(self: AvailObject) =
		self.mutableSlot(LEVEL_TWO_OFFSET)

	override fun o_NameForDebugger(self: AvailObject) =
		buildString {
			append(super.o_NameForDebugger(self))
			append(": ")
			val code = self.function().code()
			append(code.methodName().asNativeString())
			append(":")
			append(self.currentLineNumber())
			val primitive = code.primitive()
			if (primitive === P_CatchException) {
				append(", CATCH var = ")
				append(self.frameAt(4).value().value())
			}
		}

	/**
	 * Answer the number of slots allocated for arguments, locals, and stack
	 * entries.
	 */
	@AvailMethod
	override fun o_NumSlots(self: AvailObject) = self.variableObjectSlotsCount()

	@AvailMethod
	override fun o_Pc(self: AvailObject) = self.slot(PROGRAM_COUNTER)

	override fun o_RegisterDump(self: AvailObject) =
		self.slot(LEVEL_TWO_REGISTER_DUMP)

	override fun o_ReplacingCaller(
		self: AvailObject,
		newCaller: A_Continuation
	): A_Continuation {
		val mutableVersion =
			if (isMutable) self
			else newLike(mutable, self, 0, 0)
		mutableVersion.setSlot(CALLER, newCaller)
		return mutableVersion
	}

	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.CONTINUATION

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	/**
	 * Read from the stack at the given subscript, which is one-relative and
	 * based on just the stack area.
	 */
	@AvailMethod
	override fun o_StackAt(self: AvailObject, subscript: Int) =
		self.slot(FRAME_AT_, subscript)

	@AvailMethod
	override fun o_Stackp(self: AvailObject) = self.slot(STACK_POINTER)

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object {
		/** The [CheckedMethod] for [A_Continuation.function]. */
		@JvmField
		val continuationFunctionMethod: CheckedMethod = instanceMethod(
			A_Continuation::class.java,
			A_Continuation::function.name,
			A_Function::class.java)

		/**
		 * Create a new continuation with the given data.  The continuation
		 * should represent the state upon entering the new context - i.e., set
		 * the pc to the first instruction, clear the stack, and set up new
		 * local variables.
		 *
		 * @param function
		 *   The function being invoked.
		 * @param caller
		 *   The calling continuation, or [nil].
		 * @param startingChunk
		 *   The level two chunk to invoke.
		 * @param startingOffset
		 *   The offset into the chunk at which to resume.
		 * @param args
		 *   The [List] of arguments.
		 * @return
		 *   The new continuation.
		 */
		@JvmStatic
		fun createLabelContinuation(
			function: A_Function,
			caller: A_Continuation,
			startingChunk: L2Chunk,
			startingOffset: Int,
			args: List<AvailObject>
		): A_Continuation {
			val code = function.code()
			assert(code.primitive() == null)
			val frameSize = code.numSlots()
			val cont = mutable.create(frameSize)
			cont.setSlot(CALLER, caller)
			cont.setSlot(FUNCTION, function)
			cont.setSlot(LEVEL_TWO_REGISTER_DUMP, nil)
			cont.setSlot(PROGRAM_COUNTER, 0) // Indicates this is a label.
			cont.setSlot(STACK_POINTER, frameSize + 1)
			cont.levelTwoChunkOffset(startingChunk, startingOffset)
			//  Set up arguments...
			val numArgs = args.size
			assert(numArgs == code.numArgs())

			// Arguments area.  These are used by P_RestartContinuation, but they're
			// replaced before resumption if using
			// P_RestartContinuationWithArguments.
			cont.setSlotsFromList(FRAME_AT_, 1, args, 0, numArgs)

			// All the remaining slots.  DO NOT capture or build locals.
			cont.fillSlots(FRAME_AT_, numArgs + 1, frameSize - numArgs, nil)
			return cont
		}

		/**
		 * Create a mutable continuation with the specified fields.  Fill the
		 * stack frame slots with [nil].
		 *
		 * @param function
		 *   The function being invoked/resumed.
		 * @param caller
		 *   The calling continuation of this continuation.
		 * @param registerDump
		 *   Either `nil` or a [ContinuationRegisterDumpDescriptor] instance
		 *   that an [L2Chunk] will use upon resumption.
		 * @param pc
		 *   The level one program counter.
		 * @param stackp
		 *   The level one operand stack depth.
		 * @param levelTwoChunk
		 *   The [level two chunk][L2Chunk] to execute.
		 * @param levelTwoOffset
		 *   The level two chunk offset at which to resume.
		 * @return
		 *   A new mutable continuation.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun createContinuationExceptFrame(
			function: A_Function,
			caller: A_Continuation,
			registerDump: AvailObject,
			pc: Int,
			stackp: Int,
			levelTwoChunk: L2Chunk,
			levelTwoOffset: Int
		): AvailObject {
			val frameSize = function.code().numSlots()
			return mutable.create(frameSize).apply {
				setSlot(CALLER, caller)
				setSlot(FUNCTION, function)
				setSlot(LEVEL_TWO_REGISTER_DUMP, registerDump)
				setSlot(PROGRAM_COUNTER, pc)
				setSlot(STACK_POINTER, stackp)
				setSlot(LEVEL_TWO_CHUNK, levelTwoChunk.chunkPojo)
				setSlot(LEVEL_TWO_OFFSET, levelTwoOffset)
				fillSlots(FRAME_AT_, 1, frameSize, nil)
			}
		}

		/** The [CheckedMethod] for [createContinuationExceptFrame]. */
		@JvmField
		val createContinuationExceptFrameMethod: CheckedMethod = staticMethod(
			ContinuationDescriptor::class.java,
			::createContinuationExceptFrame.name,
			AvailObject::class.java,
			A_Function::class.java,
			A_Continuation::class.java,
			AvailObject::class.java,
			Int::class.javaPrimitiveType,
			Int::class.javaPrimitiveType,
			L2Chunk::class.java,
			Int::class.javaPrimitiveType)

		/**
		 * Create a mutable continuation with the specified fields.  Initialize
		 * the stack slot from the list of fields.
		 *
		 * @param function
		 *   The function being invoked/resumed.
		 * @param caller
		 *   The calling continuation of this continuation.
		 * @param registerDump
		 *   Either [nil] or a [ContinuationRegisterDumpDescriptor] instance
		 *   that an [L2Chunk] will use upon resumption.
		 * @param pc
		 *   The level one program counter.
		 * @param stackp
		 *   The level one operand stack depth.
		 * @param levelTwoChunk
		 *   The [level two chunk][L2Chunk] to execute.
		 * @param levelTwoOffset
		 *   The level two chunk offset at which to resume.
		 * @param frameValues
		 *   The list of values that populate the frame slots.
		 * @param zeroBasedStartIndex
		 *   The zero-based slot number at which to start writing frame values.
		 * @return
		 *   A new mutable continuation.
		 */
		@JvmStatic
		fun createContinuationWithFrame(
			function: A_Function,
			caller: A_Continuation,
			registerDump: AvailObject,
			pc: Int,
			stackp: Int,
			levelTwoChunk: L2Chunk,
			levelTwoOffset: Int,
			frameValues: List<A_BasicObject>,
			zeroBasedStartIndex: Int
		) = createContinuationExceptFrame(
			function,
			caller,
			registerDump,
			pc,
			stackp,
			levelTwoChunk,
			levelTwoOffset
		).apply {
			setSlotsFromList(
				FRAME_AT_, 1, frameValues, zeroBasedStartIndex, numSlots())
		}

		/**
		 * Create a private continuation with the specified fields.  The
		 * continuation will never be visible to level one code, but is used to
		 * carry register state (and [L2Chunk] & offset) during stack unwinding
		 * for a reification.  It will be executed (in the reverse of stack
		 * order) to run L2 code that reconstitutes a real continuation, which
		 * is pushed on the [Interpreter.getReifiedContinuation] stack.
		 *
		 * @param function
		 *   The [A_Function] that was running when this dummy continuation was
		 *   made.
		 * @param boxedRegisters
		 *   An [AvailObject] containing values to save in a register dump.
		 * @param unboxedRegisters
		 *   A `long[]` containing values to save in a register dump.
		 * @param levelTwoChunk
		 *   The [level two chunk][L2Chunk] to execute.
		 * @param levelTwoOffset
		 *   The level two chunk offset at which to resume.
		 * @return
		 *   A new continuation, which can be resumed but is not reflectively
		 *   meaningful.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun createDummyContinuation(
			function: A_Function,
			boxedRegisters: Array<AvailObject>,
			unboxedRegisters: LongArray,
			levelTwoChunk: L2Chunk,
			levelTwoOffset: Int
		): AvailObject = mutable.create(0).apply {
			setSlot(CALLER, nil)
			setSlot(FUNCTION, function)
			setSlot(
				LEVEL_TWO_REGISTER_DUMP,
				createRegisterDump(boxedRegisters, unboxedRegisters))
			setSlot(PROGRAM_COUNTER, -1)
			setSlot(STACK_POINTER, -1)
			setSlot(LEVEL_TWO_CHUNK, levelTwoChunk.chunkPojo)
			setSlot(LEVEL_TWO_OFFSET, levelTwoOffset)
		}

		/** The [CheckedMethod] for [createDummyContinuation]. */
		@JvmField
		val createDummyContinuationMethod: CheckedMethod = staticMethod(
			ContinuationDescriptor::class.java,
			::createDummyContinuation.name,
			AvailObject::class.java,
			A_Function::class.java,
			Array<AvailObject>::class.java,
			LongArray::class.java,
			L2Chunk::class.java,
			Int::class.javaPrimitiveType)

		/** The mutable [ContinuationDescriptor]. */
		private val mutable = ContinuationDescriptor(Mutability.MUTABLE)

		/** The immutable [ContinuationDescriptor]. */
		private val immutable = ContinuationDescriptor(Mutability.IMMUTABLE)

		/** The shared [ContinuationDescriptor]. */
		private val shared = ContinuationDescriptor(Mutability.SHARED)

		/**
		 * A substitute for [nil][AvailObject], for use by
		 * [P_ContinuationStackData].
		 */
		private val nilSubstitute: AvailObject =
			newVariableWithContentType(bottom()).makeShared()

		/**
		 * Answer a substitute for [nil] for a non-existent caller. This is
		 * primarily for use by [P_ContinuationStackData].
		 *
		 * @return
		 *   An immutable bottom-typed variable.
		 */
		fun nilSubstitute() = nilSubstitute

		/**
		 * Create a list of descriptions of the stack frames ([A_Continuation]s)
		 * of the specified continuation. Invoke the specified Kotlin function
		 * with the resultant list. This list begins with the newest frame and
		 * ends with the base frame.
		 *
		 * @param runtime
		 *   The [Avail runtime][AvailRuntime] to use for stringification.
		 * @param textInterface
		 *   The [text interface][TextInterface] for [fibers][A_Fiber] started
		 *   due to stringification. This need not be the default
		 *   [AvailRuntime.textInterface].
		 * @param availContinuation
		 *   The [A_Continuation] to dump.
		 * @param action
		 *   What to do with the list of [String]s.
		 */
		fun dumpStackThen(
			runtime: AvailRuntime,
			textInterface: TextInterface,
			availContinuation: A_Continuation,
			action: (List<String>) -> Unit
		) {
			val frames = mutableListOf<A_Continuation>()
			var c = availContinuation
			while (!c.equalsNil()) {
				frames.add(c)
				c = c.caller()
			}
			val lines = frames.size
			if (lines == 0) {
				action(emptyList())
				return
			}
			val allTypes: MutableList<A_Type> = ArrayList()
			for (frame in frames)
			{
				val code = frame.function().code()
				val paramsType = code.functionType().argsTupleType()
				for (i in 1..code.numArgs())
				{
					allTypes.add(paramsType.typeAtIndex(i))
				}
			}
			val strings = arrayOfNulls<String>(lines)
			stringifyThen(runtime, textInterface, allTypes) { allTypeNames ->
				var allTypesIndex = 0
				for (frameIndex in 0 until frames.size)
				{
					val frame = frames[frameIndex]
					val code = frame.function().code()
					val signature = (1..code.numArgs()).joinToString {
						allTypeNames[allTypesIndex++]
					}
					val module = code.module()
					strings[frameIndex] = String.format(
						"#%d: %s [%s] (%s:%d)",
						lines - frameIndex,
						code.methodName().asNativeString(),
						signature,
						if (module.equalsNil()) "?"
						else module.moduleName().asNativeString(),
						frame.currentLineNumber())
				}
				assert (allTypesIndex == allTypeNames.size)
				action(strings.map { it!! })
			}
		}
	}
}