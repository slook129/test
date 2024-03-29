package com.blazemeter.jmeter.correlation.gui.templates.validations;

import com.blazemeter.jmeter.correlation.gui.common.PlaceHolderTextField;
import com.blazemeter.jmeter.correlation.gui.templates.PlaceHolderComboBox;
import java.awt.Color;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;

public class BaseValidation<T extends JTextComponent> implements ComponentValidation<T> {
  public static final Color INVALID_COLOR = Color.red;
  protected final Border fallbackBorder = new JTextField().getBorder();
  protected final Border invalidBorder = BorderFactory.createLineBorder(INVALID_COLOR);
  protected Border defaultBorder;

  protected T field;
  protected JLabel error;
  protected List<Condition> conditions = new ArrayList<>();

  public BaseValidation(T field, JLabel error, List<Condition> conditionRules) {
    this.field = field;
    this.error = error;
    this.conditions = conditionRules;
    this.defaultBorder = getDefaultBorderForComponent(field);
  }

  public void applyFormat(boolean valid) {
    getOutermostComponent(field).setBorder(valid ? defaultBorder : invalidBorder);
    error.setVisible(!valid);
    error.setText(valid ? "" : conditions.stream()
        .filter(validation -> !validation.isValid())
        .map(Condition::getErrorMessage)
        .collect(Collectors.joining(". ")));
  }

  @Override
  public void applyFormat() {
    boolean valid = isValid();
    applyFormat(valid);
  }

  @Override
  public void clearFormat() {
    applyFormat(true);
  }

  private JComponent getOutermostComponent(T field) {
    if (field instanceof JTextArea) {
      Container parent = field.getParent();
      if (parent instanceof JViewport) {
        Container grandParent = parent.getParent();
        if (grandParent instanceof JScrollPane) {
          return (JScrollPane) grandParent;
        }
      }
    }

    if (field instanceof PlaceHolderTextField) {
      Container parent = field.getParent();
      if (parent instanceof PlaceHolderComboBox) {
        return (PlaceHolderComboBox) parent;
      }
    }

    return field;
  }

  private Border getDefaultBorderForComponent(JComponent component) {
    if (component instanceof JTextField
        && UIManager.getBorder("TextField.border") != null) {
      return UIManager.getBorder("TextField.border");
    } else if (component instanceof JTextArea
        && UIManager.getBorder("TextArea.border") != null) {
      return UIManager.getBorder("TextArea.border");
    }

    return fallbackBorder;
  }

  private String getValidString() {
    return "It is " + (isValid() ? "valid" : "invalid");
  }

  @Override
  public void updateValidationStates() {
    for (Condition condition : conditions) {
      condition.updateState(field.getText());
    }
  }

  @Override
  public boolean isValid() {
    for (Condition condition : conditions) {
      if (!condition.isValid()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public T getField() {
    return field;
  }

  @Override
  public void setErrorMessage(String message) {
    error.setText(message);
  }

  @Override
  public String getErrorMessage() {
    return error.getText();
  }

  @Override
  public boolean getErrorVisible() {
    return error.isVisible();
  }

  @Override
  public void reset() {
    field.setText("");
    applyFormat(true);
  }
}

