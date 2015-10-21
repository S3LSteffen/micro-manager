///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.quickaccess.internal;

import com.bulenkov.iconloader.IconLoader;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.Point;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;

import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ScreenImage;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.MMPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

/**
 * This class shows the Quick-Access Window for frequently-used controls.
 */
public class QuickAccessFrame extends MMFrame {
   // Default dimensions of a cell in the controls grid.
   private static final int CELL_HEIGHT = 50;
   private static final int CELL_WIDTH = 150;
   // Profile keys.
   private static final String NUM_COLS = "Number of columns in the Quick-Access Window";
   private static final String NUM_ROWS = "Number of rows in the Quick-Access Window";

   private static QuickAccessFrame staticInstance_;

   /**
    * Show the Quick-Access Window, creating it if it does not already exist.
    */
   public static void showFrame(Studio studio) {
      if (staticInstance_ == null) {
         staticInstance_ = new QuickAccessFrame(studio);
      }
      staticInstance_.setVisible(true);
   }

   /**
    * Dead-simple container class for a widget, and its icon when in configure
    * mode.
    */
   private static class ControlCell {
      public JComponent widget_;
      public DraggableIcon icon_;
      public ControlCell(JComponent control, DraggableIcon icon) {
         widget_ = control;
         icon_ = icon;
      }
   }

   private Studio studio_;
   // Holds everything.
   private JPanel contentsPanel_;
   // Holds the active controls.
   private JPanel controlsPanel_;
   // Holds icons representing the current active controls.
   private JPanel configuringControlsPanel_;
   // Holds the "source" icons that can be added to the active controls, as
   // well as the rows/columns configuration fields.
   private JPanel configurePanel_;
   // Switches between normal and configure modes.
   private JToggleButton configureButton_;

   // Maps controls' locations on the grid to those controls, and their
   // corresponding icons when in configure mode.
   private HashMap<Point, ControlCell> gridToControl_;
   private int numCols_;
   private int numRows_;

   private int mouseX_ = -1;
   private int mouseY_ = -1;
   private ImageIcon draggedIcon_ = null;
   private QuickAccessPlugin draggedPlugin_ = null;
   private int iconOffsetX_ = -1;
   private int iconOffsetY_ = -1;

