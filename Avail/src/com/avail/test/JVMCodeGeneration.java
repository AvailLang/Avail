/**
 * JVMCodeGeneration.java
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

package com.avail.test;

import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import com.avail.annotations.Nullable;
import com.avail.interpreter.jvm.*;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader.Array;

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

	/**
	 * Emit a simple class definition onto the specified {@linkplain
	 * CodeGenerator code generator}. The class will be {@linkplain
	 * ClassModifier#PUBLIC public} and its superclass will be {@link Object}.
	 *
	 * @param cg
	 *        A code generator.
	 */
	private void simpleClassPreambleOn (final CodeGenerator cg)
	{
		final EnumSet<ClassModifier> cmods = EnumSet.of(ClassModifier.PUBLIC);
		cg.setModifiers(cmods);
		cg.setSuperclass(Object.class);
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
//		public class X
//		{
//		}
		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
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
//		public class X
//		{
//			public static String foo;
//		}
		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
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
	public void instanceField ()
		throws
		IOException,
		ClassNotFoundException,
		SecurityException,
		IllegalArgumentException,
		NoSuchFieldException,
		IllegalAccessException,
		NoSuchMethodException,
		InstantiationException,
		InvocationTargetException
	{
//		The compiler may emit slightly different instructions.
//		public class Constructor
//		{
//			public String foo;
//			public Constructor (String foo)
//			{
//				this.foo = foo;
//			}
//		}
		final CodeGenerator cg = new CodeGenerator();
		final ConstantPool cp = cg.constantPool();
		simpleClassPreambleOn(cg);
		// Declare a field: public String foo
		final String fieldName = "foo";
		final Field f = cg.newField(fieldName, "Ljava/lang/String;");
		final EnumSet<FieldModifier> fmods = EnumSet.of(FieldModifier.PUBLIC);
		f.setModifiers(fmods);
		// Declare a constructor: public <init> (String)
		final Method con = cg.newConstructor("(Ljava/lang/String;)V");
		final EnumSet<MethodModifier> mmods = EnumSet.of(MethodModifier.PUBLIC);
		con.setModifiers(mmods);
		// Give the parameter the same name as the field.
		final LocalVariable param = con.newParameter(fieldName);
		// The constructor just sets the field.
		con.load(con.self());
		con.dup();
		con.invokeSpecial(cp.methodref(
			Object.class, Method.constructorName, Void.TYPE));
		con.load(param);
		con.store(f);
		con.returnToCaller();
		con.finish();
		// Load the class.
		final Class<?> newClass = loadClass(cg);
		// Instantiate the class and test the field.
		final Constructor<?> constructor =
			newClass.getConstructor(String.class);
		final String expected = "bar";
		final Object newInstance = constructor.newInstance(expected);
		final java.lang.reflect.Field field = newClass.getField(fieldName);
		assertEquals(expected, field.get(newInstance));
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
//		public abstract class X
//		{
//			public abstract String hello (String str);
//		}
		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
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
//		public class X
//		{
//			public static String hello (String str)
//			{
//				return str;
//			}
//		}
		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
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
//		public class X
//		{
//			public String hello (String str)
//			{
//				return str;
//			}
//		}
		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
		cg.newDefaultConstructor();
		final String methodName = "hello";
		final Method m = cg.newMethod(
			methodName,
			"(Ljava/lang/String;)Ljava/lang/String;");
		final EnumSet<MethodModifier> mmods = EnumSet.of(MethodModifier.PUBLIC);
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

	@SuppressWarnings("javadoc")
	@Test
	public void exceptionHandling ()
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
		simpleClassPreambleOn(cg);
		cg.newDefaultConstructor();
		// Generate a method that answers an integer…
		final String methodName = "returnConstant";
		final Method m = cg.newMethod(methodName, "()I");
		final EnumSet<MethodModifier> mmods = EnumSet.of(MethodModifier.PUBLIC);
		m.setModifiers(mmods);
		// …by sending hashCode() to null…
		final int expected = 29;
		final Label guardStart = m.newLabel("guardStart");
		m.addLabel(guardStart);
		m.pushNull();
		m.invokeVirtual(cp.methodref(Object.class, "hashCode", Integer.TYPE));
		m.returnToCaller();
		// …and answering the magic constant from the exception handler.
		final HandlerLabel guardEnd = m.newHandlerLabel(
			"guardEnd", NullPointerException.class);
		m.addLabel(guardEnd);
		m.enterScope();
		final LocalVariable e = m.newLocalVariable(
			"e", NullPointerException.class);
		m.store(e);
		m.pushConstant(expected);
		m.returnToCaller();
		m.exitScope();
		m.addGuardedZone(
			guardStart, guardEnd, guardEnd, NullPointerException.class);
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method = newClass.getMethod(methodName);
		final Object newInstance = newClass.newInstance();
		final int actual = (int) method.invoke(newInstance);
		assertEquals(expected, actual);
	}

	@SuppressWarnings("javadoc")
	@Test
	public void sums ()
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
//		public class X
//		{
//			public int add5 (int augend, int addend)
//			{
//				return augend + addend + 5;
//			}
//		}
		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
		cg.newDefaultConstructor();
		final String methodName = "add5";
		final Method m = cg.newMethod(methodName, "(II)I");
		final EnumSet<MethodModifier> mmods = EnumSet.of(MethodModifier.PUBLIC);
		m.setModifiers(mmods);
		final LocalVariable augend = m.newParameter("augend");
		final LocalVariable addend = m.newParameter("addend");
		m.load(augend);
		m.load(addend);
		m.doOperator(BinaryOperator.ADDITION, Integer.TYPE);
		m.pushConstant(5);
		m.doOperator(BinaryOperator.ADDITION, Integer.TYPE);
		m.returnToCaller();
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method =
			newClass.getMethod(methodName, Integer.TYPE, Integer.TYPE);
		final Object newInstance = newClass.newInstance();
		for (int i = 0; i < 100; i++)
		{
			for (int j = 0; j < 100; j++)
			{
				final int expected = i + j + 5;
				final int actual = (int) method.invoke(newInstance, i, j);
				assertEquals(expected, actual);
			}
		}
	}

	@SuppressWarnings("javadoc")
	@Test
	public void gotoJump ()
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
//		public class X
//		{
//			public void gotoJump ()
//			{
//				goto label;
//			label:
//			}
//		}
		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
		cg.newDefaultConstructor();
		final String methodName = "gotoJump";
		final Method m = cg.newMethod(methodName, "()V");
		final EnumSet<MethodModifier> mmods = EnumSet.of(MethodModifier.PUBLIC);
		m.setModifiers(mmods);
		final Label label = m.newLabel("label");
		m.branchTo(label);
		m.addLabel(label);
		m.returnToCaller();
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method =
			newClass.getMethod(methodName);
		final Object newInstance = newClass.newInstance();
		method.invoke(newInstance);
	}

	@SuppressWarnings("javadoc")
	@Test
	public void whileLoop ()
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
//		public class X
//		{
//			public int whileLoop (int start)
//			{
//				int i = 0;
//				while (i < start)
//				{
//					i++;
//				}
//				return start;
//			}
//		}
		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
		cg.newDefaultConstructor();
		final String methodName = "whileLoop";
		final Method m = cg.newMethod(methodName, "(I)I");
		final EnumSet<MethodModifier> mmods = EnumSet.of(MethodModifier.PUBLIC);
		m.setModifiers(mmods);
		final LocalVariable start = m.newParameter("start");
		m.enterScope();
		final LocalVariable i = m.newLocalVariable("i", Integer.TYPE);
		m.pushConstant(0);
		m.store(i);
		final Label topLabel = m.newLabel("top");
		m.addLabel(topLabel);
		m.load(i);
		m.load(start);
		final Label bottomLabel = m.newLabel("bottom");
		bottomLabel.copyOperandStackFrom(topLabel);
		m.branchUnless(PrimitiveComparisonOperator.LESS_THAN,
			bottomLabel);
		m.increment(i, 1);
		m.branchTo(topLabel);
		m.addLabel(bottomLabel);
		m.load(start);
		m.returnToCaller();
		m.exitScope();
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method =
			newClass.getMethod(methodName, Integer.TYPE);
		final Object newInstance = newClass.newInstance();
		for (int j = 0; j < 10; j++)
		{
			final int expected = j;
			final int actual = (int) method.invoke(newInstance, j);
			assertEquals(expected, actual);
		}
	}

	@SuppressWarnings("javadoc")
	@Test
	public void ifElse ()
		throws
			IOException,
			ClassNotFoundException,
			IllegalAccessException,
			NoSuchMethodException,
			SecurityException,
			IllegalArgumentException,
			InvocationTargetException
	{
//		public class X
//		{
//			public static int ifElse(int x)
//			{
//				int z = 5;
//				if (x < 0)
//				{
//					int y = x + z + 1;
//					z = y * 2;
//				}
//				else
//				{
//					z = z + x;
//				}
//				return z * 2;
//			}
//		}
		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
		cg.newDefaultConstructor();
		final String methodName = "ifElse";
		final Method m = cg.newMethod(methodName, "(I)I");
		final EnumSet<MethodModifier> mmods =
			EnumSet.of(MethodModifier.PUBLIC, MethodModifier.STATIC);
		m.setModifiers(mmods);
		final LocalVariable x = m.newParameter("x");
		m.enterScope();
		final LocalVariable z = m.newLocalVariable("z", Integer.TYPE);
		m.pushConstant(5);
		m.store(z);
		final Label predicateLabel = m.newLabel("predicate");
		m.addLabel(predicateLabel);
		m.load(x);
		final Label elseLabel = m.newLabel("else");
		m.branchUnless(
			PrimitiveComparisonOperator.LESS_THAN_ZERO,
			elseLabel);
		m.enterScope();
		m.load(x);
		m.load(z);
		m.doOperator(BinaryOperator.ADDITION, Integer.TYPE);
		m.pushConstant(1);
		m.doOperator(BinaryOperator.ADDITION, Integer.TYPE);
		final LocalVariable y = m.newLocalVariable("y", Integer.TYPE);
		m.store(y);
		m.load(y);
		m.pushConstant(2);
		m.doOperator(BinaryOperator.MULTIPLICATION, Integer.TYPE);
		m.store(z);
		m.exitScope();
		final Label doneLabel = m.newLabel("done");
		m.branchTo(doneLabel);
		elseLabel.copyOperandStackFrom(predicateLabel);
		m.addLabel(elseLabel);
		m.load(z);
		m.load(x);
		m.doOperator(BinaryOperator.ADDITION, Integer.TYPE);
		m.store(z);
		doneLabel.copyOperandStackFrom(predicateLabel);
		m.addLabel(doneLabel);
		m.load(z);
		m.pushConstant(2);
		m.doOperator(BinaryOperator.MULTIPLICATION, Integer.TYPE);
		m.returnToCaller();
		m.exitScope();
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method =
			newClass.getMethod(methodName, Integer.TYPE);
		final int[] expectedAnswers = {16, 20, 10, 12, 14};
		for (int j = 0; j <= 4; j++)
		{
			final int expected = expectedAnswers[j];
			final int actual = (int) method.invoke(null, j - 2);
			assertEquals(expected, actual);
		}
	}

