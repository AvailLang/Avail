/*
 * L2_VIRTUAL_SET_VARIABLE.kt
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

import avail.descriptor.functions.A_RegisterDump
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.unsupported
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor

/**
 * Assign a value to a [variable][VariableDescriptor].  This is a placeholder
 * that facilitates code motion of reads and writes during the
 * [L2Optimizer::postponeConditionallyUsedValues](postponement) optimization.
 * That's also when variable elision takes place, exposing more opportunities
 * for optimizing call sites (e.g., polymorphic -> monomorphic) and folding
 * newly exposed constants.
 *
 * During postponement, we look for transformations that help us avoid some
 * variable operations.  Here's a table of some, with this notation:
 *
 * Create x := y   -> Create a local in register x and initialize it to y.
 * Set x := y      -> Set variable x to y.
 * Flush x := y    -> Force variable x to be set to y.  This virtual instruction
 *                    can't move past certain others that Set can.
 * FlushAllBarrier -> Prevent any Set instructions from moving past this,
 *                    although a Create can get through.
 * y := Get x      -> Read variable x into register y.
 * Check x         -> Ensure variable x still has no reactors/shared, otherwise
 *                    fall out to L1.  The virtual form of this just assumes it
 *                    will be successful.
 * CallUnsafe      -> Invoke a function that might make an already escaped
 *                    variable become shared or have a reactor.
 * CallSafe        -> Invoke a primitive that can't make an escaped variable
 *                    become shared or have a reactor.
 * SaveAll         -> Save all live registers into an [A_RegisterDump], which is
 *                    made available to a subsequent [L2_CREATE_CONTINUATION].
 *                    It also records which variables are currently elided, and
 *                    records in the dump any information for the continuation
 *                    to be able to construct them if it it becomes immutable or
 *                    shared.
 * Return          -> Return from this function.
 *
 * ┌─────────────────┬───────────────────┐
 * │ Pattern         │ Transformation    │
 * ├─────────────────┼───────────────────┤
 * │ Create x := y   │ Create x := z     │
 * │ Set x := z      │                   │
 * ├─────────────────┼───────────────────┤
 * │ Create x := y   │ Move z ← y        │
 * │ z ← Get x       │ Create x := y     │
 * ├─────────────────┼───────────────────┤
 * │ Set x := y      │ Set x := z        │
 * │ Set x := z      │                   │
 * ├─────────────────┼───────────────────┤
 * │ Set x := y      │ Move z ← y        │
 * │ z ← Get x       │ Set x := y        │
 * ├─────────────────┼───────────────────┤
 * │ Set x := y      │ (eliminate Set)   │
 * │  with no later  │                   │
 * │  Get or Check   │                   │
 * ├─────────────────┼───────────────────┤
 * │ Create x := y   │ (eliminate Create)│
 * │  with no later  │                   │
 * │  Get or Check   │                   │
 * ├─────────────────┼───────────────────┤
 * │ Create x := y   │ Move z ← y        │
 * │ z ← Get x       │ Create x := y     │
 * ├─────────────────┼───────────────────┤
 * │ Create x := y   │ Create x := y     │
 * │ Check x         │                   │
 * ├─────────────────┼───────────────────┤
 * │ Create x := y   │ CallSafe ..y..    │
 * │ CallSafe ..y..  │ Create x := y     │
 * ├─────────────────┼───────────────────┤
 * │ Create x := y   │ CallUnsafe ..x..  │
 * │ CallUnsafe ..x..│ Check x           │
 * │                 │ Create x := y     │
 * ├─────────────────┼───────────────────┤
 * │ Create x := y   │ CallUnsafe ..y..  │
 * │ CallUnsafe ..y..│ Create x := y     │
 * ├─────────────────┼───────────────────┤
 * │ Create x := y   │ Create x := y     │
 * │ Arbitrary call  │                   │
 * │  (****)         │ Create x := y     │
 * │ Check x         │ Create x := y     │
 * └─────────────────┴───────────────────┘
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_VIRTUAL_SET_VARIABLE(
	var variable: L2ReadBoxedOperand,
	var valueToWrite: L2ReadBoxedOperand
): L2ControlFlowInstruction()
{
	override val isPlaceholder: Boolean get() = true

	override val hasSideEffect get() = true

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		builder.append(" Virtual set ↓")
		builder.append(variable.registerString())
		builder.append(" ← ")
		builder.append(valueToWrite.registerString())
	}

	override fun generateReplacement(
		regenerator: L2Regenerator,
		originalInstruction: L2Instruction)
	{
		TODO()
		super.generateReplacement(regenerator, originalInstruction)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor
	) = unsupported
}
