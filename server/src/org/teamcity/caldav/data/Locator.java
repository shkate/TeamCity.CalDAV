package org.teamcity.caldav.data;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.MultiValuesMap;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.teamcity.caldav.errors.LocatorProcessException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class that support parsing of "locators".
 * Locator is a string with single value or several named "dimensions".
 * Example:
 * <tt>31</tt> - locator wth single value "31"
 * <tt>name:Frodo</tt> - locator wth single dimension "name" which has value "Frodo"
 * <tt>name:Frodo,age:14</tt> - locator with two dimensions "name" which has value "Frodo" and "age", which has value "14"
 * <tt>text:(Freaking symbols:,name)</tt> - locator with single dimension "text" which has value "Freaking symbols:,name"
 * <p/>
 * Dimension name should be is alpha-numeric. Dimension value should not contain symbol "," if not enclosed in "(" and ")" or
 * should not contain symbol ")" (if enclosed in "(" and ")")
 *
 * @author Yegor.Yarko
 *         Date: 13.08.2010
 */
public class Locator {
  private static final Logger LOG = Logger.getInstance(Locator.class.getName());
  private static final String DIMENSION_NAME_VALUE_DELIMITER = ":";
  private static final String DIMENSIONS_DELIMITER = ",";
  private static final String DIMENSION_COMPLEX_VALUE_START_DELIMITER = "(";
  private static final String DIMENSION_COMPLEX_VALUE_END_DELIMITER = ")";
  public static final String LOCATOR_SINGLE_VALUE_UNUSED_NAME = "single value";

  private final String myRawValue;
  private boolean modified = false;
  private final MultiValuesMap<String, String> myDimensions;
  private final String mySingleValue;

  @NotNull
  private final Set<String> myUsedDimensions = new HashSet<String>();
  @Nullable
  private String[] mySupportedDimensions;
  @NotNull
  private final Collection<String> myIgnoreUnusedDimensions = new HashSet<String>();
  @NotNull
  private final Collection<String> myHddenSupportedDimensions = new HashSet<String>();

  public Locator(@Nullable final String locator) throws LocatorProcessException {
    this(locator, null);
  }

  /**
   * @param locator
   * @param supportedDimensions dimensions supported in this locator, used in {@link #checkLocatorFullyProcessed()}
   * @throws LocatorProcessException
   */
  public Locator(@Nullable final String locator, final String... supportedDimensions) throws LocatorProcessException {
    myRawValue = locator;
    if (StringUtil.isEmpty(locator)) {
      throw new LocatorProcessException("Invalid locator. Cannot be empty.");
    }
    @SuppressWarnings("ConstantConditions") final boolean hasDimensions = locator.contains(DIMENSION_NAME_VALUE_DELIMITER);
    if (!hasDimensions) {
      mySingleValue = locator;
      myDimensions = new MultiValuesMap<String, String>();
    } else {
      mySingleValue = null;
      myDimensions = parse(locator);
    }
    mySupportedDimensions = supportedDimensions;
  }

  /**
   * Creates an empty locator with dimensions.
   */
  private Locator() {
    myRawValue = "";
    mySingleValue = null;
    myDimensions = new MultiValuesMap<String, String>();
    mySupportedDimensions = null;
  }

  public static Locator createEmptyLocator(final String... supportedDimensions) {
    final Locator result = new Locator();
    result.mySupportedDimensions = supportedDimensions;
    return result;
  }

  public boolean isEmpty() {
    return mySingleValue == null && myDimensions.isEmpty();
  }

  public void addIgnoreUnusedDimensions(final String... ignoreUnusedDimensions) {
    myIgnoreUnusedDimensions.addAll(Arrays.asList(ignoreUnusedDimensions));
  }

  /**
   * Sets dimensions which will not be reported by checkLocatorFullyProcessed method as used but not declared
   *
   * @param hiddenDimensions
   */
  public void addHiddenDimensions(final String... hiddenDimensions) {
    myHddenSupportedDimensions.addAll(Arrays.asList(hiddenDimensions));
  }

