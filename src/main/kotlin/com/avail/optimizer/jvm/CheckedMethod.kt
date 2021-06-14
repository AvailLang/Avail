/*
 * CheckedMethod.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import com.avail.descriptor.representation.AvailObject
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.CHECKCAST
import org.objectweb.asm.Opcodes.INVOKEINTERFACE
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Type
import java.lang.reflect.Modifier

/**
 * A helper class for referring to a method.  It verifies at construction
 * time that the referenced method is marked with the
 * [ReferencedInGeneratedCode] annotation, and that the specified
 * argument types are correct.  When it generates a call, it uses the
 * appropriate invoke instruction, and even generates a
 * [Opcodes.CHECKCAST] instruction if the result type isn't strong
 * enough.
 *
 *
 * The main power this class brings is the ability to check the type
 * signature prior to code generation, rather than have the JVM verifier be
 * the failure point.  It also fails early if the referenced method isn't
 * marked with [ReferencedInGeneratedCode], helping to keep critical
 * methods from changing or disappearing under maintenance.  It also keeps
 * method names out of the code generator, making it easier to find the real
 * uses of methods in generator code.
 *
 *
 * Factory methods that indicate the method is part of the Java library are
 * not expected to have the [ReferencedInGeneratedCode] annotation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property isStatic
 *   Whether the method is static.
 * @property methodNameString
 *   The simple name of the method.
 *
 * @constructor
 * Create a `CheckedMethod`, reflecting as needed to verify and precompute as
 * much as possible.
 *
 * @param verifyAnnotation
 *   Whether to look for the [ReferencedInGeneratedCode] annotation.
 * @param isStatic
 *   Whether the method is expected to be static.
 * @param receiverClass
 *   The type of the receiver of the method.
 * @param methodNameString
 *   The name of the method.
 * @param returnClass
 *   The required return type.
 * @param argumentTypes
 *   A vararg array of argument types.
 */
