/*
 * AboutAction.java
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

package com.avail.environment.actions

import com.avail.AvailRuntimeConfiguration
import com.avail.environment.AvailWorkbench
import com.avail.environment.AvailWorkbench.AdaptiveColor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog.ModalityType
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * An `AboutAction` presents the "About Avail" dialog.
 *
 * @constructor
 * Construct a new `AboutAction`.
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class AboutAction constructor(workbench: AvailWorkbench)
	: AbstractWorkbenchAction(workbench, "About Avail…")
{
	override fun actionPerformed(event: ActionEvent?)
	{
		showDialog()
	}

	/**
	 * Actually show the About dialog.  This is provided separately from the
	 * usual [ActionListener.actionPerformed] mechanism so that we can invoke it
	 * directly whenever we want, without having to synthesize an [ActionEvent].
	 */
	fun showDialog()
	{
		val panel = JPanel(BorderLayout(20, 20))
		panel.border = EmptyBorder(30, 50, 30, 50)

		val logo = ImageIcon(
			this.javaClass.getResource(
				AvailWorkbench.resource("Avail-logo-about.png")))
		panel.add(JLabel(logo))

		val builder = StringBuilder(200)
		builder.append("<html><center>")
		builder.append("<font size=+2>The Avail Workbench</font><br>")
		builder.append("<font size=-1>Supported Versions:</font>")
		for (version in AvailRuntimeConfiguration.activeVersions())
		{
			builder.append("<br><font size=-2>")
			builder.append(version.asNativeString())
			builder.append("</font>")
		}
		builder.append("<br><br>")
		builder.append(
			"Copyright \u00A9 1993-2019 The Avail Foundation, LLC.<br>")
		builder.append("All rights reserved.<br><br>")
		val siteColor = AdaptiveColor(
			light = Color(16, 16, 192),
			dark = Color(128, 160, 255))
		val site = "www.availlang.org"
		builder.append("<font color=${siteColor.hex}>$site</font><br><br><br>")
		builder.append("</center></html>")
		panel.add(
			JLabel(
				builder.toString(),
				SwingConstants.CENTER),
			BorderLayout.SOUTH)

		val aboutBox = JDialog(workbench, "About")
		aboutBox.modalityType = ModalityType.APPLICATION_MODAL
		aboutBox.contentPane.add(panel)
		aboutBox.isResizable = false
		aboutBox.pack()
		val topLeft = workbench.location
		aboutBox.setLocation(
			topLeft.getX().toInt() + 22, topLeft.getY().toInt() + 22)
		aboutBox.isVisible = true
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"About Avail")
	}
}
