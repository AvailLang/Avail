/*
 * L2_SAVE_ALL_AND_PC_TO_INT.java
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ContinuationRegisterDumpDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.REGISTER_DUMP;
import com.avail.interpreter.levelTwo.WritesHiddenVariable;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.jvm.JVMChunk;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.avail.interpreter.Interpreter.saveRegistersMethod;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.REFERENCED_AS_INT;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_INT;
import static com.avail.interpreter.levelTwo.register.L2Register.RegisterKind.BOXED;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toMap;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.getInternalName;

/**
 * Extract the given "reference" edge's target level two offset as an {@code
 * int}, then follow the fall-through edge.  The int value will be used in the
 * fall-through code to assemble a continuation, which, when returned into, will
 * start at the reference edge target.  Note that the L2 offset of the reference
 * edge is not known until just before JVM code generation.
 *
 * <p>This is a special operation, in that during final JVM code generation it
 * saves all objects in a register dump
 * ({@link ContinuationRegisterDumpDescriptor}), and the
 * {@link L2_ENTER_L2_CHUNK} at the reference target will restore them.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@WritesHiddenVariable(
	REGISTER_DUMP.class)
public final class L2_SAVE_ALL_AND_PC_TO_INT
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_CONTINUATION}.
	 */
	private L2_SAVE_ALL_AND_PC_TO_INT ()
	{
		super(
			PC.is("fall-through", SUCCESS),
			PC.is("reference", REFERENCED_AS_INT),
			WRITE_INT.is("L2 address"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_SAVE_ALL_AND_PC_TO_INT
		instance = new L2_SAVE_ALL_AND_PC_TO_INT();

	@Override
	public List<L2PcOperand> targetEdges (final L2Instruction instruction)
	{
		assert this == instruction.operation();
		final List<L2PcOperand> edges = new ArrayList<>(2);
		edges.add(instruction.operand(0));
		edges.add(instruction.operand(1));
		return edges;
	}

	@Override
	public boolean altersControlFlow ()
	{
		return true;
	}

	/**
	 * Answer true if this instruction leads to multiple targets, *multiple* of
	 * which can be reached.  This is not the same as a branch, in which only
	 * one will be reached for any circumstance of reaching this instruction.
	 * In particular, the {@code L2_SAVE_ALL_AND_PC_TO_INT} instruction jumps
	 * to its fall-through label, but after reification has saved the live
	 * register state, it gets restored again and winds up traversing the other
	 * edge.
	 *
	 * <p>This is an important distinction, in that this type of instruction
	 * should act as a barrier against redundancy elimination.  Otherwise an
	 * object with identity (i.e., a variable) created in the first branch won't
	 * be the same as the one produced again in the second branch.</p>
	 *
	 * <p>Also, we must treat as always-live-in to this instruction any values
	 * that are used in <em>either</em> branch, since they'll both be taken.</p>
	 *
	 * @return Whether multiple branches may be taken following the circumstance
	 *         of arriving at this instruction.
	 */
	@Override
	public boolean goesMultipleWays ()
	{
		return true;
	}

	@Override
	public L2Instruction optionalReplacementForDeadInstruction (
		final L2Instruction instruction)
	{
		// Nobody is using the targetAsInt, so nobody is synthesizing a
		// continuation that could ever resume, so nobody needs to capture the
		// live register state.  Turn the instruction into an unconditional jump
		// along the fallThrough edge.
		final L2PcOperand fallThroughEdge = instruction.operand(0);
		return new L2Instruction(
			instruction.basicBlock,
			L2_JUMP.instance,
			fallThroughEdge);
	}

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		assert this == instruction.operation();
		final L2PcOperand fallThrough = instruction.operand(0);
		final L2PcOperand target = instruction.operand(1);
		final L2WriteIntOperand targetAsInt = instruction.operand(2);

		// Install the operands in this order.  First target, which will not be
		// able to access targetAsInt.  Then targetAsInt.  Then fallThrough,
		// which will be able to see targetAsInt.
		target.instructionWasAdded(instruction, manifest);
		targetAsInt.instructionWasAdded(instruction, manifest);
		fallThrough.instructionWasAdded(instruction, manifest);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		// final L2PcOperand fallThrough = instruction.operand(0);
		final L2PcOperand target = instruction.operand(1);
		final L2WriteIntOperand targetAsInt = instruction.operand(2);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(targetAsInt);
		builder.append(" ← address of label $[");
		builder.append(target.targetBlock().name());
		if (target.offset() != -1)
		{
			builder.append("(=").append(target.offset()).append(")");
		}
		builder.append("]");
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		assert this == instruction.operation();
		final L2PcOperand fallThrough = instruction.operand(0);
		final L2PcOperand target = instruction.operand(1);
		final L2WriteIntOperand targetAsInt = instruction.operand(2);

		// Even though it looks like we're just capturing the constant L2 offset
		// here, we also capture the live register state and stash it for
		// continuation construction to use.  We also use it to restore the live
		// register state at the L2_ENTER_L2_CHUNK at the end of this edge.
		final L2BasicBlock targetBlock = target.targetBlock();
		final L2Instruction targetInstruction =
			targetBlock.instructions().get(0);
		assert targetInstruction.operation() == L2_ENTER_L2_CHUNK.instance;

		final EnumMap<RegisterKind, List<Integer>> liveMap =
			Arrays.stream(RegisterKind.values()).collect(
				toMap(
					Function.identity(),
					k -> new ArrayList<>(),
					(a, b) -> { throw new RuntimeException("Impossible"); },
					() -> new EnumMap<>(RegisterKind.class)));
		final Set<L2Register> liveRegistersSet =
			new HashSet<>(target.alwaysLiveInRegisters);
		liveRegistersSet.addAll(target.sometimesLiveInRegisters);
		final List<L2Register> liveRegistersList =
			new ArrayList<>(liveRegistersSet);
		liveRegistersList.sort(comparingInt(L2Register::finalIndex));
		liveRegistersList.forEach(reg ->
			liveMap.get(reg.registerKind()).add(
				translator.localNumberFromRegister(reg)));

		translator.liveLocalNumbersByKindPerEntryPoint.put(
			targetInstruction, liveMap);

		// Push the interpreter for later...
		translator.loadInterpreter(method);
		// Emit code to save those registers' values.  Start with the objects.
		// :: array = new «arrayClass»[«limit»];
		// :: array[0] = ...; array[1] = ...;
		final List<Integer> boxedLocalNumbers = liveMap.get(BOXED);
		if (boxedLocalNumbers.isEmpty())
		{
			JVMChunk.noObjectsField.generateRead(method);
		}
		else
		{
			translator.intConstant(method, boxedLocalNumbers.size());
			method.visitTypeInsn(ANEWARRAY, getInternalName(AvailObject.class));
			for (int i = 0; i < boxedLocalNumbers.size(); i++)
			{
				method.visitInsn(DUP);
				translator.intConstant(method, i);
				method.visitVarInsn(
					BOXED.loadInstruction,
					boxedLocalNumbers.get(i));
				method.visitInsn(AASTORE);
			}
		}
		// Now create the array of longs, including both ints and doubles.
		final List<Integer> intLocalNumbers = liveMap.get(RegisterKind.INTEGER);
		final List<Integer> floatLocalNumbers = liveMap.get(RegisterKind.FLOAT);
		if (intLocalNumbers.size() + floatLocalNumbers.size() == 0)
		{
			JVMChunk.noLongsField.generateRead(method);
		}
		else
		{
			translator.intConstant(
				method, intLocalNumbers.size() + floatLocalNumbers.size());
			method.visitIntInsn(NEWARRAY, T_LONG);
			int i;
			for (i = 0; i < intLocalNumbers.size(); i++)
			{
				method.visitInsn(DUP);
				translator.intConstant(method, i);
				method.visitVarInsn(
					RegisterKind.INTEGER.loadInstruction,
					intLocalNumbers.get(i));
				method.visitInsn(I2L);
				method.visitInsn(LASTORE);
			}
			for (int j = 0; j < floatLocalNumbers.size(); j++, i++)
			{
				method.visitInsn(DUP);
				translator.intConstant(method, i);
				method.visitVarInsn(
					RegisterKind.FLOAT.loadInstruction,
					floatLocalNumbers.get(i));
				method.visitInsn(D2L);
				method.visitInsn(LASTORE);
			}
		}
		// The stack is now interpreter, AvailObject[], long[].
		saveRegistersMethod.generateCall(method);
		// Stack is empty

		translator.intConstant(method, target.offset());
		translator.store(method, targetAsInt.register());

		// Jump is usually elided.
		translator.jump(method, instruction, fallThrough);
	}
}