@SuppressWarnings("javadoc")
@Test
public void tableSwitchCase ()
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
//		public class TableSwitchCase {
//
//		  	public int switchCase (int input)
//		  	{
//		  		int output;
//		  		switch (input)
//		  		{
//		  			case 0:
//		  			{
//		  				output = input - 1;
//		  				break;
//		  			}
//		  			case 1:
//		  			{
//		  				output = 2 * input;
//		  				break;
//		  			}
//		  			case 2:
//		  			{
//		  				output = 2 + input;
//		  				break;
//		  			}
//		  			case 3:
//		  			{
//		  				output = 3 + input;
//		  				break;
//		  			}
//		  			case 4:
//		  			{
//		  				output = 32 / input;
//		  				break;
//		  			}
//		  			default:
//		  			{
//		  				output = input;
//		  				break;
//		  			}
//		  		}
//		  		return output;
//		  	}
//		}

		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
		cg.newDefaultConstructor();
		final String methodName = "switchCase";
		final Method m = cg.newMethod(methodName, "(I)I");
		final EnumSet<MethodModifier> mmods =
			EnumSet.of(MethodModifier.PUBLIC);
		m.setModifiers(mmods);
		final LocalVariable input = m.newParameter("input");
		m.enterScope();
		final LocalVariable output = m.newLocalVariable("output", Integer.TYPE);
		m.load(input);
		final ArrayList<Label> switchLabels = new ArrayList<>();
		final Label label0 = m.newLabel("L0");
		final Label label1 = m.newLabel("L1");
		final Label label2 = m.newLabel("L2");
		final Label label3 = m.newLabel("L3");
		final Label label4 = m.newLabel("L4");
		final Label labelDefault = m.newLabel("default");
		switchLabels.add(label0);
		switchLabels.add(label1);
		switchLabels.add(label2);
		switchLabels.add(label3);
		switchLabels.add(label4);
		m.tableSwitch(0, 4, switchLabels, labelDefault);
		m.addLabel(label0);
		m.enterScope();
		m.load(input);
		m.pushConstant(1);
		m.doOperator(BinaryOperator.SUBTRACTION, Integer.TYPE);
		m.store(output);
		final Label doneLabel = m.newLabel("done");
		m.exitScope();
		m.branchTo(doneLabel);
		m.addLabel(label1);
		m.enterScope();
		m.pushConstant(2);
		m.load(input);
		m.doOperator(BinaryOperator.MULTIPLICATION, Integer.TYPE);
		m.store(output);
		m.exitScope();
		m.branchTo(doneLabel);
		m.addLabel(label2);
		m.enterScope();
		m.pushConstant(2);
		m.load(input);
		m.doOperator(BinaryOperator.ADDITION, Integer.TYPE);
		m.store(output);
		m.exitScope();
		m.branchTo(doneLabel);
		m.addLabel(label3);
		m.enterScope();
		m.pushConstant(3);
		m.load(input);
		m.doOperator(BinaryOperator.ADDITION, Integer.TYPE);
		m.store(output);
		m.exitScope();
		m.branchTo(doneLabel);
		m.addLabel(label4);
		m.enterScope();
		m.pushConstant(32);
		m.load(input);
		m.doOperator(BinaryOperator.DIVISION, Integer.TYPE);
		m.store(output);
		m.exitScope();
		m.branchTo(doneLabel);
		m.addLabel(labelDefault);
		m.enterScope();
		m.load(input);
		m.store(output);
		m.exitScope();
		m.addLabel(doneLabel);
		m.load(output);
		m.returnToCaller();
		m.exitScope();
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method =
			newClass.getMethod(methodName, Integer.TYPE);
		final Object newInstance = newClass.newInstance();
		final int[] expectedAnswers = {-1, 2, 4, 6, 8, 5};
		for (int j = 0; j <= 5; j++)
		{
			final int expected = expectedAnswers[j];
			final int actual = (int) method.invoke(newInstance, j);
			assertEquals(expected, actual);
		}
	}

