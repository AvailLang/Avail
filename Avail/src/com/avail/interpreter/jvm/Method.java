/**
 * Method.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.jvm;

import static com.avail.interpreter.jvm.JavaBytecode.*;
import static com.avail.interpreter.jvm.MethodModifier.*;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedList;
import com.avail.interpreter.jvm.ConstantPool.FieldrefEntry;
import com.avail.interpreter.jvm.ConstantPool.MethodrefEntry;
import com.avail.interpreter.jvm.ConstantPool.Utf8Entry;

/**
 * {@code Method} describes a Java method specified by the {@linkplain
 * CodeGenerator code generator}. It provides a plethora of facilities for
 * emitting {@linkplain JavaInstruction instructions} abstractly.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class Method
extends Emitter<MethodModifier>
{
	/**
	 * The {@linkplain CodeGenerator code generator} that produced this
	 * {@linkplain Method method}.
	 */
	private final CodeGenerator codeGenerator;

	/** The name {@linkplain Utf8Entry entry}. */
	private final Utf8Entry nameEntry;

	/**
	 * Answer the name of the {@linkplain Method method}.
	 *
	 * @return The name of the field.
	 */
	public String name ()
	{
		return nameEntry.toString();
	}

	/** The descriptor {@linkplain Utf8Entry entry}. */
	private final Utf8Entry descriptorEntry;

	/**
	 * Answer the signature descriptor of the {@linkplain Method method}.
	 *
	 * @return The type descriptor of the method.
	 */
	public String descriptor ()
	{
		return descriptorEntry.toString();
	}

	/**
	 * Construct a new {@link Method}.
	 *
	 * <p>If the {@linkplain CodeGenerator code generator} is building an
	 * {@code interface}, then the new method will be automatically marked as
	 * {@code abstract}.</p>
	 *
	 * @param codeGenerator
	 *        The {@linkplain CodeGenerator code generator}.
	 * @param nameEntry
	 *        The method name.
	 * @param descriptorEntry
	 *        The method's signature descriptor.
	 */
	Method (
		final CodeGenerator codeGenerator,
		final String nameEntry,
		final String descriptorEntry)
	{
		super(codeGenerator.constantPool, MethodModifier.class, SYNTHETIC);
		this.codeGenerator = codeGenerator;
		this.nameEntry = constantPool.utf8(nameEntry);
		this.descriptorEntry = constantPool.utf8(descriptorEntry);
		if (codeGenerator.modifiers().contains(ClassModifier.INTERFACE))
		{
			modifiers.add(ABSTRACT);
			writer.fixInstructions();
		}
	}

	/**
	 * Answer a {@linkplain MethodrefEntry method reference entry} for the
	 * {@linkplain Method receiver}.
	 *
	 * @return A method reference entry for this method.
	 */
	MethodrefEntry reference ()
	{
		return constantPool.methodref(
			codeGenerator.classEntry.toString(),
			nameEntry.toString(),
			descriptorEntry.toString());
	}

	/** The {@linkplain InstructionWriter instruction writer}. */
	final InstructionWriter writer = new InstructionWriter();

	@Override
	public void setModifiers (final EnumSet<MethodModifier> mods)
	{
		super.setModifiers(mods);
		if (mods.contains(ABSTRACT))
		{
			assert writer.instructionCount() == 0;
			writer.fixInstructions();
		}
	}

	/** The {@linkplain Scope scope} {@linkplain Deque stack}. */
	private final Deque<Scope> scopeStack = new LinkedList<>();

	/**
	 * Enter a new {@linkplain Scope scope}.
	 */
	public void enterScope ()
	{
		final Scope currentScope = scopeStack.peekFirst();
		final Scope newScope = currentScope.newInnerScope();
		scopeStack.addFirst(newScope);
	}

	/**
	 * Exit the current {@linkplain Scope scope}, retiring all {@linkplain
	 * LocalVariable local variables} that it defined.
	 */
	public void exitScope ()
	{
		final Scope doomedScope = scopeStack.removeFirst();
		doomedScope.exit();
	}

	/**
	 * Introduce a new {@linkplain LocalVariable local variable} into the
	 * current {@linkplain Scope scope}.
	 *
	 * @param name
	 *        The name of the local variable.
	 * @param type
	 *        The {@linkplain Class type} of the local variable.
	 * @return A new local variable.
	 */
	public LocalVariable newLocalVariable (
		final String name,
		final Class<?> type)
	{
		assert LocalVariable.isValid(type);
		final Scope currentScope = scopeStack.peekFirst();
		return currentScope.newLocalVariable(name, type);
	}

	/**
	 * Emit code to push the specified {@code boolean} value onto the operand
	 * stack.
	 *
	 * @param value
	 *        A {@code boolean} value.
	 */
	public void pushConstant (final boolean value)
	{
		if (value)
		{
			writer.append(iconst_1.create());
		}
		else
		{
			writer.append(iconst_0.create());
		}
	}

	/**
	 * The {@linkplain JavaInstruction instructions} that push immediate {@code
	 * int}s.
	 */
	private static final JavaBytecode[] immediateIntBytecodes =
		{iconst_m1, iconst_0, iconst_1, iconst_2, iconst_4, iconst_5};

	/**
	 * Emit code to push the specified {@code int} value onto the operand stack.
	 *
	 * @param value
	 *        An {@code int} value.
	 */
	public void pushConstant (final int value)
	{
		if (value >= -1 && value <= 5)
		{
			writer.append(immediateIntBytecodes[value + 1].create());
		}
		else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)
		{
			writer.append(bipush.create(value));
		}
		else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
		{
			writer.append(sipush.create(value));
		}
		else
		{
			writer.append(new LoadConstantInstruction(
				constantPool.constant(value)));
		}
	}

	/**
	 * Emit code to push the specified {@code long} value onto the operand
	 * stack.
	 *
	 * @param value
	 *        A {@code long} value.
	 */
	public void pushConstant (final long value)
	{
		final JavaInstruction instruction;
		if (value == 0L)
		{
			instruction = lconst_0.create();
		}
		else if (value == 1L)
		{
			instruction = lconst_1.create();
		}
		else
		{
			instruction = new LoadConstantInstruction(
				constantPool.constant(value));
		}
		writer.append(instruction);
	}

	/**
	 * Emit code to push the specified {@code float} value onto the operand
	 * stack.
	 *
	 * @param value
	 *        A {@code float} value.
	 */
	public void pushConstant (final float value)
	{
		final JavaInstruction instruction;
		if (value == 0.0f)
		{
			instruction = fconst_0.create();
		}
		else if (value == 1.0f)
		{
			instruction = fconst_1.create();
		}
		else if (value == 2.0f)
		{
			instruction = fconst_2.create();
		}
		else
		{
			instruction = new LoadConstantInstruction(
				constantPool.constant(value));
		}
		writer.append(instruction);
	}

	/**
	 * Emit code to push the specified {@code double} value onto the operand
	 * stack.
	 *
	 * @param value
	 *        A {@code double} value.
	 */
	public void pushConstant (final double value)
	{
		final JavaInstruction instruction;
		if (value == 0.0d)
		{
			instruction = dconst_0.create();
		}
		else if (value == 1.0d)
		{
			instruction = dconst_1.create();
		}
		else
		{
			instruction = new LoadConstantInstruction(
				constantPool.constant(value));
		}
		writer.append(instruction);
	}

	/**
	 * Emit code to push the specified {@link String} value onto the operand
	 * stack.
	 *
	 * @param value
	 *        A {@code String} value.
	 */
	public void pushConstant (final String value)
	{
		writer.append(new LoadConstantInstruction(
			constantPool.constant(value)));
	}

	/**
	 * Emit code to push the specified {@link Class} value onto the operand
	 * stack.
	 *
	 * @param value
	 *        A {@code Class} value.
	 */
	public void pushConstant (final Class<?> value)
	{
		writer.append(new LoadConstantInstruction(
			constantPool.constant(value)));
	}

	/**
	 * Emit code to push {@code null} onto the operand stack.
	 */
	public void pushNull ()
	{
		writer.append(aconst_null.create());
	}

	/**
	 * Emit code to push the contents of the specified {@linkplain LocalVariable
	 * local variable} onto the operand stack.
	 *
	 * @param local
	 *        A local variable.
	 */
	public void load (final LocalVariable local)
	{
		writer.append(new LoadInstruction(local));
	}

	/**
	 * Emit code to push the contents of the specified {@linkplain Field field}
	 * onto the operand stack.
	 *
	 * @param field
	 *        A field.
	 */
	public void load (final Field field)
	{
		writer.append(new GetFieldInstruction(
			field.reference(), field.modifiers.contains(FieldModifier.STATIC)));
	}

	/**
	 * Emit code to push the contents of the specified {@linkplain Field field}
	 * onto the operand stack.
	 *
	 * @param fieldrefEntry
	 *        A {@linkplain FieldrefEntry field reference entry}.
	 * @param isStatic
	 *        {@code true} if the referenced field is {@code static}, {@code
	 *        false} otherwise.
	 */
	public void load (final FieldrefEntry fieldrefEntry, final boolean isStatic)
	{
		writer.append(new GetFieldInstruction(fieldrefEntry, isStatic));
	}

	/**
	 * Emit code to push an array element onto the operand stack.
	 *
	 * @param type
	 *        The {@linkplain Class type} of the array, either a {@linkplain
	 *        Class#isPrimitive() primitive type} or {@link Object Object.class}
	 *        for a reference type.
	 */
	public void loadArrayElement (final Class<?> type)
	{
		assert type.isPrimitive() || type == Object.class;
		writer.append(new ArrayLoadInstruction(type));
	}

	/**
	 * Emit code to pop the operand stack and store the result into the
	 * specified {@linkplain LocalVariable local variable}.
	 *
	 * @param local
	 *        A local variable.
	 */
	public void store (final LocalVariable local)
	{
		writer.append(new StoreInstruction(local));
	}

	/**
	 * Emit code to pop the operand stack and store the result into the
	 * specified {@linkplain Field field}.
	 *
	 * @param field
	 *        A field.
	 */
	public void store (final Field field)
	{
		writer.append(new SetFieldInstruction(
			field.reference(), field.modifiers.contains(FieldModifier.STATIC)));
	}

	/**
	 * Emit code to push the contents of the specified {@linkplain Field field}
	 * onto the operand stack.
	 *
	 * @param fieldrefEntry
	 *        A {@linkplain FieldrefEntry field reference entry}.
	 * @param isStatic
	 *        {@code true} if the referenced field is {@code static}, {@code
	 *        false} otherwise.
	 */
	public void store (
		final FieldrefEntry fieldrefEntry,
		final boolean isStatic)
	{
		writer.append(new SetFieldInstruction(fieldrefEntry, isStatic));
	}

	/**
	 * Emit code to pop a value from the operand stack and store it into an
	 * array.
	 *
	 * @param type
	 *        The {@linkplain Class type} of the array, either a {@linkplain
	 *        Class#isPrimitive() primitive type} or {@link Object Object.class}
	 *        for a reference type.
	 */
	public void storeArrayElement (final Class<?> type)
	{
		assert type.isPrimitive() || type == Object.class;
		writer.append(new ArrayStoreInstruction(type));
	}

	/**
	 * Emit code to pop the operand stack and enter the result's monitor.
	 */
	public void enterMonitor ()
	{
		writer.append(monitorenter.create());
	}

	/**
	 * Emit code to pop the operand stack and exit from the result's monitor.
	 */
	public void exitMonitor ()
	{
		writer.append(monitorexit.create());
	}

	// TODO: [TLS] Add missing code generation methods!

	/**
	 * Fix all {@linkplain JavaInstruction instructions} at concrete {@linkplain
	 * JavaInstruction#address() addresses}. No more code should be emitted to
	 * the method body.
	 */
	public void finish ()
	{
		writer.fixInstructions();
		// TODO: Generate a CodeAttribute and install it.
	}

	@Override
	void writeBodyTo (final DataOutput out) throws IOException
	{
		nameEntry.writeIndexTo(out);
		descriptorEntry.writeIndexTo(out);
	}
}
