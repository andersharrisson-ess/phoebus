/*******************************************************************************
 * Copyright (c) 2010-2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.trends.databrowser3.ui.properties;

import java.util.List;

import org.csstudio.trends.databrowser3.Activator;
import org.csstudio.trends.databrowser3.Messages;
import org.csstudio.trends.databrowser3.model.Model;
import org.csstudio.trends.databrowser3.model.ModelItem;
import org.phoebus.ui.undo.UndoableActionManager;

import javafx.scene.control.MenuItem;

/** MenuItem to delete items from Model
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class DeleteItemsMenuItem extends MenuItem
{
    public DeleteItemsMenuItem(final Model model, final UndoableActionManager undo,
                               final List<ModelItem> selected)
    {
        super(Messages.DeleteItem, Activator.getIcon("delete_obj"));
        setOnAction(event ->
        {
            for (ModelItem item : selected)
            {
            // TODO Check if item is used as input for formula
//            final Optional<FormulaItem> formula = model.getFormulaWithInput(items[i]);
//            if (formula.isPresent())
//            {
//                MessageDialog.openError(trace_table.getTable().getShell(),
//                        Messages.Error,
//                        NLS.bind(Messages.PVUsedInFormulaFmt, items[i].getName(), formula.get().getName()));
//                return;
//            }
            }
            new DeleteItemsCommand(undo, model, selected);
        });
    }
}