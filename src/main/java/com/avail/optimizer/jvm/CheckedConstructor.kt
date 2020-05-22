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

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Type.getConstructorDescriptor;
import static org.objectweb.asm.Type.getInternalName;

/**
 * A helper class for referring to a constructor.  It verifies at construction time that the referenced method is marked with the {@link ReferencedInGeneratedCode} annotation, and that the specified argument types are correct.  When it generates a call, it uses the appropriate invoke instruction.
 *
 * <p>The main power this class brings is the ability to check the type signature prior to code generation, rather than have the JVM verifier be the failure point.  It also fails early if the referenced method isn't marked with {@link ReferencedInGeneratedCode}, helping to keep critical methods from changing or disappearing under maintenance.  It also keeps method names out of the code generator, making it easier to find the real uses of methods in generator code.</p>
 *
 * <p>Factory methods that indicate the method is part of the Java library are not expected to have the {@link ReferencedInGeneratedCode} annotation.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class CheckedConstructor
{
	/**
	 * Create a {@code CheckedMethod} for invoking a constructor method that has been annotated with {@link ReferencedInGeneratedCode}, failing if there is a problem.
	 *
	 * @param receiverClass
	 *        The type of the receiver of the method.
	 * @param argumentTypes
	 *        A vararg array of argument types.
	 * @return
	 * The {@code CheckedMethod}.
	 */
	public static CheckedConstructor constructorMethod (
		final Class<?> receiverClass,
		final Class<?>... argumentTypes)
	{
		return new CheckedConstructor(
			true,
			receiverClass,
			argumentTypes);
	}

	/**
	 * Create a {@code CheckedMethod} for invoking an instance method that cannot have a {@link ReferencedInGeneratedCode} annotation, failing if there is a problem.
	 *
	 * @param receiverClass
	 * The type of the receiver of the method.
	 * @param argumentTypes
	 * A vararg array of argument types.
	 * @return
	 * The {@code CheckedMethod}.
	 */
	public static CheckedConstructor javaLibraryConstructorMethod (
		final Class<?> receiverClass,
		final Class<?>... argumentTypes)
	{
		return new CheckedConstructor(
			false,
			receiverClass,
			argumentTypes);
	}

	/**
	 * Create a {@code CheckedConstructor}, reflecting as needed to verify and
	 * precompute as much as possible.
	 *
	 * @param verifyAnnotation
	 *        Whether to look for the {@link ReferencedInGeneratedCode} annotation.
	 * @param receiverClass
	 *        The type of the receiver of the method.
	 * @param argumentTypes
	 *        A vararg array of argument types.
	 */
	private CheckedConstructor (
		final boolean verifyAnnotation,
		final Class<?> receiverClass,
		final Class<?>... argumentTypes)
	{
		this.methodNameString = "<init>";
		final Constructor<?> constructor;
		try
		{
			constructor = receiverClass.getConstructor(argumentTypes);
		}
		catch (final NoSuchMethodException | SecurityException e)
		{
			throw new RuntimeException(e);
		}
		if (verifyAnnotation)
		{
			// Check the annotation before anything else, in case we selected
			// the wrong constructor.
			final @Nullable ReferencedInGeneratedCode annotation =
				constructor.getAnnotation(ReferencedInGeneratedCode.class);
			assert annotation != null :
				"Constructor should have had ReferencedInGeneratedCode "
				+ "annotation";
		}
		final int modifiers = constructor.getModifiers();
		assert (modifiers & Modifier.PUBLIC) != 0;
		receiverClassInternalName =
			getInternalName(constructor.getDeclaringClass());
		methodDescriptorString = getConstructorDescriptor(constructor);
	}

	/** The simple name of the method. */
	private final String methodNameString;

	/** The canonical name of the class in which the method is defined. */
	private final String receiverClassInternalName;

	/** The canonical name of the method arguments. */
	private final String methodDescriptorString;

	/**
	 * Emit a call to this method on the given {@link MethodVisitor}.  The arguments must already be ready on the stack.
	 *
	 * @param methodVisitor
	 *        Where to write the call.
	 */
	public void generateCall (final MethodVisitor methodVisitor)
	{
		methodVisitor.visitMethodInsn(
			INVOKESPECIAL,
			receiverClassInternalName,
			methodNameString,
			methodDescriptorString,
			false);
	}
}
