/*
 * SerializerTest.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.AvailRuntime;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ModuleRoots;
import com.avail.builder.RenamesFileParser;
import com.avail.builder.RenamesFileParserException;
import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.primitive.floats.P_FloatFloor;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.serialization.Deserializer;
import com.avail.serialization.MalformedSerialStreamException;
import com.avail.serialization.Serializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.avail.descriptor.AtomDescriptor.falseObject;
import static com.avail.descriptor.AtomDescriptor.trueObject;
import static com.avail.descriptor.CharacterDescriptor.fromCodePoint;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerDescriptor.fromLong;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.ModuleDescriptor.newModule;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.SetDescriptor.setFromCollection;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tupleFromIntegerList;
import static com.avail.descriptor.TypeDescriptor.Types.FLOAT;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.Math.min;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for object serialization.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@TestInstance(Lifecycle.PER_CLASS)
public final class SerializerTest
{
	/**
	 * The {@link AvailRuntime} for use by the {@link #serializer} or the
	 * {@link #deserializer}.
	 */
	@Nullable AvailRuntime runtime = null;

	/**
	 * @return The {@link AvailRuntime} used by the serializer and deserializer.
	 */
	AvailRuntime runtime ()
	{
		return stripNull(runtime);
	}

	/**
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime.
	 */
	@BeforeAll
	public void initializeAllWellKnownObjects ()
	{
		final IndexedRepositoryManager repository =
			IndexedRepositoryManager.createTemporary(
				"avail", "test repository", null);
		final File repositoryFile = repository.getFileName();
		repository.close();
		final ModuleRoots roots = new ModuleRoots(String.format(
			"avail=%s,%s",
			repositoryFile.getAbsolutePath(),
			new File("distro/src/avail").getAbsolutePath()));
		final RenamesFileParser parser =
			new RenamesFileParser(new StringReader(""), roots);
		final ModuleNameResolver resolver;
		try
		{
			resolver = parser.parse();
		}
		catch (final RenamesFileParserException e)
		{
			throw new RuntimeException(e);
		}
		runtime = new AvailRuntime(resolver);
	}

	/**
	 * Test fixture: clear all special objects.
	 */
	@AfterAll
	public void clearAllWellKnownObjects ()
	{
		final @Nullable AvailRuntime theRuntime = runtime;
		if (theRuntime != null)
		{
			theRuntime.destroy();
			runtime = null;
		}
	}

	/**
	 * The stream onto which the serializer writes its bytes.
	 */
	@Nullable ByteArrayOutputStream out;

	/**
	 * The {@link Serializer} which converts objects to bytes.
	 */
	@Nullable Serializer serializer;

	/**
	 * @return The {@link Serializer} used by this test class.
	 */
	public Serializer serializer ()
	{
		return stripNull(serializer);
	}

	/**
	 * The source of bytes for the {@link Deserializer}.
	 */
	@Nullable ByteArrayInputStream in;

	/**
	 * @return The {@link ByteArrayInputStream} used by the deserializer.
	 */
	public ByteArrayInputStream in ()
	{
		return stripNull(in);
	}

	/**
	 * The {@link Deserializer} which (re)produces objects from bytes.
	 */
	@Nullable Deserializer deserializer;

	/**
	 * @return The {@link Deserializer} used by this test class.
	 */
	public Deserializer deserializer ()
	{
		return stripNull(deserializer);
	}

	/**
	 * Get ready to write objects to the {@link #serializer}.
	 */
	private void prepareToWrite ()
	{
		out = new ByteArrayOutputStream(1000);
		serializer = new Serializer(out);
		deserializer = null;
	}

	/**
	 * Finish writing serialized objects and prepare to deserialize them back
	 * again.
	 */
	private void prepareToReadBack ()
	{
		final ByteArrayOutputStream theByteStream = stripNull(out);
		final byte[] bytes = theByteStream.toByteArray();
		in = new ByteArrayInputStream(bytes);
		deserializer = new Deserializer(in, runtime());
		serializer = null;
	}

	/**
	 * Serialize then deserialize the given object.
	 *
	 * @param object The object to serialize and deserialize.
	 * @return The result of serializing and deserializing the argument.
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	private AvailObject roundTrip (final A_BasicObject object)
	throws MalformedSerialStreamException
	{
		prepareToWrite();
		serializer().serialize(object);
		prepareToReadBack();
		final AvailObject newObject = stripNull(deserializer().deserialize());
		assertEquals(
			0,
			in().available(),
			"Serialization stream was not fully emptied");
		final @Nullable AvailObject objectAfter = deserializer().deserialize();
		assert objectAfter == null;
		return newObject;
	}

	/**
	 * Ensure that the given object can be serialized and deserialized,
	 * producing an object equal to the original.  This is not universally
	 * expected to be true for all objects, as for example {@linkplain
	 * AtomDescriptor atoms} need to be looked up or recreated (unequally) by a
	 * {@link Deserializer}.
	 *
	 * @param object
	 *            The object to serialize, deserialize, and compare to the
	 *            original.
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	private void checkObject (final A_BasicObject object)
	throws MalformedSerialStreamException
	{
		final A_BasicObject newObject = roundTrip(object);
		assertEquals(object, newObject);
	}

	/**
	 * Test serialization of booleans.
	 *
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	@Test
	public void testBooleans ()
	throws MalformedSerialStreamException
	{
		checkObject(trueObject());
		checkObject(falseObject());
	}

	/**
	 * Test serialization of various integers.
	 *
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	@Test
	public void testIntegers ()
	throws MalformedSerialStreamException
	{
		for (int i = -500; i < 500; i++)
		{
			checkObject(fromInt(i));
		}
		checkObject(fromInt(10000));
		checkObject(fromInt(100000));
		checkObject(fromInt(1000000));
		checkObject(fromInt(10000000));
		checkObject(fromInt(100000000));
		checkObject(fromInt(1000000000));
		checkObject(fromInt(Integer.MAX_VALUE));
		checkObject(fromInt(Integer.MIN_VALUE));
		checkObject(fromLong(Long.MIN_VALUE));
		checkObject(fromLong(Long.MAX_VALUE));
	}

	/**
	 * Test serialization of some strings.
	 *
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	@Test
	public void testStrings ()
	throws MalformedSerialStreamException
	{
		checkObject(stringFrom(""));
		for (int i = 1; i < 100; i++)
		{
			checkObject(stringFrom(String.valueOf(i)));
		}
		checkObject(stringFrom("\u0000"));
		checkObject(stringFrom("\u0001"));
		checkObject(stringFrom("\u0003\u0002\u0001\u0000"));
		checkObject(stringFrom("Cheese \"cake\" surprise"));
		checkObject(stringFrom("\u00FF"));
		checkObject(stringFrom("\u0100"));
		checkObject(stringFrom("\u0101"));
		checkObject(stringFrom("I like peace ☮"));
		checkObject(stringFrom("I like music 𝄞"));
		checkObject(stringFrom("I really like music 𝄞𝄞"));
	}

	/**
	 * Test serialization of some tuples of integers.
	 *
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	@Test
	public void testNumericTuples ()
	throws MalformedSerialStreamException
	{
		checkObject(tupleFromIntegerList(singletonList(0)));
		checkObject(tupleFromIntegerList(singletonList(1)));
		checkObject(tupleFromIntegerList(singletonList(2)));
		checkObject(tupleFromIntegerList(singletonList(3)));
		checkObject(tupleFromIntegerList(asList(10, 20)));
		checkObject(tupleFromIntegerList(asList(10, 20, 10)));
		checkObject(tupleFromIntegerList(asList(100, 200)));
		checkObject(tupleFromIntegerList(asList(100, 2000)));
		checkObject(tupleFromIntegerList(singletonList(999999999)));
		for (int i = -500; i < 500; i++)
		{
			checkObject(tupleFromIntegerList(singletonList(i)));
		}
		checkObject(
			tuple(
				fromLong(Integer.MIN_VALUE),
				fromLong(Integer.MAX_VALUE)));
		checkObject(
			tuple(
				fromLong(Long.MIN_VALUE),
				fromLong(Long.MAX_VALUE)));
	}

	/**
	 * Test random combinations of tuples, sets, maps, integers, and characters.
	 *
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	@Test
	public void testRandomSimpleObjects ()
	throws MalformedSerialStreamException
	{
		final Random random = new Random(-4673928647810677481L);
		for (int run = 0; run < 100; run++)
		{
			final int partsCount = 1 + random.nextInt(1000);
			final List<A_BasicObject> parts =
				new ArrayList<>(partsCount);
			for (int partIndex = 0; partIndex < partsCount; partIndex++)
			{
				final A_BasicObject newObject;
				final int choice = (partIndex == partsCount - 1)
					? random.nextInt(3) + 2
					: random.nextInt(5);
				if (choice == 0)
				{
					newObject = fromInt(random.nextInt());
				}
				else if (choice == 1)
				{
					newObject = fromCodePoint(random.nextInt(0x110000));
				}
				else
				{
					int size = random.nextInt(min(20, partIndex + 1));
					if (choice == 4)
					{
						// For a map.
						size &= ~1;
					}
					final List<A_BasicObject> members =
						new ArrayList<>(size);
					for (int i = 0; i < size; i++)
					{
						members.add(parts.get(random.nextInt(partIndex)));
					}
					if (choice == 2)
					{
						newObject = tupleFromList(members);
					}
					else if (choice == 3)
					{
						newObject = setFromCollection(members);
					}
					else //if (choice == 4)
					{
						A_Map map = emptyMap();
						for (int i = 0; i < size; i+=2)
						{
							map = map.mapAtPuttingCanDestroy(
								members.get(i),
								members.get(i + 1),
								true);
						}
						newObject = map;
					}
				}
				newObject.makeImmutable();
				parts.add(newObject);
			}
			assert parts.size() == partsCount;
			checkObject(parts.get(partsCount - 1));
		}
	}

	/**
	 * Test serialization and deserialization of atom references.
	 *
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	@Test
	public void testAtoms ()
	throws MalformedSerialStreamException
	{
		final A_Module inputModule = newModule(
			stringFrom("Imported"));
		final A_Module currentModule = newModule(
			stringFrom("Current"));
		final A_Atom atom1 = AtomDescriptor.createAtom(
			stringFrom("importAtom1"),
			inputModule);
		inputModule.addPrivateName(atom1);
		final A_Atom atom2 = AtomDescriptor.createAtom(
			stringFrom("currentAtom2"),
			currentModule);
		currentModule.addPrivateName(atom2);
		final A_Tuple tuple = tuple(atom1, atom2);

		prepareToWrite();
		serializer().serialize(tuple);
		prepareToReadBack();
		runtime().addModule(inputModule);
		deserializer().setCurrentModule(currentModule);
		final @Nullable A_BasicObject newObject = deserializer().deserialize();
		assertNotNull(newObject);
		assertEquals(
			0,
			in().available(),
			"Serialization stream was not fully emptied");
		final @Nullable A_BasicObject nullObject = deserializer().deserialize();
		assertNull(nullObject);
		assertEquals(tuple, newObject);
	}

	/**
	 * Test serialization and deserialization of functions.
	 *
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	@Test
	public void testFunctions ()
	throws MalformedSerialStreamException
	{
		final L1InstructionWriter writer = new L1InstructionWriter(
			nil, 0, nil);
		writer.argumentTypes(FLOAT.o());
		writer.setPrimitive(P_FloatFloor.instance);
		writer.setReturnType(FLOAT.o());
		final A_RawFunction code = writer.compiledCode();
		final A_Function function =
			createFunction(code, emptyTuple());
		final A_Function newFunction = roundTrip(function);
		final A_RawFunction code2 = newFunction.code();
		assertEquals(code.numOuters(), code2.numOuters());
		assertEquals(
			code.numSlots(),
			code2.numSlots());
		assertEquals(code.numArgs(), code2.numArgs());
		assertEquals(code.numLocals(), code2.numLocals());
		assertEquals(code.primitiveNumber(), code2.primitiveNumber());
		assertEquals(code.nybbles(), code2.nybbles());
		assertEquals(code.functionType(), code2.functionType());
		for (int i = code.numLiterals(); i > 0; i--)
		{
			assertEquals(code.literalAt(i), code2.literalAt(i));
		}
	}
}
