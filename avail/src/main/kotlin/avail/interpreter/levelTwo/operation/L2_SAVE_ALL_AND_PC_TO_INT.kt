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

import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_RegisterDump
import avail.descriptor.functions.ContinuationRegisterDumpDescriptor
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.REFERENCED_AS_INT
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadMixedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticValue
import org.objectweb.asm.MethodVisitor

/**
 * Extract the given "reference" edge's target level two offset as an [Int],
 * then follow the fall-through edge.  The int value will be used in the
 * fall-through code to assemble a continuation, which, when returned into, will
 * start at the reference edge target.  Note that the L2 offset of the reference
 * edge is not known until just before JVM code generation.
 *
 * This is a special operation, in that during final JVM code generation it
 * saves all objects in a register dump ([ContinuationRegisterDumpDescriptor]),
 * and the [L2_ENTER_L2_CHUNK] at the reference target will restore them.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property preserveOnReferenceEdge
 *   The boxed values to capture in the register dump, and restore if / when /
 *   each time the [reference]'s target (starting with an [L2_ENTER_L2_CHUNK] is
 *   resumed.
 * @property dirtyLocals
 *   Mixed vector holding the current dirty values to be written into fresh
 *   variables if/when the continuation becomes immutable or shared.
 * @property dirtyLocalIndices
 *   The one-based local variable indices for which to get initialization values
 *   from the boxed, unboxed int, and unboxed float vectors, in that order, when
 *   creating local variables due to the continuation becoming immutable or
 *   shared.  Unmentioned local variables are initialized to nil (unassigned).
 * @property reference
 *   Where control flow will resume when the reified continuation resumes, if it
 *   hasn't been invalidated in the meanwhile.  The actual offset [Int]
 *   associated with this edge's target is separately recorded in [l2Address]
 *   for use in creating a continuation.
 * @property l2Address
 *   The [Int] version of [reference].  This is used later when constructing the
 *   actual [A_Continuation], written to the [A_Continuation.levelTwoOffset], so
 *   that when the continuation resumes it knows what L2 offset to jump to.
 * @property registerDump
 *   Where to write an [A_RegisterDump] of all live register values.
 * @property ifFallThrough
 */
class L2_SAVE_ALL_AND_PC_TO_INT
constructor(
	var preserveOnReferenceEdge: L2ReadBoxedVectorOperand,
	@On(REFERENCED_AS_INT) var reference: L2PcOperand,
	@On(SUCCESS) var l2Address: L2WriteIntOperand,
	@On(SUCCESS) var registerDump: L2WriteBoxedOperand,
	@On(SUCCESS) var ifFallThrough: L2PcOperand,
	var dirtyLocals: L2ReadMixedVectorOperand,
	val dirtyLocalIndices: IntArray
): L2Instruction()
{
	override val targetEdges: List<L2PcOperand> get() = layout.pcOperands(this)

	override val hasSideEffect get() = true

	override val altersControlFlow get() = true

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		builder.append(' ')
		builder.append(l2Address)
		builder.append(" ← address of label $[")
		builder.append(reference.targetBlock().name())
		builder.append("]")
		if (reference.offset() != -1)
		{
			builder.append("(=").append(reference.offset()).append(")")
		}
		builder.append(",\n\tdump registers ")
		builder.append(registerDump)
		val sources = dirtyLocals.elements
		when
		{
			sources.isEmpty() && dirtyLocalIndices.isEmpty() -> { }
			sources.size == dirtyLocalIndices.size ->
			{
				dirtyLocalIndices.zip(sources).joinTo(
					builder, ",\n\t\t", ",\n\tDirties:\n\t\t"
				) { (localIndex, source) -> "local#$localIndex = $source" }
			}
			else ->
			{
				warningStyleChange(true)
				builder.append("\n\tMismatched dirty locals:\n\t\t")
				builder.append(dirtyLocalIndices)
				builder.append("\n\t\t")
				builder.append(sources)
				warningStyleChange(false)
			}
		}
	}

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		// A backward `reference` edge is strictly for creating a label.
		val strippedManifest: L2ValueManifest
		if (reference.isBackward)
		{
			// Now only the `reference` edge has to be processed.  Restrict the
			// manifest to those entities mentioned in `preserveOnReferenceEdge`.
			strippedManifest = L2ValueManifest(manifest)
			val semanticValuesToKeep = mutableSetOf<L2SemanticValue<*>>()
			val registersToKeep = mutableSetOf<L2Register<*>>()
			preserveOnReferenceEdge.elements.forEach { read ->
				semanticValuesToKeep.add(read.semanticValue())
				registersToKeep.add(read.register())
			}
			strippedManifest.clearPostponedInstructions()
			strippedManifest.retainSemanticValues(semanticValuesToKeep)
			strippedManifest.retainRegisters(registersToKeep)
			// Indicate on the edge that these values are all that should be
			// visible.
			reference.forcedClampedEntities =
				(semanticValuesToKeep + registersToKeep).toSet()
		}
		else
		{
			// For forward edges, ignore `preserveOnReferenceEdge`, or more
			// precisely, make sure it's empty.
			assert(preserveOnReferenceEdge.elements.isEmpty())
			strippedManifest = manifest
		}
		// Note: We process `reference` with the strippedManifest.
		reference.instructionWasAdded(strippedManifest)
		preserveOnReferenceEdge.instructionWasAdded(manifest)
		l2Address.instructionWasAdded(manifest)
		registerDump.instructionWasAdded(manifest)
		ifFallThrough.instructionWasAdded(manifest)
		dirtyLocals.instructionWasAdded(manifest)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		reference.createAndPushRegisterDump(translator, method)
		// :: [registerDump]
		translator.store(method, registerDump.register())
		// :: []
		translator.intConstant(method, reference.offset())
		translator.store(method, l2Address.register())

		// Jump is usually elided.
		translator.jumpOrFallThrough(method, ifFallThrough)
	}
}
