/*
 * TreeTupleTest.java
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


import com.avail.descriptor.*;
import com.avail.utility.IndexedIntGenerator;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static com.avail.descriptor.ByteArrayTupleDescriptor.tupleForByteArray;
import static com.avail.descriptor.ByteBufferTupleDescriptor.tupleForByteBuffer;
import static com.avail.descriptor.ByteTupleDescriptor.generateByteTupleFrom;
import static com.avail.descriptor.ByteTupleDescriptor.mutableObjectOfSize;
import static com.avail.descriptor.CharacterDescriptor.fromCodePoint;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerIntervalTupleDescriptor.createInterval;
import static com.avail.descriptor.SmallIntegerIntervalTupleDescriptor.createSmallInterval;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TreeTupleDescriptor.createTwoPartTreeTuple;
import static com.avail.descriptor.TupleDescriptor.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A test of TreeTupleDescriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class TreeTupleTest
{

	/**
	 * Test: Check reverse of {@link IntegerIntervalTupleDescriptor}.
	 */
	@Test
	public void testIntegerIntervalTupleDescriptorReverse ()
	{
		final A_Tuple integerInterval =
			createInterval(
				fromInt(1),
				fromInt(23),
				fromInt(1));
		final A_Tuple integerIntervalOppositeDirection =
			createInterval(
				fromInt(23),
				fromInt(1),
				fromInt(-1));

		final A_Tuple shouldBeSame =
			integerInterval.tupleReverse().tupleReverse();

		assertEquals(
			integerInterval.tupleReverse(),
			integerIntervalOppositeDirection);

		assertEquals(
			integerInterval.tupleReverse().tupleAt(1),
			integerInterval.tupleAt(23));

		assertEquals(integerInterval,shouldBeSame);

		//Small size where copies are made
		assertEquals(
			createInterval(
				fromInt(1),
				fromInt(3),
				fromInt(1)).tupleReverse(),
			createInterval(
				fromInt(3),
				fromInt(1),
				fromInt(-1)));
	}

	/**
	 * Test: Check reverse of {@link SmallIntegerIntervalTupleDescriptor}.
	 */
	@Test
	public void testSmallIntegerIntervalTupleDescriptorReverse ()
	{
		final A_Tuple integerInterval = createSmallInterval(1,23,1);
		final A_Tuple integerIntervalOppositeDirection =
			createSmallInterval(23,1,-1);

		final A_Tuple shouldBeSame =
			integerInterval.tupleReverse().tupleReverse();

		assertEquals(
			integerInterval.tupleReverse(),
			integerIntervalOppositeDirection);

		assertEquals(
			integerInterval.tupleReverse().tupleAt(1),
			integerInterval.tupleAt(23));

		assertEquals(integerInterval,shouldBeSame);

		//Small size where copies are made
		assertEquals(
			createSmallInterval(1,3,1).tupleReverse(),
			createSmallInterval(3,1,-1));
	}

	/**
	 * Test: Check reverse of {@link ObjectTupleDescriptor}
	 */
	@Test
	public void testObjectTupleDescriptorReverse()
	{
		final A_Tuple integerInterval =
			createInterval(
				fromInt(1),
				fromInt(36),
				fromInt(1));

		final A_Tuple integerIntervalReversed =
			createInterval(
				fromInt(36),
				fromInt(1),
				fromInt(-1));

		final A_Tuple anObjectTupleReversed =
			integerIntervalReversed.tupleAtPuttingCanDestroy(
				35, fromCodePoint(97), false)
			.makeImmutable();

		final A_Tuple anObjectTuple =
			integerInterval.tupleAtPuttingCanDestroy(
				2, fromCodePoint(97), false)
			.makeImmutable();

		final A_Tuple shouldBeSame =
			anObjectTuple.tupleReverse().tupleReverse();

		assertEquals(
			anObjectTuple.tupleReverse(),
			anObjectTupleReversed);

		assertEquals(anObjectTuple, shouldBeSame);

		assertEquals(
			anObjectTuple.tupleAt(2),
			anObjectTuple.tupleReverse().tupleAt(35));

		//Test Subrange
		final A_Tuple anObjectTupleSubrange =
			anObjectTuple.copyTupleFromToCanDestroy(2, 34, false)
			.makeImmutable();

		assertEquals(anObjectTupleSubrange.tupleAt(2),
			anObjectTupleSubrange.tupleReverse().tupleAt(32));

		final A_Tuple anObjectTupleSubrangeSmall =
			anObjectTuple.copyTupleFromToCanDestroy(1, 5, false)
			.makeImmutable();

		assertEquals(anObjectTupleSubrangeSmall.tupleAt(2),
			anObjectTupleSubrangeSmall.tupleReverse().tupleAt(4));

		//Small size where copies are made
		assertEquals(
			createInterval(
					fromInt(1),
					fromInt(5),
					fromInt(1))
				.tupleAtPuttingCanDestroy(2, fromCodePoint(97), false)
				.makeImmutable().tupleReverse(),
			createInterval(
					fromInt(5),
					fromInt(1),
					fromInt(-1))
				.tupleAtPuttingCanDestroy(4, fromCodePoint(97), false)
				.makeImmutable());
	}

	/**
	 * Test: Check reverse of {@link ByteTupleDescriptor}
	 */
	@Test
	public void testByteTupleDescriptorReverse()
	{
		A_Tuple myByteTuple = mutableObjectOfSize(36);
		for (int i = 1; i < 37; i++)
		{
			myByteTuple = myByteTuple.tupleAtPuttingCanDestroy(
				i, fromInt(1 + i), true);
		}
		myByteTuple.makeImmutable();

		A_Tuple myByteTupleReverse = mutableObjectOfSize(36);
		for (int i = 36; i > 0; i--)
		{
			myByteTupleReverse = myByteTupleReverse.tupleAtPuttingCanDestroy(
				37 - i, fromInt(1 + i), true);
		}
		myByteTupleReverse.makeImmutable();

		final A_Tuple shouldBeSame = myByteTuple.tupleReverse().tupleReverse();

		assertEquals(myByteTuple.tupleReverse(), myByteTupleReverse);
		assertEquals(myByteTuple, shouldBeSame);
		assertEquals(
			myByteTuple.tupleAt(2),
			myByteTuple.tupleReverse().tupleAt(35));

		// Small size where copies are made

		final AvailObject myByteTupleSmall = generateByteTupleFrom(
			3, index -> (short) index);
		final AvailObject myByteTupleSmallReversed =
			generateByteTupleFrom(
				3,
				new IndexedIntGenerator()
				{
					private short counter = 3;

					@Override
					public int value (final int ignored)
					{
						return counter--;
					}
				});
		assertEquals(
			myByteTupleSmall.tupleReverse(),
			myByteTupleSmallReversed);
	}

	/**
	 *  Test: Check reverse of {@link ByteBufferTupleDescriptor}
	 */
	@Test
	public void testByteBufferTupleDescriptorReverse ()
	{
		final ByteBuffer aByteBuffer = ByteBuffer.allocate(36);
		for (int i = 1; i < 37; i++)
		{
			aByteBuffer.put((byte) (1 + i));
		}
		aByteBuffer.flip();
		final A_Tuple myByteBufferTuple =
			tupleForByteBuffer(aByteBuffer).makeImmutable();

		final ByteBuffer aByteBufferReversed = ByteBuffer.allocate(36);
		for (int i = 36; i > 0; i--)
		{
			aByteBufferReversed.put((byte) (1 + i));
		}
		aByteBufferReversed.flip();
		final A_Tuple myByteBufferTupleReversed =
			tupleForByteBuffer(aByteBufferReversed).makeImmutable();

		final A_Tuple shouldBeSame =
			myByteBufferTuple.tupleReverse().tupleReverse();

		assertEquals(myByteBufferTuple.tupleReverse(),
			myByteBufferTupleReversed);

		assertEquals(myByteBufferTuple, shouldBeSame);
		assertEquals(myByteBufferTuple.tupleAt(2),
			myByteBufferTuple.tupleReverse().tupleAt(35));

		//Small size where copies are made
		final ByteBuffer aByteBufferSmall = ByteBuffer.allocate(3);
		aByteBufferSmall.put((byte) 1);
		aByteBufferSmall.put((byte) 2);
		aByteBufferSmall.put((byte) 3);
		aByteBufferSmall.flip();
		final A_Tuple myByteBufferTupleSmall =
			tupleForByteBuffer(aByteBufferSmall).makeImmutable();

		final ByteBuffer aByteBufferSmallReversed = ByteBuffer.allocate(3);
		aByteBufferSmallReversed.put((byte) 3);
		aByteBufferSmallReversed.put((byte) 2);
		aByteBufferSmallReversed.put((byte) 1);
		aByteBufferSmallReversed.flip();
		final A_Tuple myByteBufferTupleSmallReversed =
			tupleForByteBuffer(aByteBufferSmallReversed).makeImmutable();

		assertEquals(myByteBufferTupleSmall.tupleReverse(),
			myByteBufferTupleSmallReversed);
	}

	/**
	 * Test: Check reverse of {@link ByteArrayTupleDescriptor}
	 */
	@Test
	public void testByteArrayTupleDescriptorReverse ()
	{

		final byte[] aByteArray = new byte[36];

		for (int i = 0; i < 36; i++)
		{
			aByteArray[i] = (byte) (2 + i);
		}

		final A_Tuple myByteArrayTuple =
			tupleForByteArray(aByteArray).makeImmutable();

		final byte[] aByteArrayReversed = new byte[36];

		for (int i = 35; i >= 0; i--)
		{
			aByteArrayReversed[35-i] = (byte) (2 + i);
		}

		final A_Tuple myByteBufferTupleReversed =
			tupleForByteArray(aByteArrayReversed).makeImmutable();

		final A_Tuple shouldBeSame =
			myByteArrayTuple.tupleReverse().tupleReverse();

		assertEquals(myByteArrayTuple.tupleReverse(),
			myByteBufferTupleReversed);

		assertEquals(myByteArrayTuple,shouldBeSame);
		assertEquals(myByteArrayTuple.tupleAt(2),
			myByteArrayTuple.tupleReverse().tupleAt(35));

		//Small size where copies are made
		final byte[] aByteArraySmall = new byte[3];
		aByteArraySmall[0] = (byte) 1;
		aByteArraySmall[1] = (byte) 2;
		aByteArraySmall[2] = (byte) 3;

		final A_Tuple myByteArrayTupleSmall =
			tupleForByteArray(aByteArraySmall).makeImmutable();

		final byte[] aByteArraySmallReversed = new byte[3];
		aByteArraySmallReversed[0] = (byte) 3;
		aByteArraySmallReversed[1] = (byte) 2;
		aByteArraySmallReversed[2] = (byte) 1;

		final A_Tuple myByteBufferTupleSmallReversed =
			tupleForByteArray(aByteArraySmallReversed).makeImmutable();

		assertEquals(myByteArrayTupleSmall.tupleReverse(),
			myByteBufferTupleSmallReversed);

	}

	/**
	 * Test: Check reverse of {@link StringDescriptor}
	 */
	@Test
	public void testStringDescriptorReverse()
	{
		//Test ByteStringDescriptor
		final A_Tuple byteString =
			stringFrom("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz")
			.makeImmutable();

		final A_Tuple byteStringReverse =
			stringFrom("zyxwvutsrqponmlkjihgfedcbazyxwvutsrqponmlkjihgfedcba")
			.makeImmutable();

		final A_Tuple shouldBeSame = byteString.tupleReverse().tupleReverse();

		assertEquals(byteString.tupleReverse(), byteStringReverse);
		assertEquals(byteString, shouldBeSame);
		assertEquals(byteString.tupleAt(51),
			byteString.tupleReverse().tupleAt(2));

		//Small size ByteStringDescriptor where copies are made
		final A_Tuple byteStringSmall =
			stringFrom("abcd")
			.makeImmutable();

		final A_Tuple byteStringReverseSmall =
			stringFrom("dcba")
			.makeImmutable();

		final A_Tuple shouldBeSameSmall =
			byteStringSmall.tupleReverse().tupleReverse();

		assertEquals(byteStringSmall.tupleReverse(), byteStringReverseSmall);
		assertEquals(byteStringSmall, shouldBeSameSmall);
		assertEquals(byteStringSmall.tupleAt(2),
			byteStringSmall.tupleReverse().tupleAt(3));

		//Test TwoByteStringDescriptor
		final A_Tuple twoByteString =
			stringFrom("ĀbcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxĐz")
			.makeImmutable();

		final A_Tuple twoByteStringReverse =
			stringFrom("zĐxwvutsrqponmlkjihgfedcbazyxwvutsrqponmlkjihgfedcbĀ")
			.makeImmutable();

		final A_Tuple twoShouldBeSame =
			twoByteString.tupleReverse().tupleReverse();

		assertEquals(twoByteString.tupleReverse(), twoByteStringReverse);
		assertEquals(twoByteString, twoShouldBeSame);
		assertEquals(
			twoByteString.tupleAt(51),
			twoByteString.tupleReverse().tupleAt(2));

		//Small size TwoByteStringDescriptor where copies are made
		final A_Tuple twoByteStringSmall =
			stringFrom("abĐd")
			.makeImmutable();

		final A_Tuple twoByteStringReverseSmall =
			stringFrom("dĐba")
			.makeImmutable();

		final A_Tuple twoShouldBeSameSmall =
			twoByteStringSmall.tupleReverse().tupleReverse();

		assertEquals(twoByteStringSmall.tupleReverse(),
			twoByteStringReverseSmall);
		assertEquals(twoByteStringSmall, twoShouldBeSameSmall);
		assertEquals(twoByteStringSmall.tupleAt(2),
			twoByteStringSmall.tupleReverse().tupleAt(3));
	}

	/**
	 * Test: Check reverse of {@link NybbleTupleDescriptor}
	 */
	@Test
	public void testNybbleTupleDescriptorReverse ()
	{
		A_Tuple nybbleTuple = mutableObjectOfSize(17);
		nybbleTuple = nybbleTuple
			.tupleAtPuttingCanDestroy(1, fromInt(1), true)
			.tupleAtPuttingCanDestroy(2, fromInt(7), true)
			.tupleAtPuttingCanDestroy(17, fromInt(9), true)
			.makeImmutable();

		A_Tuple nybbleTupleReverse = mutableObjectOfSize(17);
		nybbleTupleReverse = nybbleTupleReverse
			.tupleAtPuttingCanDestroy(1, fromInt(9), true)
			.tupleAtPuttingCanDestroy(16, fromInt(7), true)
			.tupleAtPuttingCanDestroy(17, fromInt(1), true)
			.makeImmutable();

		final A_Tuple shouldBeSame = nybbleTuple.tupleReverse().tupleReverse();

		assertEquals(nybbleTuple.tupleReverse(), nybbleTupleReverse);
		assertEquals(
			nybbleTuple.tupleReverse().tupleAt(17),
			nybbleTuple.tupleAt(1));
		assertEquals(
			nybbleTuple.tupleReverse().tupleAt(16),
			nybbleTuple.tupleAt(2));
		assertEquals(
			nybbleTuple.tupleReverse().tupleAt(15),
			nybbleTuple.tupleAt(3));
		assertEquals(shouldBeSame, nybbleTuple);

		A_Tuple nybbleTupleSmall = mutableObjectOfSize(5);
		nybbleTupleSmall = nybbleTupleSmall
			.tupleAtPuttingCanDestroy(2, fromInt(7), true)
			.makeImmutable();

		A_Tuple nybbleTupleReverseSmall = mutableObjectOfSize(5);
		nybbleTupleReverseSmall = nybbleTupleReverseSmall
			.tupleAtPuttingCanDestroy(4, fromInt(7), true)
			.makeImmutable();

		final A_Tuple shouldBeSameSmall =
			nybbleTupleSmall.tupleReverse().tupleReverse();

		assertEquals(nybbleTupleSmall.tupleReverse(),nybbleTupleReverseSmall);
		assertEquals(nybbleTupleSmall.tupleReverse().tupleAt(4),
			nybbleTupleSmall.tupleAt(2));
		assertEquals(shouldBeSameSmall, nybbleTupleSmall);
	}

	/**
	 * Test: Check reverse of {@link NybbleTupleDescriptor}
	 */
	@Test
	public void testTreeTupleDescriptorReverse ()
	{
		final A_Tuple byteString =
			stringFrom("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz")
			.makeImmutable();

		final A_Tuple byteStringReversed = byteString.tupleReverse();

		final A_Tuple integerInterval =
			createInterval(
				fromInt(1),
				fromInt(36),
				fromInt(1));

		final A_Tuple anObjectTuple =
			integerInterval.tupleAtPuttingCanDestroy(
				2, fromCodePoint(411), false)
			.makeImmutable();

		final A_Tuple anObjectTupleReveresed = anObjectTuple.tupleReverse();
		final A_Tuple aTreeTuple =
			createTwoPartTreeTuple(byteString, anObjectTuple, 1, 0);
		final A_Tuple aTreeTupleReversed =
			createTwoPartTreeTuple(anObjectTupleReveresed, byteStringReversed, 1, 0);
		assert(aTreeTupleReversed.descriptor() instanceof TreeTupleDescriptor);

		// Compare all the elements but not the tuples themselves, to avoid
		// transforming one into an indirection.
		assertEquals(
			toList(aTreeTuple.tupleReverse()),
			toList(aTreeTupleReversed));

		final A_Tuple aTreeTupleReversedSubrange =
			aTreeTuple
				.tupleReverse()
				.copyTupleFromToCanDestroy(17, 63, false);
		assert(aTreeTupleReversedSubrange.descriptor()
			instanceof ReverseTupleDescriptor);
		assertEquals(aTreeTupleReversedSubrange.tupleSize(), 63 - 17 + 1);

		final A_Tuple aConcatenation =
			aTreeTuple
				.tupleReverse()
				.concatenateWith(aTreeTupleReversed.tupleReverse(), true);
		assert(aConcatenation.descriptor()
			instanceof TreeTupleDescriptor);
		assertEquals(aConcatenation.childCount(), 4);
		assertEquals(aConcatenation.childAt(4), anObjectTuple);
		assertEquals(aConcatenation.childAt(3), byteString);
		assertEquals(aConcatenation.childAt(2), byteString.tupleReverse());
		assertEquals(aConcatenation.childAt(1), anObjectTuple.tupleReverse());
		assertEquals(
			aConcatenation.tupleAt(142),
			fromCodePoint(411));
	}
}
