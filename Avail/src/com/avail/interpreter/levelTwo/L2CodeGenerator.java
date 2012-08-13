/**
 * L2CodeGenerator.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import java.util.logging.*;
import com.avail.descriptor.*;
import com.avail.optimizer.L2Translator;
import com.avail.interpreter.levelTwo.operation.L2_LABEL;
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
	/** A {@linkplain Logger logger}. */
	private static final Logger logger =
		Logger.getLogger(L2CodeGenerator.class.getCanonicalName());

	/**
	 * The instruction stream emitted thus far of the {@linkplain
	 * L2ChunkDescriptor chunk} undergoing {@linkplain L2CodeGenerator code
	 * generation}.
	 */
	private final List<L2NamedOperandType> expectedNamedOperandTypes =
		new ArrayList<L2NamedOperandType>(10);

	/**
	 * The {@linkplain AvailObject literals} that will be embedded into the
	 * created {@linkplain L2ChunkDescriptor chunk}.
	 */
	private final List<AvailObject> literals =
		new ArrayList<AvailObject>(20);

	/**
	 * Emit the specified {@linkplain AvailObject literal} into the instruction
	 * stream.
	 *
	 * @param literal A {@linkplain AvailObject literal}.
	 */
	public void emitLiteral (final AvailObject literal)
	{
		final L2NamedOperandType expected = expectedNamedOperandTypes.remove(0);
		final L2OperandType expectedOperandType = expected.operandType();
		assert expectedOperandType == L2OperandType.CONSTANT
			|| expectedOperandType == L2OperandType.SELECTOR;
		assert !literal.traversed().descriptor().isMutable();
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
	 * L2ObjectRegister object registers}, grouped by {@linkplain
	 * L2RegisterVector register vector}.
	 */
	private final List<List<Integer>> vectors =
		new ArrayList<List<Integer>>(20);

	/**
	 * The inverse of {@link #vectors}.  That is, if vectors contains a list of
	 * integers at index x, this {@link Map} contains the integer x at that list
	 * of integers.
	 */
	private final Map<List<Integer>, Integer> inverseVectors =
		new HashMap<List<Integer>, Integer>(20);

	/**
	 * Emit the {@linkplain L2Register#finalIndex() indices} of the {@link
	 * L2ObjectRegister}s within the specified {@linkplain L2RegisterVector
	 * register vector} into the instruction stream.
	 *
	 * @param registerVector A {@linkplain L2RegisterVector register vector}.
	 */
	public void emitVector (final L2RegisterVector registerVector)
	{
		final L2NamedOperandType expected = expectedNamedOperandTypes.remove(0);
		final L2OperandType expectedOperandType = expected.operandType();
		assert expectedOperandType == L2OperandType.READ_VECTOR
			|| expectedOperandType == L2OperandType.READWRITE_VECTOR
			|| expectedOperandType == L2OperandType.WRITE_VECTOR;
		final List<L2ObjectRegister> registersList = registerVector.registers();
		final List<Integer> registerIndices =
			new ArrayList<Integer>(registersList.size());
		for (int i = 0; i < registersList.size(); i++)
		{
			registerIndices.add(registersList.get(i).finalIndex());
		}
		Integer vectorIndex = inverseVectors.get(registerIndices);
		if (vectorIndex == null)
		{
			vectors.add(registerIndices);
			// Extract the size after the add to make it one-based.
			vectorIndex = vectors.size();
			inverseVectors.put(registerIndices, vectorIndex);
		}
		emitWord(vectorIndex);
	}

	/**
	 * The highest numbered {@linkplain L2ObjectRegister object registers}
	 * emitted thus far for the {@linkplain L2ChunkDescriptor chunk} undergoing
	 * {@linkplain L2CodeGenerator code generation}.
	 */
	private int maxObjectRegisterIndex = 0;

	/**
	 * Emit the {@linkplain L2Register#finalIndex() index} of the specified
	 * {@linkplain L2ObjectRegister object register} into the instruction
	 * stream.
	 *
	 * @param objectRegister An {@linkplain L2ObjectRegister object register}.
	 */
	public void emitObjectRegister (
		final L2ObjectRegister objectRegister)
	{
		final L2NamedOperandType expected = expectedNamedOperandTypes.remove(0);
		final L2OperandType expectedOperandType = expected.operandType();
		assert expectedOperandType == L2OperandType.READ_POINTER
			|| expectedOperandType == L2OperandType.READWRITE_POINTER
			|| expectedOperandType == L2OperandType.WRITE_POINTER;
		final int index = objectRegister.finalIndex();
		assert index >= 0;
		assert index != FixedRegister.NULL.ordinal()
			|| expectedOperandType == L2OperandType.READ_POINTER;
		maxObjectRegisterIndex = max(maxObjectRegisterIndex, index);
		emitWord(index);
	}

	/**
	 * The number of {@linkplain L2IntegerRegister integer registers} emitted
	 * thus far for the {@linkplain L2ChunkDescriptor chunk} undergoing
	 * {@linkplain L2CodeGenerator code generation}.
	 */
	private int integerRegisterCount = 0;

	/**
	 * Emit the {@linkplain L2Register#finalIndex() index} of the specified
	 * {@linkplain L2IntegerRegister integer register} into the instruction
	 * stream.
	 *
	 * @param integerRegister
	 *        An {@linkplain L2IntegerRegister integer register}.
	 */
	public void emitIntegerRegister (
		final L2IntegerRegister integerRegister)
	{
		final L2NamedOperandType expected = expectedNamedOperandTypes.remove(0);
		final L2OperandType expectedOperandType = expected.operandType();
		assert expectedOperandType == L2OperandType.READ_INT
			|| expectedOperandType == L2OperandType.READWRITE_INT
			|| expectedOperandType == L2OperandType.WRITE_INT;
		final int index = integerRegister.finalIndex();
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
	 * Emit the {@linkplain L2Register#finalIndex() index} of the specified
	 * {@linkplain L2FloatRegister float register} into the instruction stream.
	 *
	 * @param floatRegister An {@linkplain L2FloatRegister float register}.
	 */
	public void emitFloatRegister (final L2FloatRegister floatRegister)
	{
		final int index = floatRegister.finalIndex();
		if (index != -1)
		{
			floatRegisterCount = max(floatRegisterCount, index);
		}
		emitWord(index);
	}

	/**
	 * A Java {@link Set} of {@linkplain MethodDescriptor
	 * methods} upon which the {@linkplain L2ChunkDescriptor chunk}
	 * is dependent.
	 */
	private final Set<AvailObject> contingentImpSets =
		new HashSet<AvailObject>();

	/**
	 * Merge the specified {@link Set} of {@linkplain
	 * MethodDescriptor methods} with those already
	 * upon which the {@linkplain L2ChunkDescriptor chunk} undergoing code
	 * generation is already dependent.
	 *
	 * @param setOfImpSets
	 *            A Java {@link Set} of {@linkplain MethodDescriptor
	 *            methods}.
	 */
	public void addContingentMethods (
		final Set<AvailObject> setOfImpSets)
	{
		contingentImpSets.addAll(setOfImpSets);
	}

	/**
	 * The instruction stream emitted thus far of the {@linkplain
	 * L2ChunkDescriptor chunk} undergoing {@linkplain L2CodeGenerator code
	 * generation}.
	 */
	private List<Integer> wordcodes = new ArrayList<Integer>(20);

	/**
	 * Emit the specified wordcode into the instruction stream.
	 *
	 * @param wordcode A wordcode.
	 */
	private void emitWord (final int wordcode)
	{
		wordcodes.add(wordcode);
	}

	/**
	 * Emit the wordcode offset of the specified {@link L2Instruction} into the
	 * instruction stream.
	 *
	 * @param targetInstruction
	 *            The {@link L2Instruction} whose offset is to be written into
	 *            the instruction stream.
	 */
	public void emitWordcodeOffsetOf (final L2Instruction targetInstruction)
	{
		final L2NamedOperandType expected = expectedNamedOperandTypes.remove(0);
		final L2OperandType expectedOperandType = expected.operandType();
		assert expectedOperandType == L2OperandType.PC;
		wordcodes.add(targetInstruction.offset());
	}

	/**
	 * Emit the specified primitive number into the instruction stream.
	 *
	 * @param primitive The primitive number to record.
	 */
	public void emitPrimitiveNumber (final int primitive)
	{
		final L2NamedOperandType expected = expectedNamedOperandTypes.remove(0);
		final L2OperandType expectedOperandType = expected.operandType();
		assert expectedOperandType == L2OperandType.PRIMITIVE;
		wordcodes.add(primitive);
	}

	/**
	 * Emit the specified immediate value into the instruction stream.
	 *
	 * @param immediate The immediate {@code int} to record.
	 */
	public void emitImmediate (final int immediate)
	{
		final L2NamedOperandType expected = expectedNamedOperandTypes.remove(0);
		final L2OperandType expectedOperandType = expected.operandType();
		assert expectedOperandType == L2OperandType.IMMEDIATE;
		wordcodes.add(immediate);
	}

	/**
	 * Emit the wordcode for the specified {@link L2Operation} into the
	 * instruction stream.  Also set up the expectation for the operation's
	 * {@linkplain L2OperandType operand types}.
	 *
	 * @param operation
	 *            The {@link L2Operation} to record.
	 */
	public void emitL2Operation (final L2Operation operation)
	{
		assert expectedNamedOperandTypes.isEmpty();
		wordcodes.add(operation.ordinal());
		Collections.addAll(expectedNamedOperandTypes, operation.operandTypes());
	}

	/**
	 * Emit the specified {@linkplain L2Instruction instructions}. Use a
	 * two-pass algorithm: the first pass measures instruction lengths to
	 * correctly determine their offsets (to ensure that references to
	 * {@linkplain L2_LABEL labels} will be correct), the second
	 * pass generates the real instruction stream.
	 *
	 * @param instructions
	 *        The {@linkplain L2Instruction instructions} that should be
	 *        emitted.
	 */
	public void setInstructions (
		final List<L2Instruction> instructions)
	{
		// Generate the instructions, but be prepared to discard the generated
		// wordcodes. The wordcodes are generated as useless side-effect on this
		// pass, as the main intent is to measure instruction lengths to set
		// their offsets (so references to labels will be correct).
		assert wordcodes.size() == 0;
		for (final L2Instruction instruction : instructions)
		{
			instruction.setOffset(wordcodes.size() + 1);
			instruction.emitOn(this);
		}

		// Now that the instruction positions are known, discard the scratch
		// code generator and generate on a real code generator. This is a
		// trivial two-pass scheme to calculate jumps.
		wordcodes = new ArrayList<Integer>(wordcodes.size());
		for (final L2Instruction instruction : instructions)
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
	public AvailObject createChunkFor (final AvailObject code)
	{
		assert expectedNamedOperandTypes.isEmpty();
		if (logger.isLoggable(Level.FINE))
		{
			logger.fine(String.format(
				"translating L1 compiled code: %s ...", code));
		}

		final AvailObject newChunk = L2ChunkDescriptor.allocate(
			code,
			literals,
			vectors,
			maxObjectRegisterIndex + 1,
			integerRegisterCount,
			floatRegisterCount,
			wordcodes,
			contingentImpSets);
		if (logger.isLoggable(Level.FINE))
		{
			logger.fine(String.format(
				"... into L2 optimized chunk: %s", newChunk));
		}

		return newChunk;
	}
}