   public QuickAccessFrame(Studio studio) {
      super("Quick-Access Tools");
      studio_ = studio;
      gridToControl_ = new HashMap<Point, ControlCell>();
      numCols_ = studio_.profile().getInt(QuickAccessFrame.class,
            NUM_COLS, 3);
      numRows_ = studio_.profile().getInt(QuickAccessFrame.class,
            NUM_ROWS, 3);

      // Hide ourselves when the close button is clicked.
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            setVisible(false);
         }
      });

      // Layout overview: the active controls, then a configure button, then
      // the configuration panel (normally hidden).
      contentsPanel_ = new JPanel(new MigLayout("flowy, fill"));

      // We have two panels that are mutually-exclusive: controlsPanel_ and
      // configuringControlsPanel_. The latter is only visible in configuration
      // mode, and has special painting logic.
      controlsPanel_ = new GridPanel(false);
      configuringControlsPanel_ = new GridPanel(true);

      contentsPanel_.add(controlsPanel_);

      configureButton_ = new JToggleButton("Configure");
      configureButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            toggleConfigurePanel(configureButton_.isSelected());
         }
      });
      contentsPanel_.add(configureButton_);

      configurePanel_ = new ConfigurationPanel();

      add(contentsPanel_);
      pack();
      // Note that showFrame() will call setVisible() for us.
   }

   /**
    * We need to paint any dragged icon being moved from place to place.
    */
   @Override
   public void paint(Graphics g) {
      super.paint(g);
      if (draggedIcon_ != null) {
         g.drawImage(draggedIcon_.getImage(),
               mouseX_ - iconOffsetX_ + getInsets().left,
               mouseY_ - iconOffsetY_ + getInsets().top, null);
      }
   }

   /**
    * Drop the specified plugin into the controlsPanel_ at the current mouse
    * location, creating a new control (and configuring it if necessary).
    * @param plugin The source plugin to create the new control.
    */
   private void dropPlugin(QuickAccessPlugin plugin) {
      Point p = getCell(mouseX_, mouseY_);
      if (p != null && plugin != null) {
         // Embed the control in a panel for better sizing.
         JPanel panel = new JPanel(new MigLayout("fill"));
         JComponent control = null;
         if (plugin instanceof WidgetPlugin) {
            // Configure the plugin first.
            // TODO this needs to be spun off to a new thread.
            PropertyMap config = ((WidgetPlugin) plugin).configureControl(this);
            control = ((WidgetPlugin) plugin).createControl(config);
         }
         else {
            control = QuickAccessFactory.makeGUI(plugin);
         }
         panel.add(control, "align center");
         addControl(p, panel);
      }
      QuickAccessFrame.this.repaint();
   }

   /**
    * Add a control to the grid of controls at the specified location.
    * This requires removing and then re-adding all components because the
    * size of the MigLayout entity may have changed.
    */
   private void addControl(Point loc, JComponent control) {
      controlsPanel_.removeAll();
      configuringControlsPanel_.removeAll();
      gridToControl_.put(loc, new ControlCell(control,
               new DraggableIcon(control, null, null)));;
      for (int i = 0; i < numCols_; ++i) {
         for (int j = 0; j < numRows_; ++j) {
            Point p = new Point(i, j);
            JComponent component;
            JComponent dragger;
            if (gridToControl_.containsKey(p)) {
               component = gridToControl_.get(p).widget_;
               dragger = gridToControl_.get(p).icon_;
            }
            else {
               // Insert a dummy element to take up space.
               component = new JLabel(" ");
               dragger = new JLabel(" ");
            }
            String format = String.format("cell %d %d, w %d!, h %d!",
                  i, j, CELL_WIDTH, CELL_HEIGHT);
            controlsPanel_.add(component, format);
            configuringControlsPanel_.add(dragger, format);
         }
      }
      validate();
   }

   /**
    * Clear a control from the grid.
    */
   private void removeControl(JComponent component) {
      for (Point p : gridToControl_.keySet()) {
         ControlCell control = gridToControl_.get(p);
         if (control.widget_ == component) {
            gridToControl_.remove(p);
            controlsPanel_.remove(component);
            configuringControlsPanel_.remove(control.icon_);
            validate();
            repaint();
            break;
         }
      }
   }

   /**
    * Toggle visibility of the customization panel.
    */
   private void toggleConfigurePanel(boolean isShown) {
      contentsPanel_.removeAll();
      contentsPanel_.add(isShown ? configuringControlsPanel_ : controlsPanel_);
      contentsPanel_.add(configureButton_);
      if (isShown) {
         contentsPanel_.add(configurePanel_, "growx");
      }
      pack();
   }

   /**
    * Change the shape of the grid we use.
    */
   private void setGridSize(int cols, int rows) {
      numCols_ = cols;
      numRows_ = rows;
      // Show/hide controls depending on if they're in-bounds.
      for (Point p : gridToControl_.keySet()) {
         ControlCell control = gridToControl_.get(p);
         control.widget_.setVisible(p.x < numCols_ && p.y < numRows_);
         control.icon_.setVisible(p.x < numCols_ && p.y < numRows_);
      }
      contentsPanel_.invalidate();
      pack();
      repaint();
   }

   /**
    * Map a provided location (relative to the window) into a cell location
    * (in the controlsPanel_). Returns null if the location is not valid.
    */
   private Point getCell(int x, int y) {
      Point p = new Point(x, y);
      SwingUtilities.convertPointFromScreen(p, controlsPanel_);
      int cellX = p.x / CELL_WIDTH;
      int cellY = p.y / CELL_HEIGHT;
      if (cellX >= numCols_ || cellY >= numRows_ || cellX < 0 || cellY < 0) {
         // Out of bounds.
         return null;
      }
      return new Point(cellX, cellY);
   }

   /**
    * These panels have a fixed size based on the number of rows/columns.
    * Depending on the boolean isConfigurePanel_, they may also draw a
    * semitransparent grid over their contents (which in turn are expected to
    * be DraggableIcons instead of normal controls).
    */
   private class GridPanel extends JPanel {
      private boolean isConfigurePanel_;
      public GridPanel(boolean isConfigurePanel) {
         super(new MigLayout(
                  "flowy, insets 0, gap 0, wrap " + numRows_));
         isConfigurePanel_ = isConfigurePanel;
      }

      @Override
      public void paint(Graphics g) {
         super.paint(g);
         if (!isConfigurePanel_ || !configureButton_.isSelected()) {
            // We aren't in configure mode; just draw normally.
            return;
         }
         // Draw a grid showing the cells.
         int width = getSize().width;
         int height = getSize().height;
         g.setColor(new Color(200, 255, 200, 128));
         g.fillRect(0, 0, numCols_ * CELL_WIDTH, numRows_ * CELL_HEIGHT);
         if (draggedIcon_ != null) {
            // Draw the current cell in a highlighted color. Note that
            // mouse coordinates are with respect to the window, not this
            // panel.
            g.setColor(new Color(255, 200, 200, 128));
            Point p = getCell(mouseX_, mouseY_);
            if (p != null) {
               g.fillRect(p.x * CELL_WIDTH, p.y * CELL_HEIGHT,
                     CELL_WIDTH, CELL_HEIGHT);
            }
         }
         // Draw the grid lines.
         g.setColor(Color.BLACK);
         for (int i = 0; i < numCols_ + 1; ++i) {
            g.drawLine(i * CELL_WIDTH, 0, i * CELL_WIDTH,
                  CELL_HEIGHT * numRows_);
         }
         for (int i = 0; i < numRows_ + 1; ++i) {
            g.drawLine(0, i * CELL_HEIGHT, numCols_ * CELL_WIDTH,
                  i * CELL_HEIGHT);
         }
      }

      public Dimension getPreferredSize() {
         return new Dimension(numCols_ * CELL_WIDTH,
               numRows_ * CELL_HEIGHT);
      }
   }

   /**
    * This class exposes a GUI for adding and removing controls from the
    * main window.
    */
   private class ConfigurationPanel extends JPanel {
      // Maps iconified versions of controls to the plugins that generated
      // them.
      private HashMap<ImageIcon, MMPlugin> iconToPlugin_;
      private JTextField colsControl_;
      private JTextField rowsControl_;

      public ConfigurationPanel() {
         super(new MigLayout("flowx, wrap 8"));
         iconToPlugin_ = new HashMap<ImageIcon, MMPlugin>();
         setBorder(BorderFactory.createLoweredBevelBorder());

         // Add controls for setting rows/columns.
         // It really bugs me how redundant this code is. Java!
         JPanel subPanel = new JPanel(new MigLayout("flowx"));
         subPanel.add(new JLabel("Columns: "));
         colsControl_ = new JTextField(
               Integer.toString(numCols_));
         subPanel.add(colsControl_);
         colsControl_.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {}
            @Override
            public void insertUpdate(DocumentEvent event) {
               updateSize();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
               updateSize();
            }
         });

         subPanel.add(new JLabel("Rows: "));
         rowsControl_ = new JTextField(
               Integer.toString(numRows_));
         subPanel.add(rowsControl_);
         rowsControl_.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {}
            @Override
            public void insertUpdate(DocumentEvent event) {
               updateSize();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
               updateSize();
            }
         });

         add(subPanel, "span, wrap");

         // Populate the panel with icons corresponding to controls we can
         // provide. List them in alphabetical order.
         HashMap<String, QuickAccessPlugin> plugins = studio_.plugins().getQuickAccessPlugins();
         ArrayList<String> keys = new ArrayList<String>(plugins.keySet());
         Collections.sort(keys);
         for (String key : keys) {
            QuickAccessPlugin plugin = plugins.get(key);
            DraggableIcon dragger = new DraggableIcon(
                  QuickAccessFactory.makeGUI(plugin), plugin.getIcon(), plugin);
            iconToPlugin_.put(dragger.getIcon(), plugin);
            add(dragger, "split 2, flowy");
            add(new JLabel(plugins.get(key).getName()), "alignx center");
         }
      }

      /**
       * Change the grid size. Just a helper function to avoid duplication.
       */
      private void updateSize() {
         try {
            setGridSize(Integer.parseInt(colsControl_.getText()),
                  Integer.parseInt(rowsControl_.getText()));
         }
         catch (Exception e) {
            // Ignore it.
         }
      }
   }

   /**
    * This class represents an icon that can be dragged around the window,
    * for adding or removing controls.
    */
   private class DraggableIcon extends JLabel {
      private JComponent component_;
      private QuickAccessPlugin plugin_;
      private ImageIcon icon_;

      /**
       * icon and plugin are both optional. If plugin is null, then we assume
       * this references an instantiated control which can be removed from
       * the controlsPanel_ when dragged out of the grid. Otherwise, we want to
       * create a new control when the icon is dragged into the grid). If icon
       * is null, then we generate an icon from the JComponent.
       */
      public DraggableIcon(final JComponent component, ImageIcon icon,
            final QuickAccessPlugin plugin) {
         super();
         component_ = component;
         plugin_ = plugin;
         icon_ = icon;
         if (icon_ == null) {
            icon_ = new ImageIcon(ScreenImage.createImage(component));
         }
         setIcon(icon_);
         if (plugin != null) {
            setToolTipText(plugin.getHelpText());
         }
         // When the user clicks and drags the icon, we need to move it
         // around.
         MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
               draggedIcon_ = icon_;
               draggedPlugin_ = plugin_;
               iconOffsetX_ = e.getX();
               iconOffsetY_ = e.getY();
            }
            @Override
            public void mouseDragged(MouseEvent e) {
               // We need to get the mouse coordinates with respect to the
               // upper-left corner of the window's content pane.
               Window parent = SwingUtilities.getWindowAncestor(
                     DraggableIcon.this);
               mouseX_ = e.getXOnScreen() - parent.getLocation().x -
                  parent.getInsets().left;
               mouseY_ = e.getYOnScreen() - parent.getLocation().y -
                  parent.getInsets().top;
               QuickAccessFrame.this.repaint();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
               draggedIcon_ = null;
               // Stop dragging; create or destroy controls as appropriate.
               if (plugin_ == null) {
                  // Remove the control, if it's been dragged out of the cell.
                  Point p = getCell(mouseX_, mouseY_);
                  if (p == null) {
                     removeControl(component_);
                  }
               }
               else {
                  dropPlugin(plugin_);
               }
            }
         };
         addMouseListener(adapter);
         addMouseMotionListener(adapter);
      }

      public ImageIcon getIcon() {
         return icon_;
      }
   }
}