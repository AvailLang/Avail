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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import com.avail.interpreter.jvm.ConstantPool.ClassEntry;
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
		return nameEntry.data();
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
		return descriptorEntry.data();
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
			nameEntry.data(),
			descriptorEntry.data());
	}

	/** The {@linkplain InstructionWriter instruction writer}. */
	private final InstructionWriter writer = new InstructionWriter();

	/**
	 * Answer the count of {@linkplain JavaInstruction instructions} already
	 * emitted.
	 *
	 * @return The instruction count.
	 */
	int instructionCount ()
	{
		return writer.instructionCount();
	}

	/**
	 * Answer the code size of the {@linkplain Method method}, in bytes.
	 *
	 * @return The code size.
	 */
	int codeSize ()
	{
		return writer.codeSize();
	}

	/**
	 * Answer the maximum stack depth of the {@linkplain Method method}.
	 *
	 * @return The maximum stack depth.
	 */
	int maxStackDepth ()
	{
		return writer.maxStackDepth();
	}

	/** The maximum number of {@linkplain LocalVariable local variables}. */
	private int maxLocals = 0;

	/**
	 * Answer the maximum number of {@linkplain LocalVariable local variables}.
	 *
	 * @return The maximum number of local variables.
	 */
	int maxLocals ()
	{
		return maxLocals;
	}

	/**
	 * The parameter index, measured in {@linkplain LocalVariable#slotUnits()
	 * slot units}.
	 */
	private int parameterIndex = 0;

	/**
	 * The self reference, {@code this}, available only for non-{@code static}
	 * {@linkplain Method methods}.
	 */
	private LocalVariable self;

	@Override
	public void setModifiers (final EnumSet<MethodModifier> mods)
	{
		assert parameterIndex == 0;
		super.setModifiers(mods);
		if (mods.contains(ABSTRACT))
		{
			assert writer.instructionCount() == 0;
			writer.fixInstructions();
		}
		if (!mods.contains(STATIC))
		{
			final String descriptor = codeGenerator.classEntry.descriptor();
			self = new LocalVariable("this", descriptor, parameterIndex);
			parameterIndex += JavaDescriptors.slotUnits(descriptor);
			maxLocals = parameterIndex;
		}
	}

	/**
	 * Answer the {@linkplain LocalVariable local variable} that contains the
	 * self reference, {@code this}. Note that this local variable is only
	 * available for non-{@code static} {@linkplain Method methods}.
	 *
	 * @return The local variable containing the self reference.
	 */
	public LocalVariable self ()
	{
		assert self != null;
		return self;
	}

	/** The {@linkplain Set set} of {@linkplain LocalVariable parameters}. */
	private final Set<LocalVariable> parameters = new HashSet<>();

	/**
	 * Bind the specified name to the next unbound parameter, and answer a new
	 * {@linkplain LocalVariable local variable} that represents that parameter.
	 *
	 * @param name
	 *        The name of the parameter.
	 * @return A new local variable.
	 */
	public LocalVariable newParameter (final String name)
	{
		final String methodDescriptor = descriptor();
		final int index = parameterIndex - (modifiers.contains(STATIC) ? 0 : 1);
		assert index < JavaDescriptors.slotUnits(methodDescriptor);
		final String descriptor = JavaDescriptors.parameterDescriptor(
			methodDescriptor, index);
		final LocalVariable param = new LocalVariable(
			name, descriptor, parameterIndex);
		parameterIndex += JavaDescriptors.slotUnits(descriptor);
		maxLocals = parameterIndex;
		parameters.add(param);
		return param;
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
		final int localIndex = doomedScope.localIndex();
		if (localIndex > maxLocals)
		{
			maxLocals = localIndex;
		}
	}

	/** The {@linkplain Set set} of {@linkplain Label labels}. */
	private final Set<String> labelNames = new HashSet<>();

	/**
	 * Introduce a new {@linkplain Label label} with the specified name, but do
	 * not emit it to the {@linkplain InstructionWriter instruction stream}.
	 * This label can be the target of a branch.
	 *
	 * @param name
	 *        The name of the label.
	 * @return A new label.
	 */
	public Label newLabel (final String name)
	{
		assert !labelNames.contains(name);
		labelNames.add(name);
		return new Label(name);
	}

	/** The pattern to use for anonymous labels. */
	private static final String anonymousLabelPattern = "__L%d";

	/** The next ordinal to use for an anonymous label. */
	private int labelOrdinal = 0;

	/**
	 * Introduce a new anonymous {@linkplain Label label}, but do not emit it to
	 * the {@linkplain InstructionWriter instruction stream}. This label can be
	 * the target of a branch.
	 *
	 * @return A new label.
	 */
	public Label newLabel ()
	{
		final String name = String.format(
			anonymousLabelPattern, labelOrdinal++);
		return newLabel(name);
	}

	/**
	 * Emit the specified {@linkplain Label label}.
	 *
	 * @param label
	 *        A label.
	 */
	public void addLabel (final Label label)
	{
		writer.append(label);
	}

	/**
	 * Introduce a new {@linkplain LocalVariable local variable} into the
	 * current {@linkplain Scope scope}.
	 *
	 * @param name
	 *        The name of the local variable.
	 * @param descriptor
	 *        The type descriptor of the local variable. May be the nonstandard
	 *        descriptor {@code "R"} to indicate a return address.
	 * @return A new local variable.
	 */
	public LocalVariable newLocalVariable (
		final String name,
		final String descriptor)
	{
		assert parameterIndex == JavaDescriptors.slotUnits(descriptor());
		final Scope currentScope = scopeStack.peekFirst();
		return currentScope.newLocalVariable(name, descriptor);
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
		assert parameterIndex == JavaDescriptors.slotUnits(descriptor());
		final String descriptor = JavaDescriptors.forType(type);
		return newLocalVariable(name, descriptor);
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
	public void pushClassConstant (final Class<?> value)
	{
		writer.append(new LoadConstantInstruction(
			constantPool.classConstant(value)));
	}

	/**
	 * Emit code to push the specified class descriptor onto the operand stack.
	 *
	 * @param descriptor
	 *        A class descriptor.
	 */
	public void pushClassConstant (final String descriptor)
	{
		writer.append(new LoadConstantInstruction(
			constantPool.classConstant(descriptor)));
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
	 * Append to the specified {@linkplain List list} of {@linkplain
	 * JavaBytecode bytecodes} a bytecode that will convert the {@code int} on
	 * top of the operand stack to the requested target {@linkplain
	 * Class#isPrimitive() primitive type}.
	 *
	 * @param to
	 *        The target primitive type.
	 * @param bytecodes
	 *        A list of converter bytecodes.
	 * @see #convert(Class, Class)
	 */
	private void convertInt (
		final Class<?> to,
		final List<JavaBytecode> bytecodes)
	{
		if (to == Byte.TYPE || to == Boolean.TYPE)
		{
			bytecodes.add(i2b);
		}
		else if (to == Character.TYPE)
		{
			bytecodes.add(i2c);
		}
		else if (to == Short.TYPE)
		{
			bytecodes.add(i2s);
		}
		else if (to == Long.TYPE)
		{
			bytecodes.add(i2l);
		}
		else if (to == Float.TYPE)
		{
			bytecodes.add(i2f);
		}
		else if (to == Double.TYPE)
		{
			bytecodes.add(i2d);
		}
	}

	/**
	 * Append to the specified {@linkplain List list} of {@linkplain
	 * JavaBytecode bytecodes} a bytecode that will convert the {@code double}
	 * on top of the operand stack to the requested target {@linkplain
	 * Class#isPrimitive() primitive type}.
	 *
	 * @param to
	 *        The target primitive type.
	 * @param bytecodes
	 *        A list of converter bytecodes.
	 * @see #convert(Class, Class)
	 */
	private void convertDouble (
		final Class<?> to,
		final List<JavaBytecode> bytecodes)
	{
		if (to == Float.TYPE)
		{
			bytecodes.add(d2f);
		}
		else if (to == Long.TYPE)
		{
			bytecodes.add(d2l);
		}
		else
		{
			bytecodes.add(d2i);
			convertInt(to, bytecodes);
		}
	}

	/**
	 * Append to the specified {@linkplain List list} of {@linkplain
	 * JavaBytecode bytecodes} a bytecode that will convert the {@code float}
	 * on top of the operand stack to the requested target {@linkplain
	 * Class#isPrimitive() primitive type}.
	 *
	 * @param to
	 *        The target primitive type.
	 * @param bytecodes
	 *        A list of converter bytecodes.
	 * @see #convert(Class, Class)
	 */
	private void convertFloat (
		final Class<?> to,
		final List<JavaBytecode> bytecodes)
	{
		if (to == Double.TYPE)
		{
			bytecodes.add(f2d);
		}
		else if (to == Long.TYPE)
		{
			bytecodes.add(f2l);
		}
		else
		{
			bytecodes.add(f2i);
			convertInt(to, bytecodes);
		}
	}

	/**
	 * Append to the specified {@linkplain List list} of {@linkplain
	 * JavaBytecode bytecodes} a bytecode that will convert the {@code long}
	 * on top of the operand stack to the requested target {@linkplain
	 * Class#isPrimitive() primitive type}.
	 *
	 * @param to
	 *        The target primitive type.
	 * @param bytecodes
	 *        A list of converter bytecodes.
	 * @see #convert(Class, Class)
	 */
	private void convertLong (
		final Class<?> to,
		final List<JavaBytecode> bytecodes)
	{
		if (to == Double.TYPE)
		{
			bytecodes.add(l2d);
		}
		else if (to == Float.TYPE)
		{
			bytecodes.add(l2f);
		}
		else
		{
			bytecodes.add(l2i);
			convertInt(to, bytecodes);
		}
	}

	/**
	 * Emit code that will perform the requested {@linkplain Class#isPrimitive()
	 * primitive} conversion.
	 *
	 * @param from
	 *        The source primitive type.
	 * @param to
	 *        The destination primitive type.
	 */
	public void convert (final Class<?> from, final Class<?> to)
	{
		assert from.isPrimitive();
		assert from != Void.TYPE;
		assert to.isPrimitive();
		assert to != Void.TYPE;
		// No conversion is needed if both types are the same.
		if (from != to)
		{
			final List<JavaBytecode> bytecodes = new ArrayList<>(2);
			if (from == Double.TYPE)
			{
				convertDouble(to, bytecodes);
			}
			else if (from == Float.TYPE)
			{
				convertFloat(to, bytecodes);
			}
			else if (from == Long.TYPE)
			{
				convertLong(to, bytecodes);
			}
			else
			{
				convertInt(to, bytecodes);
			}
			for (final JavaBytecode bytecode : bytecodes)
			{
				writer.append(bytecode.create());
			}
		}
	}

	/**
	 * Emit an unconditional branch to the specified {@linkplain Label label}.
	 *
	 * @param label
	 *        A label.
	 */
	public void branchTo (final Label label)
	{
		writer.append(new GotoInstruction(label));
	}

	/**
	 * Emit a subroutine jump to the specified {@linkplain Label label}.
	 *
	 * @param label
	 *        A label.
	 */
	public void jumpSubroutine (final Label label)
	{
		writer.append(new JumpSubroutineInstruction(label));
	}

	/**
	 * Emit a subroutine return.
	 *
	 * @param returnAddress
	 *        The {@linkplain LocalVariable variable} that holds the return
	 *        address.
	 */
	public void returnSubroutine (final LocalVariable returnAddress)
	{
		writer.append(new ReturnSubroutineInstruction(returnAddress));
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

	// TODO: [TLS] Add support for invokedynamic.

	/**
	 * Emit code to invoke an {@code interface} method. The receiver and
	 * arguments are sourced from the operand stack.
	 *
	 * @param methodref
	 */
	public void invokeInterface (final MethodrefEntry methodref)
	{
		writer.append(invokeinterface.create(methodref));
	}

	/**
	 * Emit code to invoke a {@code super}, {@code private}, or instance
	 * initialization ({@code "<init>"}) method. The receiver and arguments are
	 * sourced from the operand stack.
	 *
	 * @param methodref
	 *        A {@linkplain MethodrefEntry method reference}.
	 */
	public void invokeSpecial (final MethodrefEntry methodref)
	{
		writer.append(invokespecial.create(methodref));
	}

	/**
	 * Emit code to invoke a static method. The arguments are sourced from the
	 * operand stack.
	 *
	 * @param methodref
	 *        A {@linkplain MethodrefEntry method reference}.
	 */
	public void invokeStatic (final MethodrefEntry methodref)
	{
		writer.append(invokestatic.create(methodref));
	}

	/**
	 * Emit code to invoke a method. The method will be dispatched based on the
	 * {@linkplain Class class} of the receiver. The receiver and arguments are
	 * sourced from the operand stack.
	 *
	 * @param methodref
	 *        A {@linkplain MethodrefEntry method reference}.
	 */
	public void invokeVirtual (final MethodrefEntry methodref)
	{
		writer.append(invokevirtual.create(methodref));
	}

	// TODO: [TLS] Add missing code generation methods!

	/** The {@linkplain ExceptionTable exception table}. */
	private final ExceptionTable exceptionTable = new ExceptionTable();

	/**
	 * Answer the {@linkplain ExceptionTable exception table}.
	 *
	 * @return The exception table.
	 */
	ExceptionTable exceptionTable ()
	{
		return exceptionTable;
	}

	/**
	 * Add a guarded zone to the {@linkplain ExceptionTable exception table}.
	 *
	 * @param startLabel
	 *        The inclusive start {@linkplain Label label}.
	 * @param endLabel
	 *        The exclusive end label.
	 * @param handlerLabel
	 *        The label of the handler subroutine.
	 * @param catchEntry
	 *        The {@linkplain ClassEntry class entry} for the {@linkplain
	 *        Throwable throwable} type.
	 */
	public void addGuardedZone (
		final Label startLabel,
		final Label endLabel,
		final Label handlerLabel,
		final ClassEntry catchEntry)
	{
		exceptionTable.addGuardedZone(
			startLabel, endLabel, handlerLabel, catchEntry);
	}

	/**
	 * Add a guarded zone to the {@linkplain ExceptionTable exception table}.
	 *
	 * @param startLabel
	 *        The inclusive start {@linkplain Label label}.
	 * @param endLabel
	 *        The exclusive end label.
	 * @param handlerLabel
	 *        The label of the handler subroutine.
	 * @param catchDescriptor
	 *        The descriptor for the intercepted {@linkplain Throwable
	 *        throwable} type.
	 */
	public void addGuardedZone (
		final Label startLabel,
		final Label endLabel,
		final Label handlerLabel,
		final String catchDescriptor)
	{
		final ClassEntry catchEntry =
			constantPool.classConstant(catchDescriptor);
		addGuardedZone(startLabel, endLabel, handlerLabel, catchEntry);
	}

	/**
	 * Add a guarded zone to the {@linkplain ExceptionTable exception table}.
	 *
	 * @param startLabel
	 *        The inclusive start {@linkplain Label label}.
	 * @param endLabel
	 *        The exclusive end label.
	 * @param handlerLabel
	 *        The label of the handler subroutine.
	 * @param throwableType
	 *        The {@linkplain Throwable throwable} {@linkplain Class type}.
	 */
	public void addGuardedZone (
		final Label startLabel,
		final Label endLabel,
		final Label handlerLabel,
		final Class<? extends Throwable> throwableType)
	{
		final ClassEntry catchEntry =
			constantPool.classConstant(throwableType);
		addGuardedZone(startLabel, endLabel, handlerLabel, catchEntry);
	}

	/**
	 * Emit code that will affect a {@code return} to the caller. If the
	 * {@linkplain Method method} produces a value, then that value will be
	 * consumed from the top of the stack.
	 */
	public void returnToCaller ()
	{
		final JavaOperand returnType =
			JavaDescriptors.returnOperand(descriptor());
		final JavaBytecode bytecode;
		if (returnType == null)
		{
			bytecode = return_;
		}
		else
		{
			switch (returnType.baseOperand())
			{
				case OBJECTREF:
					bytecode = areturn;
					break;
				case INT:
					bytecode = ireturn;
					break;
				case LONG:
					bytecode = lreturn;
					break;
				case FLOAT:
					bytecode = freturn;
					break;
				case DOUBLE:
					bytecode = dreturn;
					break;
				default:
					assert false : "This never happens!";
					throw new IllegalStateException();
			}
		}
		writer.append(bytecode.create());
	}

	/**
	 * Fix all {@linkplain JavaInstruction instructions} at concrete {@linkplain
	 * JavaInstruction#address() addresses}. No more code should be emitted to
	 * the method body.
	 */
	public void finish ()
	{
		assert scopeStack.isEmpty();
		writer.fixInstructions();
		final CodeAttribute code = new CodeAttribute(
			this, Collections.<Attribute>emptyList());
		setAttribute(code);
	}

	@Override
	void writeBodyTo (final DataOutput out) throws IOException
	{
		nameEntry.writeIndexTo(out);
		descriptorEntry.writeIndexTo(out);
	}

	/**
	 * Write the size-prefixed contents of the {@linkplain InstructionWriter
	 * instruction stream} to the specified {@linkplain DataOutput output
	 * stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	void writeInstructionsTo (final DataOutput out) throws IOException
	{
		writer.writeTo(out);
	}

	@Override
	public String toString ()
	{
		@SuppressWarnings("resource")
		final Formatter formatter = new Formatter();
		final String mods = MethodModifier.toString(modifiers);
		formatter.format("%s%s", mods, mods.isEmpty() ? "" : " ");
		formatter.format("%s : %s", name(), descriptor());
		formatter.format("%n\tCode:");
		final String codeString = writer.toString().replaceAll(
			String.format("%n"), String.format("%n\t"));
		formatter.format("%n\t%s", codeString);
		if (exceptionTable.size() > 0)
		{
			final String tableString = exceptionTable.toString().replaceAll(
				String.format("%n"), String.format("%n\t"));
			formatter.format("%n\t%s", tableString);
		}
		return formatter.toString();
	}
}
