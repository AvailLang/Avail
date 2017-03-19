/**
 * EditModuleTask.java
 * Copyright © 1993-2017, The Avail Foundation, LLC. All rights reserved.
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

package com.avail.environment.tasks;
import com.avail.builder.ResolvedModuleName;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.AvailWorkbench.AbstractWorkbenchTask;
import com.avail.environment.AvailWorkbench.LayoutConfiguration;
import com.avail.environment.editor.ModuleEditor;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.prefs.Preferences;

/**
 * A {@code EditModuleTask} is a {@link AbstractWorkbenchTask} used to open
 * a module in a new window.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class EditModuleTask
extends AbstractWorkbenchTask
implements WindowListener
{
	private final @NotNull JFrame frame;

	@Override
	protected void executeTask () throws Exception
	{
		EventQueue.invokeLater(() ->
		{
			final Preferences preferences =
				workbench.placementPreferencesNodeForScreenNames(
					AvailWorkbench.allScreenNames());
			final LayoutConfiguration savedConfiguration =
				new LayoutConfiguration(
					preferences.get(AvailWorkbench.placementLeafKeyString, ""));
			if (savedConfiguration.moduleViewerPlacement != null)
			{
				frame.setBounds(savedConfiguration.moduleViewerPlacement);
			}
			//JFXPanel must be created before scene due to initialization issues
			final JFXPanel fxPanel = new JFXPanel();
			final ModuleEditor viewer =
				ModuleEditor.moduleViewer(
					targetModuleName(), workbench, frame.getBounds());
			fxPanel.setScene(viewer);
			//This must be called to circumvent a bug that won't be fixed
			//See https://bugs.openjdk.java.net/browse/JDK-8090517
			Platform.setImplicitExit(false);
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.setOpaque(true);

			frame.getContentPane().add(BorderLayout.CENTER, panel);
			frame.pack();
			frame.setLocationByPlatform(true);
			frame.setVisible(true);
			frame.setResizable(true);
			frame.add(fxPanel);
			if (savedConfiguration.moduleViewerPlacement != null)
			{
				frame.setSize(
					savedConfiguration.moduleViewerPlacement.width,
					savedConfiguration.moduleViewerPlacement.height);
			}
			else
			{
				frame.setSize(800, 600);
			}

			workbench.openedSourceModules.put(targetModuleName, frame);
			frame.addWindowListener(this);
		});
	}

	@Override
	protected void done ()
	{
		workbench.backgroundTask = null;
		reportDone();
		workbench.availBuilder.checkStableInvariants();
		workbench.setEnablements();
		workbench.setCursor(Cursor.getDefaultCursor());
	}

	@Override
	public void windowOpened (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowClosing (final WindowEvent e)
	{
		final Preferences preferences =
			workbench.placementPreferencesNodeForScreenNames(
				AvailWorkbench.allScreenNames());
		final LayoutConfiguration saveConfiguration =
			new LayoutConfiguration(
				preferences.get(AvailWorkbench.placementLeafKeyString, ""));
		saveConfiguration.moduleViewerPlacement = frame.getBounds();
		preferences.put(
			AvailWorkbench.placementLeafKeyString,
			saveConfiguration.stringToStore());
	}

	@Override
	public void windowClosed (final WindowEvent e)
	{
		workbench.openedSourceModules.remove(targetModuleName);
	}

	@Override
	public void windowIconified (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowDeiconified (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowActivated (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowDeactivated (final WindowEvent e)
	{
		//Do Nothing
	}

	/**
	 * Construct a new {@link EditModuleTask}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 * @param targetModuleName
	 *        The resolved name of the target {@linkplain ModuleDescriptor
	 *        module} to unload, or null to unload all modules.
	 */
	public EditModuleTask (
		final AvailWorkbench workbench,
		final @Nullable ResolvedModuleName targetModuleName)
	{
		super(workbench, targetModuleName);
		this.frame = new JFrame(this.targetModuleName.localName());
		final Preferences preferences =
			workbench.placementPreferencesNodeForScreenNames(
				AvailWorkbench.allScreenNames());
		final LayoutConfiguration savedConfiguration =
			new LayoutConfiguration(
				preferences.get(AvailWorkbench.placementLeafKeyString, ""));
	}
}
