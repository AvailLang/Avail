/*
 * CheckedField.java
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

package com.avail.optimizer.jvm;

import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Type.getDescriptor;
import static org.objectweb.asm.Type.getInternalName;

/**
 * A helper class for referring to a field.  It verifies at construction
 * time that the referenced field is marked with the
 * {@link ReferencedInGeneratedCode} annotation, and that the specified type
 * agrees.  When it generates a read (getfield/getstatic) or write
 * (putfield/putstatic),, it uses the appropriate instruction.  It substitutes a
 * literal if the field is final, and forbids generating a write to it.
 *
 * <p>The main power this class brings is the ability to check the type
 * signature prior to code generation, rather than have the JVM verifier be
 * the failure point.  It also fails early if the referenced field isn't
 * marked with {@link ReferencedInGeneratedCode}, helping to keep critical
 * fields from changing or disappearing under maintenance.  It also keeps
 * field names out of the code generator, making it easier to find the real
 * uses of fields in generator code.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class CheckedField
{
	/**
	 * Create a {@code CheckedField} for accessing an instance field that has
	 * been annotated with {@link ReferencedInGeneratedCode}, failing if there
	 * is a problem.
	 *
	 * @param receiverClass The type of the object containing the field.
	 * @param fieldName The name of the field.
	 * @param fieldClass The type of the field.
	 * @return The {@code CheckedField}.
	 */
	public static CheckedField instanceField (
		final Class<?> receiverClass,
		final String fieldName,
		final Class<?> fieldClass)
	{
		return new CheckedField(
			true,
			false,
			receiverClass,
			fieldName,
			fieldClass);
	}

	/**
	 * Create a {@code CheckedField} for accessing a static field that has been
	 * annotated with {@link ReferencedInGeneratedCode}, failing if there is a
	 * problem.
	 *
	 * @param receiverClass The type defining the static field.
	 * @param fieldName The name of the static field.
	 * @param fieldClass The type of the field.
	 * @return The {@code CheckedField}.
	 */
	public static CheckedField staticField (
		final Class<?> receiverClass,
		final String fieldName,
		final Class<?> fieldClass)
	{
		return new CheckedField(
			true,
			true,
			receiverClass,
			fieldName,
			fieldClass);
	}

	/**
	 * Create a {@code CheckedField} for accessing an {@code enum} instance that
	 * has been annotated with {@link ReferencedInGeneratedCode}, failing if
	 * there is a problem.
	 *
	 * @param <T> The {@code enum} type.
	 * @param enumInstance The {@code enum} value.
	 * @return The {@code CheckedField}.
	 */
	public static <T extends Enum<T>> CheckedField enumField (
		final T enumInstance)
	{
		final Class<?> enumClass = enumInstance.getDeclaringClass();
		return new CheckedField(
			false,
			true,
			enumClass,
			enumInstance.name(),
			enumClass);
	}

	/**
	 * Create a {@code CheckedField} for accessing an instance field that has
	 * not been annotated with {@link ReferencedInGeneratedCode}, failing if
	 * there is a problem.
	 *
	 * @param receiverClass The type of the object containing the field.
	 * @param fieldName The name of the field.
	 * @param fieldClass The type of the field.
	 * @return The {@code CheckedField}.
	 */
	public static CheckedField javaLibraryInstanceField (
		final Class<?> receiverClass,
		final String fieldName,
		final Class<?> fieldClass)
	{
		return new CheckedField(
			false,
			false,
			receiverClass,
			fieldName,
			fieldClass);
	}

	/**
	 * Create a {@code CheckedField} for accessing a static field that has not
	 * been annotated with {@link ReferencedInGeneratedCode}, failing if there
	 * is a problem.
	 *
	 * @param receiverClass The type defining the static field.
	 * @param fieldName The name of the static field.
	 * @param fieldClass The type of the field.
	 * @return The {@code CheckedField}.
	 */
	public static CheckedField javaLibraryStaticField (
		final Class<?> receiverClass,
		final String fieldName,
		final Class<?> fieldClass)
	{
		return new CheckedField(
			false,
			true,
			receiverClass,
			fieldName,
			fieldClass);
	}

	/**
	 * Create a {@code CheckedMethod}, reflecting as needed to verify and
	 * precompute as much as possible.
	 *
	 * @param verifyAnnotation
	 *        Whether to look for the {@link ReferencedInGeneratedCode}
	 *        annotation.
	 * @param isStatic Whether the field is expected to be static.
	 * @param receiverClass The class in which the field should be found.
	 * @param fieldName The name of the field.
	 * @param fieldClass The type of the field.
	 */
	private CheckedField (
		final boolean verifyAnnotation,
		final boolean isStatic,
		final Class<?> receiverClass,
		final String fieldName,
		final Class<?> fieldClass)
	{
		this.isStatic = isStatic;
		this.fieldNameString = fieldName;
		final Field field;
		try
		{
			field = receiverClass.getField(fieldName);
		}
		catch (final NoSuchFieldException | SecurityException e)
		{
			throw new RuntimeException(e);
		}
		if (verifyAnnotation)
		{
			// Check the annotation before anything else, in case we selected
			// the wrong method.
			final @Nullable ReferencedInGeneratedCode annotation =
				field.getAnnotation(ReferencedInGeneratedCode.class);
			assert annotation != null :
				"Field "
					+ fieldName
					+ " should have had ReferencedInGeneratedCode annotation";
		}
		final int modifiers = field.getModifiers();
		assert (modifiers & Modifier.PUBLIC) != 0;
		assert ((modifiers & Modifier.STATIC) != 0) == isStatic;
		isFinal = (modifiers & Modifier.FINAL) != 0;
		final Class<?> actualFieldType = field.getType();
		assert fieldClass.equals(actualFieldType);
		receiverClassInternalName = getInternalName(field.getDeclaringClass());
		fieldTypeDescriptorString = getDescriptor(field.getType());
		if (isStatic && isFinal)
		{
			try
			{
				valueIfFinalAndStatic = field.get(null);
			}
			catch (final IllegalAccessException e)
			{
				throw new RuntimeException(e);
			}
		}
		else
		{
			valueIfFinalAndStatic = null;
		}
	}

	/** Whether the method is static. */
	private final boolean isStatic;

	/** Whether the method is final. */
	private final boolean isFinal;

	/** If the field is static and final, this is the value of the field. */
	private final @Nullable Object valueIfFinalAndStatic;

	/** The simple name of the method. */
	private final String fieldNameString;

	/** The canonical name of the class in which the method is defined. */
	private final String receiverClassInternalName;

	/** The canonical name of the method arguments. */
	private final String fieldTypeDescriptorString;

	/**
	 * Emit a read of this field.  The receiver, if this is not static, must
	 * already be on the stack.
	 *
	 * @param methodVisitor Which {@link MethodVisitor} to emit the read into.
	 */
	public void generateRead (
		final MethodVisitor methodVisitor)
	{
		if (isStatic)
		{
			if (isFinal && valueIfFinalAndStatic == null)
			{
				methodVisitor.visitInsn(ACONST_NULL);
			}
			else
			{
				methodVisitor.visitFieldInsn(
					GETSTATIC,
					receiverClassInternalName,
					fieldNameString,
					fieldTypeDescriptorString);
			}
		}
		else
		{
			methodVisitor.visitFieldInsn(
				GETFIELD,
				receiverClassInternalName,
				fieldNameString,
				fieldTypeDescriptorString);
		}
	}


	/**
	 * Emit a write of this field for the given {@link JVMTranslator} and
	 * {@link MethodVisitor}.  The receiver, if any, and the new field value
	 * must already be on the stack.  Fail right away if the field is final.
	 *
	 * @param methodVisitor Which {@link MethodVisitor} to emit the read into.
	 */
	public void generateWrite (
		final MethodVisitor methodVisitor)
	{
		assert !isFinal;
		if (isStatic)
		{
			methodVisitor.visitFieldInsn(
				PUTSTATIC,
				receiverClassInternalName,
				fieldNameString,
				fieldTypeDescriptorString);
		}
		else
		{
			methodVisitor.visitFieldInsn(
				PUTFIELD,
				receiverClassInternalName,
				fieldNameString,
				fieldTypeDescriptorString);
		}
	}
}
