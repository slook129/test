package com.blazemeter.jmeter.correlation.gui;

import com.blazemeter.jmeter.correlation.core.CorrelationComponentsRegistry;
import com.blazemeter.jmeter.correlation.core.CorrelationRule;
import com.blazemeter.jmeter.correlation.core.CorrelationRulePartTestElement;
import com.blazemeter.jmeter.correlation.core.ParameterDefinition;
import com.blazemeter.jmeter.correlation.core.extractors.CorrelationExtractor;
import com.blazemeter.jmeter.correlation.core.replacements.CorrelationReplacement;
import com.blazemeter.jmeter.correlation.core.replacements.FunctionCorrelationReplacement;
import com.blazemeter.jmeter.correlation.core.replacements.RegexCorrelationReplacement;
import com.helger.commons.annotation.VisibleForTesting;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.testelement.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CorrelationRuleTestElement extends AbstractTestElement {

  private static final Logger LOG = LoggerFactory.getLogger(CorrelationRuleTestElement.class);
  private static final String REFERENCE_NAME = "CorrelationRule.referenceName";
  private static final String IS_ENABLE = "CorrelationRule.isEnabled";
  private static final String EXTRACTOR_CLASS = "CorrelationRule.CorrelationExtractorClass";
  private static final String REPLACEMENT_CLASS = "CorrelationRule.CorrelationReplacementClass";
  //For backward compatibility
  public static Map<String, String> conversion;

  static {
    conversion = new HashMap<>();
    conversion.put("CorrelationRule.CorrelationExtractor.target", "CorrelationRule.targetField");
    conversion.put("CorrelationRule.CorrelationExtractor.matchNr", "CorrelationRule.matchNumber");
    conversion.put("CorrelationRule.CorrelationExtractor.groupNr", "CorrelationRule.groupNumber");
    conversion.put("CorrelationRule.CorrelationExtractor.regex", "CorrelationRule.extractorRegex");
    conversion
        .put("CorrelationRule.CorrelationReplacement.regex", "CorrelationRule.replacementRegex");
    conversion.put(EXTRACTOR_CLASS, "CorrelationRule.extractorClass");
    conversion.put(REPLACEMENT_CLASS, "CorrelationRule.replacementClass");
  }

  private final CorrelationComponentsRegistry registry;
  private final CorrelationRulePartTestElement<?> extractorDefinitions;
  private final CorrelationRulePartTestElement<?> replacementDefinitions;
  private boolean oldTestPlan;
  private final Function<String, String> propertyProvider = property -> oldTestPlan ?
      conversion.get(property) : property;

  public CorrelationRuleTestElement() {
    this(new CorrelationComponentsRegistry());
  }

  public CorrelationRuleTestElement(
      CorrelationComponentsRegistry registry) {
    this.registry = registry; 
    extractorDefinitions = null;
    replacementDefinitions = null;
  }
  

  @VisibleForTesting
  public CorrelationRuleTestElement(String referenceName,
      CorrelationRulePartTestElement<?> extractor,
      CorrelationRulePartTestElement<?> replacement,
      CorrelationComponentsRegistry registry) {

    setReferenceName(referenceName);
    this.extractorDefinitions = extractor;
    this.replacementDefinitions = replacement;
    this.registry = registry;
    setProperty(REFERENCE_NAME, referenceName);
    setPropertiesForCorrelationPart(extractor);
    setPropertiesForCorrelationPart(replacement);
  }

  private void setPropertiesForCorrelationPart(CorrelationRulePartTestElement<?> part) {
    String type = part instanceof CorrelationReplacement
        ? "CorrelationReplacement" : "CorrelationExtractor";
    setProperty("CorrelationRule." + type + "Class", part.getClass().getName());
    List<ParameterDefinition> definitions = part.getParamsDefinition();
    List<String> params = part.getParams();

    if (params != null && definitions != null) {
      IntStream.range(0, definitions.size())
          .forEach(i -> setProperty(definitions.get(i).getName(), params.get(i)));
    } else {
      LOG.warn("Failed to generate the Correlation rule for {}. Definitions: {}. Params: {}.", type,
          definitions,
          params);
    }
  }

  public String getReferenceName() {
    return getPropertyAsString(REFERENCE_NAME);
  }

  public void setReferenceName(String referenceName) {
    setProperty(new StringProperty(REFERENCE_NAME, referenceName));
  }

  public boolean isRuleEnabled() {
    return getPropertyAsBoolean(IS_ENABLE, true);
  }

  public void setRuleEnable(boolean enable) {
    setProperty(new BooleanProperty(IS_ENABLE, enable));
  }


  public Class<? extends CorrelationExtractor<?>> getExtractorClass() {
    String extractorClass = getPropertyAsString(EXTRACTOR_CLASS);

    if (extractorClass == null || extractorClass.isEmpty()) {
      extractorClass = getPropertyAsString(conversion.get(EXTRACTOR_CLASS));
      if (extractorClass.isEmpty()) {
        return null;
      }
      extractorClass = !extractorClass.contains("CorrelationExtractor") ? extractorClass.replace(
          "Extractor", "CorrelationExtractor") : extractorClass;
      oldTestPlan = true;
    }

    try {
      return (Class<? extends CorrelationExtractor<?>>) Class.forName(extractorClass);
    } catch (ClassNotFoundException e) {
      LOG.error("Unsupported extractor type {}", extractorClass, e);
      return null;
    }
  }

  public void setExtractorClass(Class<? extends CorrelationExtractor<?>> extractorClass) {
    setProperty(EXTRACTOR_CLASS, extractorClass.getCanonicalName());
  }

  public Class<? extends CorrelationReplacement<?>> getReplacementClass() {
    String replacementClass = getPropertyAsString(REPLACEMENT_CLASS);
    //Implemented for backward compatibility with Siebel CRM Recorder
    if (replacementClass == null || replacementClass.isEmpty()) {
      replacementClass = getPropertyAsString(conversion.get(REPLACEMENT_CLASS))
          .replace("Replacement", "CorrelationReplacement");
      if (replacementClass.isEmpty()) {
        return null;
      }
      oldTestPlan = true;
    }

    try {
      return (Class<? extends CorrelationReplacement<?>>) Class.forName(replacementClass);
    } catch (ClassNotFoundException e) {
      LOG.error("Unsupported replacement type {}", replacementClass, e);
      return null;
    }
  }

  public void setReplacementClass(Class<? extends CorrelationReplacement<?>> replacementClass) {
    setProperty(REPLACEMENT_CLASS, replacementClass.getCanonicalName());
  }

  public CorrelationExtractor<?> getCorrelationExtractor()
      throws InvalidRulePartElementException {
    Class<? extends CorrelationExtractor<?>> extractorClass = getExtractorClass();
    //Only applies when no Extractor is selected
    if (extractorClass == null) {
      return null;
    }

    CorrelationExtractor<?> correlationExtractor = registry.getCorrelationExtractor(extractorClass);

    correlationExtractor.setParams((correlationExtractor.getParamsDefinition()).stream()
        .map(this::getStoredValueFromParam)
        .collect(Collectors.toList()));
    return correlationExtractor;
  }

  private String getStoredValueFromParam(ParameterDefinition parameter) {
    return getPropertyAsString(propertyProvider.apply(parameter.getName()),
        parameter.getDefaultValue());
  }

  public CorrelationReplacement<?> getCorrelationReplacement()
      throws InvalidRulePartElementException {

    Class<? extends CorrelationReplacement<?>> replacement = getReplacementClass();
    //Only applies when no Replacement is selected
    if (replacement == null) {
      return null;
    }

    CorrelationReplacement<?> correlationReplacement = registry
        .getCorrelationReplacement(replacement);

    correlationReplacement.setParams(correlationReplacement.getParamsDefinition().stream()
        .map(this::getStoredValueFromParam)
        .collect(Collectors.toList()));

    return correlationReplacement;
  }

  public CorrelationRulePartTestElement<?> getExtractorRulePart() {
    Class<? extends CorrelationExtractor<?>> extractorClass = getExtractorClass();
    if (extractorClass == null) {
      return CorrelationComponentsRegistry.NONE_EXTRACTOR;
    }

    String canonicalName = extractorClass.getCanonicalName();
    return canonicalName.isEmpty() ? CorrelationComponentsRegistry.NONE_EXTRACTOR :
        getCorrelationRulePart(canonicalName);
  }

  private CorrelationRulePartTestElement<?> getCorrelationRulePart(String correlationClassName) {
    CorrelationRulePartTestElement<?> correlationInstance = registry
        .getCorrelationRulePartTestElement(correlationClassName);

    correlationInstance
        .setParams(correlationInstance.getParamsDefinition().stream()
            .map(this::getStoredValueFromParam)
            .collect(Collectors.toList()));
    return correlationInstance;
  }

  public CorrelationRulePartTestElement<?> getReplacementRulePart() {
    Class<? extends CorrelationReplacement<?>> replacementClass = getReplacementClass();
    if (replacementClass == null) {
      return CorrelationComponentsRegistry.NONE_REPLACEMENT;
    }

    String canonicalName = replacementClass.getCanonicalName();
    return canonicalName.isEmpty() ? CorrelationComponentsRegistry.NONE_REPLACEMENT :
        getCorrelationRulePart(canonicalName);
  }

  @Override
  public String toString() {
    return "CorrelationRuleTestElement{" +
        "refVariable=" + getReferenceName() +
        ", enabled=" + isRuleEnabled() +
        ", extractor=" + getExtractorRulePart() +
        ", replacement=" + getReplacementRulePart() +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    CorrelationRuleTestElement that = (CorrelationRuleTestElement) o;
    return getExtractorRulePart().equals(that.getExtractorRulePart())
        && getReplacementRulePart().equals(that.getReplacementRulePart())
        && isRuleEnabled() == that.isRuleEnabled()
        && getReferenceName().equals(that.getReferenceName());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        extractorDefinitions,
        replacementDefinitions,
        oldTestPlan,
        propertyProvider);
  }

  public CorrelationRuleTestElement ensureBackwardCompatibility() {
    Class<? extends CorrelationReplacement<?>> clazz = getReplacementClass();
    if (clazz != null && clazz.equals(FunctionCorrelationReplacement.class)) {
      RegexCorrelationReplacement<?> compatibleReplacement =
          ((FunctionCorrelationReplacement<?>) getReplacementRulePart())
              /* consider that the refVarName of a FunctionCorrelationReplacement is the 
              expression to evaluate */
              .translateToRegexReplacement(getReferenceName());
      CorrelationRule correlationRule = new CorrelationRule();
      correlationRule.setEnabled(true);
      correlationRule.setCorrelationReplacement(compatibleReplacement);
      try {
        correlationRule.setCorrelationExtractor(getCorrelationExtractor());
      } catch (InvalidRulePartElementException e) {
        LOG.warn("Error while processing CorrelationExtractor=({}). CorrelationExtractor might "
                + "be None, if this is considered an error, please contact support.",
            this.getExtractorClass(), e);
      }
      return correlationRule.buildTestElement(registry);
    }
    return this;
  }
}
