/*
 * JavaLibrary.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.interpreter

import com.avail.optimizer.jvm.CheckedField
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.javaLibraryInstanceMethod
import com.avail.optimizer.jvm.CheckedMethod.javaLibraryStaticMethod
import java.util.concurrent.atomic.LongAdder

/**
 * [CheckedMethod]s and [CheckedField]s for the Java class library.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
@Suppress("unused")
object JavaLibrary
{
	// The Java primitive classes.
	private val void = java.lang.Void::class.javaPrimitiveType!!
	private val boolean = java.lang.Boolean::class.javaPrimitiveType!!
	private val byte = java.lang.Byte::class.javaPrimitiveType!!
	private val short = java.lang.Short::class.javaPrimitiveType!!
	private val int = java.lang.Integer::class.javaPrimitiveType!!
	private val long = java.lang.Long::class.javaPrimitiveType!!
	private val float = java.lang.Float::class.javaPrimitiveType!!
	private val double = java.lang.Double::class.javaPrimitiveType!!

	// The Java boxed primitive classes.
	private val booleanBoxed = java.lang.Boolean::class.java
	private val byteBoxed = java.lang.Byte::class.java
	private val shortBoxed = java.lang.Short::class.java
	private val intBoxed = java.lang.Integer::class.java
	private val longBoxed = java.lang.Long::class.java
	private val floatBoxed = java.lang.Float::class.java
	private val doubleBoxed = java.lang.Double::class.java

	/** Static method to cast from `long` to `double`.  */
	@JvmStatic
	var bitCastLongToDoubleMethod: CheckedMethod =
		javaLibraryStaticMethod(
			doubleBoxed,
			"longBitsToDouble",
			double,
			long)

	/** Static method to cast from `double` to `long`.  */
	@JvmStatic
	var bitCastDoubleToLongMethod: CheckedMethod =
		javaLibraryStaticMethod(
			doubleBoxed,
			"doubleToRawLongBits",
			long,
			double)

	/** The [CheckedMethod] for [Class.getClassLoader]. */
	@JvmStatic
	val getClassLoader: CheckedMethod =
		javaLibraryInstanceMethod(
			Class::class.java,
			"getClassLoader",
			ClassLoader::class.java)

	/** The [CheckedMethod] for *Java* [Integer.valueOf] boxing. */
	@JvmStatic
	val javaUnboxIntegerMethod: CheckedMethod = javaLibraryStaticMethod(
		intBoxed,
		"valueOf",
		intBoxed,
		int)

	/** The [CheckedMethod] for [java.util.List.get].  */
	@JvmStatic
	val listGetMethod: CheckedMethod = javaLibraryInstanceMethod(
		java.util.List::class.java,
		"get",
		Object::class.java,
		int)

	/** The [CheckedMethod] for [java.util.List.clear].  */
	@JvmStatic
	val listClearMethod: CheckedMethod = javaLibraryInstanceMethod(
		java.util.List::class.java,
		"clear",
		void)

	/** The [CheckedMethod] for [java.util.List.add].  */
	@JvmStatic
	val listAddMethod: CheckedMethod = javaLibraryInstanceMethod(
		java.util.List::class.java,
		"add",
		boolean,
		Object::class.java)

	/** The [CheckedMethod] for [LongAdder.increment]. */
	@JvmStatic
	val longAdderIncrement: CheckedMethod = javaLibraryInstanceMethod(
		LongAdder::class.java,
		"increment",
		void)
}