class CheckedMethod private constructor(
	verifyAnnotation: Boolean,
	private val isStatic: Boolean,
	receiverClass: Class<*>,
	private val methodNameString: String,
	returnClass: Class<*>,
	vararg argumentTypes: Class<*>)
{

	/** Whether the method is defined in an interface.  */
	private val isInterface: Boolean

	/** The canonical name of the class in which the method is defined.  */
	private val receiverClassInternalName: String

	/** The canonical name of the method arguments.  */
	private val methodDescriptorString: String

	/** The canonical name of the cast target, or null if no cast is needed.  */
	private var internalNameToCheckCastOrNull: String? = null

	/**
	 * Emit a call to this method on the given [MethodVisitor].  The arguments
	 * must already be ready on the stack.
	 *
	 * @param methodVisitor
	 *   Where to write the call.
	 */
	fun generateCall(methodVisitor: MethodVisitor)
	{
		if (isStatic)
		{
			methodVisitor.visitMethodInsn(
				INVOKESTATIC,
				receiverClassInternalName,
				methodNameString,
				methodDescriptorString,
				false)
		}
		else
		{
			methodVisitor.visitMethodInsn(
				if (isInterface) INVOKEINTERFACE else INVOKEVIRTUAL,
				receiverClassInternalName,
				methodNameString,
				methodDescriptorString,
				isInterface)
		}
		if (internalNameToCheckCastOrNull !== null)
		{
			methodVisitor.visitTypeInsn(
				CHECKCAST, internalNameToCheckCastOrNull)
		}
	}

	companion object
	{
		/**
		 * Create a `CheckedMethod` for invoking an instance method that has
		 * been annotated with [ReferencedInGeneratedCode], failing if there is
		 * a problem.
		 *
		 * @param receiverClass
		 *   The type of the receiver of the method.
		 * @param methodName
		 *   The name of the method.
		 * @param returnClass
		 *   The required return type.
		 * @param argumentTypes
		 *   A vararg array of argument types.
		 * @return
		 *   The `CheckedMethod`.
		 */
		fun instanceMethod(
			receiverClass: Class<*>,
			methodName: String,
			returnClass: Class<*>,
			vararg argumentTypes: Class<*>): CheckedMethod =
				CheckedMethod(
					true,
					false,
					receiverClass,
					methodName,
					returnClass,
					*argumentTypes)

		/**
		 * Create a `CheckedMethod` for invoking a static method that has been
		 * annotated with [ReferencedInGeneratedCode], failing if there is a
		 * problem.
		 *
		 * @param receiverClass
		 *   The [Class] that defines the method.
		 * @param methodName
		 *   The name of the method.
		 * @param returnClass
		 *   The required return type.
		 * @param argumentTypes
		 *   A vararg array of argument types.
		 * @return
		 *   The `CheckedMethod`.
		 */
		fun staticMethod(
			receiverClass: Class<*>,
			methodName: String,
			returnClass: Class<*>,
			vararg argumentTypes: Class<*>): CheckedMethod =
				CheckedMethod(
					true,
					true,
					receiverClass,
					methodName,
					returnClass,
					*argumentTypes)

		/**
		 * Create a `CheckedMethod` for invoking an instance method that cannot
		 * have a [ReferencedInGeneratedCode] annotation, failing if there is a
		 * problem.
		 *
		 * @param receiverClass
		 *   The type of the receiver of the method.
		 * @param methodName
		 *   The name of the method.
		 * @param returnClass
		 *   The required return type.
		 * @param argumentTypes
		 *   A vararg array of argument types.
		 * @return
		 *   The `CheckedMethod`.
		 */
		fun javaLibraryInstanceMethod(
			receiverClass: Class<*>,
			methodName: String,
			returnClass: Class<*>,
			vararg argumentTypes: Class<*>): CheckedMethod =
				CheckedMethod(
					false,
					false,
					receiverClass,
					methodName,
					returnClass,
					*argumentTypes)

		/**
		 * Create a `CheckedMethod` for invoking a static method that cannot
		 * have a [ReferencedInGeneratedCode] annotation, failing if there is a
		 * problem.
		 *
		 * @param receiverClass
		 *   The type of the receiver of the method.
		 * @param methodName
		 *   The name of the method.
		 * @param returnClass
		 *   The required return type.
		 * @param argumentTypes
		 *   A vararg array of argument types.
		 * @return
		 *   The `CheckedMethod`.
		 */
		fun javaLibraryStaticMethod(
			receiverClass: Class<*>,
			methodName: String,
			returnClass: Class<*>,
			vararg argumentTypes: Class<*>): CheckedMethod =
				CheckedMethod(
					false,
					true,
					receiverClass,
					methodName,
					returnClass,
					*argumentTypes)
	}

	/* The [Method] that was looked up during construction of this instance. */
	private val method =
		try
		{
			receiverClass.getMethod(methodNameString, *argumentTypes)
		}
		catch (e: NoSuchMethodException)
		{
			throw RuntimeException(e)
		}
		catch (e: SecurityException)
		{
			throw RuntimeException(e)
		}

	init
	{
		if (verifyAnnotation)
		{
			// Check the annotation before anything else, in case we selected
			// the wrong method.
			@Suppress("UNUSED_VARIABLE")
			val annotation = method.getAnnotation(
				ReferencedInGeneratedCode::class.java)
					 ?: error("Method $methodNameString should have had " +
						  "ReferencedInGeneratedCode annotation")
		}
		val modifiers = method.modifiers
		assert(modifiers and Modifier.PUBLIC != 0)
		assert(modifiers and Modifier.STATIC != 0 == isStatic)
		val methodReturnType = method.returnType
		internalNameToCheckCastOrNull =
			when
			{
				methodReturnType == AvailObject::class.java -> null
				methodReturnType.isAssignableFrom(AvailObject::class.java) ->
					Type.getInternalName(AvailObject::class.java)
				returnClass.isAssignableFrom(methodReturnType) -> null
				else ->
				{
					// For sanity, the type to check-cast-strengthen to should be a
					// subtype of the method's return type.
					assert(methodReturnType.isAssignableFrom(returnClass))
					Type.getInternalName(returnClass)
				}
			}
		receiverClassInternalName = Type.getInternalName(method.declaringClass)
		isInterface = method.declaringClass.isInterface
		methodDescriptorString = Type.getMethodDescriptor(method)
	}
}
