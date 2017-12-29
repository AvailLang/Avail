/**
 * L2_JUMP_IF_SUBTYPE_OF_CONSTANT.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.optimizer.jvm.JVMCodeGenerationUtility.emitIntConstant;
import static com.avail.optimizer.jvm.JVMTranslator.instructionFieldName;
import static com.avail.utility.Nulls.stripNull;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Type.*;

/**
 * Conditionally jump, depending on whether the type to check is a subtype of
 * the constant type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_JUMP_IF_SUBTYPE_OF_CONSTANT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_JUMP_IF_SUBTYPE_OF_CONSTANT().init(
			READ_POINTER.is("type to check"),
			CONSTANT.is("constant type"),
			PC.is("is subtype"),
			PC.is("not subtype"));

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadPointerOperand typeReg =
			instruction.readObjectRegisterAt(0);
		final A_Type constantType = instruction.constantAt(1);
		final int isSubtypeIndex = instruction.pcOffsetAt(2);
		final int notSubtypeIndex = instruction.pcOffsetAt(3);

		interpreter.offset(
			typeReg.in(interpreter).isSubtypeOf(constantType)
				? isSubtypeIndex
				: notSubtypeIndex);
		return null;
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1Translator translator)
	{
		// Eliminate tests due to type propagation.
		final L2ReadPointerOperand typeReg =
			instruction.readObjectRegisterAt(0);
		final A_Type constantType = instruction.constantAt(1);
		final L2PcOperand isSubtype = instruction.pcAt(2);
		final L2PcOperand notSubtype = instruction.pcAt(3);

		final @Nullable A_BasicObject typeToTest = typeReg.constantOrNull();
		if (typeToTest != null)
		{
			translator.addInstruction(
				L2_JUMP.instance,
				typeToTest.isInstanceOf(constantType) ? isSubtype : notSubtype);
			return true;
		}
		final A_Type knownType = typeReg.type().instance();
		if (knownType.isSubtypeOf(constantType))
		{
			// It's a subtype, so it must always pass the type test.
			translator.addInstruction(L2_JUMP.instance, isSubtype);
			return true;
		}
		final A_Type intersection = constantType.typeIntersection(knownType);
		if (intersection.isBottom())
		{
			// The types don't intersect, so it can't ever pass the type test.
			translator.addInstruction(L2_JUMP.instance, notSubtype);
			return true;
		}
		// The branch direction isn't known statically.  However, since it's
		// already known to be an X and we're testing for Y, we might be better
		// off testing for an X∩Y instead.  Let's just assume it's quicker for
		// now.  Eventually we can extend this idea to testing things other than
		// types, such as if we know we have a tuple but we want to dispatch
		// based on the tuple's size.
		if (!intersection.equals(constantType))
		{
			translator.addInstruction(
				L2_JUMP_IF_SUBTYPE_OF_CONSTANT.instance,
				typeReg,
				new L2ConstantOperand(intersection),
				isSubtype,
				notSubtype);
			return true;
		}
		// The test could not be eliminated or improved.
		return super.regenerate(instruction, registerSet, translator);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final L2ReadPointerOperand typeReg =
			instruction.readObjectRegisterAt(0);
		final A_Type constantType = instruction.constantAt(1);
//		final L2PcOperand isSubtype = instruction.pcAt(2);
//		final L2PcOperand notSubtype = instruction.pcAt(3);

		assert registerSets.size() == 2;
		final RegisterSet isSubtypeSet = registerSets.get(0);
//		final RegisterSet notSubtypeSet = registerSets.get(1);

		assert isSubtypeSet.hasTypeAt(typeReg.register());
		//noinspection StatementWithEmptyBody
		if (isSubtypeSet.hasConstantAt(typeReg.register()))
		{
			// The *exact* type is already known.  Don't weaken it by recording
			// type information for it (a meta).
		}
		else
		{
			final A_Type existingMeta = isSubtypeSet.typeAt(typeReg.register());
			final A_Type existingType = existingMeta.instance();
			final A_Type intersectionType =
				existingType.typeIntersection(constantType);
			final A_Type intersectionMeta = instanceMeta(intersectionType);
			isSubtypeSet.strengthenTestedTypeAtPut(
				typeReg.register(), intersectionMeta);
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final int typeRegisterIndex =
			instruction.readObjectRegisterAt(0).finalIndex();
//		final A_Type constantType = instruction.constantAt(1);
		final int isSubtypeIndex = instruction.pcOffsetAt(2);
		final int notSubtypeIndex = instruction.pcOffsetAt(3);

		method.visitVarInsn(ALOAD, translator.interpreterLocal());
		emitIntConstant(method, typeRegisterIndex);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"pointerAt",
			getMethodDescriptor(getType(AvailObject.class), INT_TYPE),
			false);
		method.visitFieldInsn(
			GETSTATIC,
			translator.classInternalName,
			instructionFieldName(instruction),
			getDescriptor(instruction.getClass()));
		emitIntConstant(method,1);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(L2Instruction.class),
			"constantAt",
			getMethodDescriptor(getType(AvailObject.class), INT_TYPE),
			false);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Type.class),
			"isSubtypeOf",
			getMethodDescriptor(BOOLEAN_TYPE, getType(A_Type.class)),
			true);
		method.visitJumpInsn(
			IFNE,
			stripNull(translator.instructionLabels)[isSubtypeIndex]);
		method.visitJumpInsn(
			GOTO,
			stripNull(translator.instructionLabels)[notSubtypeIndex]);
	}
}
