/*
 * L2_STRIP_MANIFEST.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.values.L2SemanticValue;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR;

/**
 * This is a helper operation which produces no JVM code, but is useful to limit
 * which {@link L2Register}s and {@link L2SemanticValue}s are live when reaching
 * a back-edge.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_STRIP_MANIFEST
extends L2Operation
{
	/**
	 * Construct an {@code L2_STRIP_MANIFEST}, which will produce no code during
	 * final translation to JVM bytecodes.
	 */
	private L2_STRIP_MANIFEST ()
	{
		super(
			READ_BOXED_VECTOR.is("live values"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_STRIP_MANIFEST instance =
		new L2_STRIP_MANIFEST();

	@Override
	public boolean hasSideEffect ()
	{
		// Prevent this instruction from being removed, because it constrains
		// the manifest along a back-edge, even after optimization.
		return true;
	}

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		final L2ReadBoxedVectorOperand liveVector = instruction.operand(0);

		// Clear the manifest, other than the mentioned semantic values and
		// registers.
		final Set<L2SemanticValue> liveSemanticValues = new HashSet<>();
		final Set<L2Register> liveRegisters = new HashSet<>();
		for (final L2ReadBoxedOperand read : liveVector.elements())
		{
			liveSemanticValues.add(read.semanticValue());
			liveRegisters.add(read.register());
		}
		manifest.retainSemanticValues(liveSemanticValues);
		manifest.retainRegisters(liveRegisters);

		liveVector.instructionWasAdded(manifest);
	}

	@Override
	public void updateManifest (
		final L2Instruction instruction,
		final L2ValueManifest manifest,
		final @Nullable Purpose optionalPurpose)
	{
		assert this == instruction.operation();
		// Update the given manifest with the effect of this instruction.  This
		// is an {@code L2_STRIP_MANIFEST}, so it doesn't alter control flow, so
		// it must not have a {@link Purpose} specified.
		assert optionalPurpose == null;

		final L2ReadBoxedVectorOperand liveVector = instruction.operand(0);

		// Clear the manifest, other than the mentioned semantic values and
		// registers.
		final Set<L2SemanticValue> liveSemanticValues = new HashSet<>();
		final Set<L2Register> liveRegisters = new HashSet<>();
		for (final L2ReadBoxedOperand read : liveVector.elements())
		{
			liveSemanticValues.add(read.semanticValue());
			liveRegisters.add(read.register());
		}
		manifest.retainSemanticValues(liveSemanticValues);
		manifest.retainRegisters(liveRegisters);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		// No effect.
	}
}
