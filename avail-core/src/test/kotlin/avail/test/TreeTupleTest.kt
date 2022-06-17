/*
 * TreeTupleTest.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.test

import avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.childAt
import avail.descriptor.tuples.A_Tuple.Companion.childCount
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleReverse
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ByteArrayTupleDescriptor
import avail.descriptor.tuples.ByteArrayTupleDescriptor.Companion.tupleForByteArray
import avail.descriptor.tuples.ByteBufferTupleDescriptor
import avail.descriptor.tuples.ByteBufferTupleDescriptor.Companion.tupleForByteBuffer
import avail.descriptor.tuples.ByteTupleDescriptor
import avail.descriptor.tuples.ByteTupleDescriptor.Companion.generateByteTupleFrom
import avail.descriptor.tuples.ByteTupleDescriptor.Companion.mutableObjectOfSize
import avail.descriptor.tuples.IntegerIntervalTupleDescriptor
import avail.descriptor.tuples.IntegerIntervalTupleDescriptor.Companion.createInterval
import avail.descriptor.tuples.NybbleTupleDescriptor
import avail.descriptor.tuples.ObjectTupleDescriptor
import avail.descriptor.tuples.ReverseTupleDescriptor
import avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor
import avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor.Companion.createSmallInterval
import avail.descriptor.tuples.StringDescriptor
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TreeTupleDescriptor
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.createTwoPartTreeTuple
import avail.descriptor.tuples.TupleDescriptor.Companion.toList
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

/**
 * A test of TreeTupleDescriptor.
 *
 * @author
 *   Mark van Gulik &lt;mark@availlang.org&gt;
 */
class TreeTupleTest
{
	/**
	 * Test: Check reverse of [IntegerIntervalTupleDescriptor].
	 */
	@Test
	fun testIntegerIntervalTupleDescriptorReverse()
	{
		val integerInterval = createInterval(
			fromInt(1),
			fromInt(23),
			fromInt(1))
		val integerIntervalOppositeDirection = createInterval(
			fromInt(23),
			fromInt(1),
			fromInt(-1))
		val shouldBeSame = integerInterval
			.tupleReverse()
			.tupleReverse()
		Assertions.assertEquals(
			integerInterval.tupleReverse(),
			integerIntervalOppositeDirection)
		Assertions.assertEquals(
			integerInterval.tupleReverse().tupleAt(1),
			integerInterval.tupleAt(23))
		Assertions.assertEquals(integerInterval, shouldBeSame)

		//Small size where copies are made
		Assertions.assertEquals(
			createInterval(
				fromInt(1),
				fromInt(3),
				fromInt(1)
			).tupleReverse(),
			createInterval(
				fromInt(3),
				fromInt(1),
				fromInt(-1)))
	}

	/**
	 * Test: Check reverse of [SmallIntegerIntervalTupleDescriptor].
	 */
	@Test
	fun testSmallIntegerIntervalTupleDescriptorReverse()
	{
		val integerInterval = createSmallInterval(
			1,
			23,
			1)
		val integerIntervalOppositeDirection = createSmallInterval(
			23,
			1,
			-1)
		val shouldBeSame = integerInterval
			.tupleReverse()
			.tupleReverse()
		Assertions.assertEquals(
			integerInterval.tupleReverse(),
			integerIntervalOppositeDirection)
		Assertions.assertEquals(
			integerInterval.tupleReverse().tupleAt(1),
			integerInterval.tupleAt(23))
		Assertions.assertEquals(integerInterval, shouldBeSame)

		//Small size where copies are made
		Assertions.assertEquals(
			createSmallInterval(1, 3, 1)
				.tupleReverse(),
			createSmallInterval(
				3,
				1,
				-1))
	}

