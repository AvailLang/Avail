/**
 * AboutAction.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import com.avail.AvailRuntime;
import com.avail.descriptor.A_String;
import com.avail.environment.AvailWorkbench;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static com.avail.environment.AvailWorkbench.resourcePrefix;

/**
 * An {@code AboutAction} presents the "About Avail" dialog.
 */
@SuppressWarnings("serial")
public final class AboutAction
extends AbstractWorkbenchAction
{
	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		showDialog();
	}

	/**
	 * Actually show the About dialog.  This is provided separately from the
	 * usual {@link ActionListener#actionPerformed(ActionEvent)} mechanism so
	 * that we can invoke it directly whenever we want, without having to
	 * synthesize an {@link ActionEvent}.
	 */
	public void showDialog ()
	{
		final JPanel panel = new JPanel(new BorderLayout(20, 20));
		panel.setBorder(new EmptyBorder(30, 50, 30, 50));

		final ImageIcon logo = new ImageIcon(
			this.getClass().getResource(
				resourcePrefix + "Avail-logo-about.png"));
		panel.add(new JLabel(logo));

		final StringBuilder builder = new StringBuilder(200);
		builder.append("<html><center>");
		builder.append("<font color=blue>www.availlang.org</font><br><br><br>");
		builder.append("<font size=+2>The Avail Workbench</font><br>");
		builder.append("<font size=-1>Supported Versions:</font>");
		for (final A_String version : AvailRuntime.activeVersions())
		{
			builder.append("<br><font size=-2>");
			builder.append(version.asNativeString());
			builder.append("</font>");
		}
		builder.append("<br><br>");
		builder.append(
			"Copyright \u00A9 1993-2017 The Avail Foundation, LLC.<br>");
		builder.append("All rights reserved.");
		builder.append("</center></html>");
		panel.add(
			new JLabel(
				builder.toString(),
				SwingConstants.CENTER),
			BorderLayout.SOUTH);

		final JDialog aboutBox = new JDialog(workbench, "About");
		aboutBox.setModalityType(ModalityType.APPLICATION_MODAL);
		aboutBox.getContentPane().add(panel);
		aboutBox.setResizable(false);
		aboutBox.pack();
		final Point topLeft = workbench.getLocation();
		aboutBox.setLocation(
			(int)topLeft.getX() + 22, (int)topLeft.getY() + 22);
		aboutBox.setVisible(true);
	}

	/**
	 * Construct a new {@link AboutAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public AboutAction (final AvailWorkbench workbench)
	{
		super(workbench, "About Avail…");
		putValue(
			SHORT_DESCRIPTION,
			"About Avail");
	}
}
