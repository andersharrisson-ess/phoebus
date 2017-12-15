/*******************************************************************************
 * Copyright (c) 2017 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.ui.docking;

import static org.phoebus.ui.docking.DockPane.logger;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

import org.phoebus.framework.spi.AppDescriptor;
import org.phoebus.framework.spi.AppInstance;
import org.phoebus.ui.javafx.ImageCache;

import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Item for a {@link DockPane}
 *
 *  <p>Technically a {@link Tab},
 *  should be treated as a new type of node,
 *  calling only
 *  <ul>
 *  <li>the methods declared in here
 *  <li>{@link Tab#setContent(javafx.scene.Node)},
 *  <li>{@link Tab#setClosable(boolean)}
 *  </ul>
 *  to allow changes to the internals.
 *
 *  <p>Specifically,
 *  {@link Tab#setOnCloseRequest(EventHandler)}
 *  and
 *  {@link Tab#setOnClosed(EventHandler)}
 *  are used internally and should not be called.
 *
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class DockItem extends Tab
{
    /** Property key used for the {@link AppDescriptor} */
    public static final String KEY_APPLICATION = "application";

    private final static ImageView detach_icon = ImageCache.getImageView(DockItem.class, "/icons/detach.png");

    /** The item that's currently being dragged
     *
     *  <p>The actual DockItem cannot be serialized
     *  for drag-and-drop,
     *  and since docking is limited to windows within
     *  the same JVM, this reference points to the item
     *  that's being dragged.
     */
    static final AtomicReference<DockItem> dragged_item = new AtomicReference<>();

    static final Border DROP_ZONE_BORDER = new Border(new BorderStroke(Color.GREEN, BorderStrokeStyle.SOLID,
                                                                       new CornerRadii(5.0), BorderStroke.MEDIUM));

    /** Drag-and-drop data format
     *
     *  Custom format to prevent dropping a tab into e.g. a text editor
     */
    private static final DataFormat DOCK_ITEM = new DataFormat("dock_item.custom");

    /** Name of the tab */
    protected String name;

    /** Label used for the Tab because Tab itself cannot participate in drag-and-drop */
    protected final Label name_tab;

    /** Called to check if OK to close the tab */
    private List<BooleanSupplier> close_check = null;

    /** Called after tab was closed */
    private List<Runnable> closed_callback = null;

    /** Create dock item for instance of an application
     *  @param application {@link AppInstance}
     *  @param content Content for this application instance
     */
    public DockItem(final AppInstance applicationInstance, final Node content)
    {
        this(applicationInstance.getAppDescriptor().getDisplayName());
        getProperties().put(KEY_APPLICATION, applicationInstance);
        setContent(content);
    }

    /** Create dock item
     *  @param label Initial label
     */
    public DockItem(final String label)
    {
        getProperties().put(DockStage.KEY_ID, DockStage.createID("DockItem"));

        // Create tab with no 'text',
        // instead using a Label for the text
        // because the label can react to drag operations
        name = label;
        name_tab = new Label(label);
        setGraphic(name_tab);

        name_tab.setOnDragDetected(this::handleDragDetected);
        name_tab.setOnDragOver(this::handleDragOver);
        name_tab.setOnDragEntered(this::handleDragEntered);
        name_tab.setOnDragExited(this::handleDragExited);
        name_tab.setOnDragDropped(this::handleDrop);
        name_tab.setOnDragDone(this::handleDragDone);

        createContextMenu();
    }

    private void createContextMenu()
    {
        final MenuItem detach = new MenuItem("Detach", detach_icon);
        detach.setOnAction(event -> detach());

        final MenuItem close = new MenuItem("Close");
        close.setOnAction(event -> close());

        final MenuItem close_other = new MenuItem("Close Others");
        close_other.setOnAction(event ->
        {
            for (Tab tab : new ArrayList<>(getTabPane().getTabs()))
                if ((tab instanceof DockItem)  &&  tab != DockItem.this)
                    ((DockItem)tab).close();
        });

        final MenuItem close_all = new MenuItem("Close All");
        close_all.setOnAction(event ->
        {
            for (Tab tab : new ArrayList<>(getTabPane().getTabs()))
                if ((tab instanceof DockItem))
                    ((DockItem)tab).close();
        });


        final ContextMenu menu = new ContextMenu(detach,
                                                 new SeparatorMenuItem(),
                                                 close,
                                                 close_other,
                                                 new SeparatorMenuItem(),
                                                 close_all);
        name_tab.setContextMenu(menu );
    }

    /** @return Unique ID of this dock item */
    public String getID()
    {
        return (String) getProperties().get(DockStage.KEY_ID);
    }

    /** @return Application instance of this dock item, may be <code>null</code> */
    @SuppressWarnings("unchecked")
    public <INST extends AppInstance> INST getApplication()
    {
        return (INST) getProperties().get(KEY_APPLICATION);
    }

    /** Label of this item */
    public String getLabel()
    {
        return name;
    }

    /** @return Label node text, limited to in-package access */
    StringProperty labelTextProperty()
    {
        return name_tab.textProperty();
    }

    /** @param label Label of this item */
    public void setLabel(final String label)
    {
        name = label;
        name_tab.setText(label);
    }

    /**    Allow dragging this item */
    private void handleDragDetected(final MouseEvent event)
    {
        final Dragboard db = name_tab.startDragAndDrop(TransferMode.MOVE);

        final ClipboardContent content = new ClipboardContent();
        content.put(DOCK_ITEM, getLabel());
        db.setContent(content);

        final DockItem previous = dragged_item.getAndSet(this);
        if (previous != null)
        {
            System.err.println("Already dragging " + previous);
        }

        event.consume();

    }

    /** Accept other items that are dropped onto this one */
    private void handleDragOver(final DragEvent event)
    {
        final DockItem item = dragged_item.get();
        if (item != null  &&  item != this)
            event.acceptTransferModes(TransferMode.MOVE);
        event.consume();
    }

    /** Highlight while 'drop' is possible */
    private void handleDragEntered(final DragEvent event)
    {
        final DockItem item = dragged_item.get();
        if (item != null  &&  item != this)
        {
            name_tab.setBorder(DROP_ZONE_BORDER);
            name_tab.setTextFill(Color.GREEN);
        }
        event.consume();
    }

    /** Remove Highlight */
    private void handleDragExited(final DragEvent event)
    {
        name_tab.setBorder(Border.EMPTY);
        name_tab.setTextFill(Color.BLACK);
        event.consume();
    }

    /** Accept a dropped tab */
    private void handleDrop(final DragEvent event)
    {
        final DockItem item = dragged_item.getAndSet(null);
        if (item == null)
            logger.log(Level.SEVERE, "Empty drop, " + event);
        else
        {
            // System.out.println("Somebody dropped " + item + " onto " + this);
            final TabPane old_parent = item.getTabPane();
            final TabPane new_parent = getTabPane();

            // Unexpected, but would still "work" at this time
            if (! (old_parent instanceof DockPane))
                throw new IllegalStateException("Expected DockPane for " + item + ", got " + old_parent);
            if (! (new_parent instanceof DockPane))
                throw new IllegalStateException("Expected DockPane for " + item + ", got " + new_parent);

            if (new_parent != old_parent)
            {
                old_parent.getTabs().remove(item);
                // Insert after the item on which it was dropped
                final int index = new_parent.getTabs().indexOf(this);
                new_parent.getTabs().add(index+1, item);
            }
            else
            {
                final int index = new_parent.getTabs().indexOf(this);
                new_parent.getTabs().remove(item);
                // If item was 'left' of this, it will be added just after this.
                // If item was 'right' of this, it'll be added just before this.
                new_parent.getTabs().add(index, item);
            }
            // Select the new item
            new_parent.getSelectionModel().select(item);
        }
        event.setDropCompleted(true);
        event.consume();
    }

    /** Handle that this tab was dragged elsewhere, or drag aborted */
    private void handleDragDone(final DragEvent event)
    {
        final DockItem item = dragged_item.getAndSet(null);
        if (item != null  &&  !event.isDropCompleted())
        {
            // Would like to position new stage where the mouse was released,
            // but event.getX(), getSceneX(), getScreenX() are all 0.0.
            // --> Using MouseInfo, which is actually AWT code
            final Stage other = item.detach();
            final PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi != null)
            {
                final Point loc = pi.getLocation();
                other.setX(loc.getX());
                other.setY(loc.getY());
            }
        }
        event.consume();
    }

    private Stage detach()
    {
        final Stage other = new Stage();

        final TabPane old_parent = getTabPane();

        // Unexpected, but would still "work" at this time
        if (! (old_parent instanceof DockPane))
            throw new IllegalStateException("Expected DockPane for " + this + ", got " + old_parent);

        old_parent.getTabs().remove(this);
        DockStage.configureStage(other, this);

        // For size of new stage, use the old one.
        // (Initially used size of old _Window_,
        //  but that resulted in a new Stage that's
        //  too high, about one title bar height taller).
        final Scene old_scene = old_parent.getScene();
        other.setWidth(old_scene.getWidth());
        other.setHeight(old_scene.getHeight());

        other.show();

        return other;
    }

    /** Select this tab, i.e. raise it in case another tab is currently visible */
    public void select()
    {
        final TabPane pane = getTabPane();
        final Window window = pane.getScene().getWindow();
        if (window instanceof Stage)
        {
            Stage stage = (Stage) window;
            if (stage.isShowing())
                stage.toFront();
        }
        pane.getSelectionModel().select(this);
    }

    /** Register check for closing the tab
     *
     *  @param ok_to_close Will be called when tab is about to close.
     *                     Should return <code>true</code> if OK to close,
     *                     <code>false</code> to leave the tab open.
     */
    public void addCloseCheck(final BooleanSupplier ok_to_close)
    {
        if (close_check == null)
        {
            close_check = new ArrayList<>();
            setOnCloseRequest(event ->
            {
                for (BooleanSupplier check : close_check)
                    if (! check.getAsBoolean())
                    {
                        event.consume();
                        return;
                    }
            });
        }
        close_check.add(ok_to_close);
    }

    /** Register for notification when tab was closed
     *
     *  @param closed Will be called after tab was closed
     */
    public void addClosedNotification(final Runnable closed)
    {
        if (closed_callback == null)
        {
            closed_callback = new ArrayList<>();
            setOnClosed(event ->
            {
                for (Runnable check : closed_callback)
                    check.run();
            });
        }
        closed_callback.add(closed);
    }


    /** Programmatically close this tab
     *
     *  <p>Will invoke on-close-request handler that can abort the action,
     *  otherwise invoke the on-closed handler and remove the tab
     *
     *  @return <code>true</code> if tab closed, <code>false</code> if it remained open
     */
    public boolean close()
    {
        EventHandler<Event> handler = getOnCloseRequest();
        if (handler != null)
        {
            final Event event = new Event(Tab.TAB_CLOSE_REQUEST_EVENT);
            handler.handle(event);
            if (event.isConsumed())
                return false;
        }

        handler = getOnClosed();
        if (null != handler)
            handler.handle(null);

        getTabPane().getTabs().remove(this);

        return true;
    }

    @Override
    public String toString()
    {
        return "DockItem(\"" + getLabel() + "\")";
    }
}