  private static MultiValuesMap<String, String> parse(final String locator) {
    MultiValuesMap<String, String> result = new MultiValuesMap<String, String>();
    String currentDimensionName;
    String currentDimensionValue;
    int parsedIndex = 0;
    while (parsedIndex < locator.length()) {
      int nameEnd = locator.indexOf(DIMENSION_NAME_VALUE_DELIMITER, parsedIndex);
      if (nameEnd == parsedIndex || nameEnd == -1) {
        throw new LocatorProcessException(locator, parsedIndex, "Could not find '" + DIMENSION_NAME_VALUE_DELIMITER + "'");
      }
      currentDimensionName = locator.substring(parsedIndex, nameEnd);
      if (!isValidName(currentDimensionName)) {
        throw new LocatorProcessException(locator, parsedIndex, "Invalid dimension name :'" + currentDimensionName + "'. Should contain only alpha-numeric symbols");
      }
      final String valueAndRest = locator.substring(nameEnd + DIMENSION_NAME_VALUE_DELIMITER.length());
      if (valueAndRest.startsWith(DIMENSION_COMPLEX_VALUE_START_DELIMITER)) {
        //complex value detected
        final int complexValueEnd = findMatchingEndDelimeterIndex(valueAndRest);
        if (complexValueEnd == -1) {
          throw new LocatorProcessException(locator, nameEnd + DIMENSION_NAME_VALUE_DELIMITER.length() + DIMENSION_COMPLEX_VALUE_START_DELIMITER.length(),
                  "Could not find matching '" + DIMENSION_COMPLEX_VALUE_END_DELIMITER + "'");
        }
        currentDimensionValue = valueAndRest.substring(DIMENSION_COMPLEX_VALUE_START_DELIMITER.length(), complexValueEnd);
        parsedIndex = nameEnd + DIMENSION_NAME_VALUE_DELIMITER.length() + complexValueEnd + DIMENSION_COMPLEX_VALUE_END_DELIMITER.length();
        if (parsedIndex != locator.length()) {
          if (!locator.startsWith(DIMENSIONS_DELIMITER, parsedIndex)) {
            throw new LocatorProcessException(locator, parsedIndex, "No dimensions delimiter '" + DIMENSIONS_DELIMITER + "' after complex value");
          } else {
            parsedIndex += DIMENSIONS_DELIMITER.length();
          }
        }
      } else {
        int valueEnd = valueAndRest.indexOf(DIMENSIONS_DELIMITER);
        if (valueEnd == -1) {
          currentDimensionValue = valueAndRest;
          parsedIndex = locator.length();
        } else {
          currentDimensionValue = valueAndRest.substring(0, valueEnd);
          parsedIndex = nameEnd + DIMENSION_NAME_VALUE_DELIMITER.length() + valueEnd + DIMENSIONS_DELIMITER.length();
        }
      }
      result.put(currentDimensionName, currentDimensionValue);
    }

    return result;
  }

  private static int findMatchingEndDelimeterIndex(final String valueAndRest) {
    int pos = DIMENSION_COMPLEX_VALUE_START_DELIMITER.length();
    int nesting = 1;
    while (nesting != 0) {
      final int endDelimeterPosition = valueAndRest.indexOf(DIMENSION_COMPLEX_VALUE_END_DELIMITER, pos);
      final int startDelimeterPosition = valueAndRest.indexOf(DIMENSION_COMPLEX_VALUE_START_DELIMITER, pos);
      if (endDelimeterPosition == -1) {
        return -1;
      }
      if (startDelimeterPosition == -1 || endDelimeterPosition < startDelimeterPosition) {
        nesting--;
        pos = endDelimeterPosition + DIMENSION_COMPLEX_VALUE_END_DELIMITER.length();
      } else if (endDelimeterPosition > startDelimeterPosition) {
        nesting++;
        pos = startDelimeterPosition + DIMENSION_COMPLEX_VALUE_START_DELIMITER.length();
      }
    }
    return pos - DIMENSION_COMPLEX_VALUE_END_DELIMITER.length();
  }

  private static boolean isValidName(final String name) {
    for (int i = 0; i < name.length(); i++) {
      if (!Character.isLetter(name.charAt(i)) && !Character.isDigit(name.charAt(i))) return false;
    }
    return true;
  }

