/*
 * L2_CREATE_CONTINUATION.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationExceptFrameMethod
import avail.descriptor.functions.RegisterDumpDescriptor.Companion.storeFreezeOffsetMethod
import avail.descriptor.representation.A_RawFunction.Companion.declarationNamesWithoutOuters
import avail.descriptor.representation.A_RawFunction.Companion.numArgs
import avail.descriptor.representation.A_RawFunction.Companion.numLocals
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Tuple.Companion.tupleAt
import avail.descriptor.representation.A_Tuple.Companion.tupleSize
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.HideInSimpleVisualization
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FREEZE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operation.variables.L2_CREATE_VARIABLE
import avail.interpreter.levelTwo.operation.variables.L2_SET_ELIDABLE_LOCAL_VARIABLE
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.manifest.L2ValueManifest
import avail.utility.isNullOr
import org.objectweb.asm.Opcodes

/**
 * Create a continuation from scratch, using the specified caller, function,
 * constant level one program counter, constant stack pointer, continuation
 * slot values, and level two program counter.  Write the new continuation
 * into the specified register, then continue along [ifFallThrough].
 *
 * A frame slot whose local variable has not been created yet, because its
 * creation is still postponed here, holds [nil] in the new continuation.
 * The [freeze] edge then leads to a freeze subgraph, which creates those
 * variables and writes them into the frame if the continuation ever becomes
 * immutable or shared.  Control never flows along that edge, but the
 * postponed creations travel along it, and the registers live along it are
 * saved in room reserved for them in the [registerDump]
 * (doc/Optimization/Level_Two/Variable_elision.md §Freezing).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property function
 *   The function whose frame the continuation represents.
 * @property code
 *   The function's raw function.
 * @property caller
 *   The continuation's caller.
 * @property levelOnePc
 *   The L1 program counter at which the continuation resumes in L1.
 * @property levelOneStackp
 *   The L1 stack pointer at which the continuation resumes in L1.
 * @property slotValues
 *   The values of the continuation's frame slots.
 * @property destination
 *   Where to write the new continuation, along [ifFallThrough].
 * @property labelAddress
 *   The L2 offset at which the continuation resumes in this chunk.
 * @property registerDump
 *   The register dump created by the preceding [L2_SAVE_ALL_AND_PC_TO_INT].
 * @property ifFallThrough
 *   Where to continue after creating the continuation.
 * @property freeze
 *   Either empty, or a single edge to the freeze subgraph, which starts with
 *   an [L2_ENTER_FREEZE] and ends with an [L2_FINISH_FREEZE].  The edge is
 *   added by [emitTransformedInstruction] when a variable creation is still
 *   postponed here, and is never followed at runtime.
 * @property comment
 *   A description of why the continuation is created.
 */
