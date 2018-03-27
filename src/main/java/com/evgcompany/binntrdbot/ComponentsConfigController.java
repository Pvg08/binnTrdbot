/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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
        for (Map.Entry<String, Component> entry : components.entrySet()) {
            componentPrefSave(entry.getValue(), entry.getKey());
        }
    }
    public void Load() {
        for (Map.Entry<String, Component> entry : components.entrySet()) {
            componentPrefLoad(entry.getValue(), entry.getKey());
        }
    }
    
    private void componentPrefLoad(Component cb, String name) {
        try {
            if (cb instanceof JCheckBox) {
                ((JCheckBox)cb).setSelected("true".equals(prefs.get(name, ((JCheckBox)cb).isSelected() ? "true" : "false")));
            } else if (cb instanceof JTextField) {
                ((JTextField)cb).setText(prefs.get(name, ((JTextField)cb).getText()));
            } else if (cb instanceof JSpinner) {
                ((JSpinner)cb).setValue(Integer.parseInt(prefs.get(name, ((Integer) ((JSpinner)cb).getValue()).toString())));
            } else if (cb instanceof JComboBox) {
                ((JComboBox<String>)cb).setSelectedIndex(Integer.parseInt(prefs.get(name, ((Integer) ((JComboBox<String>)cb).getSelectedIndex()).toString())));
            } else if (cb instanceof Window) {
                cb.setBounds(
                    Integer.parseInt(prefs.get(name+"_pos_x", String.valueOf(Math.round(cb.getBounds().getX())))),
                    Integer.parseInt(prefs.get(name+"_pos_y", String.valueOf(Math.round(cb.getBounds().getY())))),
                    Integer.parseInt(prefs.get(name+"_size_x", String.valueOf(Math.round(cb.getBounds().getWidth())))),
                    Integer.parseInt(prefs.get(name+"_size_y", String.valueOf(Math.round(cb.getBounds().getHeight()))))
                );
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
                prefs.put(name, ((JSpinner)cb).getValue().toString());
            } else if (cb instanceof JComboBox) {
                prefs.put(name, ((Integer) ((JComboBox<String>)cb).getSelectedIndex()).toString());
            } else if (cb instanceof Window) {
                prefs.put(name+"_pos_x", String.valueOf(Math.round(cb.getBounds().getX())));
                prefs.put(name+"_pos_y", String.valueOf(Math.round(cb.getBounds().getY())));
                prefs.put(name+"_size_x", String.valueOf(Math.round(cb.getBounds().getWidth())));
                prefs.put(name+"_size_y", String.valueOf(Math.round(cb.getBounds().getHeight())));
            }
        } catch(Exception e) {}
    }
}
