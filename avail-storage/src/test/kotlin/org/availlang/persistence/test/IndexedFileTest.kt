/*
 * IndexedFileTest.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package org.availlang.persistence.test

import org.availlang.persistence.IndexedFile
import org.availlang.persistence.test.TestDocument.Companion.record0
import org.availlang.persistence.test.TestDocument.Companion.record1
import org.availlang.persistence.test.TestDocument.Companion.record2
import org.availlang.persistence.test.TestDocument.Companion.record3
import org.availlang.persistence.test.TestDocument.Companion.record4
import org.availlang.persistence.test.TestIndexedFileBuilder.testDirectory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * An [IndexedFileTest] performs tests associated with [IndexedFile]s.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class IndexedFileTest
{
    @Test
    @DisplayName("Normal Page Size")
    internal fun test ()
    {
        val testFileName = "test_file.cap"
        val indexedFile = TestIndexedFileBuilder.create(testFileName)
        val record0Serialized = record0.serialize()
        val record1Serialized = record1.serialize()
        val record2Serialized = record2.serialize()
        val record3Serialized = record3.serialize()
        val record4Serialized = record4.serialize()

        val zerothIndex = indexedFile.add(record0Serialized)
        assertEquals(0, zerothIndex)
        val firstIndex = indexedFile.add(record1Serialized)
        assertEquals(1, firstIndex)
        val secondIndex = indexedFile.add(record2Serialized)
        assertEquals(2, secondIndex)
        val thirdIndex = indexedFile.add(record3Serialized)
        assertEquals(3, thirdIndex)
        val fourthIndex = indexedFile.add(record4Serialized)
        assertEquals(4, fourthIndex)

        indexedFile.commit()
        indexedFile.close()

        val reopenIndexedFile = TestIndexedFileBuilder.open(testFileName)

        val record0RetrievedBytes = reopenIndexedFile[zerothIndex]
        assert(record0Serialized.contentEquals(record0RetrievedBytes))
        assertEquals(record0, TestDocument.deserialize(record0RetrievedBytes))

        val record1RetrievedBytes = reopenIndexedFile[firstIndex]
        assert(record1Serialized.contentEquals(record1RetrievedBytes))
        assertEquals(record1, TestDocument.deserialize(record1RetrievedBytes))

        val record2RetrievedBytes = reopenIndexedFile[secondIndex]
        assert(record2Serialized.contentEquals(record2RetrievedBytes))
        assertEquals(record2, TestDocument.deserialize(record2RetrievedBytes))

        val record3RetrievedBytes = reopenIndexedFile[thirdIndex]
        assert(record3Serialized.contentEquals(record3RetrievedBytes))
        assertEquals(record3, TestDocument.deserialize(record3RetrievedBytes))

        val record4RetrievedBytes = reopenIndexedFile[fourthIndex]
        assert(record4Serialized.contentEquals(record4RetrievedBytes))
        assertEquals(record4, TestDocument.deserialize(record4RetrievedBytes))
    }

    @Test
    @DisplayName("Small Page Size")
    internal fun testSmallPageSize ()
    {
        val testFileName = "test_file_small_page_size.cap"
        val indexedFile = TestIndexedFileBuilder.create(testFileName, 512)
        val record0Serialized = record0.serialize()
        val record1Serialized = record1.serialize()
        val record2Serialized = record2.serialize()
        val record3Serialized = record3.serialize()
        val record4Serialized = record4.serialize()
        val longList = (999_999..2_000_000).toList()
        val record5 = TestDocument(
            1344525,
            124622.45354,
            "Doc 4 Is in the sT0r3",
            listOf(true, true),
            listOf(TestSubDocument("Lotsa Numbers", longList)))
        val record5Serialized = record5.serialize()

        val zerothIndex = indexedFile.add(record0Serialized)
        assertEquals(0, zerothIndex)
        val record0RetrievedBytes = indexedFile[zerothIndex]
        assert(record0Serialized.contentEquals(record0RetrievedBytes))
        assertEquals(record0, TestDocument.deserialize(record0RetrievedBytes))
        val firstIndex = indexedFile.add(record1Serialized)
        assertEquals(1, firstIndex)
        indexedFile.commit()
        val secondIndex = indexedFile.add(record5Serialized)
        assertEquals(2, secondIndex)
        val thirdIndex = indexedFile.add(record2Serialized)
        assertEquals(3, thirdIndex)
        val fourthIndex = indexedFile.add(record3Serialized)
        assertEquals(4, fourthIndex)
        indexedFile.commit()
        val fifthIndex = indexedFile.add(record4Serialized)
        assertEquals(5, fifthIndex)
        val record1RetrievedBytes = indexedFile[firstIndex]
        assert(record1RetrievedBytes.contentEquals(record1Serialized))
        val record5RetrievedBytes = indexedFile[secondIndex]
        assert(record5RetrievedBytes.contentEquals(record5Serialized))
        val record5Deserialized =
            TestDocument.deserialize(record5RetrievedBytes)
        assertEquals(record5, record5Deserialized)
        indexedFile.commit()
        indexedFile.close()

        val reopenIndexedFile = TestIndexedFileBuilder.open(testFileName)
        val record0RetrievedBytes2 = reopenIndexedFile[zerothIndex]
        assert(record0Serialized.contentEquals(record0RetrievedBytes2))
        assertEquals(record0, TestDocument.deserialize(record0RetrievedBytes2))

        val record1RetrievedBytes2 = reopenIndexedFile[firstIndex]
        assert(record1Serialized.contentEquals(record1RetrievedBytes2))
        assertEquals(record1, TestDocument.deserialize(record1RetrievedBytes2))

        val record5RetrievedBytes2 = reopenIndexedFile[secondIndex]
        assert(record5Serialized.contentEquals(record5RetrievedBytes2))
        assertEquals(record5, TestDocument.deserialize(record5RetrievedBytes2))

        val record2RetrievedBytes = reopenIndexedFile[thirdIndex]
        assert(record2Serialized.contentEquals(record2RetrievedBytes))
        assertEquals(record2, TestDocument.deserialize(record2RetrievedBytes))

        val record3RetrievedBytes = reopenIndexedFile[fourthIndex]
        assert(record3Serialized.contentEquals(record3RetrievedBytes))
        assertEquals(record3, TestDocument.deserialize(record3RetrievedBytes))

        val record4RetrievedBytes = reopenIndexedFile[fifthIndex]
        assert(record4Serialized.contentEquals(record4RetrievedBytes))
        assertEquals(record4, TestDocument.deserialize(record4RetrievedBytes))
    }

	companion object
	{
		@BeforeAll
		@JvmStatic
		fun initialize ()
		{
			val dir = File(testDirectory)
			if (!dir.exists())
			{
				dir.mkdir()
			}
		}

		@AfterAll
		@JvmStatic
		fun cleanup ()
		{
			File(testDirectory).deleteRecursively()
		}
	}
}
