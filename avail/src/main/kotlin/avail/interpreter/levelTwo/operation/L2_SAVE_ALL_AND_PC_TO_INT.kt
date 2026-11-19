/*
 * L2_SAVE_ALL_AND_PC_TO_INT.kt
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

import avail.descriptor.functions.RegisterDumpDescriptor
import avail.descriptor.representation.A_Continuation
import avail.descriptor.representation.A_RegisterDump
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.REFERENCED_AS_INT
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.REENTRY_FROM_REIFIED_CALL
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.L2SemanticDummy
import avail.optimizer.values.L2SemanticValue

/**
 * Extract the given "reference" edge's target level two offset as an [Int],
 * then follow the fall-through edge.  The int value will be used in the
 * fall-through code to assemble a continuation, which, when returned into, will
 * start at the reference edge target.  Note that the L2 offset of the reference
 * edge is not known until just before JVM code generation.
 *
 * This is a special operation, in that during final JVM code generation it
 * saves all live register values in a register dump ([RegisterDumpDescriptor]),
 * and the [L2_ENTER_L2_CHUNK] at the reference target will restore them into
 * the same registers.  If the [L2_CREATE_CONTINUATION] that uses the dump has
 * a [freeze][L2_CREATE_CONTINUATION.freeze] edge, the dump also has room
 * reserved after those registers, which that instruction fills with the
 * registers its freeze subgraph needs
 * (doc/Optimization/Level_Two/Variable_elision.md §Freezing).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property ifFallThrough
 *   Where to unconditionally jump after this instruction.
 * @property reference
 *   Where control flow will resume when the reified continuation resumes, if it
 *   hasn't been invalidated in the meanwhile.  The actual offset [Int]
 *   associated with this edge's target is separately recorded in
 *   [referenceOffset] for use in creating a continuation.
 * @property referenceOffset
 *   The [Int] version of [reference].  This is used later when constructing the
 *   actual [A_Continuation], written to the [A_Continuation.levelTwoOffset], so
 *   that when the continuation resumes it knows what L2 offset to jump to.
 * @property registerDump
 *   Where to write an [A_RegisterDump] of all live register values.
 * @property finalSavedBoxedRegisters
 *   During insertion of [L2_MAKE_IMMUTABLE] instructions, this gets populated
 *   by [processForMakeImmutable] with reads of the boxed registers that will be
 *   saved into the [registerDump], based on which boxed values are live along
 *   the [reference] edge.
 */
class L2_SAVE_ALL_AND_PC_TO_INT
constructor(
	@On(SUCCESS) var ifFallThrough: L2PcOperand,
	@On(REFERENCED_AS_INT) var reference: L2PcOperand,
	@On(SUCCESS) var referenceOffset: L2WriteIntOperand,
	@On(SUCCESS) var registerDump: L2WriteBoxedOperand,
	var finalSavedBoxedRegisters: L2ReadBoxedVectorOperand
): L2ControlFlowInstruction()
{
	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(referenceOffset)
		append(" ← offset of label $[")
		append(reference.targetBlock().name())
		append("]")
		if (reference.offset() != -1)
		{
			append("(=").append(reference.offset()).append(")")
		}
		append(",\n\tdump registers ")
		append(registerDump)
	}

	/**
	 * Postponed instructions travel along both edges.  Along the [reference]
	 * edge they stay postponed until the resumed code needs them.  Along the
	 * [ifFallThrough] edge, the [L2_CREATE_CONTINUATION] that builds the
	 * continuation keeps the postponed creation of a variable out of the main
	 * path, moving it into its freeze subgraph instead.  A backward
	 * [reference] edge, used to create a label, carries nothing.
	 */
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		val manifestAlongReference = L2ValueManifest(manifest).apply {
			if (reference.isBackward)
			{
				// A backward `reference` edge is strictly for creating a label.
				// Restrict the 'reference' edge's manifest to those entities
				// mentioned in `preserveOnReferenceEdge`.
				clearPostponedInstructions()
				retainSemanticValues(emptySet())
				retainRegisters(emptySet())
				// Indicate on the edge that these values are all that should be
				// visible.
				reference.forcedClampedRegisters = emptySet()
				reference.forcedClampedSemanticValues = emptySet()
			}
		}
		val fallThroughManifest = L2ValueManifest(manifest)
		referenceOffset.instructionWasAdded(fallThroughManifest)
		registerDump.instructionWasAdded(fallThroughManifest)
		ifFallThrough.instructionWasAdded(fallThroughManifest)
		reference.instructionWasAdded(manifestAlongReference)
	}

	override fun replaceConstantReads(
		generator: L2GeneratorInterface,
		registerToValueMap: MutableMap<L2Register<*>, L2SemanticValue>)
	{
		// Don't replace any saved registers with constants, since there would
		// be nowhere to restore them to (they don't occupy JVM locals).
	}

	/**
	 * Use the [reference] edge's [L2PcOperand.liveness] to populate this
	 * instruction's [finalSavedBoxedRegisters].  This happens very late during
	 * optimization, as part of insertion of the [L2_MAKE_IMMUTABLE]
	 * instructions.
	 */
	override fun processForMakeImmutable(
		firstUses: MutableMap<L2BoxedRegister, Pair<Int, L2ReadBoxedOperand>>,
		insertions: MutableList<Pair<Int, L2ReadBoxedOperand>>,
		mutables: MutableSet<L2BoxedRegister>,
		uniqueGenerator: ()->Int)
	{
		assert(finalSavedBoxedRegisters.elements.isEmpty())
		val boxedRegisters = reference.liveness!!.registers
			.filterIsInstance<L2BoxedRegister>()
		if (boxedRegisters.isEmpty()) return
		val referenceManifest = reference.manifest()
		val reads = boxedRegisters.map { register ->
			val synonyms = referenceManifest.synonymsForRegister(register)
			val semanticValue = when
			{
				synonyms.isNotEmpty() -> synonyms.first().pickSemanticValue()
				else ->
				{
					// Register is live but has no synonym in manifest (common at
					// this late stage when dataflow is register-based). Create a
					// dummy semantic value to represent it.
					L2SemanticDummy.create("Live register: $register")
				}
			}
			val read = L2ReadBoxedOperand(semanticValue, register)
			register.addUse(read)
			read
		}
		finalSavedBoxedRegisters = L2ReadBoxedVectorOperand(reads)
		finalSavedBoxedRegisters.setInstruction(this)
		sourceRegisters.addAll(boxedRegisters)
		super.processForMakeImmutable(
			firstUses, insertions, mutables, uniqueGenerator)
	}

	override fun JVMTranslator.translateToJVM()
	{
		reference.run {
			createAndPushRegisterDump(REENTRY_FROM_REIFIED_CALL)
		}
		// :: [registerDump]
		store(registerDump.register())
		// :: []
		intConstant(reference.offset())
		store(referenceOffset.register())

		// Jump is usually elided.
		jumpOrFallThrough(ifFallThrough)
	}
}
