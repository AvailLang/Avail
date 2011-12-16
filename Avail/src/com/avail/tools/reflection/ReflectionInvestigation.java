/**
 * ReflectionInvestigation.java
 * Copyright (c) 2011, Mark van Gulik.
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

package com.avail.tools.reflection;

import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * A reflective Java class browser. I write this to supplement my understanding
 * of the reflection APIs. The Javadocs are too underspecified to gain a real
 * understanding without also experimenting.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
@SuppressWarnings("all")
public final class ReflectionInvestigation
{
	private static void printClassPreamble (
		final Class<?> aClass,
		final PrintStream printer)
	{
		for (final Annotation annotation : aClass.getAnnotations())
		{
			printer.printf("%s ", annotation);
		}
		final String modifiersString = Modifier.toString(aClass.getModifiers());
		printer.printf(
			"%s%s",
			modifiersString + (modifiersString.length() > 0 ? " " : ""),
			aClass.getName());
		final Type[] classTypeParameters = aClass.getTypeParameters();
		if (classTypeParameters.length > 0)
		{
			printer.print("<");
			boolean firstParameter = true;
			for (final Type type : classTypeParameters)
			{
				if (!firstParameter)
				{
					printer.print(", ");
				}
				printer.printf("%s", type);
				firstParameter = false;
			}
			printer.print(">");
		}
		final Type superclass = aClass.getGenericSuperclass();
		if (superclass != null)
		{
			printer.printf(" extends %s", superclass);
		}
		final Type[] interfaces = aClass.getGenericInterfaces();
		if (interfaces.length > 0)
		{
			printer.print(" implements ");
			boolean firstInterface = true;
			for (final Type type : interfaces)
			{
				if (!firstInterface)
				{
					printer.print(", ");
				}
				printer.printf("%s", type);
				firstInterface = false;
			}
		}
		printer.printf(":%n");
	}

	private static void printFields (
		final Class<?> aClass,
		final PrintStream printer)
	{
		for (final Field field : aClass.getDeclaredFields())
		{
			final String modifiersString =
				Modifier.toString(field.getModifiers());
			printer.printf(
				"\t%s",
				modifiersString + (modifiersString.length() > 0 ? " " : ""));
			for (final Annotation annotation : field.getAnnotations())
			{
				printer.printf("%s ", annotation);
			}
			printer.printf(
				"%s;%n",
				field.getGenericType(),
				field.getName());
		}
	}

	private static void printConstructors (
		final Class<?> aClass,
		final PrintStream printer)
	{
		for (final Constructor<?> constructor : aClass.getConstructors())
		{
			final String modifiersString =
				Modifier.toString(constructor.getModifiers());
			printer.printf(
				"\t%s",
				modifiersString + (modifiersString.length() > 0 ? " " : ""));
			for (final Annotation annotation : constructor.getAnnotations())
			{
				printer.printf("%s ", annotation);
			}
			printer.printf("%s (", constructor.getName());
			boolean firstParameter = true;
			for (final Type type : constructor.getGenericParameterTypes())
			{
				if (!firstParameter)
				{
					printer.printf(", ");
				}
				printer.printf("%s", type);
				firstParameter = false;
			}
			printer.print(")");
			final Type[] exceptionTypes = constructor.getGenericExceptionTypes();
			if (exceptionTypes.length > 0)
			{
				printer.print(" throws ");
				boolean firstException = true;
				for (final Type type : exceptionTypes)
				{
					if (!firstException)
					{
						printer.print(", ");
					}
					printer.printf("%s", type);
					firstException = false;
				}
			}
			printer.printf(";%n");
		}
	}

	private static void printMethods (
		final Class<?> aClass,
		final PrintStream printer)
	{
		for (final Method method : aClass.getDeclaredMethods())
		{
			final String modifiersString =
				Modifier.toString(method.getModifiers());
			printer.printf(
				"\t%s",
				modifiersString + (modifiersString.length() > 0 ? " " : ""));
			for (final Annotation annotation : method.getAnnotations())
			{
				printer.printf("%s ", annotation);
			}
			printer.printf(
				"%s %s (",
				method.getGenericReturnType(),
				method.getName());
			final Type[] parameterTypes = method.getGenericParameterTypes();
			final Annotation[][] parameterAnnotations =
				method.getParameterAnnotations();
			for (int i = 0; i < parameterTypes.length; i++)
			{
				if (i > 0)
				{
					printer.printf(", ");
				}
				for (final Annotation annotation : parameterAnnotations[i])
				{
					printer.printf("%s ", annotation);
				}
				printer.printf("%s", parameterTypes[i]);
			}
			printer.print(")");
			final Type[] exceptionTypes = method.getGenericExceptionTypes();
			if (exceptionTypes.length > 0)
			{
				printer.print(" throws ");
				boolean firstException = true;
				for (final Type type : exceptionTypes)
				{
					if (!firstException)
					{
						printer.print(", ");
					}
					printer.printf("%s", type);
					firstException = false;
				}
			}
			printer.printf(";%n");
		}
	}

	private static void printClass (
		final Class<?> aClass,
		final PrintStream printer)
	{
		printClassPreamble(aClass, printer);
		printFields(aClass, printer);
		printConstructors(aClass, printer);
		printMethods(aClass, printer);
		printer.println();
	}

	private static Class<?> readClass (final Scanner scanner)
		throws ClassNotFoundException
	{
		final String className = scanner.nextLine().trim();
		final Class<?> aClass = Class.forName(className);
		return aClass;
	}

	private static void printPrompt (final PrintStream printer)
	{
		printer.print("Enter a fully-qualified class name: ");
	}

	public static void main (final String[] args)
	{
		final PrintStream printer = System.out;
		final Scanner scanner = new Scanner(System.in);
		while (true)
		{
			printPrompt(printer);
			try
			{
				final Class<?> aClass = readClass(scanner);
				printClass(aClass, printer);
			}
			catch (final ClassNotFoundException e)
			{
				printer.println("Class not found");
				printer.println();
			}
			catch (final NoSuchElementException e)
			{
				return;
			}
			catch (final Exception e)
			{
				printer.println("Unexpected exception");
				e.printStackTrace();
				return;
			}
		}
	}
}
