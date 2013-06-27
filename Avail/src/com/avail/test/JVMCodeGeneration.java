/**
 * JVMCodeGeneration.java
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

package com.avail.test;

import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import org.junit.Test;
import com.avail.annotations.Nullable;
import com.avail.interpreter.jvm.ClassModifier;
import com.avail.interpreter.jvm.CodeGenerator;
import com.avail.interpreter.jvm.ConstantPool;
import com.avail.interpreter.jvm.Field;
import com.avail.interpreter.jvm.FieldModifier;
import com.avail.interpreter.jvm.LocalVariable;
import com.avail.interpreter.jvm.Method;
import com.avail.interpreter.jvm.MethodModifier;

/**
 * Test {@link CodeGenerator}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class JVMCodeGeneration
{
	/**
	 * {@code ByteStreamClassLoader} loads provides facilities for loading a
	 * single Java {@linkplain Class class} from a {@link
	 * ByteArrayOutputStream} that contains a binary class file.
	 */
	private static final class ByteStreamClassLoader
	extends ClassLoader
	{
		/**
		 * The {@linkplain ByteArrayOutputStream stream} that contains the Java
		 * class file bytes.
		 */
		private final ByteArrayOutputStream bytes;

		/**
		 * Construct a new {@link ByteStreamClassLoader}.
		 *
		 * @param bytes
		 *        The {@linkplain ByteArrayOutputStream stream} that contains
		 *        the Java class file bytes.
		 */
		ByteStreamClassLoader (final ByteArrayOutputStream bytes)
		{
			this.bytes = bytes;
		}

		@Override
		protected Class<?> findClass (final @Nullable String name)
			throws ClassNotFoundException
		{
			assert name != null;
			final byte[] classFile = bytes.toByteArray();
			final Class<?> newClass =
				defineClass(name, classFile, 0, classFile.length);
			assertEquals(name, newClass.getName());
			return newClass;
		}
	}

	/**
	 * Load the {@linkplain Class class} defined by the specified {@linkplain
	 * CodeGenerator code generator}.
	 *
	 * @param cg
	 *        A code generator that is ready to {@linkplain
	 *        CodeGenerator#emitOn(DataOutput) emit} its contents.
	 * @return The new class.
	 * @throws IOException
	 *         If emission fails.
	 * @throws ClassNotFoundException
	 *         If the class could not be found.
	 */
	private Class<?> loadClass (final CodeGenerator cg)
		throws IOException, ClassNotFoundException
	{
		final ByteArrayOutputStream bs = new ByteArrayOutputStream(1000);
		final DataOutputStream out = new DataOutputStream(bs);
		cg.emitOn(out);
		final ByteStreamClassLoader loader = new ByteStreamClassLoader(bs);
		return loader.findClass(cg.name());
	}

	@SuppressWarnings("javadoc")
	@Test
	public void empty ()
		throws
		IOException,
		ClassNotFoundException,
		SecurityException,
		IllegalArgumentException
	{
		final CodeGenerator cg = new CodeGenerator();
		final EnumSet<ClassModifier> cmods = EnumSet.of(ClassModifier.PUBLIC);
		cg.setModifiers(cmods);
		cg.setSuperclass(Object.class);
		loadClass(cg);
	}

	@SuppressWarnings("javadoc")
	@Test
	public void staticField ()
		throws
		IOException,
		ClassNotFoundException,
		SecurityException,
		IllegalArgumentException,
		NoSuchFieldException,
		IllegalAccessException
	{
		final CodeGenerator cg = new CodeGenerator();
		final EnumSet<ClassModifier> cmods = EnumSet.of(ClassModifier.PUBLIC);
		cg.setModifiers(cmods);
		cg.setSuperclass(Object.class);
		final String fieldName = "foo";
		final Field f = cg.newField(fieldName, "Ljava/lang/String;");
		final EnumSet<FieldModifier> fmods =
			EnumSet.of(FieldModifier.PUBLIC, FieldModifier.STATIC);
		f.setModifiers(fmods);
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Field field = newClass.getField(fieldName);
		field.set(null, "hello");
		assertEquals("hello", field.get(null));
	}

	@SuppressWarnings("javadoc")
	@Test
	public void abstractHello ()
		throws
			IOException,
			ClassNotFoundException,
			NoSuchMethodException,
			SecurityException,
			IllegalArgumentException
	{
		final CodeGenerator cg = new CodeGenerator();
		final EnumSet<ClassModifier> cmods = EnumSet.of(ClassModifier.PUBLIC);
		cg.setModifiers(cmods);
		cg.setSuperclass(Object.class);
		final String methodName = "hello";
		final Method m = cg.newMethod(methodName, "(Ljava/lang/String;)V");
		final EnumSet<MethodModifier> mmods =
			EnumSet.of(MethodModifier.PUBLIC, MethodModifier.ABSTRACT);
		m.setModifiers(mmods);
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method =
			newClass.getMethod(methodName, String.class);
		assertEquals(
			Modifier.ABSTRACT,
			method.getModifiers() & Modifier.ABSTRACT);
	}

	@SuppressWarnings("javadoc")
	@Test
	public void staticHello ()
		throws
			IOException,
			ClassNotFoundException,
			IllegalAccessException,
			NoSuchMethodException,
			SecurityException,
			IllegalArgumentException,
			InvocationTargetException
	{
		final CodeGenerator cg = new CodeGenerator();
		final EnumSet<ClassModifier> cmods = EnumSet.of(ClassModifier.PUBLIC);
		cg.setModifiers(cmods);
		cg.setSuperclass(Object.class);
		final String methodName = "hello";
		final Method m = cg.newMethod(
			methodName,
			"(Ljava/lang/String;)Ljava/lang/String;");
		final EnumSet<MethodModifier> mmods =
			EnumSet.of(MethodModifier.PUBLIC, MethodModifier.STATIC);
		m.setModifiers(mmods);
		final LocalVariable str = m.newParameter("str");
		m.load(str);
		m.returnToCaller();
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method =
			newClass.getMethod(methodName, String.class);
		final String expected = "Hello, world!\n";
		final Object actual = method.invoke(null, expected);
		assertEquals(expected, actual);
	}

	@SuppressWarnings("javadoc")
	@Test
	public void hello ()
		throws
			IOException,
			ClassNotFoundException,
			IllegalAccessException,
			NoSuchMethodException,
			SecurityException,
			IllegalArgumentException,
			InvocationTargetException,
			InstantiationException
	{
		final CodeGenerator cg = new CodeGenerator();
		final ConstantPool cp = cg.constantPool();
		final EnumSet<ClassModifier> cmods = EnumSet.of(ClassModifier.PUBLIC);
		cg.setModifiers(cmods);
		cg.setSuperclass(Object.class);
		final EnumSet<MethodModifier> mmods = EnumSet.of(MethodModifier.PUBLIC);
		final Method c = cg.newMethod("<init>", "()V");
		c.setModifiers(mmods);
		c.load(c.self());
		c.invokeSpecial(cp.methodref(Object.class, "<init>", Void.TYPE));
		c.returnToCaller();
		c.finish();
		final String methodName = "hello";
		final Method m = cg.newMethod(
			methodName,
			"(Ljava/lang/String;)Ljava/lang/String;");
		m.setModifiers(mmods);
		final LocalVariable str = m.newParameter("str");
		m.load(str);
		m.returnToCaller();
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method =
			newClass.getMethod(methodName, String.class);
		final Object newInstance = newClass.newInstance();
		final String expected = "Hello, world!\n";
		final Object actual = method.invoke(newInstance, expected);
		assertEquals(expected, actual);
	}
}
