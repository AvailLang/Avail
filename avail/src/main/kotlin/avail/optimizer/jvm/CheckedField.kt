/*
 * CheckedField.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.optimizer.jvm

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * A helper class for referring to a field.  It verifies at construction time
 * that the referenced field is marked with the [ReferencedInGeneratedCode]
 * annotation, and that the specified type agrees.  When it generates a read
 * (`getfield`/`getstatic`) or write (`putfield`/`putstatic`), it uses the
 * appropriate instruction.  It substitutes a literal if the field is final, and
 * forbids generating a write to it.
 *
 * The main power this class brings is the ability to check the type signature
 * prior to code generation, rather than have the JVM verifier be the failure
 * point.  It also fails early if the referenced field isn't marked with
 * [ReferencedInGeneratedCode], helping to keep critical fields from changing or
 * disappearing under maintenance.  It also keeps field names out of the code
 * generator, making it easier to find the real uses of fields in generator
 * code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property isStatic
 *   Whether the method is static.
 * @property fieldNameString
 *   The simple name of the method.
 *
 * @constructor
 * Create a `CheckedMethod`, reflecting as needed to verify and precompute as
 * much as possible.
 *
 * @param verifyAnnotation
 *   Whether to look for the [ReferencedInGeneratedCode] annotation.
 * @param isStatic
 *   Whether the field is expected to be static.
 * @param receiverClass
 *   The class in which the field should be found.
 * @param fieldNameString
 *   The name of the field.
 * @param fieldClass
 *   The type of the field.
 */