@SuppressWarnings("javadoc")
@Test
public void lookupSwitchCase ()
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
//	public class SwitchLookupCase {
//
//	  	public int switchLookupCase (int input)
//	  	{
//	  		int output;
//	  		switch (input)
//	  		{
//	  			case 0:
//	  			{
//	  				output = input - 1;
//	  				break;
//	  			}
//	  			case 10:
//	  			{
//	  				output = 2 * input;
//	  				break;
//	  			}
//	  			case 100:
//	  			{
//	  				output = 2 + input;
//	  				break;
//	  			}
//	  			case 1000:
//	  			{
//	  				output = 3 + input;
//	  				break;
//	  			}
//	  			case 10000:
//	  			{
//	  				output = 100000 / input;
//	  				break;
//	  			}
//	  			default:
//	  			{
//	  				output = input;
//	  				break;
//	  			}
//	  		}
//	  		return output;
//	  	}
//	}

		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
		cg.newDefaultConstructor();
		final String methodName = "switchCase";
		final Method m = cg.newMethod(methodName, "(I)I");
		final EnumSet<MethodModifier> mmods =
			EnumSet.of(MethodModifier.PUBLIC);
		m.setModifiers(mmods);
		final LocalVariable input = m.newParameter("input");
		m.enterScope();
		final LocalVariable output = m.newLocalVariable("output", Integer.TYPE);
		m.load(input);
		final ArrayList<Label> switchLabels = new ArrayList<>();
		final Label label0 = m.newLabel("L0");
		final Label label1 = m.newLabel("L10");
		final Label label2 = m.newLabel("L100");
		final Label label3 = m.newLabel("L1000");
		final Label label4 = m.newLabel("L10000");
		final Label labelDefault = m.newLabel("default");
		switchLabels.add(label0);
		switchLabels.add(label1);
		switchLabels.add(label2);
		switchLabels.add(label3);
		switchLabels.add(label4);
		final List<Integer> keys = new ArrayList<>();
		keys.add(0);
		keys.add(10);
		keys.add(100);
		keys.add(1000);
		keys.add(10000);
		m.lookupSwitch(keys , switchLabels, labelDefault);
		m.addLabel(label0);
		m.enterScope();
		m.load(input);
		m.pushConstant(1);
		m.doOperator(BinaryOperator.SUBTRACTION, Integer.TYPE);
		m.store(output);
		final Label doneLabel = m.newLabel("done");
		m.exitScope();
		m.branchTo(doneLabel);
		m.addLabel(label1);
		m.enterScope();
		m.pushConstant(2);
		m.load(input);
		m.doOperator(BinaryOperator.MULTIPLICATION, Integer.TYPE);
		m.store(output);
		m.exitScope();
		m.branchTo(doneLabel);
		m.addLabel(label2);
		m.enterScope();
		m.pushConstant(2);
		m.load(input);
		m.doOperator(BinaryOperator.ADDITION, Integer.TYPE);
		m.store(output);
		m.exitScope();
		m.branchTo(doneLabel);
		m.addLabel(label3);
		m.enterScope();
		m.pushConstant(3);
		m.load(input);
		m.doOperator(BinaryOperator.ADDITION, Integer.TYPE);
		m.store(output);
		m.exitScope();
		m.branchTo(doneLabel);
		m.addLabel(label4);
		m.enterScope();
		m.pushConstant(100000);
		m.load(input);
		m.doOperator(BinaryOperator.DIVISION, Integer.TYPE);
		m.store(output);
		m.exitScope();
		m.branchTo(doneLabel);
		m.addLabel(labelDefault);
		m.enterScope();
		m.load(input);
		m.store(output);
		m.exitScope();
		m.addLabel(doneLabel);
		m.load(output);
		m.returnToCaller();
		m.exitScope();
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method =
			newClass.getMethod(methodName, Integer.TYPE);
		final Object newInstance = newClass.newInstance();
		final int[] expectedAnswers = {-1, 20, 102, 1003, 10, 5};
		final List<Integer> testValues = new ArrayList<>();
		testValues.add(0);
		testValues.add(10);
		testValues.add(100);
		testValues.add(1000);
		testValues.add(10000);
		testValues.add(5);
		int i = 0;
		for (final Integer j : testValues)
		{
			final int expected = expectedAnswers[i];
			final int actual = (int) method.invoke(newInstance, j);
			i++;
			assertEquals(expected, actual);
		}
	}