	/**
	 * Test: Check reverse of [ObjectTupleDescriptor].
	 */
	@Test
	fun testObjectTupleDescriptorReverse()
	{
		val integerInterval = createInterval(
			fromInt(1),
			fromInt(36),
			fromInt(1))
		val integerIntervalReversed = createInterval(
			fromInt(36),
			fromInt(1),
			fromInt(-1))
		val anObjectTupleReversed: A_Tuple = integerIntervalReversed
			.tupleAtPuttingCanDestroy(
				35, fromCodePoint(97), false)
			.makeImmutable()
		val anObjectTuple: A_Tuple = integerInterval.tupleAtPuttingCanDestroy(
			2, fromCodePoint(97), false)
			.makeImmutable()
		val shouldBeSame = anObjectTuple.tupleReverse().tupleReverse()
		Assertions.assertEquals(
			anObjectTuple.tupleReverse(),
			anObjectTupleReversed)
		Assertions.assertEquals(anObjectTuple, shouldBeSame)
		Assertions.assertEquals(
			anObjectTuple.tupleAt(2),
			anObjectTuple.tupleReverse().tupleAt(35))

		//Test Subrange
		val anObjectTupleSubrange: A_Tuple = anObjectTuple
			.copyTupleFromToCanDestroy(2, 34, false)
			.makeImmutable()
		Assertions.assertEquals(
			anObjectTupleSubrange.tupleAt(2),
			anObjectTupleSubrange.tupleReverse().tupleAt(32))
		val anObjectTupleSubrangeSmall: A_Tuple = anObjectTuple
			.copyTupleFromToCanDestroy(1, 5, false)
			.makeImmutable()
		Assertions.assertEquals(
			anObjectTupleSubrangeSmall.tupleAt(2),
			anObjectTupleSubrangeSmall.tupleReverse().tupleAt(4))

		//Small size where copies are made
		Assertions.assertEquals(
			createInterval(
				fromInt(1),
				fromInt(5),
				fromInt(1))
				.tupleAtPuttingCanDestroy(
					2,
					fromCodePoint(97),
					false)
				.makeImmutable().tupleReverse(),
			createInterval(
				fromInt(5),
				fromInt(1),
				fromInt(-1))
				.tupleAtPuttingCanDestroy(
					4,
					fromCodePoint(97),
					false)
				.makeImmutable())
	}

	/**
	 * Test: Check reverse of [ByteTupleDescriptor].
	 */
	@Test
	fun testByteTupleDescriptorReverse()
	{
		var myByteTuple: A_Tuple = mutableObjectOfSize(
			36)
		for (i in 1 .. 36)
		{
			myByteTuple = myByteTuple.tupleAtPuttingCanDestroy(
				i, fromInt(1 + i), true)
		}
		myByteTuple.makeImmutable()
		var myByteTupleReverse: A_Tuple = mutableObjectOfSize(
			36)
		for (i in 36 downTo 1)
		{
			myByteTupleReverse = myByteTupleReverse.tupleAtPuttingCanDestroy(
				37 - i, fromInt(1 + i), true)
		}
		myByteTupleReverse.makeImmutable()
		val shouldBeSame = myByteTuple.tupleReverse().tupleReverse()
		Assertions.assertEquals(myByteTuple.tupleReverse(), myByteTupleReverse)
		Assertions.assertEquals(myByteTuple, shouldBeSame)
		Assertions.assertEquals(
			myByteTuple.tupleAt(2),
			myByteTuple.tupleReverse().tupleAt(35))

		// Small size where copies are made
		val myByteTupleSmall = generateByteTupleFrom(
			3
		) { index: Int? -> index!! }
		val myByteTupleSmallReversed = generateByteTupleFrom(
			3
		) { i: Int -> 4 - i }
		Assertions.assertEquals(
			myByteTupleSmall.tupleReverse(),
			myByteTupleSmallReversed)
	}