  //todo: use this whenever possible
  public void checkLocatorFullyProcessed() {
    String reportKindString = TeamCityProperties.getProperty("rest.report.unused.locator", "error");
    if (!TeamCityProperties.getBooleanOrTrue("rest.report.locator.errors")) {
      reportKindString = "off";
    }
    if (!reportKindString.equals("off")) {
      if (reportKindString.contains("reportKnownButNotReportedDimensions")) {
        reportKnownButNotReportedDimensions();
      }
      final Set<String> unusedDimensions = getUnusedDimensions();
      unusedDimensions.removeAll(myIgnoreUnusedDimensions);
      if (unusedDimensions.size() > 0) {
        String message;
        if (unusedDimensions.size() > 1) {
          message = "Locator dimensions " + unusedDimensions + " are ignored or unknown.";
        } else {
          if (!unusedDimensions.contains(LOCATOR_SINGLE_VALUE_UNUSED_NAME)) {
            message = "Locator dimension " + unusedDimensions + " is ignored or unknown.";
          } else {
            message = "Single value locator is not supported here.";
          }
        }
        if (mySupportedDimensions != null && mySupportedDimensions.length > 0)
          message += " Supported dimensions are: " + Arrays.toString(mySupportedDimensions);
        if (reportKindString.contains("log")) {
          if (reportKindString.contains("log-warn")) {
            LOG.warn(message);
          } else {
            LOG.debug(message);
          }
        }
        if (reportKindString.contains("error")) {
          throw new LocatorProcessException(message);
        }
      }
    }
  }

  private void reportKnownButNotReportedDimensions() {
    final Set<String> usedDimensions = new HashSet<String>(myUsedDimensions);
    if (mySupportedDimensions != null) usedDimensions.removeAll(Arrays.asList(mySupportedDimensions));
    usedDimensions.removeAll(myHddenSupportedDimensions);
    if (usedDimensions.size() > 0) {
      //found used dimensions which are not declared as used.

      //noinspection ThrowableInstanceNeverThrown
      final Exception exception = new Exception("Helper exception to get stacktrace");
      LOG.info("Locator dimensions " + usedDimensions + " are actually used but not declared as such in the message to the user (" +
              Arrays.toString(mySupportedDimensions) + ").", exception);
    }
  }

  public boolean isSingleValue() {
    return mySingleValue != null;
  }

  /**
   * @return locator's not-null value if it is single-value locator, 'null' otherwise
   */
  @Nullable
  public String getSingleValue() {
    myUsedDimensions.add(LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    return mySingleValue;
  }

  @Nullable
  public Long getSingleValueAsLong() {
    final String singleValue = getSingleValue();
    if (singleValue == null) {
      return null;
    }
    try {
      return Long.parseLong(singleValue);
    } catch (NumberFormatException e) {
      throw new LocatorProcessException("Invalid single value: '" + singleValue + "'. Should be a number.");
    }
  }

  @Nullable
  public Long getSingleDimensionValueAsLong(@NotNull final String dimensionName) {
    final String value = getSingleDimensionValue(dimensionName);
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new LocatorProcessException("Invalid value of dimension '" + dimensionName + "': '" + value + "'. Should be a number.");
    }
  }

  @Nullable
  public Boolean getSingleDimensionValueAsBoolean(@NotNull final String dimensionName) {
    final String value = getSingleDimensionValue(dimensionName);
    if (value == null || "all".equalsIgnoreCase(value) || "any".equalsIgnoreCase(value)) {
      return null;
    }
    if ("true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "in".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "out".equalsIgnoreCase(value)) {
      return false;
    }
    throw new LocatorProcessException("Invalid value of dimension '" + dimensionName + "': '" + value + "'. Should be 'true', 'false' or 'any'.");
  }

  /**
   * @param dimensionName name of the dimension
   * @param defaultValue  default value to use if no dimension with the name is found
   * @return value specified by the dimension with name "dimensionName" (one of the possible values can be "null") or
   * "defaultValue" if such dimension is not present
   */
  @Nullable
  public Boolean getSingleDimensionValueAsBoolean(@NotNull final String dimensionName, @Nullable Boolean defaultValue) {
    final String value = getSingleDimensionValue(dimensionName);
    if (value == null) {
      return defaultValue;
    }
    return getSingleDimensionValueAsBoolean(dimensionName);
  }