@SuppressWarnings("javadoc")
@Test
public void forLoop ()
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
//	public class Spin
//	{
//		public void forLoop()
//		{
//			for (int j = 0; j < 57; j++)
//			{
//					;
//			}
//		}
//	}

		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
		cg.newDefaultConstructor();
		final String methodName = "forLoop";
		final Method m = cg.newMethod(methodName, "()I");
		final EnumSet<MethodModifier> mmods =
			EnumSet.of(MethodModifier.PUBLIC);
		m.setModifiers(mmods);
		m.enterScope();
		final LocalVariable i = m.newLocalVariable("i", Integer.TYPE);
		m.pushConstant(0);
		m.store(i);
		final LocalVariable j = m.newLocalVariable("j", Integer.TYPE);
		m.pushConstant(0);
		m.store(j);
		final Label startLabel = m.newLabel("start");
		m.addLabel(startLabel);
		m.load(j);
		m.pushConstant(57);
		final Label doneLabel = m.newLabel("done");
		m.branchUnless(
			PrimitiveComparisonOperator.LESS_THAN,
			doneLabel);
		m.enterScope();
		m.increment(i, 1);
		m.increment(j, 1);
		m.exitScope();
		m.branchTo(startLabel);
		m.addLabel(doneLabel);
		m.load(i);
		m.returnToCaller();
		m.exitScope();
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method =
			newClass.getMethod(methodName);
		final Object newInstance = newClass.newInstance();

		final int actual = (int) method.invoke(newInstance);
		assertEquals(57, actual);
	}

