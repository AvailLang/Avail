/*
 * CheckedMethod.java
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
import org.objectweb.asm.Opcodes;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;

/**
 * A helper class for referring to a method.  It verifies at construction
 * time that the referenced method is marked with the
 * {@link ReferencedInGeneratedCode} annotation, and that the specified
 * argument types are correct.  When it generates a call, it uses the
 * appropriate invoke instruction, and even generates a
 * {@link Opcodes#CHECKCAST} instruction if the result type isn't strong
 * enough.
 *
 * <p>The main power this class brings is the ability to check the type
 * signature prior to code generation, rather than have the JVM verifier be
 * the failure point.  It also fails early if the referenced method isn't
 * marked with {@link ReferencedInGeneratedCode}, helping to keep critical
 * methods from changing or disappearing under maintenance.  It also keeps
 * method names out of the code generator, making it easier to find the real
 * uses of methods in generator code.</p>
 *
 * <p>Factory methods that indicate the method is part of the Java library are
 * not expected to have the {@link ReferencedInGeneratedCode} annotation.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class CheckedMethod
{
	/**
	 * Create a {@code CheckedMethod} for invoking an instance method that has
	 * been annotated with {@link ReferencedInGeneratedCode}, failing if there
	 * is a problem.
	 *
	 * @param receiverClass The type of the receiver of the method.
	 * @param methodName The name of the method.
	 * @param returnClass The required return type.
	 * @param argumentTypes A vararg array of argument types.
	 * @return The {@code CheckedMethod}.
	 */
	public static CheckedMethod instanceMethod (
		final Class<?> receiverClass,
		final String methodName,
		final Class<?> returnClass,
		final Class<?>... argumentTypes)
	{
		return new CheckedMethod(
			true,
			false,
			receiverClass,
			methodName,
			returnClass,
			argumentTypes);
	}

	/**
	 * Create a {@code CheckedMethod} for invoking a static method that has
	 * been annotated with {@link ReferencedInGeneratedCode}, failing if there
	 * is a problem.
	 *
	 * @param receiverClass The type of the receiver of the method.
	 * @param methodName The name of the method.
	 * @param returnClass The required return type.
	 * @param argumentTypes A vararg array of argument types.
	 * @return The {@code CheckedMethod}.
	 */
	public static CheckedMethod staticMethod (
		final Class<?> receiverClass,
		final String methodName,
		final Class<?> returnClass,
		final Class<?>... argumentTypes)
	{
		return new CheckedMethod(
			true,
			true,
			receiverClass,
			methodName,
			returnClass,
			argumentTypes);
	}

	/**
	 * Create a {@code CheckedMethod} for invoking an instance method that
	 * cannot have a {@link ReferencedInGeneratedCode} annotation, failing if
	 * there is a problem.
	 *
	 * @param receiverClass The type of the receiver of the method.
	 * @param methodName The name of the method.
	 * @param returnClass The required return type.
	 * @param argumentTypes A vararg array of argument types.
	 * @return The {@code CheckedMethod}.
	 */
	public static CheckedMethod javaLibraryInstanceMethod (
		final Class<?> receiverClass,
		final String methodName,
		final Class<?> returnClass,
		final Class<?>... argumentTypes)
	{
		return new CheckedMethod(
			false,
			false,
			receiverClass,
			methodName,
			returnClass,
			argumentTypes);
	}

	/**
	 * Create a {@code CheckedMethod} for invoking a static method that
	 * cannot have a {@link ReferencedInGeneratedCode} annotation, failing if
	 * there is a problem.
	 *
	 * @param receiverClass The type of the receiver of the method.
	 * @param methodName The name of the method.
	 * @param returnClass The required return type.
	 * @param argumentTypes A vararg array of argument types.
	 * @return The {@code CheckedMethod}.
	 */
	public static CheckedMethod javaLibraryStaticMethod (
		final Class<?> receiverClass,
		final String methodName,
		final Class<?> returnClass,
		final Class<?>... argumentTypes)
	{
		return new CheckedMethod(
			false,
			true,
			receiverClass,
			methodName,
			returnClass,
			argumentTypes);
	}

	/**
	 * Create a {@code CheckedMethod}, reflecting as needed to verify and
	 * precompute as much as possible.
	 *
	 * @param verifyAnnotation
	 *        Whether to look for the {@link ReferencedInGeneratedCode}
	 *        annotation.
	 * @param isStatic Whether the method is expected to be static.
	 * @param receiverClass The type of the receiver of the method.
	 * @param methodName The name of the method.
	 * @param returnClass The required return type.
	 * @param argumentTypes A vararg array of argument types.
	 */
	private CheckedMethod (
		final boolean verifyAnnotation,
		final boolean isStatic,
		final Class<?> receiverClass,
		final String methodName,
		final Class<?> returnClass,
		final Class<?>... argumentTypes)
	{
		this.isStatic = isStatic;
		this.methodNameString = methodName;
		final Method method;
		try
		{
			method = receiverClass.getMethod(methodName, argumentTypes);
		}
		catch (final NoSuchMethodException | SecurityException e)
		{
			throw new RuntimeException(e);
		}
		if (verifyAnnotation)
		{
			// Check the annotation before anything else, in case we selected
			// the wrong method.
			final @Nullable ReferencedInGeneratedCode annotation =
				method.getAnnotation(ReferencedInGeneratedCode.class);
			assert annotation != null :
				"Method "
					+ methodName
					+ " should have had ReferencedInGeneratedCode annotation";
		}
		final int modifiers = method.getModifiers();
		assert (modifiers & Modifier.PUBLIC) != 0;
		assert ((modifiers & Modifier.STATIC) != 0) == isStatic;
		final Class<?> methodReturnType = method.getReturnType();
		if (returnClass.isAssignableFrom(methodReturnType))
		{
			internalNameToCheckCastOrNull = null;
		}
		else
		{
			// For sanity, the type to check-cast-strengthen to should be a
			// subtype of the method's return type.
			assert methodReturnType.isAssignableFrom(returnClass);
			internalNameToCheckCastOrNull = getInternalName(returnClass);
		}
		receiverClassInternalName = getInternalName(method.getDeclaringClass());
		isInterface = method.getDeclaringClass().isInterface();
		methodDescriptorString = getMethodDescriptor(method);
	}

	/** Whether the method is static. */
	private final boolean isStatic;

	/** Whether the method is defined in an interface. */
	private final boolean isInterface;

	/** The simple name of the method. */
	private final String methodNameString;

	/** The canonical name of the class in which the method is defined. */
	private final String receiverClassInternalName;

	/** The canonical name of the method arguments. */
	private final String methodDescriptorString;

	/** The canonical name of the cast target, or null if no cast is needed. */
	private final @Nullable String internalNameToCheckCastOrNull;

	/**
	 * Emit a call to this method on the given {@link MethodVisitor}.  The
	 * arguments must already be ready on the stack.
	 *
	 * @param methodVisitor Where to write the call.
	 */
	public void generateCall (final MethodVisitor methodVisitor)
	{
		if (isStatic)
		{
			methodVisitor.visitMethodInsn(
				INVOKESTATIC,
				receiverClassInternalName,
				methodNameString,
				methodDescriptorString,
				false);
		}
		else
		{
			methodVisitor.visitMethodInsn(
				isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
				receiverClassInternalName,
				methodNameString,
				methodDescriptorString,
				isInterface);
		}
		if (internalNameToCheckCastOrNull != null)
		{
			methodVisitor.visitTypeInsn(
				CHECKCAST, internalNameToCheckCastOrNull);
		}
	}
}
