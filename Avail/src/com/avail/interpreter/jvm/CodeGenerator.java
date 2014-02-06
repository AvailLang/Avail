/**
 * CodeGenerator.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.interpreter.jvm.ClassModifier.*;
import java.io.DataOutput;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.avail.annotations.Nullable;
import com.avail.annotations.ThreadSafe;
import com.avail.interpreter.jvm.ConstantPool.ClassEntry;
import com.avail.interpreter.jvm.ConstantPool.NameAndTypeKey;

/**
 * A {@code CodeGenerator} provides facilities for dynamic creation of Java
 * class files. It permits specification of {@linkplain Field fields} and
 * {@linkplain Method methods}.
 *
 * <p>The typical life cycle of a {@code CodeGenerator} is as follows:</p>
 *
 * <ol>
 * <li>Construct a new code generator for a {@linkplain #CodeGenerator(String)
 * named} or {@linkplain #CodeGenerator() anonymous} {@linkplain Class class}
 * </li>
 * <li>Build a {@linkplain EnumSet set} of {@linkplain ClassModifier class
 * modifiers} and associate it with the class: {@link #setModifiers(EnumSet)}
 * </li>
 * <li>Specify the superclass of the class: {@linkplain #setSuperclass(String)}
 * </li>
 * <li>Specify the interfaces that the class implements: {@linkplain
 * #addInterface(String)}</li>
 * <li>Plan the content of the class by:
 *    <ul>
 *    <li>Defining {@linkplain Field fields}: {@linkplain
 *    #newField(String, String)}</li>
 *    <li>Defining {@linkplain Method methods}: {@linkplain
 *    #newMethod(String, String)}</li>
 *    <li>Defining constructors: {@linkplain #newConstructor(String)} and
 *    {@linkplain #newDefaultConstructor()}</li>
 *    <li>Defining static initializers: {@linkplain #newStaticInitializer()}
 *    </li>
 *    </ul>
 * </li>
 * <li>Emit the class definition as a Java class file to a {@linkplain
 * DataOutput binary output stream}: {@linkplain #emitOn(DataOutput)}</li>
 * </ol>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class CodeGenerator
extends Emitter<ClassModifier>
{
	/** The package for anonymous {@linkplain Class classes}. */
	private static final String anonymousPackage =
		"com.avail.interpreter.jvm.dynamic$$";
	/**
	 * The {@linkplain Formatter#format(String, Object...) pattern} to use for
	 * anonymous {@linkplain Class class} names.
	 */
	private static final String anonymousPattern = anonymousPackage + ".A$$%d";

	/**
	 * The {@linkplain AtomicInteger ordinal} used to name the next anonymous
	 * {@linkplain Class class} produced by a {@linkplain CodeGenerator code
	 * generator}.
	 */
	private static final AtomicInteger ordinal = new AtomicInteger(0);

	/**
	 * Answer the next available anonymous class name. ("Anonymous" here means
	 * that the client did not explicitly provide a name for the class, but
	 * rather allowed the {@linkplain CodeGenerator code generator} to choose a
	 * unique one.).
	 *
	 * @return The next available anonymous class name.
	 */
	@ThreadSafe
	private static String nextAnonymousName ()
	{
		return String.format(anonymousPattern, ordinal.getAndIncrement());
	}

	/**
	 * The {@linkplain ClassEntry class entry} for the target {@linkplain
	 * Class class}.
	 */
	final ClassEntry classEntry;

	/**
	 * Answer the name of the {@linkplain Class class} being defined.
	 *
	 * @return The class name.
	 */
	public String name ()
	{
		return classEntry.name();
	}

	/**
	 * Construct a new {@link CodeGenerator}.
	 *
	 * @param name
	 *        The fully-qualified class name.
	 * @param isAnonymous
	 *        {@code true} if the name is supposed to be anonymous, {@code
	 *        false} otherwise.
	 */
	private CodeGenerator (final String name, final boolean isAnonymous)
	{
		super(new ConstantPool(), ClassModifier.class, SUPER, SYNTHETIC);
		assert isAnonymous || !name.startsWith(anonymousPackage);
		classEntry = constantPool.classConstant(name);
	}

	/**
	 * Construct a new {@link CodeGenerator}.
	 *
	 * @param name
	 *        The fully-qualified class name.
	 */
	public CodeGenerator (final String name)
	{
		this(name, false);
	}

	/**
	 * Construct a new {@link CodeGenerator}, giving the target {@linkplain
	 * Class class} an automatically chosen name.
	 */
	public CodeGenerator ()
	{
		this(nextAnonymousName(), true);
	}

	/**
	 * Answer the {@linkplain ConstantPool constant pool}.
	 *
	 * @return The constant pool.
	 */
	public ConstantPool constantPool ()
	{
		return constantPool;
	}

	/**
	 * The {@linkplain ClassEntry superclass entry} for the target {@linkplain
	 * Class class}.
	 */
	private @Nullable ClassEntry superEntry;

	/**
	 * Answer the {@linkplain ClassEntry superclass entry} for the target
	 * {@linkplain Class class}.
	 *
	 * @return The constant pool class entry for the target class's superclass.
	 */
	public ClassEntry superEntry ()
	{
		final ClassEntry theSuper = superEntry;
		assert theSuper != null;
		return theSuper;
	}

	/**
	 * Set the target {@linkplain Class class}'s superclass.
	 *
	 * @param name
	 *        The fully-qualified class name of the target class's superclass.
	 */
	public void setSuperclass (final String name)
	{
		assert superEntry == null;
		superEntry = constantPool.classConstant(name);
	}

	/**
	 * Set the target {@linkplain Class class}'s superclass.
	 *
	 * @param superclass
	 *        The superclass.
	 */
	public void setSuperclass (final Class<?> superclass)
	{
		setSuperclass(superclass.getName());
	}

	/**
	 * The {@linkplain List list} of {@linkplain Class#isInterface() interfaces}
	 * that the target {@linkplain Class class} implements.
	 */
	private final LinkedHashSet<ClassEntry> interfaceEntries =
		new LinkedHashSet<>();

	/**
	 * Add an implemented interface to the target {@linkplain Class class}.
	 *
	 * @param name
	 *        The fully-qualified class name of the target class's superclass.
	 */
	public void addInterface (final String name)
	{
		final ClassEntry interfaceEntry = constantPool.classConstant(name);
		assert !interfaceEntries.contains(interfaceEntry);
		interfaceEntries.add(interfaceEntry);
	}

	/**
	 * Add an implemented interface to the target {@linkplain Class class}.
	 *
	 * @param superinterface
	 *        The interface.
	 */
	public void addInterface (final Class<?> superinterface)
	{
		assert superinterface.isInterface();
		addInterface(superinterface.getName());
	}

	/**
	 * A {@linkplain Map map} from {@linkplain NameAndTypeKey field
	 * name-and-type keys} to {@linkplain Field fields}.
	 */
	private final LinkedHashMap<NameAndTypeKey, Field> fields =
		new LinkedHashMap<>();

	/**
	 * Create a new {@linkplain Field field} with the specified name and
	 * {@linkplain JavaDescriptors#forType(Class) type descriptor}.
	 *
	 * <p>If the {@linkplain CodeGenerator code generator} is defining an
	 * {@code interface}, then the field will be automatically marked as {@code
	 * static}.</p>
	 *
	 * @param name
	 *        The name of the target field.
	 * @param descriptor
	 *        The type descriptor of the target field.
	 * @return The representative for the new field.
	 */
	@ThreadSafe
	public Field newField (
		final String name,
		final String descriptor)
	{
		final NameAndTypeKey key = new NameAndTypeKey(name, descriptor);
		synchronized (fields)
		{
			assert !fields.containsKey(key);
			final Field field = new Field(this, name, descriptor);
			fields.put(key, field);
			return field;
		}
	}

	/**
	 * A {@linkplain Map map} from {@linkplain NameAndTypeKey method
	 * name-and-type keys} to {@linkplain Method methods}.
	 */
	private final LinkedHashMap<NameAndTypeKey, Method> methods =
		new LinkedHashMap<>();

	/**
	 * Create a new {@linkplain Method method} with the specified name and
	 * {@linkplain JavaDescriptors#forMethod(Class, Class...) signature
	 * descriptor}.
	 *
	 * <p>If the {@linkplain CodeGenerator code generator} is defining an
	 * {@code interface}, then the method will be automatically marked as
	 * {@code abstract}.</p>
	 *
	 * @param name
	 *        The name of the target method.
	 * @param descriptor
	 *        The signature descriptor of the target method.
	 * @return The representative for the new method.
	 */
	@ThreadSafe
	public Method newMethod (
		final String name,
		final String descriptor)
	{
		final NameAndTypeKey key = new NameAndTypeKey(name, descriptor);
		synchronized (methods)
		{
			assert !methods.containsKey(key);
			final Method method = new Method(this, name, descriptor);
			methods.put(key, method);
			return method;
		}
	}

	/**
	 * Create a new {@linkplain Method constructor} with the specified
	 * {@linkplain JavaDescriptors#forMethod(Class, Class...) signature
	 * descriptor}.
	 *
	 * <p>The code generator must not be defining an {@code interface}.</p>
	 *
	 * @param descriptor
	 *        The signature descriptor of the target constructor.
	 * @return The representative for the new constructor.
	 */
	@ThreadSafe
	public Method newConstructor (final String descriptor)
	{
		assert !modifiers.contains(INTERFACE);
		return newMethod(Method.constructorName, descriptor);
	}

	/**
	 * Create a new {@linkplain Method default constructor} that is fully
	 * specified and implemented.
	 *
	 * <p>The code generator must not be defining an {@code interface}.</p>
	 *
	 * @return The representative for the new method, already {@linkplain
	 *         Method#finish() finished}.
	 */
	@ThreadSafe
	public Method newDefaultConstructor ()
	{
		assert !modifiers.contains(INTERFACE);
		final Method method = newMethod(Method.constructorName, "()V");
		method.setModifiers(EnumSet.of(MethodModifier.PUBLIC));
		method.load(method.self());
		method.invokeSpecial(constantPool.methodref(
			superEntry().name(), Method.constructorName, "()V"));
		method.returnToCaller();
		method.finish();
		return method;
	}

	/**
	 * Create a new {@linkplain Method static initializer} that is ready to
	 * receive content.
	 *
	 * <p>The code generator must not be defining an {@code interface}.</p>
	 *
	 * @return The representative for the new static initializer, whose
	 *         {@linkplain MethodModifier method modifiers} have already been
	 *         {@linkplain Method#setModifiers(EnumSet) set}.
	 */
	@ThreadSafe
	public Method newStaticInitializer ()
	{
		assert !modifiers.contains(INTERFACE);
		final Method method = newMethod(Method.staticInitializerName, "()V");
		method.setModifiers(EnumSet.of(
			MethodModifier.PRIVATE, MethodModifier.STATIC));
		return method;
	}

	/**
	 * Is the definition of the {@linkplain Class class} consistent?
	 *
	 * @return {@code true} if the definition of the class is consistent, {@code
	 *         false} if there is a discrepancy between the {@linkplain
	 *         ClassModifier modifiers} and {@linkplain Field field} and
	 *         {@linkplain Method method} definitions.
	 */
	private boolean modifiersAreConsistentWithDefinitions ()
	{
		if (modifiers.contains(INTERFACE))
		{
			for (final Field field : fields.values())
			{
				if (!field.modifiers.contains(FieldModifier.STATIC))
				{
					return false;
				}
			}
			for (final Method method : methods.values())
			{
				if (method.instructionCount() != 0)
				{
					return false;
				}
				if (method.modifiers.contains(MethodModifier.ABSTRACT))
				{
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void setModifiers (final EnumSet<ClassModifier> mods)
	{
		super.setModifiers(mods);
		assert modifiersAreConsistentWithDefinitions();
	}

	/**
	 * The class file major version number. For {@code k} ≥ 2, JDK release
	 * {@code 1.k} supports class file format versions in the range {@code 45.0}
	 * through {@code 44+k.0} inclusive.
	 */
	private static final short majorVersion = 51;

	/** The class file minor version number. */
	private static final short minorVersion = 0;

	@Override
	void writeHeaderTo (final DataOutput out) throws IOException
	{
		out.writeInt(0xCAFEBABE);
		out.writeShort(minorVersion);
		out.writeShort(majorVersion);
		constantPool.writeTo(out);
	}

	@Override
	void writeBodyTo (final DataOutput out) throws IOException
	{
		classEntry.writeIndexTo(out);
		superEntry().writeIndexTo(out);
		out.writeShort(interfaceEntries.size());
		for (final ClassEntry interfaceEntry : interfaceEntries)
		{
			interfaceEntry.writeIndexTo(out);
		}
		out.writeShort(fields.size());
		for (final Field field : fields.values())
		{
			field.writeTo(out);
		}
		out.writeShort(methods.size());
		for (final Method method : methods.values())
		{
			method.writeTo(out);
		}
	}

	/**
	 * Emit the Java class file represented by the {@linkplain CodeGenerator
	 * code generator} to the specified {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	public void emitOn (final DataOutput out) throws IOException
	{
		writeTo(out);
	}

	@Override
	public String toString ()
	{
		@SuppressWarnings("resource")
		final Formatter formatter = new Formatter();
		final String mods = ClassModifier.toString(modifiers);
		formatter.format("%s%s", mods, mods.isEmpty() ? "" : " ");
		formatter.format("%s", name());
		if (!superEntry().internalName().equals(
			JavaDescriptors.asInternalName("java.lang.Object")))
		{
			formatter.format("%n\textends %s", superEntry().name());
		}
		if (!interfaceEntries.isEmpty())
		{
			formatter.format("%n\timplements ");
			boolean first = true;
			for (final ClassEntry entry : interfaceEntries)
			{
				if (!first)
				{
					formatter.format(", ");
				}
				formatter.format("%s", entry.name());
				first = false;
			}
		}
		synchronized (fields)
		{
			if (!fields.isEmpty())
			{
				formatter.format("%n%nFields:");
				for (final Field field : fields.values())
				{
					final String fieldString = field.toString().replaceAll(
						String.format("%n"), String.format("%n\t"));
					formatter.format("%n\t%s", fieldString);
				}
			}
		}
		synchronized (methods)
		{
			if (!methods.isEmpty())
			{
				formatter.format("%n%nMethods:");
				for (final Method method : methods.values())
				{
					final String methodString = method.toString().replaceAll(
						String.format("%n"), String.format("%n\t"));
					formatter.format("%n\t%s", methodString);
				}
			}
		}
		return formatter.toString();
	}
}
