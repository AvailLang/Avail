/*
 * L2Inliner.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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
package avail.optimizer.reoptimizer

import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.methodName
import avail.descriptor.representation.A_RawFunction.Companion.startingChunk
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Type.Companion.argsTupleType
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.returnType
import avail.descriptor.representation.A_Type.Companion.typeAtIndex
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.interpreter.levelTwo.HiddenVariable
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_CONTINUATION
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_FUNCTION
import avail.interpreter.levelTwo.HiddenVariable.LATEST_RETURN_VALUE
import avail.interpreter.levelTwo.HiddenVariable.STACK_REIFIER
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2JVMChunk
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadMixedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.L2_ADD_UNDER_FROZEN_FRAME
import avail.interpreter.levelTwo.operation.L2_CREATE_CONTINUATION
import avail.interpreter.levelTwo.operation.L2_CREATE_L1_CONTINUATION
import avail.interpreter.levelTwo.operation.L2_CREATE_LABEL
import avail.interpreter.levelTwo.operation.L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO
import avail.interpreter.levelTwo.operation.L2_ENTER_FREEZE
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK_FOR_CALL
import avail.interpreter.levelTwo.operation.L2_EXIT_FREEZE
import avail.interpreter.levelTwo.operation.L2_FALL_BACK_TO_L1
import avail.interpreter.levelTwo.operation.L2_GET_CURRENT_CONTINUATION
import avail.interpreter.levelTwo.operation.L2_GET_LATEST_RETURN_VALUE
import avail.interpreter.levelTwo.operation.L2_GET_RETURNING_FUNCTION
import avail.interpreter.levelTwo.operation.L2_INLINED_FALL_BACK_TO_L1
import avail.interpreter.levelTwo.operation.L2_INVOKE
import avail.interpreter.levelTwo.operation.L2_INVOKE_CONSTANT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_ALREADY_REIFIED
import avail.interpreter.levelTwo.operation.L2_LOOKUP_BY_VALUES
import avail.interpreter.levelTwo.operation.L2_MOVE_OUTER_VARIABLE
import avail.interpreter.levelTwo.operation.L2_REIFY_IF_INTERRUPT
import avail.interpreter.levelTwo.operation.L2_RESTART_CONTINUATION_WITH_ARGUMENTS
import avail.interpreter.levelTwo.operation.L2_RETURN
import avail.interpreter.levelTwo.operation.L2_RETURN_FROM_REIFICATION_HANDLER
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.interpreter.levelTwo.operation.L2_STRIP_MANIFEST
import avail.interpreter.levelTwo.operation.L2_TRY_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_UPDATE_SLOTS
import avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL
import avail.interpreter.primitive.Primitive.Flag
import avail.optimizer.CallSiteHelper
import avail.optimizer.CallSiteHelper.JunctionType.ReificationNoCheck
import avail.optimizer.CallSiteHelper.JunctionType.ReificationUnreturnable
import avail.optimizer.CallSiteHelper.JunctionType.ReificationWithCheck
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.L1Translator
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2Generator
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.OptimizationLevel
import avail.optimizer.OptimizationPhase
import avail.optimizer.PostPhiData
import avail.optimizer.reoptimizer.L2Inliner.Companion.canEmbed
import avail.optimizer.reoptimizer.L2Inliner.Companion.inlinableChunkFor
import avail.optimizer.reoptimizer.L2Inliner.Companion.maxInlinedInstructions
import avail.optimizer.values.Frame
import avail.optimizer.values.L2SemanticCaller
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast
import avail.utility.mapToSet
import java.util.IdentityHashMap

/**
 * An [L2Regenerator] that embeds a callee's [L2JVMChunk.inlinableGraph] at a
 * call site of the graph being generated, so that the call costs no
 * invocation, and the caller's knowledge of the arguments flows into the
 * callee's code.
 *
 * [L1Translator] offers each call site whose raw function is known to
 * [tryToInline], at an [OptimizationLevel] that
 * [inlines&#32;callees][OptimizationLevel.inlinesCallees].  The callee's graph
 * was kept when its own chunk was built, at
 * [OptimizationPhase.CAPTURE_INLINABLE_GRAPH], in SSA form and keyed by
 * semantic value.
 *
 * Every [L2SemanticValue] of the callee's graph belongs to the callee's
 * [Frame]s.  The inliner moves them into [inlinedFrame], a new frame whose
 * outer frame is the caller's frame at the call site, so they stay distinct
 * from the caller's values, and from those of any other embedding of the same
 * callee.  Before regenerating each instruction of the callee's graph, it
 * makes a copy whose operands and edge manifests already use the inlined
 * frame ([L2Instruction.sourceCopyWithOperands]), then regenerates that copy
 * exactly as any regeneration would.  The callee's [PostPhiData] is
 * translated the same way.  The callee's graph itself is only read, so
 * several translations may embed it at once.
 *
 * A few instructions describe the callee's own invocation, and are replaced:
 * * [L2_ENTER_L2_CHUNK_FOR_CALL] binds the arguments of the call site to the
 *   callee's argument slots, and the function being invoked to the callee's
 *   function, if it captures it.  The callee's outers then come from where
 *   the caller made the function (see [L2_MOVE_OUTER_VARIABLE]).
 * * [L2_STRIP_MANIFEST], which isolates a loop's back edge, only moves its
 *   inputs to its outputs, since an inlined callee has no loop.
 * * [L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO] disappears; the callee is
 *   not being invoked.
 * * [L2_REIFY_IF_INTERRUPT] disappears, along with its reification path.  A
 *   callee without a loop runs for a bounded time, and the caller checks for
 *   interrupts at its own entry and loops.
 * * [L2_FALL_BACK_TO_L1] becomes an [L2_INLINED_FALL_BACK_TO_L1], which
 *   continues the callee in L1, then either continues like a return or, if
 *   the L1 code reifies, reifies the caller's frame through the call site's
 *   reification path.
 * * [L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION], which always reifies after
 *   stacking the callee's frame, gains an edge to the call site's
 *   reification path, which stacks the caller's frame beneath it.
 * * [L2_RETURN] moves the result into the [inlinedFrame]'s
 *   [result][Frame.result] and jumps to the code after the call.
 * * [L2_EXIT_FREEZE] becomes a jump to the call site's [freezeBlock].
 *
 * The callee may make calls of its own, and reify when they do.  A
 * reification builds one continuation for the whole chunk, that of the
 * innermost frame, whose register dump holds every live register, the
 * caller's included.  Its caller is the [rootFrame]'s reified caller, since
 * the callee's outermost reified caller is translated to that.  Every
 * [L2_CREATE_CONTINUATION] of the callee gets a freeze subgraph, and each of
 * those ends at the [freezeBlock], which builds the caller's frame as an L1
 * continuation and slides it beneath the continuation being frozen
 * (doc/Optimization/Level_Two/Variable_elision.md
 * §Freezing.Embedded frames).  A callee that embeds callees of its own was
 * built the same way, so its freeze subgraphs build their callers' frames
 * before reaching its exits, which build this caller's frame in turn.
 *
 * A callee that runs a loop, creates a label, restarts, or catches exceptions
 * is not embedded (see [inlinableChunkFor]).
 *
 * @property calleeChunk
 *   The callee's [L2JVMChunk], whose [L2JVMChunk.inlinableGraph] is embedded.
 * @property inlinedFrame
 *   The [Frame] that the callee's values belong to once embedded.
 * @property function
 *   The function being invoked, as read in the caller.
 * @property arguments
 *   The arguments of the call, as read in the caller.
 *
 * @constructor
 * Create an inliner that embeds the [calleeGraph] at the current position of
 * the [targetGenerator].
 *
 * @param targetGenerator
 *   The [L2Generator] of the caller's graph.
 */