@Suppress("SpellCheckingInspection")
class CheckedField private constructor(
	verifyAnnotation: Boolean,
	private val isStatic: Boolean,
	private val receiverClass: Class<*>,
	private val fieldNameString: String,
	fieldClass: Class<*>)
{
	/** The reflected [Field] to be accessed. */
	val field: Field = run {
		val f = try
		{
			receiverClass.getField(fieldNameString)
		}
		catch (e: NoSuchFieldException)
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
			// the wrong method.
			@Suppress("UNUSED_VARIABLE")
			val annotation = f.getAnnotation(
				ReferencedInGeneratedCode::class.java) ?:
			error(
				"Field $fieldNameString should have had " +
					"ReferencedInGeneratedCode annotation")
		}
		val modifiers = f.modifiers
		assert(modifiers and Modifier.PUBLIC != 0)
		assert(modifiers and Modifier.STATIC != 0 == isStatic)
		assert(fieldClass == f.type)
		f
	}

	/** Whether the method is final. */
	private val isFinal: Boolean = field.modifiers and Modifier.FINAL != 0

	/** The canonical name of the class in which the method is defined. */
	private val receiverClassInternalName =
		Type.getInternalName(field.declaringClass)

	/** The canonical name of the method arguments. */
	private val fieldTypeDescriptorString = Type.getDescriptor(field.type)

	/**
	 * Emit a read of this field.  The receiver, if this is not static, must
	 * already be on the stack.
	 *
	 * @param methodVisitor
	 *   Which [MethodVisitor] to emit the read into.
	 */
	fun generateRead(methodVisitor: MethodVisitor) = when
	{
		!isStatic -> methodVisitor.visitFieldInsn(
			Opcodes.GETFIELD,
			receiverClassInternalName,
			fieldNameString,
			fieldTypeDescriptorString)
		isFinal && field.get(null) === null ->
			methodVisitor.visitInsn(Opcodes.ACONST_NULL)
		else -> methodVisitor.visitFieldInsn(
			Opcodes.GETSTATIC,
			receiverClassInternalName,
			fieldNameString,
			fieldTypeDescriptorString)
	}

	/**
	 * Emit a write of this field for the given [JVMTranslator] and
	 * [MethodVisitor].  The receiver, if any, and the new field value must
	 * already be on the stack.  Fail right away if the field is final.
	 *
	 * @param methodVisitor
	 *   Which [MethodVisitor] to emit the read into.
	 */
	fun generateWrite(
		methodVisitor: MethodVisitor)
	{
		assert(!isFinal)
		if (isStatic)
		{
			methodVisitor.visitFieldInsn(
				Opcodes.PUTSTATIC,
				receiverClassInternalName,
				fieldNameString,
				fieldTypeDescriptorString)
		}
		else
		{
			methodVisitor.visitFieldInsn(
				Opcodes.PUTFIELD,
				receiverClassInternalName,
				fieldNameString,
				fieldTypeDescriptorString)
		}
	}

	companion object
	{
		/**
		 * Create a `CheckedField` for accessing an instance field that has been
		 * annotated with [ReferencedInGeneratedCode], failing if there is a
		 * problem.
		 *
		 * @param receiverClass
		 *   The type of the object containing the field.
		 * @param fieldName
		 *   The name of the field.
		 * @param fieldClass
		 *   The type of the field.
		 * @return
		 *   The `CheckedField`.
		 */
		fun instanceField(
			receiverClass: Class<*>,
			fieldName: String,
			fieldClass: Class<*>
		): CheckedField =
			CheckedField(
				true,
				false,
				receiverClass,
				fieldName,
				fieldClass)

		/**
		 * Create a `CheckedField` for accessing a static field that has been
		 * annotated with [ReferencedInGeneratedCode], failing if there is a
		 * problem.
		 *
		 * @param receiverClass
		 *   The type defining the static field.
		 * @param fieldName
		 *   The name of the static field.
		 * @param fieldClass
		 *   The type of the field.
		 * @return
		 *   The `CheckedField`.
		 */
		fun staticField(
			receiverClass: Class<*>,
			fieldName: String,
			fieldClass: Class<*>
		): CheckedField =
			CheckedField(
				true,
				true,
				receiverClass,
				fieldName,
				fieldClass)

		/**
		 * Create a `CheckedField` for accessing an `enum` instance, failing if
		 * there is a problem.
		 *
		 * @param T
		 *   The `enum` type.
		 * @param enumInstance
		 *   The `enum` value.
		 * @return
		 *   The `CheckedField`.
		 */
		fun <T : Enum<T>> enumField(enumInstance: T): CheckedField
		{
			val theClass: Class<T> = enumInstance.javaClass
			val theSuper: Class<in T> = theClass.superclass
			val enumClass: Class<*> =
				if (theSuper == Enum::class.java) theClass else theSuper
			return CheckedField(
				false,
				true,
				enumClass,
				enumInstance.name,
				enumClass)
		}

		/**
		 * Create a `CheckedField` for accessing an instance field that has not
		 * been annotated with [ReferencedInGeneratedCode], failing if there is
		 * a problem.
		 *
		 * @param receiverClass
		 *   The type of the object containing the field.
		 * @param fieldName
		 *   The name of the field.
		 * @param fieldClass
		 *   The type of the field.
		 * @return
		 *   The `CheckedField`.
		 */
		fun javaLibraryInstanceField(
			receiverClass: Class<*>,
			fieldName: String,
			fieldClass: Class<*>
		): CheckedField =
			CheckedField(
				false,
				false,
				receiverClass,
				fieldName,
				fieldClass)

		/**
		 * Create a `CheckedField` for accessing a static field that has not
		 * been annotated with [ReferencedInGeneratedCode], failing if there is
		 * a problem.
		 *
		 * @param receiverClass
		 *   The type defining the static field.
		 * @param fieldName
		 *   The name of the static field.
		 * @param fieldClass
		 *   The type of the field.
		 * @return
		 *   The `CheckedField`.
		 */
		@Suppress("unused")
		fun javaLibraryStaticField(
			receiverClass: Class<*>,
			fieldName: String,
			fieldClass: Class<*>
		): CheckedField =
			CheckedField(
				false,
				true,
				receiverClass,
				fieldName,
				fieldClass)
	}
}
