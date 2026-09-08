/*
 * L2SimpleTranslator.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
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
package avail.interpreter.levelTwoSimple

import avail.AvailRuntime.HookType.INVALID_MESSAGE_SEND
import avail.AvailRuntime.HookType.RESULT_DISAGREED_WITH_EXPECTED_TYPE
import avail.AvailRuntimeSupport.captureNanos
import avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import avail.descriptor.methods.MethodDescriptor
import avail.descriptor.methods.MethodDescriptor.Companion.runtimeDispatcher
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Bundle
import avail.descriptor.representation.A_Bundle.Companion.bundleMethod
import avail.descriptor.representation.A_Bundle.Companion.numArgs
import avail.descriptor.representation.A_ChunkDependable
import avail.descriptor.representation.A_Method.Companion.definitionsAtOrBelow
import avail.descriptor.representation.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_Number.Companion.isInt
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.constantTypeAt
import avail.descriptor.representation.A_RawFunction.Companion.literalAt
import avail.descriptor.representation.A_RawFunction.Companion.localTypeAt
import avail.descriptor.representation.A_RawFunction.Companion.numArgs
import avail.descriptor.representation.A_RawFunction.Companion.numLocals
import avail.descriptor.representation.A_RawFunction.Companion.numOuters
import avail.descriptor.representation.A_RawFunction.Companion.numSlots
import avail.descriptor.representation.A_RawFunction.Companion.outerTypeAt
import avail.descriptor.representation.A_RawFunction.Companion.setStartingChunkAndReoptimizationCountdown
import avail.descriptor.representation.A_Sendable.Companion.bodyBlock
import avail.descriptor.representation.A_Sendable.Companion.bodySignature
import avail.descriptor.representation.A_Sendable.Companion.isMethodDefinition
import avail.descriptor.representation.A_Tuple.Companion.tupleIntAt
import avail.descriptor.representation.A_Tuple.Companion.tupleSize
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.acceptsListOfArgTypes
import avail.descriptor.representation.A_Type.Companion.argsTupleType
import avail.descriptor.representation.A_Type.Companion.functionType
import avail.descriptor.representation.A_Type.Companion.instance
import avail.descriptor.representation.A_Type.Companion.instanceCount
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.readType
import avail.descriptor.representation.A_Type.Companion.returnType
import avail.descriptor.representation.A_Type.Companion.sizeRange
import avail.descriptor.representation.A_Type.Companion.typeAtIndex
import avail.descriptor.representation.A_Type.Companion.typeIntersection
import avail.descriptor.representation.A_Type.Companion.typeUnion
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationTypeForFunctionType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypesList
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelOne.L1OperationDispatcher
import avail.interpreter.levelTwo.L2SimpleChunk
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.anyRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.nilRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.IMMUTABLE_FLAG
import avail.interpreter.levelTwoSimple.instructions.L2SimpleInstruction
import avail.interpreter.levelTwoSimple.instructions.L2Simple_AbstractCloseFunction
import avail.interpreter.levelTwoSimple.instructions.L2Simple_AbstractCloseFunction.Companion.createCloseFunction
import avail.interpreter.levelTwoSimple.instructions.L2Simple_AbstractMakeTuple
import avail.interpreter.levelTwoSimple.instructions.L2Simple_AbstractMakeTuple.Companion.createMakeTuple
import avail.interpreter.levelTwoSimple.instructions.L2Simple_AbstractReenter
import avail.interpreter.levelTwoSimple.instructions.L2Simple_AbstractReifiableInstruction
import avail.interpreter.levelTwoSimple.instructions.L2Simple_CheckForInterrupt
import avail.interpreter.levelTwoSimple.instructions.L2Simple_GeneralCall
import avail.interpreter.levelTwoSimple.instructions.L2Simple_GetConstant
import avail.interpreter.levelTwoSimple.instructions.L2Simple_GetOuter
import avail.interpreter.levelTwoSimple.instructions.L2Simple_GetVariable
import avail.interpreter.levelTwoSimple.instructions.L2Simple_GetVariableClearing
import avail.interpreter.levelTwoSimple.instructions.L2Simple_Invoke
import avail.interpreter.levelTwoSimple.instructions.L2Simple_InvokeIfNilpotentAttemptFails
import avail.interpreter.levelTwoSimple.instructions.L2Simple_MakeImmutable
import avail.interpreter.levelTwoSimple.instructions.L2Simple_Move
import avail.interpreter.levelTwoSimple.instructions.L2Simple_MoveConstant
import avail.interpreter.levelTwoSimple.instructions.L2Simple_PushLabel
import avail.interpreter.levelTwoSimple.instructions.L2Simple_PushOuter
import avail.interpreter.levelTwoSimple.instructions.L2Simple_ReenterFromCall
import avail.interpreter.levelTwoSimple.instructions.L2Simple_ReenterToResume
import avail.interpreter.levelTwoSimple.instructions.L2Simple_ReifyForPushLabel
import avail.interpreter.levelTwoSimple.instructions.L2Simple_Return
import avail.interpreter.levelTwoSimple.instructions.L2Simple_SetConstant
import avail.interpreter.levelTwoSimple.instructions.L2Simple_SetOuter
import avail.interpreter.levelTwoSimple.instructions.L2Simple_SetUpFrame
import avail.interpreter.levelTwoSimple.instructions.L2Simple_SetVariable
import avail.interpreter.levelTwoSimple.instructions.L2Simple_SuperCall
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.HIGHEST_LEGAL_OFFSET_int
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.NEXT
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.REIFY_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.RETURN_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.SKIP
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.UNREACHABLE
import avail.interpreter.levelTwoSimple.instructions.registers.Read
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.interpreter.levelTwoSimple.instructions.registers.WriteArray
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.OptimizationLevel
import avail.performance.Statistic
import avail.performance.StatisticReport.L2_OPTIMIZATION_TIME
import avail.utility.Strings.increaseIndentation
import avail.utility.isNullOr
import avail.utility.notNullAnd
import java.util.BitSet
import kotlin.math.max
import kotlin.streams.toList

/**
 * An [L2SimpleTranslator] produces a fast translation from L1 nybblecodes into
 * a sequence of [L2SimpleInstruction]s within an [L2SimpleExecutableChunk].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2SimpleTranslator
constructor(
	val code: A_RawFunction,
	private val optimizationLevel: OptimizationLevel,
	val interpreter: Interpreter,
) : L1OperationDispatcher
{
	/** The types of the registers at this point. */
	val restrictions = mutableMapOf<Read, TypeRestriction>()

	/** The list of [L2SimpleInstruction]s being generated. */
	val instructions = mutableListOf<L2SimpleInstruction>()

	/** The current operand stack pointer during code generation. */
	var stackp = code.numSlots + 1

	/** The [L1InstructionDecoder] that provides L1 operations and operands. */
	val instructionDecoder = L1InstructionDecoder()

	/** The current program counter, taken from the [instructionDecoder]. */
	val pc: Int get() = instructionDecoder.pc

	/** This always indicates how many registers have been allocated. */
	var registerCounter = 0

	fun newRegister(restriction: TypeRestriction): Write
	{
		val counter = registerCounter++
		restrictions[Read(counter)] = restriction
		return Write(counter)
	}

	/**
	 * The [A_ChunkDependable]s which, if changed, should invalidate the chunk
	 * being constructed.
	 */
	private val contingentValues = mutableSetOf<A_ChunkDependable>()

	val currentSlotReads = ReadArray(IntArray(code.numSlots + 1) { 0 })

	/**
	 * A map from negative "unresolved" offsets to their eventual positive
	 * "resolved" offsets.  This simplifies forward branch generation.
	 */
	val labelResolutions = mutableMapOf<Offset, Offset>()

	/**
	 * The next "unresolved" offset to hand out as a symbolic label during
	 * naive code generation.  It's always negative.
	 */
	var nextLabelOffset = -2

	/**
	 * Allocate a new "unresolved" label offset.
	 */
	fun newLabel() = Offset(nextLabelOffset--)

	/**
	 * Given an unresolved label previously created by [newLabel], resolve its
	 * actual location to the current position in the instructions stream.
	 */
	fun emitLabel(label: Offset)
	{
		assert(label.value < 0)
		assert(label !in labelResolutions) {
			"Attempting to resolve a label twice"
		}
		labelResolutions[label] = Offset(instructions.size)
	}

	fun writeSlot(
		slotIndex: Int,
		restriction: TypeRestriction
	): Write
	{
		val newRegister = newRegister(restriction)
		currentSlotReads.values[slotIndex] = newRegister.value
		return newRegister
	}

	fun readSlot(slotIndex: Int) = currentSlotReads[slotIndex]

	fun readSlots(firstSlotIndex: Int, lowestSlotIndex: Int) = ReadArray(
		(firstSlotIndex downTo lowestSlotIndex).map { readSlot(it) })

	fun setSlotRestriction(slotIndex: Int, restriction: TypeRestriction)
	{
		restrictions[readSlot(slotIndex)] = restriction
	}

	fun restrictionFor(read: Read): TypeRestriction? = restrictions[read]

	fun narrowRestriction(
		read: Read,
		restriction: TypeRestriction?)
	{
		val oldRestriction = restrictions[read]
		val newRestriction = when
		{
			oldRestriction === null -> restriction
			restriction === null -> oldRestriction
			else -> oldRestriction.intersection(restriction)
		}
		if (newRestriction !== null)
		{
			restrictions[read] = newRestriction
		}
	}

	fun slotRestriction(slotIndex: Int): TypeRestriction =
		restrictionFor(readSlot(slotIndex))!!

	/**
	 * Instructions that have not yet been written, keyed by a [Read] that would
	 * be made available if the postponed instruction is written.
	 */
	val postponedInstructions = mutableMapOf<Read, L2SimpleInstruction>()

	/**
	 * For every instruction that has been emitted, this records, under each
	 * [Write.value], the indices of the [L2SimpleInstruction]s that wrote it.
	 */
	val emittedOrigins = mutableMapOf<Int, IntArray>()

	/**
	 * Answer all instructions, whether emitted or postponed, that populate the
	 * register specified by the [Read].
	 */
	fun originInstructionsFor(read: Read): List<L2SimpleInstruction>
	{
		val emittedOffsets = emittedOrigins[read.value]
		val postponed = postponedInstructions[read]
		if (emittedOffsets == null)
		{
			// No emitted offsets, so return the postponed instruction if
			// present, otherwise empty.
			return postponed?.let(::listOf) ?: emptyList()
		}
		val emittedInstructions = emittedOffsets.map(instructions::get)
		return when (postponed)
		{
			null -> emittedInstructions
			else -> emittedInstructions + postponed
		}
	}

	/**
	 * If there's a unique [L2SimpleInstruction] that produces the value for the
	 * specified [read], answer it, otherwise `null`.  Trace back through moves
	 * as well.
	 */
	fun originInstructionSkippingMoves(read: Read): L2SimpleInstruction?
	{
		var currentRead = read
		while (true)
		{
			val origin = originInstructionsFor(currentRead).singleOrNull()
			if (origin == null) return null
			if (origin !is L2Simple_Move) return origin
			// Trace back through the move.
			currentRead = origin.from
		}
	}

	/**
	 * Given the [L2SimpleInstruction] that produces a function, attempt to
	 * determine the [A_RawFunction] that is within that function, or `null` if
	 * it can't be determined.
	 */
	fun codeForFunctionCreation(functionRead: Read): A_RawFunction?
	{
		restrictionFor(functionRead)?.constantOrNull?.code()?.let { return it }
		val functionInstruction = originInstructionSkippingMoves(functionRead)
		return when (functionInstruction)
		{
			is L2Simple_AbstractCloseFunction -> functionInstruction.code
			is L2Simple_MoveConstant -> functionInstruction.value.code()
			else -> null
		}
	}

	/**
	 * Generate a naive translation of the L1 instructions.
	 */
	fun naiveTranslateFromL1()
	{
		val funType = code.functionType()
		// Set up the restriction for r[0].  At runtime it holds the current
		// function, but at compile time it acts as nil.  The final code will
		// never fetch from slot zero of a RegisterSet.
		writeSlot(0, nilRestriction)
		// Set up the restrictions for the arguments r[1]..r[n].
		val paramTypes = funType.argsTupleType
		val numArgs = code.numArgs()
		for (i in 1..numArgs)
		{
			restrictions[readSlot(i)] =
				restrictionForType(paramTypes.typeAtIndex(i))
		}
		// Also set up the local variables (but not constants).
		for (i in 1..code.numLocals)
		{
			restrictions[readSlot(i + numArgs)] =
				restrictionForType(code.localTypeAt(i))
		}
		code.setUpInstructionDecoder(instructionDecoder, 1)
		val numLocals = code.numLocals
		val failureCode = code.codePrimitive()?.let { prim ->
			assert(!prim.hasFlag(CannotFail))
			// The first constant slot gets the failure code.
			writeSlot(
				numArgs + numLocals + 1,
				restrictionForType(code.constantTypeAt(1)))
		}
		+L2Simple_SetUpFrame(
			rawFunction = code,
			argumentsToCapture = WriteArray(
				(1 .. numArgs).map {
					writeSlot(
						it,
						restrictionForType(paramTypes.typeAtIndex(it)))
				}),
			localsToPopulate = WriteArray(
				(1 .. numLocals).map {
					writeSlot(
						numArgs + it,
						restrictionForType(code.localTypeAt(it)))
				}),
			primitiveFailureCode = failureCode)
		+L2Simple_CheckForInterrupt(
			nextOffset = SKIP,
			stateOfL1 = StateOfL1(
				stackp = stackp,
				pc = pc,
				liveSlots = liveIndices()),
			reentryOffset = NEXT)
		+L2Simple_ReenterToResume(
			nextOffset = NEXT,
			stateOfL1 = StateOfL1(
				stackp = -888,
				pc = -888,
				liveSlots = ReadArray.empty))
		while (!instructionDecoder.atEnd())
		{
			instructionDecoder.getOperation().dispatch(this)
		}
		assert(stackp == code.numSlots) {
			"One value should have been left on stack"
		}
		// Use the last architectural stack slot to get the value to return.
		+L2Simple_Return(value = readSlot(stackp))
	}

	/**
	 * Create an L2Chunk from the already optimized [instructions] list.  This
	 * must only be performed while interpreters are able to run.  Invalidation
	 * happens within safe-points, because that's when method definitions can be
	 * added and removed.
	 */
	fun createChunk(): L2SimpleChunk
	{
		val chunk = L2SimpleChunk.allocate(
			code = code,
			// See L2SimpleExecutableChunk.runChunk()
			offsetAfterInitialTryPrimitive =
				if (code.codePrimitive() == null) 0 else -1,
			registerCount = registerCounter,
			theInstructions = instructions,
			contingentValues = setFromCollection(contingentValues),
			optimizationLevel = optimizationLevel)
		code.setStartingChunkAndReoptimizationCountdown(
			chunk, optimizationLevel.countdown)
		return chunk
	}

	/**
	 * Adjust all offsets via the [labelResolutions] built up during naive
	 * translation, including relative offsets like [NEXT], [SKIP]..
	 */
	private fun resolveLabels()
	{
		val offsetResolver = object : L2SimpleInstructionTransformer()
		{
			var index = 0

			override fun target(offset: Offset): Offset
			{
				val resolved = when (offset)
				{
					UNREACHABLE, RETURN_NOW, REIFY_NOW -> return offset
					// Ignore backward looping jumps.
					Offset(0) -> return offset
					NEXT -> Offset(index + 1)
					SKIP -> Offset(index + 2)
					else ->
						if (offset.value < 0) labelResolutions[offset]!!
						else offset
				}
				assert(resolved.value > index) {
					"Must jump forward, to 0, or to a special offset."
				}
				return resolved
			}
		}
		for (index in 0 until instructions.size)
		{
			offsetResolver.index = index
			instructions[index] =
				instructions[index].run { offsetResolver.transformed() }
		}
	}

	/**
	 * Determine which instructions can be removed (because they don't produce a
	 * needed value or have a side effect), and remove them while adjusting the
	 * offsets.
	 */
	private fun removeDeadCode()
	{
		val liveInstructionIndices = BitSet()
		val neededReads = BitSet()
		for (index in instructions.size - 1 downTo 0)
		{
			val instruction = instructions[index]
			var keep = !instruction.canBePostponed
				|| instruction.allWrites.any { neededReads.get(it.value) }
			if (instruction is L2Simple_Move
				&& instruction.from.value == instruction.to.value)
			{
				// We're doing post-colored dead code elimination, and we just
				// found a move-to-same-register, which we can omit.
				keep = false
			}
			if (keep)
			{
				instruction.allReads.forEach { neededReads.set(it.value) }
				liveInstructionIndices.set(index)
			}
		}
		if (instructions.none { it is L2Simple_PushLabel })
		{
			// There are no label creation instructions, we should also remove
			// any ReifyForPushLabel instructions and their corresponding entry
			// points.
			instructions.forEachIndexed { index, instruction ->
				if (instruction is L2Simple_ReifyForPushLabel)
				{
					liveInstructionIndices.clear(index)
					liveInstructionIndices.clear(
						instruction.reentryOffset.value)
				}
			}
		}
		if (liveInstructionIndices.nextClearBit(0) != instructions.size)
		{
			val renumber = mutableMapOf<Int, Int>()
			val newInstructions = mutableListOf<L2SimpleInstruction>()
			var keepPosition = 0
			for (index in 0 until instructions.size)
			{
				renumber[index] = keepPosition
				if (liveInstructionIndices[index])
				{
					keepPosition++
					newInstructions.add(instructions[index])
				}
			}
			val renumberer = object : L2SimpleInstructionTransformer()
			{
				override fun target(offset: Offset): Offset = when
				{
					offset.value in 0..HIGHEST_LEGAL_OFFSET_int ->
						Offset(renumber[offset.value]!!)
					else -> offset
				}
			}
			instructions.clear()
			newInstructions.forEach { instruction ->
				instructions.add(instruction.run { renumberer.transformed() })
			}
		}
	}

	/**
	 * This function performs register coloring:
	 *  * Due to the simple way that L2Simple instructions are generated, every
	 *    [Offset] except to [Offset]`(0)` are *forward* – the target is at a
	 *    higher numbered [Offset] than the source of the jump.
	 *  * The [Offset]`(0)` case is special, since there are *no registers* live
	 *    upon entry or restart – the arguments are passed in
	 *    [Interpreter.argsBuffer] in both cases.
	 *  * Additionally, most L2Simple code is linear, with only short
	 *    divergences for polymorphic inlining.  So we approximate the lifetimes
	 *    of registers by the earliest [Write] (it's not quite SSA) and the
	 *    largest [Offset] in which a [Read] of that register occurs, or if
	 *    there are no reads, the latest (scratch) [Write].  We're not trying to
	 *    map onto architectural registers, just eliminating most of the wasted
	 *    storage in a call frame.
	 *  * So the first pass collects all last-read offsets, and the second pass
	 *    actually transforms the instructions, allocating register colors as it
	 *    goes.  The second pass also corrects [NEXT] uses to
	 *    the actual offsets.
	 */
	private fun colorRegisters()
	{
		val log = false
		// Pass one: Find all last-read offsets.
		val oldRegisterCount = registerCounter
		// [reg -> offset]
		val lastReads = IntArray(oldRegisterCount) { -1 }
		// Calculate last uses, where last means in instruction order, a slight
		// approximation (conservative) of lifetimes along branches, which are
		// expected to be minimal.  Ignore writes – if a scratch write happens
		// that has no corresponding reads, it will still be dropped after the
		// writing instruction by virtue of its lastReads entry still being -1.
		instructions.forEachIndexed { instructionIndex, instruction ->
			val scanner = object : L2SimpleInstructionTransformer() {
				override fun read(read: Read): Read
				{
					// Clobber as we go, since the last one is what we want.
					lastReads[read.value] = instructionIndex
					return read
				}
			}
			instruction.run { scanner.transformed() }
		}
		// Pass two: Replace all instructions, renaming registers and tracking
		// which ones are expiring from the live set.
		// Restart the numbering.
		registerCounter = 0
		val registerMap = IntArray(oldRegisterCount) { -1 }
		// Register zero is special.  Never drop it.
		lastReads[0] = Int.MAX_VALUE
		registerMap[0] = 0
		val liveNewRegisters = BitSet()
		liveNewRegisters.set(0)
		// Replace the instructions as we go, since none will be added or
		// removed or reordered.
		var currentInstructionIndex = 0
		// The accumulated *original* registers collected during an
		// instruction translation.  These will be scanned after the instruction
		// has been fully transformed, to determine if any of them are a final
		// use.  If so, their replacement register will be freed from use.
		val encounteredOriginalRegisters = mutableListOf<Int>()
		// For each instruction, first transform all reads with the
		// readTransformer.  An entry will exist in the registerMap.  Also
		// accumulate these reads into encounteredOriginalRegisters so that we
		// can deduplicate them and drop them only after we've processed all the
		// reads for this instruction.
		val readTransformer = object : L2SimpleInstructionTransformer() {
			override fun read(read: Read): Read
			{
				encounteredOriginalRegisters += read.value
				val replacement = registerMap[read.value]
				assert(replacement != -1)
				if (log) println("\tReused $read -> ${Read(replacement)}")
				return Read(replacement)
			}
		}
		// Capture live registers.
		val captureAllLiveRegistersTransformer =
			object : L2SimpleInstructionTransformer()
			{
				override fun state(stateOfL1: StateOfL1): StateOfL1
				{
					assert(stateOfL1.allLiveRegisters == null)
					val allLive = ReadArray(
						liveNewRegisters.stream()
							.dropWhile { it == 0 }
							.toList()
							.toIntArray())
					return StateOfL1(
						pc = stateOfL1.pc,
						stackp = stateOfL1.stackp,
						liveSlots = super.read(stateOfL1.liveSlots),
						allLiveRegisters = allLive)
				}
			}

		// With the partially transformed instruction, transform it with the
		// writeTransformer, which only affects writes and offsets.  This is
		// done in two steps because we do not have control over an
		// instruction's order of visits of fields.  We look explicitly for
		// scratch writes here, where no reads of that register were discovered,
		// and we deallocate it immediately after writing to it.
		val scratchWrites = mutableSetOf<Write>()
		val writeTransformer = object : L2SimpleInstructionTransformer()
		{
			/**
			 * Track the most recently dropped transformed read.  The colorer
			 * will attempt to reuse this if possible when allocating for a
			 * write, to increase the chance that a move source and destination
			 * end up in the same register, allowing it to be elided.  Since we
			 * simply check if this one is still available in liveNewRegisters,
			 * initialize it to 0.
			*/
			var latestDroppedRead = 0

			override fun write(write: Write): Write
			{
				var replacement = registerMap[write.value]
				// It's not quite SSA, so there may be multiple writes to the
				// same register.  Permit this.
				if (replacement == -1)
				{
					// Choose a free register.
					replacement = if (!liveNewRegisters[latestDroppedRead])
					{
						// The latest dropped read is still available.  Prefer
						// it, to help elide more moves.
						latestDroppedRead
					}
					else
					{
						liveNewRegisters.nextClearBit(0)
					}
					liveNewRegisters.set(replacement)
					registerCounter = max(registerCounter, replacement + 1)
					registerMap[write.value] = replacement
					if (log) println("\tAllocated $write -> ${Write(replacement)}")
					// While it would seem tempting to remove scratch writes,
					// which have no corresponding reads, right now, we can't
					// because it would expose the same register number to
					// subsequent writes within the same instruction, and those
					// might actually happen first.
					if (lastReads[write.value] == -1)
					{
						// Nobody reads from this register.  We can't drop it
						// right now, because we don't visit the write operands
						// in an order that we can control, so we might have
						// another write coming up in this instruction that
						// would allocate the same (dropped) register, even
						// though the two writes might execute in the reverse
						// order (clobbering an output with the scratch value).
						scratchWrites += write
					}
				}
				else
				{
					if (log) println("\tReused $write -> ${Write(replacement)}")
				}
				return Write(replacement)
			}

			override fun state(stateOfL1: StateOfL1): StateOfL1
			{
				return StateOfL1(
					pc = stateOfL1.pc,
					stackp = stateOfL1.stackp,
					liveSlots = super.read(stateOfL1.liveSlots),
					// This *MUST* have been set up by
					// captureAllLiveRegistersTransformer.  Don't actually do
					// a transformation on this [ReadArray].
					allLiveRegisters = stateOfL1.allLiveRegisters!!)
			}
		}
		while (currentInstructionIndex < instructions.size)
		{
			val oldInstruction = instructions[currentInstructionIndex]
			if (log) println("$currentInstructionIndex: " +
				increaseIndentation(oldInstruction.toString(), 1))
			encounteredOriginalRegisters.clear()
			// Transform the reads, also recording each original one visited.
			var readTransformedInstruction = oldInstruction.run {
				readTransformer.transformed()
			}
			readTransformedInstruction = readTransformedInstruction.run {
				captureAllLiveRegistersTransformer.transformed()
			}
			// For any visited original reads that have an entry in lastReads
			// indicating this is the instruction where it is last used, drop
			// it.  The execution machinery (step) should perform all reads
			// before any writes.
			encounteredOriginalRegisters.distinct().forEach { original ->
				val replacement = registerMap[original]
				if (lastReads[original] == currentInstructionIndex)
				{
					// That read was the last one for that register, or one of
					// multiple final reads of the same register in the same
					// instruction.
					if (log) println("\tDropped: $original->$replacement")
					registerMap[original] = -2  // Eye catcher.
					liveNewRegisters.clear(replacement)
					writeTransformer.latestDroppedRead = replacement
				}
			}
			scratchWrites.clear()
			val newInstruction = readTransformedInstruction.run {
				writeTransformer.transformed()
			}
			instructions[currentInstructionIndex] = newInstruction
			scratchWrites.distinct().forEach { write ->
				if (log) println("\tDropped SCRATCH " +
					"$write->${Write(registerMap[write.value])}")
				liveNewRegisters.clear(registerMap[write.value])
			}
			currentInstructionIndex++
		}
		// Final fixup – each reentry point has its allLiveRegisters set now,
		// and the reifiable
		instructions.forEach { instruction ->
			if (instruction is L2Simple_AbstractReifiableInstruction)
			{
				val reentryOffset = instruction.reentryOffset.value
				if (reentryOffset in 0..HIGHEST_LEGAL_OFFSET_int)
				{
					val reentryInstruction =
						instructions[reentryOffset] as L2Simple_AbstractReenter
					// Update the reifiable instruction's live registers to
					// use the reenter instruction's live registers.  They must
					// agree for reentry to work, but we only have to preserve
					// the registers that the reenter instruction needs.
					instruction.stateOfL1.allLiveRegisters =
						reentryInstruction.stateOfL1.allLiveRegisters!!
				}
			}
		}
		if (log) println("DONE coloring\n")
	}

	/**
	 * Emit the given [L2SimpleInstruction] into my [instructions] list if it
	 * cannot be postponed.  If it can be postponed, record it under the sole
	 * write that a postponed instruction is allowed to perform.
	 */
	operator fun L2SimpleInstruction.unaryPlus()
	{
		if (canBePostponed)
		{
			// Postpone it.
			val write = allWrites.single()
			postponedInstructions[write.read] = this
		}
		else
		{
			// Emit it now, including all postponed predecessors.
			forceEmit(this)
		}
	}

	fun forceEmit(instruction: L2SimpleInstruction)
	{
		instruction.allReads.forEach { read ->
			postponedInstructions.remove(read)?.let(::forceEmit)
		}
		instructions.add(instruction)
		instruction.allWrites.forEach { write ->
			emittedOrigins[write.value] =
				when (val old = emittedOrigins[write.value])
				{
					null -> IntArray(1) { instructions.size - 1 }
					else -> old + (instructions.size - 1)
				}
		}
	}

	/**
	 * Generate a dispatched call of this bundle's method.  The arguments are
	 * already scraped off the stack, and [answer] indicates where to write the
	 * result, either upon return or on reentry at some point after reification.
	 *
	 * After a successful lookup, proceed with an invocation as per
	 * [generateGeneralInvocation].  If the lookup is unsuccessful, invoke the
	 * [INVALID_MESSAGE_SEND] hook function. When the invocation of the hook
	 * function invariably returns, requesting reification (because the hook
	 * function is ⊥-valued), synthesize a continuation with the arguments
	 * popped and the expected value pushed, using the [DefaultL1Chunk]
	 * (although it won't be resumable).
	 */
	private fun generateGeneralCall(
		bundle: A_Bundle,
		arguments: ReadArray,
		argRestrictions: List<TypeRestriction>,
		expectedType: A_Type,
		answer: Write,
		stateOfL1: StateOfL1,
		superUnionType: A_Type)
	{
		// Now, figure out which actual method definitions might be called, take
		// the union of their return types, then intersect it with the expected
		// type.  This strengthened type might make subsequent calls, using the
		// returned value as an argument, more restrictive, ideally monomorphic.
		val method = bundle.bundleMethod
		val possible = method.definitionsAtOrBelow(argRestrictions)
			.filter { it.isMethodDefinition() }
		val possibleType = possible.fold(bottom) { typeUnion, def ->
			typeUnion.typeUnion(def.bodySignature().returnType)
		}
		// Try to avoid created indirections here, since the types could be used
		// in tight loops.
		val narrowedExpectedType = when
		{
			// A passed equality test will introduce an indirection if possible.
			possibleType.equals(expectedType) -> expectedType.traversed()
			else -> possibleType.typeIntersection(expectedType)
		}.makeShared()
		val mustCheck = !possibleType.isSubtypeOf(expectedType)
		when
		{
			superUnionType.isBottom ->
			{
				// For now, always plug in a brand new lookup tree.
				val newTree = runtimeDispatcher.createRoot(
					possible,
					argRestrictions,
					Unit)
				+L2Simple_GeneralCall(
					nextOffset = SKIP,
					stateOfL1 = stateOfL1,
					reentryOffset = NEXT,
					expectedType = narrowedExpectedType,
					mustCheck = mustCheck,
					answer = answer,
					bundle = bundle,
					lookupTree = newTree,
					dynamicLookupStats =
						(method.traversed().descriptor as MethodDescriptor)
							.dynamicLookupStats(),
					arguments = arguments)
			}
			else ->
				+L2Simple_SuperCall(
					nextOffset = SKIP,
					stateOfL1 = stateOfL1,
					reentryOffset = NEXT,
					expectedType = narrowedExpectedType,
					mustCheck = mustCheck,
					answer = answer,
					bundle = bundle,
					superUnionType = superUnionType,
					arguments = arguments)
		}
		+L2Simple_ReenterFromCall(
			nextOffset = NEXT,
			stateOfL1 = stateOfL1.copy(liveSlots = ReadArray.empty),
			answer = answer,
			expectedType = narrowedExpectedType,
			mustCheck = mustCheck)
	}

	/**
	 * Generate an invocation of the given function.  The arguments are already
	 * on the stack, and known to conform with the function's argument types.
	 *
	 * After the completed invocation, if the call's result meets the expected
	 * type, the result should be on the stack.  If during the invocation a
	 * reification happens, a continuation using the [DefaultL1Chunk] should
	 * be created, with the arguments popped and the expected *type* pushed on
	 * the stack. If after an unreified call, the returned value does not
	 * conform to the [expectedType], the [RESULT_DISAGREED_WITH_EXPECTED_TYPE]
	 * hook function should be invoked, with suitable arguments.  That
	 * Kotlin-level call can only return for reification, since the Avail
	 * function must have a return type of ⊥. When the reification happens, a
	 * continuation is created which is identical to what would be created had
	 * the original invocation itself requested reification.
	 *
	 * Answer the [TypeRestriction] that is guaranteed to hold for the return
	 * value *after* it has been checked successfully against the expectedType.
	 */
	fun generateGeneralInvocation(
		nilpotentAttempt: ((Interpreter)->A_BasicObject?)?,
		calledCode: A_RawFunction,
		calledFunction: Read,
		arguments: ReadArray,
		argumentRestrictions: List<TypeRestriction>,
		expectedType: A_Type,
		stateOfL1: StateOfL1,
		answer: Write
	): TypeRestriction
	{
		val prim = calledCode.codePrimitive()
		val guaranteedReturnType = when
		{
			prim != null ->
			{
				val argTypes = argumentRestrictions.map(TypeRestriction::type)
				val guaranteedType =
					prim.returnTypeGuaranteedByVM(calledCode, argTypes)
						.typeIntersection(calledCode.functionType().returnType)
				if (guaranteedType.instanceCount.equalsInt(1)
					&& !guaranteedType.isInstanceMeta
					&& prim.hasFlag(CanFold)
					&& prim.fallibilityForArgumentTypes(argTypes)
						== CallSiteCannotFail
					&& guaranteedType.isSubtypeOf(expectedType))
				{
					// The primitive has no side-effects, succeeds for these
					// input types, and returns a constant, and that constant
					// complies with the expectedType.
					+L2Simple_MoveConstant(
						value = guaranteedType.instance.makeShared(),
						to = answer)
					return restrictionForType(guaranteedType)
						.withFlag(IMMUTABLE_FLAG)
				}
				guaranteedType
			}
			else -> calledCode.functionType().returnType
		}
		val mustCheck = !guaranteedReturnType.isSubtypeOf(expectedType)
		if (nilpotentAttempt !== null)
		{
			+L2Simple_InvokeIfNilpotentAttemptFails(
				nextOffset = SKIP,
				stateOfL1 = stateOfL1,
				reentryOffset = NEXT,
				expectedType = expectedType,
				mustCheck = mustCheck,
				answer = answer,
				function = calledFunction,
				arguments = arguments,
				nilpotentAttempt = nilpotentAttempt)
		}
		else
		{
			+L2Simple_Invoke(
				nextOffset = SKIP,
				stateOfL1 = stateOfL1,
				reentryOffset = NEXT,
				expectedType = expectedType,
				mustCheck = mustCheck,
				answer = answer,
				function = calledFunction,
				arguments = arguments)
		}
		+L2Simple_ReenterFromCall(
			nextOffset = if (expectedType.isBottom) UNREACHABLE else NEXT,
			stateOfL1 = stateOfL1.copy(liveSlots = ReadArray.empty),
			answer = answer,
			expectedType = expectedType,
			mustCheck = mustCheck)
		return restrictionForType(
			guaranteedReturnType.typeIntersection(expectedType))
	}

	/**
	 * If it's possible to trace the tuple elements to origin instructions,
	 * even if it involves adding postponed constant moves, answer a [ReadArray]
	 * that says where they're from.  Otherwise answer `null`.
	 */
	fun tupleElementSources(
		tupleRead: Read
	): ReadArray?
	{
		val tupleSource = originInstructionSkippingMoves(tupleRead)
		return when (tupleSource)
		{
			is L2Simple_AbstractMakeTuple -> tupleSource.elements
			is L2Simple_MoveConstant ->
			{
				val tuple = tupleSource.value
				ReadArray(tuple.map(::constant))
			}
			else -> null
		}
	}

	/**
	 * We have a [Read] of a function to be invoked, and a [Read] of the tuple
	 * of arguments to pass to it.  If we're able to type-check it and determine
	 * the origins of the tuple elements, generate a direct invocation of the
	 * function and answer `true`.  Otherwise emit nothing and answer `false`.
	 */
	fun attemptToEmbedInvocation(
		functionToInvoke: Read,
		functionArguments: Read,
		stateOfL1: StateOfL1,
		answer: Write,
		expectedType: A_Type
	): Boolean
	{
		val tupleElements = tupleElementSources(functionArguments)
		tupleElements ?: return false
		val tupleElementRestrictions = tupleElements.values.map {
			restrictionFor(Read(it)) ?: anyRestriction
		}
		val tupleElementTypes =
			tupleElementRestrictions.map(TypeRestriction::type)
		// Check if the function itself was closed locally.
		val functionRestriction = restrictionFor(functionToInvoke)!!
		val exactFunction = functionRestriction.constantOrNull
		val functionType = functionRestriction.type
		val argsTupleType = functionType.argsTupleType
		val argsTupleSizes = argsTupleType.sizeRange
		val numArgs = argsTupleSizes.lowerBound
		when
		{
			// Check if the argument count disagrees with the tuple.
			!numArgs.isInt -> return false
			!argsTupleSizes.upperBound.equals(numArgs) -> return false
			!numArgs.equalsInt(tupleElements.size) -> return false
			// Check if the function will definitely not accept the arguments.
			!functionType.acceptsListOfArgTypes(tupleElementTypes) ->
				return false
		}
		// The provided arguments satisfy the function's argument types.
		val code = codeForFunctionCreation(functionToInvoke)
		// The function will definitely accept the arguments.  If the called
		// function is itself a primitive, give it the opportunity to do further
		// specialization.
		val calledPrim = code?.codePrimitive()
		var nilpotentAttempt: ((Interpreter)->A_BasicObject?)? = null
		calledPrim?.run {
			// It's trying to invoke a primitive function, so let that primitive
			// function do its code generation instead.
			val generated = attemptToGenerateSimpleInvocation(
				functionIfKnown = exactFunction,
				rawFunction = code,
				functionRead = functionToInvoke,
				expectedType = expectedType,
				args = tupleElements,
				argRestrictions = tupleElementRestrictions,
				stateOfL1 = stateOfL1,
				answer = answer)
			if (generated) return true
			nilpotentAttempt = simplePrimitiveNilpotentInvocation(
				functionIfKnown = exactFunction,
				rawFunction = code,
				argRestrictions = tupleElementRestrictions,
				expectedType = expectedType)
		}
		// Even though it's not a primitive or the primitive didn't generate
		// custom infallible code, we can still directly invoke the function.
		code?.run {
			val outputRestriction = generateGeneralInvocation(
				nilpotentAttempt = nilpotentAttempt,
				calledCode = code,
				calledFunction = functionToInvoke,
				arguments = tupleElements,
				argumentRestrictions = tupleElementRestrictions,
				expectedType = expectedType,
				stateOfL1 = stateOfL1,
				answer = answer)
			narrowRestriction(answer.read, outputRestriction)
			return true
		}
		// The arguments satisfy the function's requirements, so we can still
		// invoke it directly.
		val mustCheck = !functionType.returnType.isSubtypeOf(expectedType)
		+L2Simple_Invoke(
			nextOffset = SKIP,
			stateOfL1 = stateOfL1,
			reentryOffset = NEXT,
			expectedType = expectedType,
			mustCheck = mustCheck,
			answer = answer,
			function = functionToInvoke,
			arguments = tupleElements)
		+L2Simple_ReenterFromCall(
			nextOffset = if (expectedType.isBottom) UNREACHABLE else NEXT,
			stateOfL1 = stateOfL1.copy(liveSlots = ReadArray.empty),
			answer = answer,
			expectedType = expectedType,
			mustCheck = mustCheck)
		return true
	}

	/**
	 * Answer an [Array] of register indices which should constitute a reified
	 * continuation at this position in the code.  For slots that are known to
	 * be nil, rather than go to the effort of actually clearing them, we've set
	 * their restriction to the [nilRestriction], so a '0' is used to indicate
	 * we want to store a nil in the corresponding slot (since
	 * `registers.function` is reserved for the current function).
	 */
	fun liveIndices(
		rangeToNil: IntRange? = null
	): ReadArray
	{
		val array = IntArray(currentSlotReads.size - 1) { zeroIndex ->
			val slotIndex = zeroIndex + 1
			val read = readSlot(slotIndex)
			when
			{
				restrictionFor(read).isNullOr {
					constantOrNull.notNullAnd { isNil }
				} -> 0
				rangeToNil.notNullAnd { contains(slotIndex) } -> 0
				else -> read.value
			}
		}
		return interpreter.arraysForL2Simple
			.computeIfAbsent(array.asList()) { ReadArray(array) }
	}

	/**
	 * Move a value between two architectural slots.  Don't do an actual move
	 * instruction, just alter the [currentSlotReads] table.  If [makeImmutable]
	 * is true, and the register's [TypeRestriction] does not yet indicate
	 * it's known to be immutable, emit an instruction to make it so, updating
	 * the register's restriction.
	 */
	fun moveSlot(
		sourceSlot: Int,
		targetSlot: Int,
		makeImmutable: Boolean)
	{
		val restriction = slotRestriction(sourceSlot)
		currentSlotReads.values[targetSlot] = currentSlotReads[sourceSlot].value
		if (makeImmutable && !restriction.hasFlag(IMMUTABLE_FLAG))
		{
			val newRestriction = restriction.withFlag(IMMUTABLE_FLAG)
			+L2Simple_MakeImmutable(value = readSlot(sourceSlot))
			setSlotRestriction(sourceSlot, newRestriction)
		}
	}

	/**
	 * Write a zero into the slot-to-register mapping.  Register zero is always
	 * nil.
	 */
	fun nilSlot(slot: Int)
	{
		currentSlotReads.values[slot] = 0
	}

	/**
	 * A utility operation for emitting a move instruction.
 	 */
	fun move(
		source: Read,
		destination: Write)
	{
		+L2Simple_Move(from = source, to = destination)
	}

	/**
	 * A utility operation for emitting a (postponable) constant move
	 * instruction, answering a [Read] of the value.
	 */
	fun constant(value: A_BasicObject): Read
	{
		val temp = newRegister(restrictionForConstant(value))
		+L2Simple_MoveConstant(value = value as AvailObject, to = temp)
		return temp.read
	}


	// Translate L1 instructions

	override fun L1_doCall()
	{
		val bundle = code.literalAt(instructionDecoder.getOperand())
		val expectedType = code.literalAt(instructionDecoder.getOperand())
		val method = bundle.bundleMethod
		contingentValues.add(method)
		val numArgs = bundle.numArgs
		stackp += numArgs - 1
		val argRestrictions = (stackp downTo stackp - numArgs + 1)
			.map(::slotRestriction)
		val arguments = readSlots(stackp, stackp - numArgs + 1)
		val argTypes = argRestrictions.map(TypeRestriction::type)
		(stackp - numArgs  + 1.. stackp).forEach(::nilSlot)
		val answer = writeSlot(stackp, restrictionForType(expectedType))
		val possible = method.definitionsAtOrBelow(argRestrictions)
		val only = possible.singleOrNull()
		val stateOfL1 = StateOfL1(
			pc = pc,
			stackp = stackp,
			// Always exclude the return value, even if there are no zero args.
			liveSlots = liveIndices(stackp - max(numArgs, 1) + 1 .. stackp))
		if (only === null
			|| !only.isMethodDefinition()
			|| !only.bodySignature().acceptsListOfArgTypes(argTypes))
		{
			// Fall back to dynamic dispatch.
			generateGeneralCall(
				bundle = bundle,
				arguments = arguments,
				argRestrictions = argRestrictions,
				expectedType = expectedType,
				answer = answer,
				stateOfL1 = stateOfL1,
				superUnionType = bottom)
			return
		}
		val calledFunction = only.bodyBlock()
		val calledCode = calledFunction.code()
		// We now know the exact method definition that will be invoked.
		val primitive = calledCode.codePrimitive()
		var generated: Boolean = false
		if (primitive != null)
		{
			generated = primitive.run {
				attemptToGenerateSimpleInvocation(
					functionIfKnown = calledFunction,
					rawFunction = calledCode,
					functionRead = constant(calledFunction),
					expectedType = expectedType,
					args = arguments,
					argRestrictions = argRestrictions,
					stateOfL1 = stateOfL1,
					answer = answer)
			}
		}
		if (!generated)
		{
			// Nothing was generated, so fall back.
			generateGeneralInvocation(
				nilpotentAttempt = null,
				calledCode = calledCode,
				calledFunction = constant(calledFunction),
				arguments = arguments,
				argumentRestrictions = argRestrictions,
				expectedType = expectedType,
				stateOfL1 = stateOfL1,
				answer = answer)
		}
		if (expectedType.isBottom)
		{
			// The call definitely won't complete.  To make the register
			// coloring pass a bit easier, write nil into the
			// answer.  It's unreachable, but insignificant storage.
			+L2Simple_MoveConstant(value = nil, to = answer)
		}
	}

	override fun L1_doPushLiteral()
	{
		val value = code.literalAt(instructionDecoder.getOperand())
		+L2Simple_MoveConstant(
			value = value,
			to = writeSlot(--stackp, restrictionForConstant(value)))
	}

	override fun L1_doPushLastLocal()
	{
		val local = instructionDecoder.getOperand()
		moveSlot(local, --stackp, false)
		nilSlot(local)
	}

	override fun L1_doPushLocal()
	{
		val local = instructionDecoder.getOperand()
		moveSlot(local, --stackp, true)
	}

	override fun L1_doPushLastOuter()
	{
		val outer = instructionDecoder.getOperand()
		// At the moment we don't have a mechanism for preventing reording of
		// non-commutative operations like push-outer and push-last-outer, so we
		// always emit a push-outer for correctness.
		+L2Simple_PushOuter(
			outerNumber = outer,
			to = writeSlot(
				--stackp,
				restrictionForType(code.outerTypeAt(outer))))
	}

	override fun L1_doClose()
	{
		val numOuters = instructionDecoder.getOperand()
		val rawFunction = code.literalAt(instructionDecoder.getOperand())
		assert(rawFunction.numOuters == numOuters)
		val oldStackp = stackp
		stackp += numOuters - 1
		val outers = readSlots(stackp, oldStackp)
		(oldStackp..stackp).forEach(::nilSlot)
		val functionWrite = writeSlot(
			stackp,
			restrictionForType(rawFunction.functionType))
		createCloseFunction(
			code = rawFunction,
			outers = outers,
			out = functionWrite)
	}

	override fun L1_doSetLocal()
	{
		val variableSlot = instructionDecoder.getOperand()
		val variable = readSlot(variableSlot)
		val value = readSlot(stackp)
		nilSlot(stackp)
		stackp++
		+L2Simple_SetVariable(
			nextOffset = SKIP,
			reentryOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices()),
			variable = variable,
			value = value)
		+L2Simple_ReenterToResume(
			nextOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices()))
	}

	override fun L1_doGetLocalClearing()
	{
		val local = instructionDecoder.getOperand()
		--stackp
		val variable = readSlot(local)
		val variableRestriction = slotRestriction(local)
		val valueRestriction = restrictionForType(
			variableRestriction.type.readType)
		val answer = writeSlot(stackp, valueRestriction)
		+L2Simple_GetVariableClearing(
			nextOffset = SKIP,
			reentryOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices(stackp..stackp)),
			fromVariable = variable,
			answer = answer)
		+L2Simple_ReenterToResume(
			nextOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices(stackp..stackp)))
	}

	override fun L1_doPushOuter()
	{
		val outer = instructionDecoder.getOperand()
		val restriction = restrictionForType(code.outerTypeAt(outer))
		+L2Simple_PushOuter(
			outerNumber = outer,
			to = writeSlot(--stackp, restriction))
	}

	override fun L1_doPop()
	{
		// The value at stackp is being discarded.  If it has a pending
		// provenance, no observer ever sees it, so we drop the entry without
		// materializing it.
		nilSlot(stackp)
		stackp++
	}

	override fun L1_doGetLastOuter()
	{
		val outer = instructionDecoder.getOperand()
		val valueRestriction =
			restrictionForType(code.outerTypeAt(outer).readType)
		--stackp
		+L2Simple_GetOuter(
			nextOffset = SKIP,
			reentryOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices()),
			outerNumber = outer,
			answer = writeSlot(stackp, valueRestriction))
		+L2Simple_ReenterToResume(
			nextOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices()))
	}

	override fun L1_doSetOuter()
	{
		val outer = instructionDecoder.getOperand()
		val value = readSlot(stackp)
		setSlotRestriction(stackp, nilRestriction)
		+L2Simple_SetOuter(
			nextOffset = SKIP,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices()),
			reentryOffset = NEXT,
			outerNumber = outer,
			value = value)
		+L2Simple_ReenterToResume(
			nextOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices()))
		++stackp
	}

	override fun L1_doGetLocal()
	{
		val local = instructionDecoder.getOperand()
		val localType = slotRestriction(local).type
		--stackp
		+L2Simple_GetVariable(
			nextOffset = SKIP,
			reentryOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices()),
			fromVariable = readSlot(local),
			answer = writeSlot(
				stackp,
				restrictionForType(localType.readType)
					.withFlag(IMMUTABLE_FLAG)))
		+L2Simple_ReenterToResume(
			nextOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices()))
	}

	override fun L1_doMakeTuple()
	{
		val size = instructionDecoder.getOperand()
		val oldStackp = stackp
		stackp += size - 1
		val elements = readSlots(stackp, oldStackp)
		// Check if the previous `size` instructions were pushes of constants,
		// replacing them all with a new constant tuple push.
		val elementRestrictions =
			(stackp downTo oldStackp).map(::slotRestriction)
		for (i in oldStackp..stackp)
		{
			nilSlot(i)
		}
		val elementTypes = elementRestrictions.map(TypeRestriction::type)
		val answer = writeSlot(
			stackp,
			restrictionForType(tupleTypeForTypesList(elementTypes)))
		createMakeTuple(
			elements = elements,
			elementRestrictions = elementRestrictions,
			elementTypes = elementTypes,
			out = answer)

	}

	override fun L1_doGetOuter()
	{
		val outer = instructionDecoder.getOperand()
		val outerType = code.outerTypeAt(outer)
		--stackp
		+L2Simple_GetOuter(
			nextOffset = SKIP,
			reentryOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices()),
			outerNumber = outer,
			answer = writeSlot(
				stackp,
				restrictionForType(outerType.readType)
					.withFlag(IMMUTABLE_FLAG)))
		+L2Simple_ReenterToResume(
			nextOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices()))
	}

	override fun L1_doExtension()
	{
		throw AssertionError("Illegal dispatch nybblecode")
	}

	override fun L1Ext_doPushLabel()
	{
		// Defer the L2Simple_PushLabel emission.  The label's value-equality
		// guarantee (same continuation for any pushLabel within the same frame
		// invocation) means we can synthesize it later if a non-Restart/Exit
		// consumer forces materialization through [unaryPlus], OR skip the
		// construction entirely when a Restart/Exit primitive's override of
		// [attemptToGenerateSimpleInvocation] recognizes a local-frame label
		// and lowers the call into essentially a jump or return.
		// Note that the push-label instruction stays postponed until (/unless)
		// the value is needed along a path.  We capture pc-2 in the L1 frame
		// data, in case a debugger is in play.  It will then simply restore
		// the registers and restart the L1 pushLabel instruction.
		+L2Simple_ReifyForPushLabel(
			nextOffset = SKIP,
			stateOfL1 = StateOfL1(
				pc = pc - 2,
				stackp = stackp,
				liveSlots = liveIndices()),
			reentryOffset = NEXT)
		+L2Simple_ReenterToResume(
			nextOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc - 2,
				stackp = stackp,
				liveSlots = ReadArray.empty))
		--stackp
		val answer = writeSlot(
			stackp,
			restrictionForType(
				continuationTypeForFunctionType(code.functionType())))
		+L2Simple_PushLabel(
			nextOffset = NEXT,
			// Note that this is the state to capture during reification, not
			// the content of the label continuation itself.  We have to
			// capture the actual L1 state in frame slots, since the debugger
			// *can* switch to L1 when stepping past such a step.
			stateOfL1 = StateOfL1(
				pc = -999,
				stackp = -999,
				liveSlots = ReadArray((1..code.numArgs()).map(::readSlot))),
			answer = answer)
	}

	override fun L1Ext_doGetLiteral()
	{
		val variable = code.literalAt(instructionDecoder.getOperand())
		--stackp
		+L2Simple_GetConstant(
			nextOffset = SKIP,
			reentryOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveIndices()),
			variable = variable,
			answer = writeSlot(
				stackp,
				restrictionForType(variable.kind().readType)
					.withFlag(IMMUTABLE_FLAG)))
		+L2Simple_ReenterToResume(
			nextOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveIndices()))
	}

	override fun L1Ext_doSetLiteral()
	{
		val variable = code.literalAt(instructionDecoder.getOperand())
		val value = readSlot(stackp)
		nilSlot(stackp)
		stackp++
		+L2Simple_SetConstant(
			nextOffset = SKIP,
			reentryOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveSlots = liveIndices()),
			variable = variable,
			value = value)
		+L2Simple_ReenterToResume(
			nextOffset = NEXT,
			stateOfL1 = StateOfL1(
				pc = pc,
				stackp = stackp,
				liveIndices()))
	}

	override fun L1Ext_doDuplicate()
	{
		moveSlot(stackp, stackp - 1, true)
		--stackp
	}

	override fun L1Ext_doPermute()
	{
		val permutation = code.literalAt(instructionDecoder.getOperand())
		val size = permutation.tupleSize
		val earliestStackp = stackp + size - 1
		val originalSlotIndices = earliestStackp downTo stackp
		val permutedSlotIndices = IntArray(size)
		originalSlotIndices.forEachIndexed { zeroIndex, originalSlot ->
			permutedSlotIndices[permutation.tupleIntAt(zeroIndex + 1) - 1] =
				originalSlot
		}
		val permutedReads = permutedSlotIndices.map(::readSlot)
		for (i in 0 until size)
		{
			currentSlotReads.values[earliestStackp - i] = permutedReads[i].value
		}
	}

	override fun L1Ext_doSuperCall()
	{
		val bundle = code.literalAt(instructionDecoder.getOperand())
		val expectedType = code.literalAt(instructionDecoder.getOperand())
		val superUnionType = code.literalAt(instructionDecoder.getOperand())
		val method = bundle.bundleMethod
		contingentValues.add(method)
		val numArgs = bundle.numArgs
		stackp += numArgs - 1
		// Fall back to dynamic dispatch for now.
		val argRestrictions = (stackp downTo stackp - numArgs + 1)
			.map(::slotRestriction)
		val arguments = readSlots(stackp, stackp - numArgs + 1)
		(stackp - numArgs  + 1.. stackp).forEach(::nilSlot)
		val answer = writeSlot(
			stackp,
			restrictionForType(expectedType))
		val registerIndices = liveIndices(stackp - numArgs + 1 .. stackp)
		val stateOfL1 = StateOfL1(
			pc = pc,
			stackp = stackp,
			liveSlots = registerIndices)
		generateGeneralCall(
			bundle = bundle,
			arguments = arguments,
			argRestrictions = argRestrictions,
			expectedType = expectedType,
			answer = answer,
			stateOfL1 = stateOfL1,
			superUnionType = superUnionType)
	}

	override fun L1Ext_doSetLocalSlot()
	{
		val localSlot = instructionDecoder.getOperand()
		moveSlot(stackp,  localSlot, false)
		nilSlot(stackp)
		++stackp
	}

	companion object {
		/**
		 * Translate the code into an [L2SimpleChunk] and install it.
		 *
		 * Note the emoji in the name – this acts as an eye catcher in stack
		 * traces, indicating where to restart a chunk optimization either after
		 * a hot fix or to trace for understanding.
		 *
		 */
		fun `🌺translateToLevelTwoSimple`(
			code: A_RawFunction,
			optimizationLevel: OptimizationLevel,
			interpreter: Interpreter)
		{
			val before = captureNanos(interpreter)

			val translator = L2SimpleTranslator(
				code, optimizationLevel, interpreter)
			translator.naiveTranslateFromL1()
			translator.resolveLabels()
			translator.removeDeadCode()
			translator.colorRegisters()
			translator.removeDeadCode()
			val chunk = translator.createChunk()
			code.setStartingChunkAndReoptimizationCountdown(
				chunk, optimizationLevel.countdown)

			simpleTranslationStat.record(
				(captureNanos(interpreter) - before).toDouble(),
				interpreter.interpreterIndex)
		}

		/** Statistics for timing the translation per L1Operation. */
		private val simpleTranslationStat =
			Statistic(L2_OPTIMIZATION_TIME, "L2Simple translation")
	}
}
