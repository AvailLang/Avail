/**
 * StackMapTableBuilder.java
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

package com.avail.interpreter.jvm;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * This a builder class for a {@linkplain StackMapTableAttribute}.  This builder
 * utilizes a stack to construct the {@linkplain StackMapTableAttribute}.  If a
 * {@linkplain JavaInstruction} causes a branch (or jump), the instruction
 * is added to the branch stack.  Once the {@linkplain JavaInstruction
 * instruction} at the location indicated by the branch is reached, the offset
 * is adjusted and a new {@linkplain StackMapFrame} is created.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class StackMapTableBuilder
{
	/**
	 * Build the {@linkplain StackMapTableAttribute} for the given {@linkplain
	 * Method} {@linkplain JavaInstruction instructions}.
	 *
	 * @param instructions The {@linkplain Method} {@linkplain JavaInstruction
	 *      instructions}.
	 * @return The final {@linkplain StackMapTableAttribute}
	 */
	public static StackMapTableAttribute buildStackMapTable (
		final List<JavaInstruction> instructions)
	{
		//The ordered StackMapFrames that will populate the StackMapTable
		final ArrayList<StackMapFrame> stackMapFrames = new ArrayList<>();

		 //The running offset delta for each {@linkplain StackMapFrame}.  Each
		 //successive {@linkplain StackMapFrame frame} is offset relative to
		 //the prior {@linkplain StackMapFrame frame}.
		short offsetDelta = 0;

		//The sum of all the offsetDeltas
		long runningOffsetDeltaSum = 0;

		//The number of bytes passed
		long runningByteCount = offsetDelta;

		final Stack<Label> branchLocationStack = new Stack<>();

		for (final JavaInstruction tempInstruction : instructions)
		{
			if (tempInstruction.isBranch())
			{
				//Each branch JavaInstruction's Labels will populate the
				//branchLocationStack. For TableSwitchInstructions and
				//LookupSwitchInstructions, there maybe internal branch
				//JavaInstructions' Labels interleaved between the table
				//jumps.
				for (final Label label : tempInstruction.labels())
				{
					branchLocationStack.push(label);
				}
			}

			//check to see if the Label location on the top of the
			//branchLocationStack
			if (tempInstruction.address() ==
				branchLocationStack.peek().address())
			{
				assert tempInstruction.address() == runningByteCount;

				//Calculate the next offsetDelta relative to the reached
				//branch address and the running total of prior offSetDeltas
				offsetDelta = (short) (tempInstruction.address() -
					runningOffsetDeltaSum - 1);

				//Update the runningOffsetDeltaSum with the new value
				runningOffsetDeltaSum = runningOffsetDeltaSum
					+ offsetDelta + 1;

				//TODO[RAA] calculate the frame value and the VerificationType
				//then create StackMapFrame from that data and add to
				//stackMapFrames. Likely need to add some state to the
				//JavaByteCode to indicate its VerificationTypeInfo.  Each
				//JavaInstruction that gets here should have some
				//JavaByteCode backing it.  Looks as if each one has a field
				//bytecode or a method bytecode()

				branchLocationStack.pop();
			}

			//keep count of bytes the Method will write out
			runningByteCount += tempInstruction.size();
		}

		return new StackMapTableAttribute(stackMapFrames);
	}

}