  /**
   * Extracts the single dimension value from dimensions.
   *
   * @param dimensionName the name of the dimension to extract value.   @return 'null' if no such dimension is found, value of the dimension otherwise.
   * @throws LocatorProcessException if there are more then a single dimension definition for a 'dimensionName' name or the dimension has no value specified.
   */
  @Nullable
  public String getSingleDimensionValue(@NotNull final String dimensionName) {
    myUsedDimensions.add(dimensionName);
    Collection<String> idDimension = myDimensions.get(dimensionName);
    if (idDimension == null || idDimension.size() == 0) {
      return null;
    }
    if (idDimension.size() > 1) {
      throw new LocatorProcessException("Only single '" + dimensionName + "' dimension is supported in locator. Found: " + idDimension);
    }
    return idDimension.iterator().next();
  }

  public int getDimensionsCount() {
    return myDimensions.keySet().size();
  }

  /**
   * Replaces all the dimnsions values to the one specified.
   * Should be used only for multi-dimension locators.
   *
   * @param name  name of the dimension
   * @param value value of the dimension
   */
  public void setDimension(@NotNull final String name, @NotNull final String value) {
    if (isSingleValue()) {
      throw new LocatorProcessException("Attempt to set dimension '" + name + "' for single value locator.");
    }
    myDimensions.removeAll(name);
    myDimensions.put(name, value);
    myUsedDimensions.remove(name);
    modified = true; // todo: use setDimension to replace the dimension in myRawValue
  }

  /**
   * Removes the dimension from the loctor. If no other dimensions are present does nothing and returns false.
   * Should be used only for multi-dimension locators.
   *
   * @param name name of the dimension
   */
  public boolean removeDimension(@NotNull final String name) {
    if (isSingleValue()) {
      throw new LocatorProcessException("Attemt to remove dimension '" + name + "' for single value locator.");
    }
    boolean result = myDimensions.get(name) != null;
    myDimensions.removeAll(name);
    modified = true; // todo: use setDimension to replace the dimension in myRawValue
    return result;
  }

  /**
   * Provides the names of dimensions whose values were never retrieved
   *
   * @return names of the dimensions not yet queried
   */
  @NotNull
  public Set<String> getUnusedDimensions() {
    Set<String> result;
    if (isSingleValue()) {
      result = new HashSet<String>(Collections.singleton(LOCATOR_SINGLE_VALUE_UNUSED_NAME));
    } else {
      result = new HashSet<String>(myDimensions.keySet());
    }
    result.removeAll(myUsedDimensions);
    return result;
  }

  /**
   * Returns a locator based on the supplied one replacing the numeric value of the dimention specified with the passed number.
   * The structure of the returned locator might be diffeent from the passed one, while the same dimensions and values are present.
   *
   * @param locator       existing locator, should be valid!
   * @param dimensionName only alpha-numeric characters are supported! Only numeric vaues withour brackets are supported!
   * @param value         new value for the dimention, only alpha-numeric characters are supported!
   * @return
   */
  public static String setDimension(@NotNull final String locator, @NotNull final String dimensionName, final long value) {
    final Matcher matcher = Pattern.compile(dimensionName + DIMENSION_NAME_VALUE_DELIMITER + "\\d+").matcher(locator);
    String result = matcher.replaceFirst(dimensionName + DIMENSION_NAME_VALUE_DELIMITER + Long.toString(value));
    try {
      matcher.end();
    } catch (IllegalStateException e) {
      final Locator actualLocator = new Locator(locator);
      actualLocator.setDimension(dimensionName, String.valueOf(value));
      result = actualLocator.getStringRepresentation();
    }
    return result;
  }

  public String getStringRepresentation() {
    if (mySingleValue != null) {
      return mySingleValue;
    }
    if (!modified) {
      return myRawValue;
    }
    String result = "";
    for (Map.Entry<String, Collection<String>> dimensionEntries : myDimensions.entrySet()) {
      for (String value : dimensionEntries.getValue()) {
        if (!StringUtil.isEmpty(result)) {
          result += DIMENSIONS_DELIMITER;
        }
        result += dimensionEntries.getKey() + DIMENSION_NAME_VALUE_DELIMITER + getValueForRendering(value);
      }
    }
    return result;
  }

  private String getValueForRendering(final String value) {
    if (value.contains(DIMENSIONS_DELIMITER) || value.contains(DIMENSION_NAME_VALUE_DELIMITER))
      return DIMENSION_COMPLEX_VALUE_START_DELIMITER + value + DIMENSION_COMPLEX_VALUE_END_DELIMITER;
    return value;
  }
}
