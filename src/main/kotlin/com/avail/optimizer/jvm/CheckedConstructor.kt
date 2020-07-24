/*
 * CheckedMethod.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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
package com.avail.optimizer.jvm

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier

/**
 * A helper class for referring to a constructor.  It verifies at construction
 * time that the referenced method is marked with the
 * [ReferencedInGeneratedCode] annotation, and that the specified argument types
 * are correct.  When it generates a call, it uses the appropriate invoke
 * instruction.
 *
 * The main power this class brings is the ability to check the type signature
 * prior to code generation, rather than have the JVM verifier be the failure
 * point.  It also fails early if the referenced method isn't marked with
 * [ReferencedInGeneratedCode], helping to keep critical methods from changing
 * or disappearing under maintenance.  It also keeps method names out of the
 * code generator, making it easier to find the real uses of methods in
 * generator code.
 *
 *
 * Factory methods that indicate the method is part of the Java library are not
 * expected to have the [ReferencedInGeneratedCode] annotation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Create a `CheckedConstructor`, reflecting as needed to verify and
 * precompute as much as possible.
 *
 * @param verifyAnnotation
 *   Whether to look for the [ReferencedInGeneratedCode] annotation.
 * @param receiverClass
 *   The type of the receiver of the method.
 * @param argumentTypes
 *   A vararg array of argument types.
 */
class CheckedConstructor private constructor(
	verifyAnnotation: Boolean,
	receiverClass: Class<*>,
	vararg argumentTypes: Class<*>)
{
	/** The simple name of the method.  */
	private val methodNameString = "<init>"

	/** The canonical name of the class in which the method is defined.  */
	private val receiverClassInternalName: String

	/** The canonical name of the method arguments.  */
	private val methodDescriptorString: String

	/**
	 * Emit a call to this method on the given [MethodVisitor]. The arguments
	 * must already be ready on the stack.
	 *
	 * @param methodVisitor
	 *   Where to write the call.
	 */
	fun generateCall(methodVisitor: MethodVisitor)
	{
		methodVisitor.visitMethodInsn(
			Opcodes.INVOKESPECIAL,
			receiverClassInternalName,
			methodNameString,
			methodDescriptorString,
			false)
	}

	companion object
	{
		/**
		 * Create a `CheckedMethod` for invoking a constructor method that has
		 * been annotated with [ReferencedInGeneratedCode], failing if there is
		 * a problem.
		 *
		 * @param receiverClass
		 *   The type of the receiver of the method.
		 * @param argumentTypes
		 *   A vararg array of argument types.
		 * @return
		 *   The `CheckedMethod`.
		 */
		fun constructorMethod(
			receiverClass: Class<*>,
			vararg argumentTypes: Class<*>): CheckedConstructor =
				CheckedConstructor(
					true,
					receiverClass,
					*argumentTypes)

		/**
		 * Create a `CheckedMethod` for invoking an instance method that cannot
		 * have a [ReferencedInGeneratedCode] annotation, failing if there is a
		 * problem.
		 *
		 * @param receiverClass
		 *   The type of the receiver of the method.
		 * @param argumentTypes
		 *   A vararg array of argument types.
		 * @return
		 *   The `CheckedMethod`.
		 */
		@Suppress("unused")
		fun javaLibraryConstructorMethod(
			receiverClass: Class<*>,
			vararg argumentTypes: Class<*>): CheckedConstructor =
				CheckedConstructor(
					false,
					receiverClass,
					*argumentTypes)
	}

	init
	{
		val constructor: Constructor<*> =
			try
			{
				receiverClass.getConstructor(*argumentTypes)
			}
			catch (e: NoSuchMethodException)
			{
				throw RuntimeException(e)
			}
			catch (e: SecurityException)
			{
				throw RuntimeException(e)
			}
		if (verifyAnnotation)
		{
			// Check the annotation before anything else, in case we selected
			// the wrong constructor.
			@Suppress("UNUSED_VARIABLE")
			val annotation =
				constructor.getAnnotation(ReferencedInGeneratedCode::class.java)
				 	?: error(
						"Constructor should have had ReferencedInGeneratedCode "
							+ "annotation")
		}
		val modifiers = constructor.modifiers
		assert(modifiers and Modifier.PUBLIC != 0)
		receiverClassInternalName =
			Type.getInternalName(constructor.declaringClass)
		methodDescriptorString =
			Type.getConstructorDescriptor(constructor)
	}
}