class L2_CREATE_CONTINUATION
constructor (
	var function: L2ReadBoxedOperand,
	@HideInSimpleVisualization
	var code: L2ConstantOperand,
	var caller: L2ReadBoxedOperand,
	var levelOnePc: L2IntImmediateOperand,
	var levelOneStackp: L2IntImmediateOperand,
	var slotValues: L2ReadBoxedVectorOperand,
	@On(SUCCESS) var destination: L2WriteBoxedOperand,
	var labelAddress: L2ReadIntOperand,
	var registerDump: L2ReadBoxedOperand,
	@On(SUCCESS) var ifFallThrough: L2PcOperand,
	@On(FREEZE) var freeze: L2PcVectorOperand,
	var comment: L2CommentOperand
): L2ControlFlowInstruction()
{
	/**
	 * The instruction ends its block, so it must not be postponed like a pure
	 * computation of its [destination].
	 */
	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(destination)
		append(" ← $[")
		append(function)
		append("]\n\tpc=")
		append(levelOnePc.value)
		append("\n\tstack=[")
		val code = this@L2_CREATE_CONTINUATION.code.constant
		val slotNames = code.declarationNamesWithoutOuters
		// This range includes a primitive failure variable (first), if present.
		var localRangeStart = code.numArgs() + 1
		var primFailureIndex = -1
		if (code.codePrimitive() != null) primFailureIndex = localRangeStart++
		val localRange = localRangeStart .. code.numArgs() + code.numLocals
		slotValues.elements.forEachIndexed { zeroIndex, slot ->
			val slotIndex = zeroIndex + 1
			append(
				when (slotIndex == levelOneStackp.intValue)
				{
					true -> "\n\t->\t"
					else -> "\n\t\t"
				})
			append(slotIndex)
			if (slotIndex <= slotNames.tupleSize)
			{
				append(" ")
				when (slotIndex)
				{
					primFailureIndex -> append("[PrimFail] ")
					in localRange -> append("↑ ")
				}
				append(slotNames.tupleAt(slotIndex).asNativeString())
			}
			append(": ")
			append(slot.registerString())
		}
		append("]\n\tstackp=")
		append(levelOneStackp.value)
		append("\n\tcaller=")
		append(caller)
		renderOperandsExcludingFields(
			desiredOperandTypes,
			::comment,
			::destination,
			::function,
			::code,
			::slotValues,
			::levelOneStackp,
			::caller)
	}

	override fun instructionWasAdded(manifest: L2ValueManifest)
	{
		labelAddress.assertInt32(manifest)
		super.instructionWasAdded(manifest)
	}

	/**
	 * Emit this instruction, adding a [freeze] subgraph if it has none yet and
	 * some variable creation is still postponed here.  Each frame slot whose
	 * value is a variable whose creation (or the assignment that follows it)
	 * is still postponed here is written as [nil] instead, which keeps the
	 * creation postponed along [ifFallThrough].  If there are any such slots,
	 * the [freeze] edge leads to a new subgraph that reads those slots'
	 * variables, forcing their creation there, and writes them into the frame.
	 *
	 * Once the instruction has a freeze subgraph, a regeneration regenerates
	 * that subgraph along with the rest of the graph, since the creations it
	 * forces exist nowhere else on this path.  A creation that reaches this
	 * instruction only in a later regeneration is forced before it.
	 */
	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		val instruction = this@L2_CREATE_CONTINUATION
		val manifest = currentManifest
		if (!manifest.caresAboutSemanticValues || freeze.edges.isNotEmpty())
		{
			+instruction
			return
		}
		val originalSlots = slotValues.elements
		val slotsToFreeze = originalSlots.indices
			.filter { manifest.isPostponedVariable(originalSlots[it]) }
			.map { it + 1 }
		val freezeBlock = when
		{
			slotsToFreeze.isEmpty() -> null
			else ->
			{
				val freezingZone = L2ControlFlowGraph.Zone(
					L2ControlFlowGraph.ZoneType.FREEZING,
					"Freezing")
				createBasicBlock(
					"Freeze elided variables", freezingZone, isCold = true)
			}
		}
		if (freezeBlock === null)
		{
			+instruction
			return
		}
		val nilRead = boxedConstant(nil)
		slotValues = L2ReadBoxedVectorOperand(
			originalSlots.mapIndexed { zeroIndex, read ->
				when (zeroIndex + 1)
				{
					in slotsToFreeze -> nilRead
					else -> read
				}
			})
		freeze = L2PcVectorOperand(listOf(edgeTo(freezeBlock)))
		+instruction
		if (freezeBlock.predecessorEdges().isEmpty()) return
		startBlock(freezeBlock)
		+L2_ENTER_FREEZE()
		val variables = slotsToFreeze.map { slot ->
			readBoxed(originalSlots[slot - 1].semanticValue())
		}
		+L2_FINISH_FREEZE(
			L2ArbitraryConstantOperand(slotsToFreeze.toIntArray()),
			L2ReadBoxedVectorOperand(variables))
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: continuation = createContinuationExceptFrame(
		// ::    function,
		// ::    caller,
		// ::    registerDump
		// ::    levelOnePC,
		// ::    levelOneStackp,
		// ::    interpreter.chunk,
		// ::    onRampOffset);
		load(function)
		load(caller)
		load(registerDump)
		intConstant(levelOnePc.intValue)
		intConstant(levelOneStackp.intValue)
		loadInterpreter()
		load(Interpreter.chunkField)
		load(labelAddress)
		generateCall(createContinuationExceptFrameMethod)
		// Skip slots that are always nil, since the continuation was already
		// initialized with nils.
		// :: [continuation]
		frameAtPutAll(
			slotValues.elements
				.mapIndexed { zeroIndex, read -> zeroIndex + 1 to read }
				.filter { (_, read) ->
					read.register().constant.isNullOr { notNil }
				})
		freeze.edges.singleOrNull()?.let { edge ->
			// Save the registers that the freeze subgraph needs into the room
			// that the register dump reserved for them, and record where the
			// freeze subgraph starts.
			// :: [continuation, registerDump]
			load(registerDump)
			intConstant(edge.offset())
			generateCall(storeFreezeOffsetMethod)
			saveRegistersIntoDump(freezeDumpLayouts[edge.offset()]!!)
			method.visitInsn(Opcodes.POP)
			// :: [continuation]
		}
		store(destination.register())
		// :: []
		jumpOrFallThrough(ifFallThrough)
	}

	companion object
	{
		/**
		 * Answer whether the given read is of a variable whose creation, or
		 * whose assignment after its creation, is still postponed in this
		 * manifest.
		 *
		 * @receiver
		 *   The [L2ValueManifest] where the read would happen.
		 * @param read
		 *   The read of a frame slot.
		 * @return
		 *   Whether the variable has not been created yet.
		 */
		private fun L2ValueManifest.isPostponedVariable(
			read: L2ReadBoxedOperand
		): Boolean
		{
			val semanticValue = read.semanticValue()
			if (hasLiveSemanticValue(semanticValue, BOXED_KIND)) return false
			return when (postponedInstructionFor(semanticValue, BOXED_KIND))
			{
				is L2_CREATE_VARIABLE -> true
				is L2_SET_ELIDABLE_LOCAL_VARIABLE -> true
				else -> false
			}
		}
	}
}
