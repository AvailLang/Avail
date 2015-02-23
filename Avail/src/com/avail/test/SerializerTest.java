/**
 * SerializerTest.java
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
import static com.avail.descriptor.TypeDescriptor.Types.FLOAT;
import static java.lang.Math.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.builder.*;
import com.avail.descriptor.*;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.primitive.P_292_FloatFloor;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.serialization.*;

/**
 * Unit tests for object serialization.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class SerializerTest
{
	/**
	 * The {@link AvailRuntime} for use by the {@link #serializer} or the
	 * {@link #deserializer}.
	 */
	static @Nullable AvailRuntime runtime;

	/**
	 * @return The {@link AvailRuntime} used by the serializer and deserializer.
	 */
	final AvailRuntime runtime ()
	{
		final AvailRuntime theRuntime = runtime;
		assert theRuntime != null;
		return theRuntime;
	}

	/**
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime.
	 *
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	@BeforeClass
	public static void initializeAllWellKnownObjects () throws IOException
	{
		final IndexedRepositoryManager repository =
			IndexedRepositoryManager.createTemporary(
				"avail", "test repository", null);
		final File repositoryFile = repository.fileName();
		repository.close();
		final ModuleRoots roots = new ModuleRoots(String.format(
			"avail=%s,%s",
			repositoryFile.getAbsolutePath(),
			new File("distro/src/avail").getAbsolutePath()));
		final RenamesFileParser parser =
			new RenamesFileParser(new StringReader(""), roots);
		ModuleNameResolver resolver;
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
	@AfterClass
	public static void clearAllWellKnownObjects ()
	{
		final AvailRuntime theRuntime = runtime;
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
		final Serializer theSerializer = serializer;
		assert theSerializer != null;
		return theSerializer;
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
		final ByteArrayInputStream theInput = in;
		assert theInput != null;
		return theInput;
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
		final Deserializer theDeserializer = deserializer;
		assert theDeserializer != null;
		return theDeserializer;
	}

	/**
	 * Get ready to write objects to the {@link #serializer}.
	 */
	private void prepareToWrite ()
	{
		serializer = new Serializer(
			out = new ByteArrayOutputStream(1000));
		deserializer = null;
	}

	/**
	 * Finish writing serialized objects and prepare to deserialize them back
	 * again.
	 */
	private void prepareToReadBack ()
	{
		final ByteArrayOutputStream theByteStream = out;
		assert theByteStream != null;
		final byte[] bytes = theByteStream.toByteArray();
		deserializer = new Deserializer(
			in = new ByteArrayInputStream(bytes),
			runtime());
		serializer = null;
	}

	/**
	 * Serialize then deserialize the given object.
	 *
	 * @param object The object to serialize and deserialize.
	 * @return The result of serializing and deserializing the argument.
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	private @Nullable AvailObject roundTrip (final A_BasicObject object)
	throws MalformedSerialStreamException
	{
		prepareToWrite();
		serializer().serialize(object);
		prepareToReadBack();
		final A_BasicObject newObject = deserializer().deserialize();
		assertTrue(
			"Serialization stream was not fully emptied",
			in().available() == 0);
		assert deserializer().deserialize() == null;
		return (AvailObject)newObject;
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
		assertNotNull(newObject);
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
		checkObject(AtomDescriptor.trueObject());
		checkObject(AtomDescriptor.falseObject());
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
			checkObject(IntegerDescriptor.fromInt(i));
		}
		checkObject(IntegerDescriptor.fromInt(10000));
		checkObject(IntegerDescriptor.fromInt(100000));
		checkObject(IntegerDescriptor.fromInt(1000000));
		checkObject(IntegerDescriptor.fromInt(10000000));
		checkObject(IntegerDescriptor.fromInt(100000000));
		checkObject(IntegerDescriptor.fromInt(1000000000));
		checkObject(IntegerDescriptor.fromInt(Integer.MAX_VALUE));
		checkObject(IntegerDescriptor.fromInt(Integer.MIN_VALUE));
		checkObject(IntegerDescriptor.fromLong(Long.MIN_VALUE));
		checkObject(IntegerDescriptor.fromLong(Long.MAX_VALUE));
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
		checkObject(StringDescriptor.from(""));
		for (int i = 1; i < 100; i++)
		{
			checkObject(StringDescriptor.from(String.valueOf(i)));
		}
		checkObject(StringDescriptor.from("\u0000"));
		checkObject(StringDescriptor.from("\u0001"));
		checkObject(StringDescriptor.from("\u0003\u0002\u0001\u0000"));
		checkObject(StringDescriptor.from("Cheese \"cake\" surprise"));
		checkObject(StringDescriptor.from("\u00FF"));
		checkObject(StringDescriptor.from("\u0100"));
		checkObject(StringDescriptor.from("\u0101"));
		checkObject(StringDescriptor.from("I like peace ☮"));
		checkObject(StringDescriptor.from("I like music 𝄞"));
		checkObject(StringDescriptor.from("I really like music 𝄞𝄞"));
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
		checkObject(TupleDescriptor.fromIntegerList(Arrays.asList(0)));
		checkObject(TupleDescriptor.fromIntegerList(Arrays.asList(1)));
		checkObject(TupleDescriptor.fromIntegerList(Arrays.asList(2)));
		checkObject(TupleDescriptor.fromIntegerList(Arrays.asList(3)));
		checkObject(TupleDescriptor.fromIntegerList(Arrays.asList(10, 20)));
		checkObject(TupleDescriptor.fromIntegerList(Arrays.asList(10, 20, 10)));
		checkObject(TupleDescriptor.fromIntegerList(Arrays.asList(100, 200)));
		checkObject(TupleDescriptor.fromIntegerList(Arrays.asList(100, 2000)));
		checkObject(TupleDescriptor.fromIntegerList(Arrays.asList(999999999)));
		for (int i = -500; i < 500; i++)
		{
			checkObject(TupleDescriptor.fromIntegerList(Arrays.asList(i)));
		}
		checkObject(TupleDescriptor.from(
			IntegerDescriptor.fromLong(Integer.MIN_VALUE),
			IntegerDescriptor.fromLong(Integer.MAX_VALUE)));
		checkObject(TupleDescriptor.from(
			IntegerDescriptor.fromLong(Long.MIN_VALUE),
			IntegerDescriptor.fromLong(Long.MAX_VALUE)));
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
				A_BasicObject newObject = null;
				final int choice = (partIndex == partsCount - 1)
					? random.nextInt(3) + 2
					: random.nextInt(5);
				if (choice == 0)
				{
					newObject = IntegerDescriptor.fromInt(random.nextInt());
				}
				else if (choice == 1)
				{
					newObject = CharacterDescriptor.fromCodePoint(
						random.nextInt(0x110000));
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
						newObject = TupleDescriptor.fromList(members);
					}
					else if (choice == 3)
					{
						newObject = SetDescriptor.fromCollection(members);
					}
					else if (choice == 4)
					{
						A_Map map = MapDescriptor.empty();
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
				assert newObject != null;
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
		final A_Module inputModule = ModuleDescriptor.newModule(
			StringDescriptor.from("Imported"));
		inputModule.isSystemModule(false);
		final A_Module currentModule = ModuleDescriptor.newModule(
			StringDescriptor.from("Current"));
		inputModule.isSystemModule(false);
		final A_Atom atom1 = AtomDescriptor.create(
			StringDescriptor.from("importAtom1"),
			inputModule);
		inputModule.addPrivateName(atom1);
		final A_Atom atom2 = AtomDescriptor.create(
			StringDescriptor.from("currentAtom2"),
			currentModule);
		currentModule.addPrivateName(atom2);
		final A_Tuple tuple = TupleDescriptor.from(atom1, atom2);

		prepareToWrite();
		serializer().serialize(tuple);
		prepareToReadBack();
		runtime().addModule(inputModule);
		deserializer().currentModule(currentModule);
		final A_BasicObject newObject = deserializer().deserialize();
		assertTrue(
			"Serialization stream was not fully emptied",
			in().available() == 0);
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
			NilDescriptor.nil(),
			0);
		writer.argumentTypes(FLOAT.o());
		writer.primitiveNumber(P_292_FloatFloor.instance.primitiveNumber);
		writer.returnType(FLOAT.o());
		final A_RawFunction code = writer.compiledCode();
		final A_Function function = FunctionDescriptor.create(
			code,
			TupleDescriptor.empty());
		final @Nullable A_Function newFunction = roundTrip(function);
		assert newFunction != null;
		final A_RawFunction code2 = newFunction.code();
		assertEquals(code.numOuters(), code2.numOuters());
		assertEquals(
			code.numArgsAndLocalsAndStack(),
			code2.numArgsAndLocalsAndStack());
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
