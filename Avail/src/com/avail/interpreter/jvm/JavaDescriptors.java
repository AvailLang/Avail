/**
 * JavaDescriptors.java
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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code JavaDescriptors} provides utility methods for producing Java
 * descriptors for {@linkplain Class types} and {@linkplain Method methods}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a
 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3">
 *    Descriptors and Signatures</a>
 */
final class JavaDescriptors
{
	/**
	 * A {@linkplain Map map} from {@linkplain Class primitive classes} to their
	 * Java descriptor characters.
	 */
	private static final Map<Class<?>, String> baseTypeCharacters;

	static
	{
		final Map<Class<?>, String> map = new HashMap<>(9);
		map.put(Boolean.TYPE, "Z");
		map.put(Byte.TYPE, "B");
		map.put(Character.TYPE, "C");
		map.put(Double.TYPE, "D");
		map.put(Float.TYPE, "F");
		map.put(Integer.TYPE, "I");
		map.put(Long.TYPE, "J");
		map.put(Short.TYPE, "S");
		map.put(Void.TYPE, "V");
		baseTypeCharacters = map;
	}

	/**
	 * Construct and answer the Java descriptor for the specified {@linkplain
	 * Class Java type}.
	 *
	 * @param targetType
	 *        A {@code Class}, possibly an {@linkplain Class#isArray() array
	 *        type} or {@linkplain Class#isPrimitive() primitive type}.
	 * @return The requested descriptor.
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3">
	 *    Descriptors and Signatures</a>
	 */
	public static String forType (final Class<?> targetType)
	{
		final StringBuilder builder = new StringBuilder(100);
		Class<?> type = targetType;
		while (true)
		{
			if (type.isArray())
			{
				builder.append('[');
				type = type.getComponentType();
				continue;
			}
			if (type.isPrimitive())
			{
				final String baseTypeCharacter = baseTypeCharacters.get(type);
				assert baseTypeCharacter != null;
				builder.append(baseTypeCharacter);
				break;
			}
			// The type is a reference type.
			builder.append("L");
			builder.append(type.getName().replace('.', '/'));
			builder.append(";");
			break;
		}
		return builder.toString();
	}

	/**
	 * Construct and answer the Java descriptor for a method with the specified
	 * (exploded) signature.
	 *
	 * @param returnType
	 *        The {@linkplain Class return type}.
	 * @param parameterTypes
	 *        The parameter types.
	 * @return The requested descriptor.
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">
	 *    Method descriptors</a>
	 */
	public static String forMethod (
		final Class<?> returnType,
		final Class<?>... parameterTypes)
	{
		final StringBuilder builder = new StringBuilder(100);
		builder.append('(');
		for (final Class<?> parameterType : parameterTypes)
		{
			builder.append(forType(parameterType));
		}
		builder.append(')');
		builder.append(forType(returnType));
		return builder.toString();
	}

	/**
	 * Construct and answer the Java descriptor for the specified {@linkplain
	 * Method method}.
	 *
	 * @param method
	 *        A {@code Method}.
	 * @return The requested descriptor.
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">
	 *    Method descriptors</a>
	 */
	public static String forMethod (final Method method)
	{
		return forMethod(method.getReturnType(), method.getParameterTypes());
	}
}