@SuppressWarnings("javadoc")
@Test
public void arraySum ()
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

//	public class ArrayManipulator
//	{
//		public int sumArray (int input)
//		{
//			int[] intArray = {1,2,3,4,5,6};
//			int total = input;
//
//			for (int number : intArray)
//			{
//				total += number;
//			}
//
//			return total;
//		}
//	}

		final CodeGenerator cg = new CodeGenerator();
		simpleClassPreambleOn(cg);
		cg.newDefaultConstructor();
		final String methodName = "arraySum";
		final Method m = cg.newMethod(methodName, "(I)I");
		final EnumSet<MethodModifier> mmods =
			EnumSet.of(MethodModifier.PUBLIC);
		m.setModifiers(mmods);
		m.enterScope();
		final LocalVariable input = m.newParameter("input"); //#1
		m.pushConstant(6);
		m.newArray("I");
		m.dup();
		m.pushConstant(0);
		m.pushConstant(1);
		m.storeArrayElement(Integer.TYPE);
		m.dup();
		m.pushConstant(1);
		m.pushConstant(2);
		m.storeArrayElement(Integer.TYPE);
		m.dup();
		m.pushConstant(2);
		m.pushConstant(3);
		m.storeArrayElement(Integer.TYPE);
		m.dup();
		m.pushConstant(3);
		m.pushConstant(4);
		m.storeArrayElement(Integer.TYPE);
		m.dup();
		m.pushConstant(4);
		m.pushConstant(5);
		m.storeArrayElement(Integer.TYPE);
		m.dup();
		m.pushConstant(5);
		m.pushConstant(6);
		m.storeArrayElement(Integer.TYPE);
		final LocalVariable intArray =
			m.newLocalVariable("intArray", Array.class); //#2
		m.store(intArray);
		final LocalVariable total = m.newLocalVariable("total", Integer.TYPE); //#3
		m.load(input);
		m.store(total); //line 31
		final LocalVariable arr$ = m.newLocalVariable("arr$", Integer.TYPE); //#4
		m.load(intArray);
		m.store(arr$);
		final LocalVariable len$ = m.newLocalVariable("i$", Integer.TYPE); //#5
		m.arraySize();
		m.store(len$);
		final LocalVariable i$ = m.newLocalVariable("i$", Integer.TYPE); //#6
		m.pushConstant(0);
		m.store(i$);
		m.load(i$);
		m.load(len$);
		final Label endLoopLabel = m.newLabel("endLoop");
		final Label startLoopLabel = m.newLabel("startLoop");
		m.addLabel(startLoopLabel);
		m.branchUnless(PrimitiveComparisonOperator.LESS_THAN, endLoopLabel); //line 47
		m.enterScope();
		m.load(arr$);
		m.load(i$);
		m.loadArrayElement(Integer.TYPE);
		final LocalVariable number = m.newLocalVariable("number", Integer.TYPE);
		m.store(number);
		m.load(total);
		m.load(number);
		m.doOperator(BinaryOperator.ADDITION, Integer.TYPE);
		m.store(total);
		m.increment(i$, 1);
		m.exitScope();
		m.branchTo(startLoopLabel);
		m.addLabel(endLoopLabel);
		m.load(total);
		m.returnToCaller();
		m.finish();
		final Class<?> newClass = loadClass(cg);
		final java.lang.reflect.Method method =
			newClass.getMethod(methodName);
		final Object newInstance = newClass.newInstance();

		final int actual = (int) method.invoke(newInstance);
		assertEquals(21, actual);
	}
}
