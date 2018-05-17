/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.misc;

import com.evgcompany.binntrdbot.mainApplication;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTextField;

/**
 *
 * @author EVG_Adminer
 */
public class ComponentsConfigController {
    
    Preferences prefs = null;
    Map<String, Component> components = new HashMap<>();
    
    public ComponentsConfigController(mainApplication parent) {
        prefs = Preferences.userNodeForPackage(parent.getClass());
    }
    
    public void addComponent(Component cb, String name) {
        components.put(name, cb);
    }
    
    public void Save() {
        components.entrySet().forEach((entry) -> {
            componentPrefSave(entry.getValue(), entry.getKey());
        });
    }
    public void Load() {
        components.entrySet().forEach((entry) -> {
            componentPrefLoad(entry.getValue(), entry.getKey());
        });
    }
    
    private void componentPrefLoad(Component cb, String name) {
        try {
            if (cb instanceof JCheckBox) {
                ((JCheckBox)cb).setSelected("true".equals(prefs.get(name, ((JCheckBox)cb).isSelected() ? "true" : "false")));
            } else if (cb instanceof JTextField) {
                ((JTextField)cb).setText(prefs.get(name, ((JTextField)cb).getText()));
            } else if (cb instanceof JSpinner) {
                ((JSpinner)cb).setValue(Double.parseDouble(prefs.get(name, ((Number) ((JSpinner)cb).getValue()).toString())));
            } else if (cb instanceof JComboBox) {
                ((JComboBox<String>)cb).setSelectedIndex(Integer.parseInt(prefs.get(name, ((Integer) ((JComboBox<String>)cb).getSelectedIndex()).toString())));
            } else if (cb instanceof JList) {
                String indstr = prefs.get(name, "");
                int[] indexes = Arrays.stream(indstr.substring(1, indstr.length()-1).split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
                ((JList<String>)cb).setSelectedIndices(indexes);
            } else if (cb instanceof Window) {
                cb.setBounds(
                    Integer.parseInt(prefs.get(name+"_pos_x", String.valueOf(Math.round(cb.getBounds().getX())))),
                    Integer.parseInt(prefs.get(name+"_pos_y", String.valueOf(Math.round(cb.getBounds().getY())))),
                    Integer.parseInt(prefs.get(name+"_size_x", String.valueOf(Math.round(cb.getBounds().getWidth())))),
                    Integer.parseInt(prefs.get(name+"_size_y", String.valueOf(Math.round(cb.getBounds().getHeight()))))
                );
            }
            if (cb instanceof AbstractButton) {
                for(ActionListener a: ((AbstractButton)cb).getActionListeners()) {
                    a.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null) {});
                }
            }
        } catch(Exception e) {}
    }
    private void componentPrefSave(Component cb, String name) {
        try {
            if (cb instanceof JCheckBox) {
                prefs.put(name, ((JCheckBox)cb).isSelected() ? "true" : "false");
            } else if (cb instanceof JTextField) {
                prefs.put(name, ((JTextField)cb).getText());
            } else if (cb instanceof JSpinner) {
                prefs.put(name, ((Number) ((JSpinner)cb).getValue()).toString());
            } else if (cb instanceof JComboBox) {
                prefs.put(name, ((Integer) ((JComboBox<String>)cb).getSelectedIndex()).toString());
            } else if (cb instanceof JList) {
                prefs.put(name, Arrays.toString(((JList<String>)cb).getSelectedIndices()));
            } else if (cb instanceof Window) {
                prefs.put(name+"_pos_x", String.valueOf(Math.round(cb.getBounds().getX())));
                prefs.put(name+"_pos_y", String.valueOf(Math.round(cb.getBounds().getY())));
                prefs.put(name+"_size_x", String.valueOf(Math.round(cb.getBounds().getWidth())));
                prefs.put(name+"_size_y", String.valueOf(Math.round(cb.getBounds().getHeight())));
            }
        } catch(Exception e) {}
    }
}