	/**
	 * Test: Check reverse of [ByteBufferTupleDescriptor].
	 */
	@Test
	fun testByteBufferTupleDescriptorReverse()
	{
		val aByteBuffer = ByteBuffer.allocate(36)
		for (i in 1 .. 36)
		{
			aByteBuffer.put((1 + i).toByte())
		}
		aByteBuffer.flip()
		val myByteBufferTuple: A_Tuple = tupleForByteBuffer(aByteBuffer)
			.makeImmutable()
		val aByteBufferReversed = ByteBuffer.allocate(
			36)
		for (i in 36 downTo 1)
		{
			aByteBufferReversed.put((1 + i).toByte())
		}
		aByteBufferReversed.flip()
		val myByteBufferTupleReversed: A_Tuple = tupleForByteBuffer(
			aByteBufferReversed)
			.makeImmutable()
		val shouldBeSame = myByteBufferTuple
			.tupleReverse()
			.tupleReverse()
		Assertions.assertEquals(
			myByteBufferTuple.tupleReverse(),
			myByteBufferTupleReversed)
		Assertions.assertEquals(myByteBufferTuple, shouldBeSame)
		Assertions.assertEquals(
			myByteBufferTuple.tupleAt(2),
			myByteBufferTuple.tupleReverse().tupleAt(35))

		//Small size where copies are made
		val aByteBufferSmall = ByteBuffer.allocate(
			3)
		aByteBufferSmall.put(1.toByte())
		aByteBufferSmall.put(2.toByte())
		aByteBufferSmall.put(3.toByte())
		aByteBufferSmall.flip()
		val myByteBufferTupleSmall: A_Tuple =
			tupleForByteBuffer(aByteBufferSmall).makeImmutable()
		val aByteBufferSmallReversed = ByteBuffer.allocate(3)
		aByteBufferSmallReversed.put(3.toByte())
		aByteBufferSmallReversed.put(2.toByte())
		aByteBufferSmallReversed.put(1.toByte())
		aByteBufferSmallReversed.flip()
		val myByteBufferTupleSmallReversed: A_Tuple =
			tupleForByteBuffer(aByteBufferSmallReversed).makeImmutable()
		Assertions.assertEquals(
			myByteBufferTupleSmall.tupleReverse(),
			myByteBufferTupleSmallReversed)
	}

	/**
	 * Test: Check reverse of [ByteArrayTupleDescriptor].
	 */
	@Test
	fun testByteArrayTupleDescriptorReverse()
	{
		val aByteArray = ByteArray(36)
		for (i in 0 .. 35)
		{
			aByteArray[i] = (2 + i).toByte()
		}
		val myByteArrayTuple: A_Tuple = tupleForByteArray(aByteArray)
			.makeImmutable()
		val aByteArrayReversed = ByteArray(36)
		for (i in 35 downTo 0)
		{
			aByteArrayReversed[35 - i] = (2 + i).toByte()
		}
		val myByteBufferTupleReversed: A_Tuple = tupleForByteArray(
			aByteArrayReversed)
			.makeImmutable()
		val shouldBeSame = myByteArrayTuple
			.tupleReverse()
			.tupleReverse()
		Assertions.assertEquals(
			myByteArrayTuple.tupleReverse(),
			myByteBufferTupleReversed)
		Assertions.assertEquals(myByteArrayTuple, shouldBeSame)
		Assertions.assertEquals(
			myByteArrayTuple.tupleAt(2),
			myByteArrayTuple.tupleReverse().tupleAt(35))

		//Small size where copies are made
		val aByteArraySmall = ByteArray(3)
		aByteArraySmall[0] = 1.toByte()
		aByteArraySmall[1] = 2.toByte()
		aByteArraySmall[2] = 3.toByte()
		val myByteArrayTupleSmall: A_Tuple = tupleForByteArray(aByteArraySmall)
			.makeImmutable()
		val aByteArraySmallReversed = ByteArray(3)
		aByteArraySmallReversed[0] = 3.toByte()
		aByteArraySmallReversed[1] = 2.toByte()
		aByteArraySmallReversed[2] = 1.toByte()
		val myByteBufferTupleSmallReversed: A_Tuple = tupleForByteArray(
			aByteArraySmallReversed)
			.makeImmutable()
		Assertions.assertEquals(
			myByteArrayTupleSmall.tupleReverse(),
			myByteBufferTupleSmallReversed)
	}

