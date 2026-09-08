/*
 * L2ControlFlowInstruction.kt
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

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.optimizer.L2BasicBlock
import avail.optimizer.manifest.L2ValueManifest
import avail.utility.mapToSet

/**
 * An [L2Instruction] that alters control flow, and therefore does not fall
 * through to the next instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *   Protect the constructor so the subclasses can maintain a fly-weight
 *   pattern (or arguably a singleton).
 */
abstract class L2ControlFlowInstruction : L2Instruction()
{
	override val altersControlFlow get() = true

	/**
	 * Extract the operands which are [L2PcOperand]s.  These are what lead to
	 * other [L2BasicBlock]s.  They also carry an edge-specific array of slots,
	 * and edge-specific [TypeRestriction]s for registers.
	 *
	 * @return
	 *   The [List] of target [L2PcOperand]s that are operands of the given
	 *   instruction.  These may be reachable directly via a control flow
	 *   change, or reachable only from some other mechanism like continuation
	 *   reification and later resumption of a continuation.
	 */
	override val targetEdges: List<L2PcOperand> get() = layout.pcOperands(this)

	/**
	 * This instruction was just added to its [L2BasicBlock].
	 *
	 * @param manifest
	 *   The [L2ValueManifest] that is active where this instruction was just
	 *   added to its [L2BasicBlock].
	 */
	override fun justAdded(manifest: L2ValueManifest)
	{
		operands.forEach { it.setInstruction(this) }
		if (manifest.hasEliminatedPhis)
		{
			// Remove register definitions for any registers that are about to
			// be overwritten by this instruction.
			val registersToBeOverwritten =
				writeOperands.mapToSet { it.register() }
			manifest.removeRegisters(registersToBeOverwritten)
		}
		instructionWasAdded(manifest)
		// The instruction may have restrictions set on its reads and writes
		// that are stronger than what's in the manifest.  Force the manifest to
		// be as accurate as possible.
		readOperands.forEach { read ->
			manifest.updateRestriction(read.semanticValue()) {
				read.restriction()
			}
		}
		val manifestByPurpose = mutableMapOf<Purpose?, L2ValueManifest>()
		edgesAndPurposesDo { edge, purpose ->
			manifestByPurpose[purpose] = edge.manifest()
		}
		writesAndPurposesDo { write, purpose ->
			purpose?.let {
				manifestByPurpose[purpose]!!
					.updateRestriction(write.pickSemanticValue()) {
						write.restriction()
					}
			}
		}
		basicBlock().postPhiMap = manifest.extractPostPhiMap()
		writeOperands.forEach { write ->
			manifest.updateRestriction(write.pickSemanticValue()) {
				write.restriction()
			}
		}
		// All manifests have now been updated, including propagation for
		// related semantic values.  Narrow the restrictions for my reads and
		// writes.  Phi instructions are excluded: their read operands each
		// belong to a specific incoming edge's manifest (not the merged
		// currentManifest), and their write restriction was already set
		// correctly by populateOneSynonym as the union of incoming restrictions.
		// Using the (stale, pre-merge) currentManifest here would incorrectly
		// intersect those restrictions down to bottom.
		val allManifests = manifestByPurpose.values + manifest
		readOperands.forEach { read ->
			// The outbound edges may vary in how they've deduced a stronger
			// restriction for the semantic value being read here, so only
			// strengthen the read up to the *union* of what the manifests
			// have recorded.
			val union = allManifests
				.map { it.restrictionFor(read) }
				.reduce(TypeRestriction::union)
			read.restrict { union }
		}
		writesAndPurposesDo { write, purpose ->
			if (purpose == null)
			{
				val intersection = allManifests
					.map { it.restrictionFor(write.pickSemanticValue()) }
					.reduce(TypeRestriction::intersection)
				write.restrict { intersection }
			}
			else
			{
				// The write is associated with only one edge, so use the
				// manifest that was just updated on that edge.
				write.restrict {
					manifestByPurpose[purpose]!!
						.restrictionFor(write.pickSemanticValue())
				}
			}
		}
	}
}
