/**
 * SetDocumentationPathAction.java
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

package com.avail.environment.actions;

import com.avail.environment.AvailWorkbench;
import com.avail.environment.AvailWorkbench.AbstractWorkbenchAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@code SetDocumentationPathAction} displays a {@linkplain
 * JOptionPane modal dialog} that prompts the user for the Stacks
 * documentation path.
 */
@SuppressWarnings("serial")
public final class NewModuleAction
extends AbstractWorkbenchAction
{
	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		final JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setCurrentDirectory(AvailWorkbench.currentWorkingDirectory);
		chooser.setFileFilter(
			new FileNameExtensionFilter(
				".avail","avail"));

		final int result = chooser.showDialog(
			workbench, "Create");
		if (result == JFileChooser.APPROVE_OPTION)
		{
			try
			{
				final StringBuilder sb = new StringBuilder();
				final List<String> lines;
				try (
					final @NotNull BufferedReader reader =
					     new BufferedReader(new FileReader(
					     	workbench.moduleTemplateURL.getFile())))
			    {
			        lines = reader.lines().collect(Collectors.toList());
			    }

				final int size = lines.size();
				for (int i = 0; i < size - 1; i++)
				{
					sb.append(lines.get(i));
					sb.append('\n');
				}
				sb.append(lines.get(size - 1));

				final String fileName = chooser.getSelectedFile().toString();
				final String moduleName = new File(fileName).getName();
				final File file = new File(fileName + ".avail");
				file.createNewFile();

				final String year = Integer.toString(LocalDateTime.ofInstant(
					Instant.now(), ZoneOffset.UTC).getYear());
				final List<String> input = new ArrayList<>();
				input.add(sb.toString()
					.replace("${MODULE}", moduleName)
					.replace("${YEAR}", year));
				Files.write(
					file.toPath(),
					input,
					StandardCharsets.UTF_8);
				new RefreshAction(workbench).actionPerformed(event);
			}
			catch (IOException e)
			{
				System.err.println("Failed to create file: "
					+ chooser.getSelectedFile().toPath());
			}
		}
	}

	/**
	 * Construct a new {@link NewModuleAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public NewModuleAction (final AvailWorkbench workbench)
	{
		super(workbench, "New Module…");
		putValue(
			SHORT_DESCRIPTION,
			"Create a new source module");
	}
}
