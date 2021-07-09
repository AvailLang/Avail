/*
 * JavaLibrary.kt
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

@file:Suppress(
	"PLATFORM_CLASS_MAPPED_TO_KOTLIN",
	"RemoveRedundantQualifierName")

package com.avail.interpreter

import com.avail.optimizer.jvm.CheckedField
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.javaLibraryInstanceMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.javaLibraryStaticMethod
import java.util.concurrent.atomic.LongAdder


// Alias the Java primitive classes for convenience.
typealias JavaVoid = java.lang.Void
typealias JavaBoolean = java.lang.Boolean
typealias JavaByte = java.lang.Byte
typealias JavaShort = java.lang.Short
typealias JavaInteger = java.lang.Integer
typealias JavaLong = java.lang.Long
typealias JavaFloat = java.lang.Float
typealias JavaDouble = java.lang.Double

// Alias some common Java utility classes.
typealias JavaList = java.util.List<*>

/**
 * [CheckedMethod]s and [CheckedField]s for the Java class library.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object JavaLibrary
{
	// The Java primitive classes.
	val void = JavaVoid::class.javaPrimitiveType!!
	val boolean = JavaBoolean::class.javaPrimitiveType!!
	val byte = JavaByte::class.javaPrimitiveType!!
	val short = JavaShort::class.javaPrimitiveType!!
	val int = JavaInteger::class.javaPrimitiveType!!
	val long = JavaLong::class.javaPrimitiveType!!
	val float = JavaFloat::class.javaPrimitiveType!!
	val double = JavaDouble::class.javaPrimitiveType!!

	// The Java boxed primitive classes.
	val booleanBoxed = JavaBoolean::class.java
	val byteBoxed = JavaByte::class.java
	val shortBoxed = JavaShort::class.java
	val intBoxed = JavaInteger::class.java
	val longBoxed = JavaLong::class.java
	val floatBoxed = JavaFloat::class.java
	val doubleBoxed = JavaDouble::class.java

	/** Static method to cast from `long` to `double`.  */
	var bitCastLongToDoubleMethod: CheckedMethod =
		javaLibraryStaticMethod(
			doubleBoxed,
			java.lang.Double::longBitsToDouble.name,
			double,
			long)

	/** Static method to cast from `double` to `long`.  */
	var bitCastDoubleToLongMethod: CheckedMethod =
		javaLibraryStaticMethod(
			doubleBoxed,
			java.lang.Double::doubleToRawLongBits.name,
			long,
			double)

	/** The [CheckedMethod] for [Class.getClassLoader]. */
	val getClassLoader: CheckedMethod =
		javaLibraryInstanceMethod(
			Class::class.java,
			java.lang.Class<*>::getClassLoader.name,
			ClassLoader::class.java)

	/** The [CheckedMethod] for *Java* [Integer.valueOf] boxing. */
	val javaUnboxIntegerMethod: CheckedMethod = javaLibraryStaticMethod(
		intBoxed,
		"valueOf",
		intBoxed,
		int)

	/** The [CheckedMethod] for [java.util.List.get].  */
	val listGetMethod: CheckedMethod = javaLibraryInstanceMethod(
		java.util.List::class.java,
		JavaList::get.name,
		Object::class.java,
		int)

	/** The [CheckedMethod] for [java.util.List.clear].  */
	val listClearMethod: CheckedMethod = javaLibraryInstanceMethod(
		java.util.List::class.java,
		JavaList::clear.name,
		void)

	/** The [CheckedMethod] for [java.util.List.add].  */
	val listAddMethod: CheckedMethod = javaLibraryInstanceMethod(
		java.util.List::class.java,
		"add",
		boolean,
		Object::class.java)

	/** The [CheckedMethod] for [LongAdder.increment]. */
	val longAdderIncrement: CheckedMethod = javaLibraryInstanceMethod(
		LongAdder::class.java,
		LongAdder::increment.name,
		void)
}