class L2Inliner private constructor(
	targetGenerator: L2Generator,
	private val calleeChunk: L2JVMChunk,
	private val inlinedFrame: Frame,
	private val function: L2ReadBoxedOperand,
	private val arguments: List<L2ReadBoxedOperand>,
	private val callSiteHelper: CallSiteHelper
) : L2Regenerator(targetGenerator, BySemanticValue)
{
	/** The callee's [L2JVMChunk.inlinableGraph], which is embedded. */
	private val calleeGraph: L2ControlFlowGraph =
		calleeChunk.inlinableGraph!!

	/**
	 * The junction of the [callSiteHelper] where the caller's frame is
	 * reified for this call, as it would be if a real call of the callee
	 * reified.  Which junction depends on whether the callee's result must be
	 * checked, as in [L1Translator.generateGeneralFunctionInvocation].
	 */
	private val reificationJunction: L2BasicBlock by lazy {
		val resultType = function.type.returnType
		callSiteHelper[
			when
			{
				resultType.isVacuousType -> ReificationUnreturnable
				resultType.isSubtypeOf(callSiteHelper.expectedType) ->
					ReificationNoCheck
				else -> ReificationWithCheck
			}]
	}

	/**
	 * Answer a new cold block from which the caller's frame is reified for
	 * this call, after the callee's frame has been stacked for reification
	 * some other way.  Each use gets its own block, since an entry point
	 * must be the target of a single edge.  Generate its content with
	 * [generateReificationEntry] once the edge to it exists.
	 *
	 * @return
	 *   The new, empty [L2BasicBlock].
	 */
	private fun newReificationEntry(): L2BasicBlock = createBasicBlock(
		"reify caller of inlined ${inlinedFrame.codeName}",
		reificationJunction.zone,
		isCold = true)

	/**
	 * Generate the content of a block made by [newReificationEntry]: the
	 * entry point that the reifying frame is resumed at, then a jump to the
	 * [reificationJunction], exactly as after a real call of the callee
	 * reified.
	 *
	 * @param block
	 *   The block to start and fill.
	 */
	private fun generateReificationEntry(block: L2BasicBlock)
	{
		startBlock(block)
		if (!currentlyReachable()) return
		+L2_ENTER_L2_CHUNK(
			L2IntImmediateOperand(DefaultEntryPoint.TRANSIENT.offset),
			L2CommentOperand("Transient - cannot be invalid."))
		jumpTo(reificationJunction)
	}

	/**
	 * Each [Frame] of the callee's graph, mapped to the frame that replaces
	 * it.  Frames compare by identity, so each must be translated exactly
	 * once.
	 */
	private val frameMap = IdentityHashMap<Frame, Frame>()

	/** Each [L2SemanticValue] of the callee's graph, translated. */
	private val valueMap = mutableMapOf<L2SemanticValue, L2SemanticValue>()

	/** Each block's translated [PostPhiData], or `null` if it had none. */
	private val postPhiDataMap = IdentityHashMap<L2BasicBlock, PostPhiData?>()

	/** The block that each of the callee's returns jumps to. */
	private val returnBlock = createBasicBlock(
		"after inlined ${inlinedFrame.codeName}")

	/**
	 * The block that each of the callee's freeze subgraphs jumps to in place
	 * of its [L2_EXIT_FREEZE].  It builds the caller's frame, stopped at this
	 * call, and slides it beneath the frames built so far (see
	 * [L1Translator.generateFreezeOfCaller]).
	 */
	private val freezeBlock = createBasicBlock(
		"freeze caller of inlined ${inlinedFrame.codeName}",
		L2ControlFlowGraph.Zone(
			L2ControlFlowGraph.ZoneType.FREEZING,
			"Freezing caller of inlined ${inlinedFrame.codeName}"),
		isCold = true)

	/**
	 * The outermost [Frame] of the chunk being generated, whose
	 * [reified&#32;caller][Frame.reifiedCaller] is the continuation that any
	 * reification within the chunk builds upon.
	 */
	private val rootFrame =
		generateSequence(inlinedFrame, Frame::outerFrame).last()

	/**
	 * Every [L2_CREATE_CONTINUATION] of the callee gets a freeze subgraph,
	 * since freezing its continuation must also build the caller's frame.
	 */
	override val freezesBuildEnclosingFrames get() = true

	/**
	 * End a freeze subgraph of the callee by jumping to the [freezeBlock],
	 * which builds the caller's frame.
	 */
	override fun exitFreeze()
	{
		jumpTo(freezeBlock)
	}

	/** Translate a [Frame] of the callee's graph. */
	private fun mapFrame(frame: Frame): Frame =
		frameMap.getOrPut(frame) { frame.transform(inlinedFrame, ::mapFrame) }

	/**
	 * Translate an [L2SemanticValue] of the callee's graph.  The
	 * [reified&#32;caller][Frame.reifiedCaller] of the callee's outermost frame
	 * occurs only where the callee reifies, as the caller of the one
	 * continuation that a reification builds for the whole chunk.  Once
	 * embedded, that is the reified caller of the [rootFrame].
	 */
	private fun mapValue(value: L2SemanticValue): L2SemanticValue =
		valueMap.getOrPut(value) {
			when
			{
				value is L2SemanticCaller && value.frame.outerFrame === null ->
					rootFrame.reifiedCaller()
				else -> value.transform(::mapValue, ::mapFrame)
			}
		}

	/**
	 * The callee's prologue and restart blocks are loop heads in its own
	 * graph, but an embedded callee has no back edge (see
	 * [inlinableChunkFor]), and a loop head would forget what the caller
	 * knows about the values that reach it.
	 */
	override fun isLoopHead(sourceBlock: L2BasicBlock): Boolean = false

	override fun postPhiDataOf(sourceBlock: L2BasicBlock): PostPhiData?
	{
		if (sourceBlock !in postPhiDataMap)
		{
			postPhiDataMap[sourceBlock] =
				sourceBlock.postPhiData?.transform(::mapValue)
		}
		return postPhiDataMap[sourceBlock]
	}

	/**
	 * Copy each operand of a callee instruction with its semantic values, and
	 * its edges' manifests, translated into the [inlinedFrame].  An edge
	 * still leads to its block in the callee's graph, which [mapBlock]
	 * translates during regeneration.
	 */
	private val operandMapper = object : L2OperandDispatcher
	{
		/** The operand being translated, replaced by its translation. */
		var operand: L2Operand? = null

		/** Translate any operand. */
		fun <O : L2Operand> map(original: O): O
		{
			operand = original
			original.dispatchOperand(this)
			return operand!!.cast()
		}

		override fun doOperand(operand: L2CommentOperand) = Unit

		override fun doOperand(operand: L2ConstantOperand) = Unit

		override fun doOperand(operand: L2IntImmediateOperand) = Unit

		override fun doOperand(operand: L2FloatImmediateOperand) = Unit

		override fun doOperand(operand: L2ArbitraryConstantOperand<*>) = Unit

		override fun doOperand(operand: L2PcOperand)
		{
			this.operand = L2PcOperand(
				operand.targetBlock(),
				operand.isBackward,
				operand.manifest().transform(::mapValue),
				operand.optionalName)
		}

		override fun doOperand(operand: L2ReadIntOperand)
		{
			this.operand = L2ReadIntOperand(mapValue(operand.semanticValue()))
		}

		override fun doOperand(operand: L2ReadFloatOperand)
		{
			this.operand =
				L2ReadFloatOperand(mapValue(operand.semanticValue()))
		}

		override fun doOperand(operand: L2ReadBoxedOperand)
		{
			this.operand =
				L2ReadBoxedOperand(mapValue(operand.semanticValue()))
		}

		override fun doOperand(operand: L2ReadBoxedVectorOperand)
		{
			this.operand = L2ReadBoxedVectorOperand(
				operand.elements.map { map(it) })
		}

		override fun doOperand(operand: L2ReadIntVectorOperand)
		{
			this.operand = L2ReadIntVectorOperand(
				operand.elements.map { map(it) })
		}

		override fun doOperand(operand: L2ReadFloatVectorOperand)
		{
			this.operand = L2ReadFloatVectorOperand(
				operand.elements.map { map(it) })
		}

		override fun doOperand(operand: L2ReadMixedVectorOperand)
		{
			this.operand = L2ReadMixedVectorOperand(
				operand.elements.map { map<L2ReadOperand<*>>(it) })
		}

		override fun doOperand(operand: L2WriteIntOperand)
		{
			this.operand = L2WriteIntOperand(
				operand.semanticValues().mapToSet { mapValue(it) },
				operand.restriction())
		}

		override fun doOperand(operand: L2WriteFloatOperand)
		{
			this.operand = L2WriteFloatOperand(
				operand.semanticValues().mapToSet { mapValue(it) },
				operand.restriction())
		}

		override fun doOperand(operand: L2WriteBoxedOperand)
		{
			this.operand = L2WriteBoxedOperand(
				operand.semanticValues().mapToSet { mapValue(it) },
				operand.restriction())
		}

		override fun doOperand(operand: L2WriteBoxedVectorOperand)
		{
			this.operand = L2WriteBoxedVectorOperand(
				operand.elements.map { map(it) })
		}

		override fun doOperand(operand: L2PcVectorOperand)
		{
			this.operand = L2PcVectorOperand(operand.edges.map { map(it) })
		}
	}

	override fun processInstruction(sourceInstruction: L2Instruction)
	{
		when (sourceInstruction)
		{
			is L2_ENTER_L2_CHUNK_FOR_CALL ->
			{
				sourceInstruction.writeArguments.elements
					.zip(arguments)
					.forEach { (write, argument) ->
						bind(argument, write)
					}
				sourceInstruction.writeFunction.elements.forEach { write ->
					bind(function, write)
				}
			}
			is L2_STRIP_MANIFEST ->
				sourceInstruction.inputs.elements
					.zip(sourceInstruction.outputs.elements)
					.forEach { (input, output) ->
						move(
							translateRead(input).semanticValue(),
							output.semanticValues().map(::mapValue))
					}
			is L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO -> Unit
			is L2_REIFY_IF_INTERRUPT ->
				jumpTo(mapBlock(sourceInstruction.ifNotInterrupt.targetBlock()))
			is L2_RETURN ->
			{
				move(
					translateRead(sourceInstruction.returnValue)
						.semanticValue(),
					listOf(inlinedFrame.result()))
				jumpTo(returnBlock)
			}
			is L2_FALL_BACK_TO_L1 ->
			{
				val fallBack: L2_FALL_BACK_TO_L1 = basicTransformInstruction(
					sourceInstruction.sourceCopyWithOperands {
						operandMapper.map(it)
					}.cast())
				val resultType = inlinedFrame.code.functionType().returnType
				val reificationEntry = newReificationEntry()
				+L2_INLINED_FALL_BACK_TO_L1(
					fallBack.function,
					fallBack.pc,
					fallBack.stackp,
					fallBack.frameValues,
					boxedWrite(
						inlinedFrame.result(),
						restrictionForType(
							if (resultType.isBottom) Types.ANY()
							else resultType)),
					edgeTo(returnBlock),
					edgeTo(reificationEntry))
				generateReificationEntry(reificationEntry)
			}
			is L2_EXIT_FREEZE -> exitFreeze()
			// A lookup that falls back is charged to the callee's chunk as
			// well, since its graph is where the dispatch came from.
			is L2_LOOKUP_BY_VALUES ->
				embed(sourceInstruction.sourceCopyInlinedFrom(calleeChunk))
			// A report with a reification edge is in a callee's own embedded
			// callee, and already reifies the callee's frame, from which a
			// freeze builds the rest.  It is embedded as it is.
			is L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION
				if sourceInstruction.onReification.edges.isEmpty() ->
			{
				val report: L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION =
					basicTransformInstruction(
						sourceInstruction.sourceCopyWithOperands {
							operandMapper.map(it)
						}.cast())
				val reificationEntry = newReificationEntry()
				+L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION(
					report.function,
					report.returningFunction,
					report.returnedValue,
					report.expectedType,
					report.pc,
					report.stackp,
					report.frameValues,
					L2PcVectorOperand(listOf(edgeTo(reificationEntry))))
				generateReificationEntry(reificationEntry)
			}
			else -> embed(sourceInstruction)
		}
	}

	/**
	 * Regenerate an instruction of the callee's graph as it is, with its
	 * semantic values and edge manifests translated into the [inlinedFrame].
	 *
	 * @param sourceInstruction
	 *   The [L2Instruction] of the callee's graph.
	 */
	private fun embed(sourceInstruction: L2Instruction)
	{
		val copy = sourceInstruction.sourceCopyWithOperands {
			operandMapper.map(it)
		}
		with(copy) {
			if (populateFromSourceInstructionIfPossible()) return
		}
		basicTransformInstruction(copy)
			.cloneFor(this)
			.run { emitTransformedInstruction() }
	}

	/**
	 * Translate a read of the callee's graph into the [inlinedFrame], then
	 * into a read of an equivalent value that is live here, as regenerating
	 * an instruction would.
	 *
	 * @param read
	 *   A read in the callee's graph.
	 * @return
	 *   The corresponding read at the current position.
	 */
	private fun translateRead(read: L2ReadBoxedOperand): L2ReadBoxedOperand =
		transformOperand(operandMapper.map(read))

	/**
	 * Make the translated semantic values of the callee's [write] synonyms of
	 * the caller's [read], keeping the stronger of their restrictions.  The
	 * callee's restriction holds as well, since its code relies on it and the
	 * call site satisfies it.
	 *
	 * @param read
	 *   The caller's value.
	 * @param write
	 *   The callee's write that the value takes the place of.
	 */
	private fun bind(read: L2ReadBoxedOperand, write: L2WriteBoxedOperand)
	{
		val targets = write.semanticValues().map(::mapValue)
		move(read.semanticValue(), targets)
		currentManifest.updateRestriction(targets.first()) {
			intersection(write.restriction())
		}
	}

	/**
	 * Embed the [calleeGraph] at the current position, then hand its result
	 * to the [callSiteHelper], as a call would.  The block that enters the
	 * embedded code says so in its [debugNote][L2BasicBlock.debugNote], so
	 * a visualized graph shows where each callee was embedded.
	 */
	private fun inline()
	{
		val entry = createBasicBlock("inlined ${inlinedFrame.codeName}")
		entry.debugNote.appendLine("INLINING: ${inlinedFrame.codeName}")
		jumpTo(entry)
		processSourceGraph(calleeGraph, emptyMap(), entry)
		startBlock(freezeBlock)
		if (currentlyReachable())
		{
			callSiteHelper.translator.generateFreezeOfCaller(
				callSiteHelper.expectedType)
		}
		startBlock(returnBlock)
		if (currentlyReachable())
		{
			callSiteHelper.useAnswer(readBoxed(inlinedFrame.result()))
		}
	}

	companion object
	{
		/**
		 * The most instructions that an embedded callee's graph may have.
		 */
		private const val maxInlinedInstructions = 100

		/**
		 * The [HiddenVariable]s that describe the running frame: its
		 * function, its continuation, and the machinery of calls and
		 * reification.  An instruction that touches one of them is tied to
		 * the callee's own invocation.
		 */
		private val frameHiddenVariablesMask =
			CURRENT_CONTINUATION.mask or
				CURRENT_FUNCTION.mask or
				LATEST_RETURN_VALUE.mask or
				STACK_REIFIER.mask

		/**
		 * The [Flag]s of a primitive that needs the frame running it, so that
		 * an [L2_RUN_INFALLIBLE_PRIMITIVE] of it cannot be embedded.
		 */
		private val frameSensitivePrimitiveFlags = listOf(
			Flag.CanSuspend,
			Flag.Invokes,
			Flag.CanSwitchContinuations,
			Flag.AlwaysSwitchesContinuation,
			Flag.CatchException,
			Flag.Unknown)

		/**
		 * Answer whether the given instruction, reachable in a callee's graph
		 * other than through its interrupt check, can be embedded in a
		 * caller.  The instructions of the callee's own invocation are
		 * replaced (see [L2Inliner]).  Calls, and the reification and
		 * freezing of the callee's frame when a call reifies, are embedded.
		 * Any other instruction that reifies, or refers to the running frame,
		 * disqualifies the callee: label creation, restarts, the callee's own
		 * primitive, and primitives that need the frame running them.
		 *
		 * @param instruction
		 *   The [L2Instruction] to check.
		 * @return
		 *   Whether the instruction can be embedded.
		 */
		private fun canEmbed(instruction: L2Instruction): Boolean =
			when (instruction)
			{
				is L2_ENTER_L2_CHUNK_FOR_CALL,
				is L2_MOVE_OUTER_VARIABLE,
				is L2_STRIP_MANIFEST,
				is L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO,
				is L2_REIFY_IF_INTERRUPT,
				is L2_RETURN,
				is L2_FALL_BACK_TO_L1,
				is L2_INLINED_FALL_BACK_TO_L1,
				is L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION,
				// A call, and the reification of the callee's frame when the
				// call reifies, which builds the chunk's one continuation, and
				// the frames around it only when that continuation freezes.
				is L2_INVOKE,
				is L2_INVOKE_CONSTANT_FUNCTION,
				is L2_GET_LATEST_RETURN_VALUE,
				is L2_GET_RETURNING_FUNCTION,
				is L2_ENTER_L2_CHUNK,
				is L2_SAVE_ALL_AND_PC_TO_INT,
				is L2_GET_CURRENT_CONTINUATION,
				is L2_CREATE_CONTINUATION,
				is L2_RETURN_FROM_REIFICATION_HANDLER,
				is L2_ENTER_FREEZE,
				is L2_UPDATE_SLOTS,
				is L2_CREATE_L1_CONTINUATION,
				is L2_ADD_UNDER_FROZEN_FRAME,
				is L2_EXIT_FREEZE -> true
				is L2_VIRTUAL_CREATE_LABEL,
				is L2_CREATE_LABEL,
				is L2_RESTART_CONTINUATION_WITH_ARGUMENTS,
				is L2_JUMP_IF_ALREADY_REIFIED,
				is L2_TRY_PRIMITIVE -> false
				is L2_RUN_INFALLIBLE_PRIMITIVE ->
					frameSensitivePrimitiveFlags.none(
						instruction.primitive.constant::hasFlag)
				else -> !instruction.isEntryPoint
					&& ((instruction.readsHiddenVariablesMask
						or instruction.writesHiddenVariablesMask)
						and frameHiddenVariablesMask) == 0
			}

		/**
		 * Answer the graph to embed for a call of the given raw function
		 * from the given frame, or `null` if it should not be embedded.  The
		 * function must not be a primitive, must have a valid
		 * [L2JVMChunk] that kept its [L2JVMChunk.inlinableGraph], and must
		 * not already be running in an enclosing frame.  Every instruction
		 * reachable without an interrupt must be [embeddable][canEmbed], no
		 * edge may lead backward (so the callee has no loop), and there may
		 * be at most [maxInlinedInstructions] of them.
		 *
		 * A function with no such chunk is never embedded.  It has run too
		 * rarely to have been optimized, compared to the caller, so
		 * embedding it would not pay.
		 *
		 * @param rawFunction
		 *   The [A_RawFunction] being called.
		 * @param callerFrame
		 *   The [Frame] making the call.
		 * @return
		 *   The chunk whose graph to embed, or `null`.
		 */
		private fun inlinableChunkFor(
			rawFunction: A_RawFunction,
			callerFrame: Frame
		): L2JVMChunk?
		{
			if (rawFunction.codePrimitive() !== null) return null
			val chunk = rawFunction.startingChunk as? L2JVMChunk ?: return null
			if (!chunk.isValid) return null
			val graph = chunk.inlinableGraph ?: return null
			var frame: Frame? = callerFrame
			while (frame !== null)
			{
				if (frame.code.equals(rawFunction)) return null
				frame = frame.outerFrame
			}
			val reached = mutableSetOf<L2BasicBlock>()
			val toVisit = mutableListOf(graph.basicBlockOrder[0])
			var count = 0
			while (toVisit.isNotEmpty())
			{
				val block = toVisit.removeLast()
				if (!reached.add(block)) continue
				for (instruction in block.instructions())
				{
					if (!canEmbed(instruction)) return null
					count++
					val edges = when (instruction)
					{
						is L2_REIFY_IF_INTERRUPT ->
							listOf(instruction.ifNotInterrupt)
						else -> instruction.targetEdges
					}
					for (edge in edges)
					{
						if (edge.isBackward) return null
						toVisit.add(edge.targetBlock())
					}
				}
			}
			if (count > maxInlinedInstructions) return null
			return chunk
		}

		/**
		 * Embed the callee at this call site, if it is suitable (see
		 * [inlinableChunkFor]).  If so, generate its code, record the
		 * callee's dependencies as the caller's, hand the result to the
		 * [callSiteHelper], and answer `true`.  Otherwise generate nothing
		 * and answer `false`.
		 *
		 * @param translator
		 *   The [L1Translator] generating the caller.
		 * @param rawFunction
		 *   The [A_RawFunction] being called.
		 * @param function
		 *   The function being called, as read in the caller.
		 * @param arguments
		 *   The arguments of the call, as read in the caller.
		 * @param callSiteHelper
		 *   The [CallSiteHelper] of the call site.
		 * @return
		 *   Whether the callee was embedded.
		 */
		fun tryToInline(
			translator: L1Translator,
			rawFunction: A_RawFunction,
			function: L2ReadBoxedOperand,
			arguments: List<L2ReadBoxedOperand>,
			callSiteHelper: CallSiteHelper
		): Boolean = translator.run {
			val chunk = inlinableChunkFor(rawFunction, topFrame)
				?: return false
			val argumentTypes = rawFunction.functionType().argsTupleType
			arguments.forEachIndexed { zeroIndex, argument ->
				val parameterType = argumentTypes.typeAtIndex(zeroIndex + 1)
				if (!argument.type.isSubtypeOf(parameterType)) return false
			}
			val codeName = rawFunction.methodName.asNativeString()
			val inlinedFrame = Frame(topFrame, rawFunction, pc, codeName)
			for (value in chunk.contingentValues)
			{
				addContingentValue(value)
			}
			L2Inliner(
				generator,
				chunk,
				inlinedFrame,
				function,
				arguments,
				callSiteHelper
			).inline()
			true
		}
	}
}