	/**
	 * Test: Check reverse of [StringDescriptor].
	 */
	@Suppress("SpellCheckingInspection")
	@Test
	fun testStringDescriptorReverse()
	{
		//Test ByteStringDescriptor
		val byteString: A_Tuple = stringFrom(
				"abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz")
			.makeImmutable()
		val byteStringReverse: A_Tuple = stringFrom(
				"zyxwvutsrqponmlkjihgfedcbazyxwvutsrqponmlkjihgfedcba")
			.makeImmutable()
		val shouldBeSame = byteString.tupleReverse().tupleReverse()
		Assertions.assertEquals(byteString.tupleReverse(), byteStringReverse)
		Assertions.assertEquals(byteString, shouldBeSame)
		Assertions.assertEquals(
			byteString.tupleAt(51),
			byteString.tupleReverse().tupleAt(2))

		//Small size ByteStringDescriptor where copies are made
		val byteStringSmall: A_Tuple = stringFrom("abcd")
			.makeImmutable()
		val byteStringReverseSmall: A_Tuple = stringFrom("dcba")
			.makeImmutable()
		val shouldBeSameSmall = byteStringSmall
			.tupleReverse()
			.tupleReverse()
		Assertions.assertEquals(
			byteStringSmall.tupleReverse(),
			byteStringReverseSmall)
		Assertions.assertEquals(byteStringSmall, shouldBeSameSmall)
		Assertions.assertEquals(
			byteStringSmall.tupleAt(2),
			byteStringSmall.tupleReverse().tupleAt(3))

		//Test TwoByteStringDescriptor
		val twoByteString: A_Tuple = stringFrom(
				"ĀbcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxĐz")
			.makeImmutable()
		val twoByteStringReverse: A_Tuple = stringFrom(
				"zĐxwvutsrqponmlkjihgfedcbazyxwvutsrqponmlkjihgfedcbĀ")
			.makeImmutable()
		val twoShouldBeSame = twoByteString
			.tupleReverse()
			.tupleReverse()
		Assertions.assertEquals(
			twoByteString.tupleReverse(),
			twoByteStringReverse)
		Assertions.assertEquals(twoByteString, twoShouldBeSame)
		Assertions.assertEquals(
			twoByteString.tupleAt(51),
			twoByteString.tupleReverse().tupleAt(2))

		//Small size TwoByteStringDescriptor where copies are made
		val twoByteStringSmall: A_Tuple = stringFrom("abĐd")
			.makeImmutable()
		val twoByteStringReverseSmall: A_Tuple = stringFrom("dĐba")
			.makeImmutable()
		val twoShouldBeSameSmall = twoByteStringSmall
			.tupleReverse()
			.tupleReverse()
		Assertions.assertEquals(
			twoByteStringSmall.tupleReverse(),
			twoByteStringReverseSmall)
		Assertions.assertEquals(twoByteStringSmall, twoShouldBeSameSmall)
		Assertions.assertEquals(
			twoByteStringSmall.tupleAt(2),
			twoByteStringSmall.tupleReverse().tupleAt(3))
	}

