/*
 * L2_VIRTUAL_SET_LOCAL_VARIABLE.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
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

package avail.interpreter.levelTwo.operation.variables

import avail.descriptor.functions.A_RegisterDump
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.unsupported
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operation.L2_MOVE_BOXED
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.Frame
import org.objectweb.asm.MethodVisitor

/**
 * Assign a value to a [variable][VariableDescriptor].  This is a placeholder
 * that facilitates code motion of reads and writes during the
 * [L2Optimizer::postponeConditionallyUsedValues](postponement) optimization.
 * That's also when variable elision takes place, exposing more opportunities
 * for optimizing call sites (e.g., polymorphic -> monomorphic) and folding
 * newly exposed constants.
 *
 * TODO replace with reference to Variable elision.md.
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
class L2_VIRTUAL_SET_LOCAL_VARIABLE
constructor(
	val frame: Frame,
	val index: Int,
	var variable: L2ReadBoxedOperand,
	var valueToWrite: L2ReadBoxedOperand,
	var variableOut: L2WriteBoxedOperand
): L2Instruction()
{
	override val isPlaceholder: Boolean get() = true

	override val shouldPostponeEvenIfLiveIn: Boolean get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(" Virtual set ↓")
		append(variable.registerString())
		append(" ← ")
		append(valueToWrite.registerString())
		append("  (var out = ")
		append(variableOut.registerString())
		append(")")
	}

	override fun forcePostponedTranslationNow(regenerator: L2Regenerator)
	{
		// Something is forcing this postponed set to be emitted.  If the
		// original variable has already been created, write to it, otherwise
		// create a variable that has the valueToWrite as its initial value.
		// I believe this transformation is safe, because any instructions that
		// did a get would have already been generated before getting to this
		// set, and the gets can't move through the control flow graph.
		val manifest = regenerator.currentManifest
		var semanticVariable = variable.semanticValue()
		if (manifest.hasSemanticValue(semanticVariable))
		{
			// The variable definitely exists already.  Emit the set.
			super.forcePostponedTranslationNow(regenerator)
			return
		}
		// Get and set instructions have both read and write operands for the
		// variables, and the L1 translation ensures they're used by exactly one
		// subsequent L1 instruction's translation, producing (the same)
		// variable for the next get or set instruction to use.  Therefore, we
		// can trace the chain back safely through postponed instructions until
		// we reach a non-postponed one or a postponed create instruction.
		val variablesToPopulate =
			variableOut.semanticValues().toMutableSet()
		while (true)
		{
			if (manifest.hasSemanticValue(semanticVariable))
			{
				// The variable definitely exists, so for simplicity and safety,
				// force-emit `this`, the postponed set, which also causes all
				// prior postponed sets and gets to be force-emitted.
				break
			}
			variablesToPopulate.add(semanticVariable)
			// The source of the variable is still postponed.  If the create is
			// still postponed, emit it now with valueToWrite as its initial
			// value.
			val sourceInstruction =
				manifest.postponedInstructions()[semanticVariable]!!
			when
			{
				sourceInstruction is L2_MOVE_BOXED ->
				{
					semanticVariable = sourceInstruction.source.semanticValue()
				}
				sourceInstruction is L2_VIRTUAL_SET_LOCAL_VARIABLE &&
					semanticVariable in
						sourceInstruction.variableOut.semanticValues() ->
				{
					// `this` is a set causally after an unused postponed set.
					// Continue tracing back with the hope of finding the create
					// is still postponed.
					semanticVariable =
						sourceInstruction.variable.semanticValue()
				}
				sourceInstruction is L2_GET_UNESCAPED_LOCAL_VARIABLE &&
					semanticVariable in
						sourceInstruction.variableOut.semanticValues() ->
				{
					// `this` is a set causally after an unused postponed set.
					// Continue tracing back with the hope of finding the create
					// is still postponed.
					semanticVariable =
						sourceInstruction.variable.semanticValue()
				}
				sourceInstruction is L2_CREATE_VARIABLE ->
				{
					// We've reached the original creation operation and it's
					// still postponed.  Output one that is pre-initialized to
					// `this`'s valueToWrite.
					assert(semanticVariable in
						sourceInstruction.variable.semanticValues())
					regenerator.forceTranslationForRead(
						valueToWrite.semanticValue())
					regenerator.addInstruction(
						L2_CREATE_VARIABLE(
							sourceInstruction.outerType,
							regenerator.boxedWrite(
								variablesToPopulate,
								sourceInstruction.variable.restriction()),
							regenerator.readBoxed(
								valueToWrite.semanticValue())))
					// For safety, remove the creation instruction, since it
					// should only be read by instructions that we also know can
					// only have a single use, leading to `this`.  We could also
					// eliminate the intermediate variable versions.
					manifest.removePostponedSourceInstruction(sourceInstruction)
					return
				}
				else -> break
			}
		}
		super.forcePostponedTranslationNow(regenerator)
	}

	override fun generateReplacement(
		regenerator: L2Regenerator,
		originalInstruction: L2Instruction)
	{
		regenerator.addInstruction(
			L2_SET_UNESCAPED_LOCAL_VARIABLE(
				variable,
				valueToWrite,
				variableOut))
	}

	override fun sourceOfMoveToRegister(
		destinationRegister: L2Register<*>
	): L2Register<*>?
	{
		assert(destinationRegister == variableOut.register())
		return variable.register()
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor
	) = unsupported
}
