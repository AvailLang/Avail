/**
 * interpreter/levelTwo/L2CodeGenerator.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.interpreter.levelTwo;

import static java.lang.Math.max;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.instruction.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2CodeGenerator} emits {@linkplain L2Instruction Level Two Avail
 * instructions} on behalf of the {@linkplain L2Translator translator} to
 * produce a {@linkplain L2ChunkDescriptor Level Two Avail chunk} from a
 * {@linkplain CompiledCodeDescriptor compiled Level One code object}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2CodeGenerator
{
	/**
	 * The {@linkplain AvailObject literals} that will be embedded into the
	 * created {@linkplain L2ChunkDescriptor chunk}.
	 */
	private final @NotNull List<AvailObject> literals =
		new ArrayList<AvailObject>(20);

	/**
	 * Emit the specified {@linkplain AvailObject literal} into the instruction
	 * stream.
	 *
	 * @param literal A {@linkplain AvailObject literal}.
	 */
	public void emitLiteral (final @NotNull AvailObject literal)
	{
		literal.readBarrierFault();
		assert !literal.descriptor().isMutable();
		int index = literals.indexOf(literal) + 1;
		if (index == 0)
		{
			literals.add(literal);
			index = literals.size();
		}
		emitWord(index);
	}

	/**
	 * {@linkplain List Lists} of indices corresponding to {@linkplain
	 * L2RegisterIdentity register identities} and grouped by {@linkplain
	 * L2RegisterVector register vector}.
	 */
	private final @NotNull List<List<Integer>> vectors =
		new ArrayList<List<Integer>>(20);

	/**
	 * Emit the {@linkplain L2RegisterIdentity identities} of the {@linkplain
	 * L2ObjectRegister members} of the specified {@linkplain L2RegisterVector
	 * register vector} into the instruction stream.
	 *
	 * @param registerVector A {@linkplain L2RegisterVector register vector}.
	 */
	public void emitVector (final @NotNull L2RegisterVector registerVector)
	{
		List<L2ObjectRegister> registersList = registerVector.registers();
		List<Integer> registerIndices =
			new ArrayList<Integer>(registersList.size());
		for (int i = 0; i < registersList.size(); i++)
		{
			registerIndices.add(registersList.get(i).identity().finalIndex());
		}
		int vectorIndex = vectors.indexOf(registerIndices) + 1;
		if (vectorIndex == 0)
		{
			vectors.add(registerIndices);
			vectorIndex = vectors.size();
		}
		emitWord(vectorIndex);
	}

	/**
	 * The number of {@linkplain L2ObjectRegister object registers} emitted
	 * thus far for the {@linkplain L2ChunkDescriptor chunk} undergoing
	 * {@linkplain L2CodeGenerator code generation}.
	 */
	private int objectRegisterCount = 0;

	/**
	 * Emit the {@linkplain L2RegisterIdentity identity} of the specified
	 * {@linkplain L2ObjectRegister object register} into the instruction
	 * stream.
	 *
	 * @param objectRegister An {@linkplain L2ObjectRegister object register}.
	 */
	public void emitObjectRegister (
		final @NotNull L2ObjectRegister objectRegister)
	{
		final int index = objectRegister.identity().finalIndex();
		if (index != -1)
		{
			objectRegisterCount = max(objectRegisterCount, index);
		}
		emitWord(index);
	}

	/**
	 * The number of {@linkplain L2IntegerRegister integer registers} emitted
	 * thus far for the {@linkplain L2ChunkDescriptor chunk} undergoing
	 * {@linkplain L2CodeGenerator code generation}.
	 */
	private int integerRegisterCount = 0;

	/**
	 * Emit the {@linkplain L2RegisterIdentity identity} of the specified
	 * {@linkplain L2IntegerRegister integer register} into the instruction
	 * stream.
	 *
	 * @param integerRegister
	 *        An {@linkplain L2IntegerRegister integer register}.
	 */
	public void emitIntegerRegister (
		final @NotNull L2IntegerRegister integerRegister)
	{
		final int index = integerRegister.identity().finalIndex();
		if (index != -1)
		{
			integerRegisterCount = max(integerRegisterCount, index);
		}
		emitWord(index);
	}

	/**
	 * The number of {@linkplain L2FloatRegister float registers} emitted thus
	 * far for the {@linkplain L2ChunkDescriptor chunk} undergoing {@linkplain
	 * L2CodeGenerator code generation}.
	 */
	private int floatRegisterCount = 0;

	/**
	 * Emit the {@linkplain L2RegisterIdentity identity} of the specified
	 * {@linkplain L2FloatRegister float register} into the instruction stream.
	 *
	 * @param floatRegister An {@linkplain L2FloatRegister float register}.
	 */
	public void emitFloatRegister (final @NotNull L2FloatRegister floatRegister)
	{
		final int index = floatRegister.identity().finalIndex();
		if (index != -1)
		{
			floatRegisterCount = max(floatRegisterCount, index);
		}
		emitWord(index);
	}

	/**
	 * A {@linkplain SetDescriptor set} of {@linkplain
	 * ImplementationSetDescriptor implementation sets} upon which the
	 * {@linkplain L2ChunkDescriptor chunk} is dependent.
	 */
	private @NotNull AvailObject contingentImpSets = SetDescriptor.empty();

	/**
	 * Merge the specified {@linkplain SetDescriptor set} of {@linkplain
	 * ImplementationSetDescriptor implementation sets} with those already
	 * upon which the {@linkplain L2ChunkDescriptor chunk} undergoing code
	 * generation is already dependent.
	 *
	 * @param aSetOfImpSets
	 *        A {@linkplain SetDescriptor set} of {@linkplain
	 *        ImplementationSetDescriptor implementation sets}.
	 */
	public void addContingentImplementationSets (
		final @NotNull AvailObject aSetOfImpSets)
	{
		contingentImpSets = contingentImpSets.setUnionCanDestroy(
			aSetOfImpSets, true);
	}

	/**
	 * The instruction stream emitted thus far of the {@linkplain
	 * L2ChunkDescriptor chunk} undergoing {@linkplain L2CodeGenerator code
	 * generation}.
	 */
	private @NotNull List<Integer> wordcodes =
		new ArrayList<Integer>(20);

	/**
	 * Emit the specified wordcode into the instruction stream.
	 *
	 * @param wordcode A wordcode.
	 */
	public void emitWord (final int wordcode)
	{
		assert wordcode >= -0x8000 && wordcode <= 0x7FFF
			: "Word is out of range";
		wordcodes.add(wordcode);
	}

	/**
	 * Emit the specified {@linkplain L2Instruction instructions}. Use a
	 * two-pass algorithm: the first pass measures instruction lengths to
	 * correctly determine their offsets (to ensure that references to
	 * {@linkplain L2LabelInstruction labels} will be correct), the second pass
	 * generates the real instruction stream.
	 *
	 * @param instructions
	 *        The {@linkplain L2Instruction instructions} that should be
	 *        emitted.
	 */
	public void setInstructions (
		final @NotNull List<L2Instruction> instructions)
	{
		// Generate the instructions, but be prepared to discard the generated
		// wordcodes. The wordcodes are generated as useless side-effect on this
		// pass, as the main intent is to measure instruction lengths to set
		// their offsets (so references to labels will be correct).
		assert wordcodes.size() == 0;
		for (L2Instruction instruction : instructions)
		{
			instruction.setOffset(wordcodes.size() + 1);
			instruction.emitOn(this);
		}

		// Now that the instruction positions are known, discard the scratch
		// code generator and generate on a real code generator. This is a
		// trivial two-pass scheme to calculate jumps.
		wordcodes = new ArrayList<Integer>(wordcodes.size());
		for (L2Instruction instruction : instructions)
		{
			assert instruction.offset() == wordcodes.size() + 1
				: "Instruction offset is not right";
			instruction.emitOn(this);
		}
	}

	/**
	 * Create a {@linkplain L2ChunkDescriptor chunk} that represents the Level
	 * Two translation of the specified {@linkplain CompiledCodeDescriptor
	 * compiled Level One Avail code}.
	 *
	 * <p>This should be invoked after {@link #setInstructions(List)
	 * setInstructions} has caused all {@linkplain L2Instruction instructions}
	 * to be emitted.</p>
	 *
	 * @param code
	 *        The {@linkplain CompiledCodeDescriptor compiled Level One Avail
	 *        code} currently undergoing translation to Level Two.
	 * @return The translated {@linkplain L2ChunkDescriptor chunk}.
	 */
	public @NotNull AvailObject createChunkFor (final @NotNull AvailObject code)
	{
		return L2ChunkDescriptor
			.allocateIndexCodeLiteralsVectorsNumObjectsNumIntegersNumFloatsWordcodesContingentImpSets(
				true,
				code,
				literals,
				vectors,
				objectRegisterCount,
				integerRegisterCount,
				floatRegisterCount,
				wordcodes,
				contingentImpSets);
	}
}
