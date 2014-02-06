/**
 * Method.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import java.util.*;
import com.avail.annotations.Nullable;
import com.avail.interpreter.jvm.ConstantPool.ClassEntry;
import com.avail.interpreter.jvm.ConstantPool.FieldrefEntry;
import com.avail.interpreter.jvm.ConstantPool.MethodrefEntry;
import com.avail.interpreter.jvm.ConstantPool.Utf8Entry;

/**
 * {@code Method} describes a Java method specified by the {@linkplain
 * CodeGenerator code generator}. It provides a plethora of facilities for
 * emitting {@linkplain JavaInstruction instructions} abstractly.
 *
 * <p>The typical life cycle of a {@code Method} is as follows:</p>
 *
 * <ol>
 * <li>Obtain a new method from a code generator: {@link
 * CodeGenerator#newMethod(String, String)}</li>
 * <li>Build a {@linkplain EnumSet set} of {@linkplain MethodModifier method
 * modifiers} and associate it with the method: {@link #setModifiers(EnumSet)}
 * </li>
 * <li>Declare any {@linkplain Throwable checked exceptions} thrown by the
 * method: {@link #setCheckedExceptions(String...)}</li>
 * <li>Specify the parameters of the method from left to right: {@link
 * #newParameter(String)}</li>
 * <li>Plan the content of the method by:
 *    <ul>
 *    <li>Entering a scope in which {@linkplain LocalVariable local variables}
 *    may be defined: {@link #enterScope()}.</li>
 *    <li>Defining local variables: {@link
 *    #newLocalVariable(String, String)}</li>
 *    <li>Producing {@linkplain Label labels}: {@link #newLabel()}, or</li>
 *    <li>Emitting labels: {@link #addLabel(Label)}, or</li>
 *    <li>Emitting {@linkplain JavaInstruction instructions}.</li>
 *    </ul>
 * </li>
 * <li>Define the {@linkplain ExceptionTable exception table} by adding the
 * requisite guarded zones: {@link
 * #addGuardedZone(Label, Label, HandlerLabel, ClassEntry)}</li>
 * <li>Set pertinent {@linkplain Attribute attributes}: {@link
 * #setAttribute(Attribute)}</li>
 * <li>Finish editing the method, allowing generation of {@linkplain
 * JavaBytecode bytecodes} and fixation of labels to {@linkplain
 * JavaInstruction#address() addresses}: {@link #finish()}
 * </ol>
 *
 * <p>The numbered steps must be performed in the specified order. Performing
 * these steps out of order is not supported, and may result either in {@link
 * AssertionError}s or malformed methods.</p>
 *
 * <p>{@code N.B.} A scope must be entered before any local variables may be
 * defined.</p>
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

	/** The name of every constructor. */
	public static final String constructorName = "<init>";

	/** The name of the static initializer. */
	public static final String staticInitializerName = "<clinit>";

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
			codeGenerator.classEntry.internalName(),
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
	long codeSize ()
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
	private @Nullable LocalVariable self;

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
	 * Declare the {@linkplain Throwable checked exceptions} that the
	 * {@linkplain Method method} may throw.
	 *
	 * @param descriptors
	 *        The descriptors for the checked exceptions.
	 */
	public void setCheckedExceptions (final String... descriptors)
	{
		final List<ClassEntry> entries = new ArrayList<>(descriptors.length);
		for (final String descriptor : descriptors)
		{
			entries.add(constantPool.classConstant(descriptor));
		}
		final ExceptionsAttribute attr = new ExceptionsAttribute(entries);
		setAttribute(attr);
	}

	/**
	 * Declare the {@linkplain Throwable checked exceptions} that the
	 * {@linkplain Method method} may throw.
	 *
	 * @param types
	 *        The {@linkplain Class types} of the checked exceptions.
	 */
	@SafeVarargs
	public final void setCheckedExceptions (
		final Class<? extends Throwable>... types)
	{
		final List<ClassEntry> entries = new ArrayList<>(types.length);
		for (final Class<? extends Throwable> type : types)
		{
			entries.add(constantPool.classConstant(type));
		}
		final ExceptionsAttribute attr = new ExceptionsAttribute(entries);
		setAttribute(attr);
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
		final LocalVariable itself = self;
		assert itself != null;
		return itself;
	}

	/** The {@linkplain Set set} of {@linkplain LocalVariable parameters}. */
	private final Set<LocalVariable> parameters = new HashSet<>();

	/**
	 * Bind the specified name to the next unbound parameter, and answer a new
	 * {@linkplain LocalVariable local variable} that represents that parameter.
	 * The type of the parameter is inferred from the method's {@linkplain
	 * #descriptor() descriptor}.
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
		Scope currentScope = scopeStack.peekFirst();
		if (currentScope == null)
		{
			currentScope = new Scope(parameterIndex);
			scopeStack.add(currentScope);
		}
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

	/**
	 * The {@linkplain Map map} from label names to {@linkplain Label labels}.
	 */
	private final Map<String, Label> labelsByName = new HashMap<>();

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
		assert !labelsByName.containsKey(name);
		final Label label = new Label(name);
		labelsByName.put(name, label);
		return label;
	}

	/**
	 * Introduce a new {@linkplain HandlerLabel exception handler label} with
	 * the specified name, but do not emit it to the {@linkplain
	 * InstructionWriter instruction stream}. This label can also be the target
	 * of a branch.
	 *
	 * @param name
	 *        The name of the label.
	 * @param throwableDescriptor
	 *        A {@link Throwable descriptor}.
	 * @return A new label.
	 */
	public HandlerLabel newHandlerLabel (
		final String name,
		final String throwableDescriptor)
	{
		assert !labelsByName.containsKey(name);
		final HandlerLabel label = new HandlerLabel(name, throwableDescriptor);
		labelsByName.put(name, label);
		return label;
	}

	/**
	 * Introduce a new {@linkplain HandlerLabel exception handler label} with
	 * the specified name, but do not emit it to the {@linkplain
	 * InstructionWriter instruction stream}. This label can also be the target
	 * of a branch.
	 *
	 * @param name
	 *        The name of the label.
	 * @param throwableClass
	 *        A {@linkplain Class subclass} of {@link Throwable}.
	 * @return A new label.
	 */
	public HandlerLabel newHandlerLabel (
		final String name,
		final Class<? extends Throwable> throwableClass)
	{
		return newHandlerLabel(name, JavaDescriptors.forType(throwableClass));
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
	 * Introduce a new anonymous {@linkplain HandlerLabel exception handler
	 * label}, but do not emit it to the {@linkplain InstructionWriter
	 * instruction stream}. This label can be the target of a branch.
	 *
	 * @param throwableDescriptor
	 *        A {@link Throwable descriptor}.
	 * @return A new label.
	 */
	public HandlerLabel newHandlerLabel (final String throwableDescriptor)
	{
		final String name = String.format(
			anonymousLabelPattern, labelOrdinal++);
		return newHandlerLabel(name, throwableDescriptor);
	}

	/**
	 * Introduce a new anonymous {@linkplain HandlerLabel exception handler
	 * label}, but do not emit it to the {@linkplain InstructionWriter
	 * instruction stream}. This label can be the target of a branch.
	 *
	 * @param throwableClass
	 *        A {@linkplain Class subclass} of {@link Throwable}.
	 * @return A new label.
	 */
	public HandlerLabel newHandlerLabel (
		final Class<? extends Throwable> throwableClass)
	{
		final String name = String.format(
			anonymousLabelPattern, labelOrdinal++);
		return newHandlerLabel(name, JavaDescriptors.forType(throwableClass));
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
	 * Answer the first {@linkplain LocalVariable local variable} slot that
	 * does not correspond to the self reference or a parameter.
	 *
	 * @return The first local variable slot.
	 */
	private int firstLocalVariableSlot ()
	{
		return JavaDescriptors.slotUnits(descriptor()) +
			(modifiers.contains(STATIC) ? 0 : 1);
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
		assert parameterIndex == firstLocalVariableSlot();
		Scope currentScope = scopeStack.peekFirst();
		if (currentScope == null)
		{
			currentScope = new Scope(parameterIndex);
			scopeStack.add(currentScope);
		}
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
		assert parameterIndex == firstLocalVariableSlot();
		final String descriptor = JavaDescriptors.forType(type);
		return newLocalVariable(name, descriptor);
	}

	/**
	 * Emit a no-op.
	 */
	public void noOp ()
	{
		writer.append(nop.create());
	}

	/**
	 * Emit code to discard a {@linkplain JavaOperand#CATEGORY_1 Category 1}
	 * operand at the top of the operand stack.
	 */
	public void pop ()
	{
		writer.append(pop.create());
	}

	/**
	 * Emit code to discard <em>1)</em> a {@linkplain JavaOperand#CATEGORY_2
	 * Category 2} {@linkplain JavaOperand operand} at the top of the operand
	 * stack or <em>2)</em> two {@linkplain JavaOperand#CATEGORY_1 Category 1}
	 * operands at the top of the operand stack.
	 */
	public void pop2 ()
	{
		writer.append(pop2.create());
	}

	/**
	 * Emit code to duplicate the {@linkplain JavaOperand#CATEGORY_1 Category 1}
	 * {@linkplain JavaOperand operand} at the top of the operand stack.
	 */
	public void dup ()
	{
		writer.append(dup.create());
	}

	/**
	 * Emit code to duplicate the {@linkplain JavaOperand#CATEGORY_1 Category 1}
	 * {@linkplain JavaOperand operand} at the top of the operand stack
	 * <em>beneath</em> the next operand.
	 */
	public void dupX1 ()
	{
		writer.append(dup_x1.create());
	}

	/**
	 * Emit code to duplicate the {@linkplain JavaOperand#CATEGORY_1 Category 1}
	 * {@linkplain JavaOperand operand} at the top of the operand stack
	 * <em>beneath</em> <em>1)</em> the next two Category 1 operands or
	 * <em>2)</em> the next {@linkplain JavaOperand#CATEGORY_2 Category 2}
	 * operand.
	 */
	public void dupX2 ()
	{
		writer.append(dup_x2.create());
	}

	/**
	 * Emit code to duplicate <em>1)</em> a {@linkplain JavaOperand#CATEGORY_2
	 * Category 2} {@linkplain JavaOperand operand} at the top of the operand
	 * stack or <em>2)</em> two {@linkplain JavaOperand#CATEGORY_1 Category 1}
	 * operands at the top of the operand stack.
	 */
	public void dup2 ()
	{
		writer.append(dup2.create());
	}

	/**
	 * Emit code to duplicate <em>1)</em> a {@linkplain JavaOperand#CATEGORY_2
	 * Category 2} {@linkplain JavaOperand operand} at the top of the operand
	 * stack <em>beneath</em> the next {@linkplain JavaOperand#CATEGORY_1
	 * Category 1} operand or <em>2)</em> two Category 1 operands at the top of
	 * the operand stack <em>beneath</em> the third Category 1 operand.
	 */
	public void dup2X1 ()
	{
		writer.append(dup2_x1.create());
	}

	/**
	 * Emit code to duplicate <em>1)</em> a {@linkplain JavaOperand#CATEGORY_2
	 * Category 2} {@linkplain JavaOperand operand} at the top of the operand
	 * stack <em>beneath</em> the next Category 2 operand, <em>2)</em> a
	 * Category 2 operand at the top of the operand stack <em>beneath</em> the
	 * next two {@linkplain JavaOperand#CATEGORY_1 Category 1} operands,
	 * <em>3)</em> two Category 1 operands at the top of the operand stack
	 * <em>beneath</em> the third Category 2 operand, or <em>4)</em> two
	 * Category 1 operands at the top of the operand stack <em>beneath</em> the
	 * third and fourth Category 1 operand.
	 */
	public void dup2X2 ()
	{
		writer.append(dup2_x2.create());
	}

	/**
	 * Emit code to swap the top two operands. Each operand must be a
	 * {@linkplain JavaOperand#CATEGORY_1 Category 1} operand.
	 */
	public void swap ()
	{
		writer.append(swap.create());
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
		{iconst_m1, iconst_0, iconst_1, iconst_2, iconst_3, iconst_4, iconst_5};

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
			constantPool.classConstant(value)));
	}

	/**
	 * Emit code to push the specified class descriptor onto the operand stack.
	 *
	 * @param classEntry
	 *        A {@linkplain ClassEntry class entry}.
	 */
	public void pushConstant (final ClassEntry classEntry)
	{
		writer.append(new LoadConstantInstruction(classEntry));
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
	 * Emit code to pop an array from the operand stack and push its size.
	 */
	public void arraySize ()
	{
		writer.append(arraylength.create());
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
	 * Emit code to create an array of the specified type.
	 *
	 * @param classEntry
	 *        A {@linkplain ClassEntry class entry} that represents an array
	 *        with at least the specified dimensionality.
	 * @param dimensions
	 *        The dimensionality of the array.
	 */
	public void newArray (
		final ClassEntry classEntry,
		final int dimensions)
	{
		assert dimensions > 0 && dimensions < 256;
		writer.append(new NewObjectArrayInstruction(classEntry, dimensions));
	}

	/**
	 * Emit code to create an array of the specified type.
	 *
	 * @param classEntry
	 *        The {@linkplain ClassEntry class entry}.
	 */
	public void newArray (final ClassEntry classEntry)
	{
		newArray(classEntry, 1);
	}

	/**
	 * Emit code to create an array of the specified type.
	 *
	 * @param descriptor
	 *        A non-array type descriptor.
	 * @param dimensions
	 *        The dimensionality of the array.
	 */
	public void newArray (final String descriptor, final int dimensions)
	{
		assert dimensions > 0 && dimensions < 256;
		final Class<?> type = JavaDescriptors.typeForDescriptor(descriptor);
		if (type.isPrimitive() && dimensions == 1)
		{
			newArray(type, 1);
		}
		else
		{
			final String arrayDescriptor = JavaDescriptors.forArrayOf(
				descriptor, dimensions);
			final ClassEntry classEntry =
				constantPool.classConstant(arrayDescriptor);
			newArray(classEntry, dimensions);
		}
	}

	/**
	 * Emit code to create an array of the specified type.
	 *
	 * @param descriptor
	 *        A non-array type descriptor.
	 */
	public void newArray (final String descriptor)
	{
		final Class<?> type = JavaDescriptors.typeForDescriptor(descriptor);
		if (type.isPrimitive())
		{
			newArray(type, 1);
		}
		else
		{
			final String name = JavaDescriptors.nameFromDescriptor(descriptor);
			final ClassEntry classEntry = constantPool.classConstant(name);
			newArray(classEntry, 1);
		}
	}

	/**
	 * Emit code to create an array of the specified type.
	 *
	 * @param elementType
	 *        The {@linkplain Class element type} of the array.
	 * @param dimensions
	 *        The dimensionality of the array.
	 */
	public void newArray (
		final Class<?> elementType,
		final int dimensions)
	{
		assert dimensions > 0 && dimensions < 256;
		if (elementType.isPrimitive() && dimensions == 1)
		{
			assert elementType != Void.TYPE;
			writer.append(new NewArrayInstruction(elementType));
		}
		else
		{
			final String descriptor = JavaDescriptors.forType(elementType);
			final String arrayDescriptor = JavaDescriptors.forArrayOf(
				descriptor, dimensions);
			final ClassEntry classEntry =
				constantPool.classConstant(arrayDescriptor);
			newArray(classEntry, dimensions);
		}
	}

	/**
	 * Emit code to create an array of the specified type.
	 *
	 * @param elementType
	 *        The {@linkplain Class element type} of the array.
	 */
	public void newArray (final Class<?> elementType)
	{
		newArray(elementType, 1);
	}

	/**
	 * Emit code to instantiate an object of the specified type and to duplicate
	 * its reference on the operand stack.
	 *
	 * <p>{@code N.B.} The constructor {@code "<init>"} that is executed by
	 * {@link JavaBytecode#invokespecial invokespecial} to finish initialization
	 * of the new object will consume the top operand, which is why {@link
	 * JavaBytecode#dup dup} is always emitted after {@link JavaBytecode#new_
	 * new}.</p>
	 *
	 * @param classEntry
	 *        The {@linkplain ClassEntry class entry} of the target class.
	 */
	public void newObject (final ClassEntry classEntry)
	{
		writer.append(new NewObjectInstruction(classEntry));
		writer.append(dup.create());
	}

	/**
	 * Emit code to instantiate an object of the specified type and to duplicate
	 * its reference on the operand stack.
	 *
	 * <p>{@code N.B.} The constructor {@code "<init>"} that is executed by
	 * {@link JavaBytecode#invokespecial invokespecial} to finish initialization
	 * of the new object will consume the top operand, which is why {@link
	 * JavaBytecode#dup dup} is always emitted after {@link JavaBytecode#new_
	 * new}.</p>
	 *
	 * @param name
	 *        The fully-qualified name of the target {@linkplain Class class}.
	 */
	public void newObject (final String name)
	{
		final ClassEntry classEntry = constantPool.classConstant(name);
		newObject(classEntry);
	}

	/**
	 * Emit code to instantiate an object of the specified type and to duplicate
	 * its reference on the operand stack.
	 *
	 * <p>{@code N.B.} The constructor {@code "<init>"} that is executed by
	 * {@link JavaBytecode#invokespecial invokespecial} to finish initialization
	 * of the new object will consume the top operand, which is why {@link
	 * JavaBytecode#dup dup} is always emitted after {@link JavaBytecode#new_
	 * new}.</p>
	 *
	 * @param type
	 *        The target {@linkplain Class type}.
	 */
	public void newObject (final Class<?> type)
	{
		assert !type.isPrimitive();
		final ClassEntry classEntry = constantPool.classConstant(type);
		newObject(classEntry);
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
	 * Emit code to increment the specified {@code int} {@linkplain
	 * LocalVariable variable} by the specified constant amount.
	 *
	 * @param var
	 *        A local variable.
	 * @param delta
	 *        The constant amount by which the contents of the variable should
	 *        be adjusted.
	 */
	public void increment (final LocalVariable var, final int delta)
	{
		assert JavaDescriptors.typeForDescriptor(var.descriptor())
			== Integer.TYPE;
		writer.append(new IncrementInstruction(var, delta));
	}

	/**
	 * Emit code to implement the specified {@linkplain UnaryOperator unary
	 * operator} for the top operand.
	 *
	 * @param op
	 *        A unary operator.
	 * @param type
	 *        The expected {@linkplain Class#isPrimitive() primitive type} of
	 *        the top operand.
	 */
	public void doOperator (final UnaryOperator op, final Class<?> type)
	{
		assert type.isPrimitive();
		op.emitOn(type, writer);
	}

	/**
	 * Emit code to implement the specified {@linkplain BinaryOperator binary
	 * operator} for the top operand.
	 *
	 * @param op
	 *        A binary operator.
	 * @param type
	 *        The expected {@linkplain Class#isPrimitive() primitive type} of
	 *        the top operands.
	 */
	public void doOperator (final BinaryOperator op, final Class<?> type)
	{
		assert type.isPrimitive();
		op.emitOn(type, writer);
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
	 * Emit an instruction that will ascertain that the top of the operand
	 * stack is an instance of the specified {@linkplain Class class}.
	 *
	 * @param classEntry
	 *        The {@linkplain ClassEntry class entry} of the target type.
	 */
	public void instanceOf (final ClassEntry classEntry)
	{
		writer.append(new InstanceofInstruction(classEntry));
	}

	/**
	 * Emit an instruction that will ascertain that the top of the operand
	 * stack is an instance of the specified {@linkplain Class class}.
	 *
	 * @param name
	 *        The fully-qualified name of the target type.
	 */
	public void instanceOf (final String name)
	{
		final ClassEntry classEntry = constantPool.classConstant(name);
		instanceOf(classEntry);
	}

	/**
	 * Emit an instruction that will ascertain that the top of the operand
	 * stack is an instance of the specified {@linkplain Class class}.
	 *
	 * @param type
	 *        The target type.
	 */
	public void instanceOf (final Class<?> type)
	{
		final String name = type.getName();
		final ClassEntry classEntry = constantPool.classConstant(name);
		instanceOf(classEntry);
	}


	/**
	 * Emit an instruction that will ensure that the top of the operand
	 * stack is an instance of the specified {@linkplain Class class}.
	 *
	 * @param classEntry
	 *        The {@linkplain ClassEntry class entry} of the target type.
	 */
	public void checkCast (final ClassEntry classEntry)
	{
		writer.append(new CheckCastInstruction(classEntry));
	}

	/**
	 * Emit an instruction that will ensure that the top of the operand
	 * stack is an instance of the specified {@linkplain Class class}.
	 *
	 * @param name
	 *        The fully-qualified name of the target type.
	 */
	public void checkCast (final String name)
	{
		final ClassEntry classEntry = constantPool.classConstant(name);
		checkCast(classEntry);
	}

	/**
	 * Emit an instruction that will ensure that the top of the operand
	 * stack is an instance of the specified {@linkplain Class class}.
	 *
	 * @param type
	 *        The target type.
	 */
	public void checkCast (final Class<?> type)
	{
		final String name = type.getName();
		final ClassEntry classEntry = constantPool.classConstant(name);
		checkCast(classEntry);
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
	 * Emit a conditional branch that performs the comparison specified by the
	 * {@linkplain PrimitiveComparisonOperator operator} and jumps to the
	 * given {@linkplain Label label} if the operation evaluates to {@code
	 * false}.
	 *
	 * @param op
	 *        The comparison operator.
	 * @param notTaken
	 *        The label to which control should proceed if the comparison
	 *        operator evaluates to {@code false}.
	 */
	public void branchUnless (
		final PrimitiveComparisonOperator op,
		final Label notTaken)
	{
		final JavaBytecode bytecode = op.inverseBytecode();
		writer.append(new ConditionalBranchInstruction(bytecode, notTaken));
	}

	/**
	 * Emit a conditional branch that performs the comparison specified by the
	 * {@linkplain ReferenceComparisonOperator operator} and jumps to the
	 * given {@linkplain Label label} if the operation evaluates to {@code
	 * false}.
	 *
	 * @param op
	 *        The comparison operator.
	 * @param notTaken
	 *        The label to which control should proceed if the comparison
	 *        operator evaluates to {@code false}.
	 */
	public void branchUnless (
		final ReferenceComparisonOperator op,
		final Label notTaken)
	{
		final JavaBytecode bytecode = op.inverseBytecode();
		writer.append(new ConditionalBranchInstruction(bytecode, notTaken));
	}

	/**
	 * Emit code to produce a lookup switch.
	 *
	 * @param keys
	 *        The keys for the switch.
	 * @param labels
	 *        The case {@linkplain Label labels} for the switch.
	 * @param defaultLabel
	 *        The default label for the switch.
	 */
	public void lookupSwitch (
		final List<Integer> keys,
		final List<Label> labels,
		final Label defaultLabel)
	{
		final int size = keys.size();
		assert labels.size() == size;
		final int[] keysArray = new int[size];
		for (int i = 0; i < size; i++)
		{
			keysArray[i] = keys.get(i);
		}
		final Label[] labelsArray = labels.toArray(new Label[labels.size()]);
		writer.append(
			new LookupSwitchInstruction(keysArray, labelsArray, defaultLabel));
	}

	/**
	 * Emit code to produce a table switch.
	 *
	 * @param lowerBound
	 *        The lower bound of the contiguous integral range.
	 * @param upperBound
	 *        The upper bound of the contiguous integral range.
	 * @param labels
	 *        The case {@linkplain Label labels} for the switch.
	 * @param defaultLabel
	 *        The default label for the switch.
	 */
	public void tableSwitch (
		final int lowerBound,
		final int upperBound,
		final List<Label> labels,
		final Label defaultLabel)
	{
		final Label[] array = labels.toArray(new Label[labels.size()]);
		writer.append(new TableSwitchInstruction(
			lowerBound, upperBound, array, defaultLabel));
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
	 * Emit code to pop a {@linkplain Throwable throwable} from the operand
	 * stack and throw it.
	 */
	public void throwException ()
	{
		writer.append(athrow.create());
	}

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
	 *        The {@linkplain HandlerLabel label} of the handler subroutine.
	 * @param catchEntry
	 *        The {@linkplain ClassEntry class entry} for the {@linkplain
	 *        Throwable throwable} type.
	 */
	public void addGuardedZone (
		final Label startLabel,
		final Label endLabel,
		final HandlerLabel handlerLabel,
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
	 *        The {@linkplain HandlerLabel label} of the handler subroutine.
	 * @param throwableName
	 *        The fully-qualified name of the intercepted {@linkplain Throwable
	 *        throwable} type.
	 */
	public void addGuardedZone (
		final Label startLabel,
		final Label endLabel,
		final HandlerLabel handlerLabel,
		final String throwableName)
	{
		final ClassEntry catchEntry =
			constantPool.classConstant(throwableName);
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
	 *        The {@linkplain HandlerLabel label} of the handler subroutine.
	 * @param throwableType
	 *        The {@linkplain Throwable throwable} {@linkplain Class type}.
	 */
	public void addGuardedZone (
		final Label startLabel,
		final Label endLabel,
		final HandlerLabel handlerLabel,
		final Class<? extends Throwable> throwableType)
	{
		final ClassEntry catchEntry =
			constantPool.classConstant(throwableType);
		addGuardedZone(startLabel, endLabel, handlerLabel, catchEntry);
	}

	/**
	 * Has every {@linkplain Label label} been placed?
	 *
	 * @return {@code true} if every label that was allocated has also been
	 *         used, {@code false} otherwise.
	 */
	private boolean allLabelsAreUsed ()
	{
		for (final Label label : labelsByName.values())
		{
			if (!label.hasValidAddress())
			{
				return false;
			}
		}
		return true;
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
		assert allLabelsAreUsed();
		if (!modifiers.contains(ABSTRACT))
		{
			final CodeAttribute code = new CodeAttribute(
				this, Collections.<Attribute>emptyList());
			setAttribute(code);
		}
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

	/**
	 * Answer a textual representation of the {@linkplain InstructionWriter
	 * instruction stream}.
	 *
	 * @return The requested text.
	 */
	String instructionsText ()
	{
		return writer.toString();
	}

	@Override
	public String toString ()
	{
		@SuppressWarnings("resource")
		final Formatter formatter = new Formatter();
		final String mods = MethodModifier.toString(modifiers);
		formatter.format("%s%s", mods, mods.isEmpty() ? "" : " ");
		formatter.format("%s : %s", name(), descriptor());
		// Make sure that the Code attribute always comes first.
		final Comparator<Attribute> comparator = new Comparator<Attribute>()
		{
			@Override
			public int compare (
				final @Nullable Attribute a1,
				final @Nullable Attribute a2)
			{
				assert a1 != null;
				assert a2 != null;
				if (a1.name().equals(CodeAttribute.name))
				{
					return -1;
				}
				if (a2.name().equals(CodeAttribute.name))
				{
					return 1;
				}
				return a1.name().compareTo(a2.name());
			}
		};
		final List<Attribute> attributes =
			new ArrayList<>(attributes().values());
		// If necessary, synthesize a Code attribute prior to sorting.
		if (!attributes().containsKey(CodeAttribute.name))
		{
			final CodeAttribute code =
				new CodeAttribute(this, Collections.<Attribute>emptyList());
			attributes.add(code);
		}
		Collections.sort(attributes, comparator);
		for (final Attribute attribute : attributes)
		{
			final String text = attribute.toString().replaceAll(
				String.format("%n"), String.format("%n\t"));
			formatter.format("%n\t%s", text);
		}
		return formatter.toString();
	}
}
