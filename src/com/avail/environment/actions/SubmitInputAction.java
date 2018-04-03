/*
 * SubmitInputAction.java
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

package com.avail.environment.actions;

import com.avail.builder.AvailBuilder.CompiledCommand;
import com.avail.environment.AvailWorkbench;
import com.avail.interpreter.Interpreter;
import com.avail.io.ConsoleInputChannel;
import com.avail.io.ConsoleOutputChannel;
import com.avail.io.TextInterface;
import com.avail.utility.evaluation.Continuation0;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;

import static javax.swing.SwingUtilities.invokeLater;

/**
 * A {@code SubmitInputAction} sends a line of text from the input field to
 * standard input.
 */
@SuppressWarnings("serial")
public final class SubmitInputAction
extends AbstractWorkbenchAction
{
	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		if (workbench.inputField.isFocusOwner())
		{
			assert workbench.backgroundTask == null;
			if (workbench.isRunning)
			{
				// Program is running.  Feed this new line of text to the
				// input stream to be consumed by the running command, or
				// possibly discarded when that command completes.
				workbench.inputStream().update();
				return;
			}
			// No program is running.  Treat this as a command and try to
			// run it.  Do not feed the string into the input stream.
			final String string = workbench.inputField.getText();
			workbench.commandHistory.add(string);
			workbench.commandHistoryIndex = -1;
			workbench.inputStream().feedbackForCommand(string);
			workbench.inputField.setText("");
			workbench.isRunning = true;
			workbench.setEnablements();
			workbench.availBuilder.runtime.setTextInterface(new TextInterface(
				new ConsoleInputChannel(workbench.inputStream()),
				new ConsoleOutputChannel(workbench.outputStream()),
				new ConsoleOutputChannel(workbench.errorStream())));
			workbench.availBuilder.attemptCommand(
				string,
				(commands, proceed) ->
				{
					assert commands != null;
					assert proceed != null;
					final CompiledCommand[] array =
						commands.toArray(new CompiledCommand[commands.size()]);
					Arrays.sort(
						array,
						(o1, o2) ->
						{
							assert o1 != null;
							assert o2 != null;
							return o1.toString().compareTo(
								o2.toString());
						});
					final CompiledCommand selection = (CompiledCommand)
						JOptionPane.showInputDialog(
							workbench,
							"Choose the desired entry point:",
							"Disambiguate",
							JOptionPane.QUESTION_MESSAGE,
							null,
							commands.toArray(),
							null);
					// There may not be a selection, in which case the
					// command will not be run – but any necessary cleanup
					// will be run.
					proceed.value(selection);
				},
				(result, cleanup) ->
				{
					assert cleanup != null;
					final Continuation0 afterward = () ->
					{
						workbench.isRunning = false;
						invokeLater(() ->
						{
							workbench.inputStream().clear();
							workbench.availBuilder.runtime
								.setTextInterface(new TextInterface(
									new ConsoleInputChannel(
										workbench.inputStream()),
									new ConsoleOutputChannel(
										workbench.outputStream()),
									new ConsoleOutputChannel(
										workbench.errorStream())));
							workbench.setEnablements();
						});
					};
					assert result != null;
					if (result.equalsNil())
					{
						cleanup.value(afterward);
						return;
					}
					Interpreter.stringifyThen(
						workbench.availBuilder.runtime,
						workbench.availBuilder.runtime.textInterface(),
						result,
						resultString ->
						{
							workbench
								.outputStream()
								.append(resultString)
								.append("\n");
							cleanup.value(afterward);
						});
				},
				() -> invokeLater(() ->
				{
					workbench.isRunning = false;
					workbench.inputStream().clear();
					workbench.availBuilder.runtime.setTextInterface(
						new TextInterface(
							new ConsoleInputChannel(
								workbench.inputStream()),
							new ConsoleOutputChannel(
								workbench.outputStream()),
							new ConsoleOutputChannel(
								workbench.errorStream())));
					workbench.setEnablements();
				}));
		}
	}

	/**
	 * Construct a new {@code SubmitInputAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public SubmitInputAction (final AvailWorkbench workbench)
	{
		super(workbench, "Submit Input");
		putValue(
			SHORT_DESCRIPTION,
			"Submit the input field (plus a new line) to standard input.");
		putValue(
			ACCELERATOR_KEY,
			KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
	}
}