	/**
	 * Test: Check reverse of [NybbleTupleDescriptor].
	 */
	@Test
	fun testNybbleTupleDescriptorReverse()
	{
		var nybbleTuple: A_Tuple = mutableObjectOfSize(
			17)
		nybbleTuple = nybbleTuple
			.tupleAtPuttingCanDestroy(
				1,
				fromInt(1),
				true)
			.tupleAtPuttingCanDestroy(
				2,
				fromInt(7),
				true)
			.tupleAtPuttingCanDestroy(
				17,
				fromInt(9),
				true)
			.makeImmutable()
		var nybbleTupleReverse: A_Tuple = mutableObjectOfSize(
			17)
		nybbleTupleReverse = nybbleTupleReverse
			.tupleAtPuttingCanDestroy(
				1,
				fromInt(9),
				true)
			.tupleAtPuttingCanDestroy(
				16,
				fromInt(7),
				true)
			.tupleAtPuttingCanDestroy(
				17,
				fromInt(1),
				true)
			.makeImmutable()
		val shouldBeSame = nybbleTuple.tupleReverse().tupleReverse()
		Assertions.assertEquals(nybbleTuple.tupleReverse(), nybbleTupleReverse)
		Assertions.assertEquals(
			nybbleTuple.tupleReverse().tupleAt(17),
			nybbleTuple.tupleAt(1))
		Assertions.assertEquals(
			nybbleTuple.tupleReverse().tupleAt(16),
			nybbleTuple.tupleAt(2))
		Assertions.assertEquals(
			nybbleTuple.tupleReverse().tupleAt(15),
			nybbleTuple.tupleAt(3))
		Assertions.assertEquals(shouldBeSame, nybbleTuple)
		var nybbleTupleSmall: A_Tuple = mutableObjectOfSize(
			5)
		nybbleTupleSmall = nybbleTupleSmall
			.tupleAtPuttingCanDestroy(
				2,
				fromInt(7),
				true)
			.makeImmutable()
		var nybbleTupleReverseSmall: A_Tuple = mutableObjectOfSize(
			5)
		nybbleTupleReverseSmall = nybbleTupleReverseSmall
			.tupleAtPuttingCanDestroy(
				4,
				fromInt(7),
				true)
			.makeImmutable()
		val shouldBeSameSmall = nybbleTupleSmall
			.tupleReverse()
			.tupleReverse()
		Assertions.assertEquals(
			nybbleTupleSmall.tupleReverse(),
			nybbleTupleReverseSmall)
		Assertions.assertEquals(
			nybbleTupleSmall.tupleReverse().tupleAt(4),
			nybbleTupleSmall.tupleAt(2))
		Assertions.assertEquals(shouldBeSameSmall, nybbleTupleSmall)
	}

	/**
	 * Test: Check reverse of [NybbleTupleDescriptor].
	 */
	@Test
	fun testTreeTupleDescriptorReverse()
	{
		val byteString: A_Tuple = stringFrom(
				"abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz")
			.makeImmutable()
		val byteStringReversed = byteString.tupleReverse()
		val integerInterval = createInterval(
			fromInt(1),
			fromInt(36),
			fromInt(1))
		val anObjectTuple: A_Tuple = integerInterval.tupleAtPuttingCanDestroy(
			2, fromCodePoint(411), false)
			.makeImmutable()
		val anObjectTupleReversed = anObjectTuple.tupleReverse()
		val aTreeTuple: A_Tuple = createTwoPartTreeTuple(
			byteString,
			anObjectTuple,
			1,
			0)
		val aTreeTupleReversed: A_Tuple = createTwoPartTreeTuple(
			anObjectTupleReversed,
			byteStringReversed,
			1,
			0)
		assert(aTreeTupleReversed.descriptor() is TreeTupleDescriptor)

		// Compare all the elements but not the tuples themselves, to avoid
		// transforming one into an indirection.
		Assertions.assertEquals(
			toList<A_BasicObject>(aTreeTuple.tupleReverse()),
			toList<A_BasicObject>(aTreeTupleReversed))
		val aTreeTupleReversedSubrange = aTreeTuple
			.tupleReverse()
			.copyTupleFromToCanDestroy(17, 63, false)
		assert(aTreeTupleReversedSubrange.descriptor() is ReverseTupleDescriptor)
		Assertions.assertEquals(
			aTreeTupleReversedSubrange.tupleSize,
			63 - 17 + 1)
		val aConcatenation = aTreeTuple
			.tupleReverse()
			.concatenateWith(aTreeTupleReversed.tupleReverse(), true)
		assert(aConcatenation.descriptor() is TreeTupleDescriptor)
		Assertions.assertEquals(aConcatenation.childCount, 4)
		Assertions.assertEquals(aConcatenation.childAt(4), anObjectTuple)
		Assertions.assertEquals(aConcatenation.childAt(3), byteString)
		Assertions.assertEquals(
			aConcatenation.childAt(2),
			byteString.tupleReverse())
		Assertions.assertEquals(
			aConcatenation.childAt(1),
			anObjectTuple.tupleReverse())
		Assertions.assertEquals(
			aConcatenation.tupleAt(142),
			fromCodePoint(411))
	}
}
