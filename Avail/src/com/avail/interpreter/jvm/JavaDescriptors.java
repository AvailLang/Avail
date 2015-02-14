/**
 * JavaDescriptors.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.avail.annotations.Nullable;

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
	 * Construct and answer the internal Java {@linkplain Class class} name for
	 * the specified class name.
	 *
	 * @param name
	 *        A fully-qualified Java class name.
	 * @return The requested internal class name.
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.2.1">
	 *    Binary Class and Interface Names</a>
	 */
	public static String asInternalName (final String name)
	{
		return name.replace('.', '/');
	}

	/**
	 * Construct and answer the Java descriptor for the specified {@linkplain
	 * Class class} name.
	 *
	 * @param name
	 *        A fully-qualified Java class name.
	 * @return The requested descriptor.
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3">
	 *    Descriptors and Signatures</a>
	 */
	public static String forClassName (final String name)
	{
		final StringBuilder builder = new StringBuilder(50);
		builder.append("L");
		builder.append(asInternalName(name));
		builder.append(";");
		return builder.toString();
	}

	/**
	 * Given a class descriptor, answer the fully-qualified name of the
	 * {@linkplain Class class}.
	 *
	 * @param classDescriptor
	 *        A class descriptor.
	 * @return A fully-qualified class name.
	 */
	public static String nameFromDescriptor (final String classDescriptor)
	{
		return classDescriptor
			.substring(1, classDescriptor.length() - 1)
			.replace('/', '.');
	}

	/**
	 * Answer a descriptor that represents an array with the specified element
	 * type and dimensionality.
	 *
	 * @param descriptor
	 *        A descriptor.
	 * @param dimensions
	 *        The number of dimensions.
	 * @return An array descriptor.
	 */
	public static String forArrayOf (
		final String descriptor,
		final int dimensions)
	{
		final int cp = descriptor.codePointAt(0);
		if (cp == '(' || cp == '[')
		{
			throw new IllegalArgumentException();
		}
		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < dimensions; i++)
		{
			builder.append('[');
		}
		builder.append(descriptor);
		return builder.toString();
	}

	/**
	 * Answer a descriptor that represents an array with the specified element
	 * type.
	 *
	 * @param descriptor
	 *        A descriptor.
	 * @return An array descriptor.
	 */
	public static String forArrayOf (final String descriptor)
	{
		return forArrayOf(descriptor, 1);
	}

	/**
	 * Answer the type name that corresponds to the specified descriptor.
	 *
	 * @param descriptor
	 *        A descriptor.
	 * @return The type name.
	 */
	public static String asTypeName (final String descriptor)
	{
		switch (descriptor.codePointAt(0))
		{
			case 'L':
				int index = 1;
				while (true)
				{
					// Skip up to the semicolon.
					final int codePoint = descriptor.codePointAt(index);
					if (codePoint == ';')
					{
						break;
					}
					index += Character.charCount(codePoint);
				}
				final String substring = descriptor.substring(1, index);
				return substring.replace('/', '.');
			case 'Z':
				assert descriptor.length() == 1;
				return "boolean";
			case 'B':
				assert descriptor.length() == 1;
				return "byte";
			case 'C':
				assert descriptor.length() == 1;
				return "character";
			case 'S':
				assert descriptor.length() == 1;
				return "short";
			case 'I':
				assert descriptor.length() == 1;
				return "integer";
			case 'J':
				assert descriptor.length() == 1;
				return "long";
			case 'D':
				assert descriptor.length() == 1;
				return "double";
			case 'F':
				assert descriptor.length() == 1;
				return "float";
			default:
				throw new IllegalArgumentException();
		}
	}

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

	/**
	 * Answer the count of slot units indicated by the method descriptor. If the
	 * descriptor is a method descriptor, then only the parameters will be
	 * evaluated.
	 *
	 * @param descriptor
	 *        A descriptor.
	 * @return The number of argument units. {@code long}s and {@code double}s
	 *         count for {@code 2} units, but all other primitive and reference
	 *         types count for {@code 1} unit.
	 * @throws IllegalArgumentException
	 *         If the descriptor is invalid.
	 */
	static int slotUnits (final String descriptor)
	{
		boolean isMethodDescriptor = false;
		int index = 0;
		if (descriptor.codePointAt(0) == '(')
		{
			isMethodDescriptor = true;
			index = 1;
		}
		try
		{
			int count = 0;
			while (true)
			{
				switch (descriptor.codePointAt(index))
				{
					case ')':
						if (isMethodDescriptor)
						{
							return count;
						}
						throw new IllegalArgumentException();
					case 'L':
						while (true)
						{
							// Skip up to the semicolon.
							final int codePoint = descriptor.codePointAt(index);
							if (codePoint == ';')
							{
								// Don't account for the semicolon here.
								break;
							}
							index += Character.charCount(codePoint);
						}
						// $FALL-THROUGH$
					case 'B':
					case 'C':
					case 'F':
					case 'I':
					case 'R':
						// 'R' represents a return address. It is not part of
						// JVM descriptor specification.
					case 'S':
					case 'Z':
						count++;
						break;
					case 'D':
					case 'J':
						count += 2;
						break;
					default:
						throw new IllegalArgumentException();
				}
				if (!isMethodDescriptor)
				{
					return count;
				}
				index++;
			}
		}
		catch (final IndexOutOfBoundsException e)
		{
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Answer the {@linkplain VerificationTypeInfo type identifier} for the
	 * given descriptor.
	 *
	 * @param descriptor
	 *        A descriptor.
	 * @return The type identifier.
	 */
	static VerificationTypeInfo typeInfoFor (final String descriptor)
	{
		switch (descriptor.codePointAt(0))
		{
			case 'L':
			{
				return JavaOperand.OBJECTREF.create(descriptor);
			}
			case 'B':
			case 'C':
			case 'I':
			case 'S':
			case 'Z':
				return JavaOperand.INT.create();
			case 'F':
				return JavaOperand.FLOAT.create();
			case 'D':
				return JavaOperand.DOUBLE.create();
			case 'J':
				return JavaOperand.LONG.create();
			default:
				throw new IllegalArgumentException();
		}
	}

	/**
	 * Answer the {@code targetIndex}-th parameter descriptor from the specified
	 * method descriptor.
	 *
	 * @param descriptor
	 *        A method descriptor.
	 * @param targetIndex
	 *        The zero-based {@linkplain #slotUnits(String) slot index} of the
	 *        desired parameter descriptor.
	 * @return The requested parameter descriptor.
	 * @throws IllegalArgumentException
	 *         If either of the method descriptor or the index are invalid.
	 */
	static String parameterDescriptor (
		final String descriptor,
		final int targetIndex)
	{
		if (descriptor.codePointAt(0) != '(')
		{
			throw new IllegalArgumentException();
		}
		try
		{
			int parameterIndex = 0;
			int index = 1;
			while (true)
			{
				final int startIndex = index;
				switch (descriptor.codePointAt(index))
				{
					case ')':
						throw new IllegalArgumentException();
					case 'L':
						while (true)
						{
							// Skip up to the semicolon.
							final int codePoint = descriptor.codePointAt(index);
							index += Character.charCount(codePoint);
							if (codePoint == ';')
							{
								break;
							}
						}
						if (parameterIndex == targetIndex)
						{
							return descriptor.substring(startIndex, index);
						}
						parameterIndex++;
						break;
					case 'B':
					case 'C':
					case 'F':
					case 'I':
					case 'S':
					case 'Z':
						index++;
						if (parameterIndex == targetIndex)
						{
							return descriptor.substring(startIndex, index);
						}
						parameterIndex++;
						break;
					case 'D':
					case 'J':
						index++;
						if (parameterIndex == targetIndex)
						{
							return descriptor.substring(startIndex, index);
						}
						parameterIndex += 2;
						break;
					default:
						throw new IllegalArgumentException();
				}
			}
		}
		catch (final IndexOutOfBoundsException e)
		{
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * A {@linkplain Map map} from Java descriptor characters to the {@linkplain
	 * Class primitive types} used to represent constants of the corresponding
	 * types.
	 */
	private static final Map<String, Class<?>> constantTypes;

	static
	{
		final Map<String, Class<?>> map = new HashMap<>(8);
		map.put("Z", Integer.TYPE);
		map.put("B", Integer.TYPE);
		map.put("C", Integer.TYPE);
		map.put("D", Double.TYPE);
		map.put("F", Float.TYPE);
		map.put("I", Integer.TYPE);
		map.put("J", Long.TYPE);
		map.put("S", Integer.TYPE);
		constantTypes = map;
	}

	/**
	 * Answer the {@linkplain Class type} of constant data that conforms to the
	 * specified descriptor.
	 *
	 * @param typeDescriptor
	 *        A type descriptor.
	 * @return A type, or {@code null} if the type descriptor does not represent
	 *         a primitive type or {@link String}.
	 */
	public static @Nullable Class<?> toConstantType (
		final String typeDescriptor)
	{
		final Class<?> type = constantTypes.get(typeDescriptor);
		if (type != null)
		{
			return type;
		}
		if (typeDescriptor.equals("Ljava/lang/String;"))
		{
			return String.class;
		}
		return null;
	}

	/**
	 * Answer a {@linkplain List list} of {@linkplain JavaOperand operands} that
	 * describes the parameter list of a method whose signature has the
	 * specified descriptor.
	 *
	 * @param descriptor
	 *        A {@linkplain #forMethod(Class, Class...) method descriptor}.
	 * @return The operands that correspond to the parameter types.
	 */
	static List<VerificationTypeInfo> parameterOperands (
		final String descriptor)
	{
		final List<VerificationTypeInfo> operands = new ArrayList<>();
		if (descriptor.codePointAt(0) != '(')
		{
			throw new IllegalArgumentException();
		}
		try
		{
			for (int index = 1;; index++)
			{
				switch (descriptor.codePointAt(index))
				{
					case ')':
						return operands;
					case 'L':
					{
						final int startIndex = index;
						int endIndex;
						while (true)
						{
							// Skip up to the semicolon.
							final int codePoint = descriptor.codePointAt(index);
							if (codePoint == ';')
							{
								endIndex = index + 1;
								break;
							}
							index += Character.charCount(codePoint);
						}
						operands.add(JavaOperand.OBJECTREF.create(
							descriptor.substring(startIndex, endIndex)));
						break;
					}
					case 'B':
					case 'C':
					case 'I':
					case 'S':
					case 'Z':
						operands.add(JavaOperand.INT.create());
						break;
					case 'F':
						operands.add(JavaOperand.FLOAT.create());
						break;
					case 'D':
						operands.add(JavaOperand.DOUBLE.create());
						break;
					case 'J':
						operands.add(JavaOperand.LONG.create());
						break;
					default:
						throw new IllegalArgumentException();
				}
			}
		}
		catch (final IndexOutOfBoundsException e)
		{
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Answer the {@linkplain VerificationTypeInfo operand} that corresponds to
	 * the return type of a method described by the specified descriptor.
	 *
	 * @param descriptor
	 *        A {@linkplain #forMethod(Class, Class...) method descriptor}.
	 * @return The appropriate operand, or {@code null} if the method is {@code
	 *         void}.
	 */
	static @Nullable VerificationTypeInfo returnOperand (
		final String descriptor)
	{
		if (descriptor.codePointAt(0) != '(')
		{
			throw new IllegalArgumentException();
		}
		try
		{
			int index = 1;
			while (true)
			{
				if (descriptor.codePointAt(index) == ')')
				{
					index++;
					break;
				}
				index++;
			}
			switch (descriptor.codePointAt(index))
			{
				case 'L':
				{
					final int classIndex = index;
					while (true)
					{
						// Skip up to the semicolon.
						final int codePoint = descriptor.codePointAt(index);
						if (codePoint == ';')
						{
							break;
						}
						index += Character.charCount(codePoint);
					}
					return JavaOperand.OBJECTREF.create(
						descriptor.substring(classIndex));
				}
				case 'B':
				case 'C':
				case 'I':
				case 'S':
				case 'Z':
					return JavaOperand.INT.create();
				case 'F':
					return JavaOperand.FLOAT.create();
				case 'D':
					return JavaOperand.DOUBLE.create();
				case 'J':
					return JavaOperand.LONG.create();
				case 'V':
					return null;
				default:
					throw new IllegalArgumentException();
			}
		}
		catch (final IndexOutOfBoundsException e)
		{
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * A {@linkplain Map map} from Java descriptor characters to {@linkplain
	 * Class primitive classes}.
	 */
	private static final Map<String, Class<?>> typesByCharacter;

	static
	{
		final Map<String, Class<?>> map = new HashMap<>(9);
		map.put("Z", Boolean.TYPE);
		map.put("B", Byte.TYPE);
		map.put("C", Character.TYPE);
		map.put("D", Double.TYPE);
		map.put("F", Float.TYPE);
		map.put("I", Integer.TYPE);
		map.put("J", Long.TYPE);
		// "R" is not official, but represents a return address.
		map.put("R", Object.class);
		map.put("S", Short.TYPE);
		map.put("V", Void.TYPE);
		typesByCharacter = map;
	}

	/**
	 * Given a type descriptor, produce and answer a {@linkplain
	 * Class#isPrimitive() primitive} {@linkplain Class type} or {@link
	 * Object Object.class} as appropriate.
	 *
	 * @param descriptor
	 *        A type descriptor. May also be {@code "R"} to represent a return
	 *        address.
	 * @return A type.
	 */
	public static Class<?> typeForDescriptor (final String descriptor)
	{
		try
		{
			final Class<?> type = typesByCharacter.get(descriptor);
			if (type == null)
			{
				if (descriptor.codePointAt(0) == 'L')
				{
					int index = 1;
					while (true)
					{
						// Skip up to the semicolon.
						final int codePoint = descriptor.codePointAt(index);
						if (codePoint == ';')
						{
							break;
						}
						index += Character.charCount(codePoint);
					}
					return Object.class;
				}
			}
			return type;
		}
		catch (final IndexOutOfBoundsException e)
		{
			throw new IllegalArgumentException(e);
		}
	}
}
