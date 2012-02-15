/**
 * SerializerTest.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import static java.lang.Math.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.serialization.*;

/**
 * Unit tests for object serialization.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public final class SerializerTest
{
	/**
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime.
	 */
	@BeforeClass
	public static void initializeAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
	}

	/**
	 * Test fixture: clear all special objects.
	 */
	@AfterClass
	public static void clearAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
	}

	/**
	 * The stream onto which the serializer writes its bytes.
	 */
	ByteArrayOutputStream out;

	/**
	 * The {@link Serializer} which converts objects to bytes.
	 */
	Serializer serializer;

	/**
	 * The source of bytes for the {@link Deserializer}.
	 */
	ByteArrayInputStream in;

	/**
	 * The {@link Deserializer} which (re)produces objects from bytes.
	 */
	Deserializer deserializer;

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
		final byte[] bytes = out.toByteArray();
//		int count = 1;
//		for (final byte b : bytes)
//		{
//			System.out.format("%02x ", b);
//			if (count++ % 50 == 0)
//			{
//				System.out.println();
//			}
//		}
//		System.out.println();
		in = new ByteArrayInputStream(bytes);
		deserializer = new Deserializer(in);
		serializer = null;
	}

	/**
	 * Serialize then deserialize the given object.
	 *
	 * @param object The object to serialize and deserialize.
	 * @return The result of serializing and deserializing the argument.
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	private @NotNull AvailObject roundTrip (
		final @NotNull AvailObject object)
	throws MalformedSerialStreamException
	{
		prepareToWrite();
		serializer.serialize(object);
		prepareToReadBack();
		final AvailObject newObject = deserializer.deserialize();
		assertTrue(
			"Serialization stream was not fully emptied",
			in.available() == 0);
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
	private void checkObject (
		final @NotNull AvailObject object)
	throws MalformedSerialStreamException
	{
		final AvailObject newObject = roundTrip(object);
		assertEquals(object, newObject);
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
		final Random random = new Random(-4673928647810677486L);
		for (int run = 0; run < 100; run++)
		{
			final int partsCount = 1 + random.nextInt(1000);
			final List<AvailObject> parts =
				new ArrayList<AvailObject>(partsCount);
			for (int partIndex = 0; partIndex < partsCount; partIndex++)
			{
				AvailObject newObject = null;
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
					final List<AvailObject> members =
						new ArrayList<AvailObject>(size);
					for (int i = 0; i < size; i++)
					{
						members.add(parts.get(random.nextInt(partIndex)));
					}
					if (choice == 2)
					{
						newObject = TupleDescriptor.fromCollection(members);
					}
					else if (choice == 3)
					{
						newObject = SetDescriptor.fromCollection(members);
					}
					else if (choice == 4)
					{
						newObject = MapDescriptor.empty();
						for (int i = 0; i < size; i+=2)
						{
							newObject = newObject.mapAtPuttingCanDestroy(
								members.get(i),
								members.get(i + 1),
								true);
						}
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
	 */
	@Test
	public void testAtoms ()
	{
		//TODO
	}

//	@Test
//	public void testRandomSimpleObjects2 ()
//	throws MalformedSerialStreamException
//	{
//		testRandomSimpleObjects();
//	}
//
//	@Test
//	public void testRandomSimpleObjects3 ()
//	throws MalformedSerialStreamException
//	{
//		testRandomSimpleObjects();
//	}
//
//	@Test
//	public void testRandomSimpleObjects4 ()
//	throws MalformedSerialStreamException
//	{
//		testRandomSimpleObjects();
//	}
//
//	@Test
//	public void testRandomSimpleObjects5 ()
//	throws MalformedSerialStreamException
//	{
//		testRandomSimpleObjects();
//	}

}
