/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.teamcity.caldav.data;

import jetbrains.buildServer.buildTriggers.scheduler.CronExpression;
import jetbrains.buildServer.buildTriggers.scheduler.CronFieldInfo;
import jetbrains.buildServer.buildTriggers.scheduler.CronParseException;
import jetbrains.buildServer.buildTriggers.scheduler.SchedulerBuildTriggerService;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.RRule;
import org.jetbrains.annotations.NotNull;
import org.osaf.cosmo.calendar.util.Dates;

import javax.annotation.Nullable;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CronExpressionUtil {

  public static final String HOUR = "hour";
  public static final String MINUTE = "minute";
  private static final String PROP_TIMEZONE = "timezone";
  private static final String DEFAULT_TIMEZONE = "SERVER";
  public static final Dur DURATION = new Dur(0, 0, 5, 0);
  public static final Calendar UNTIL = Calendar.getInstance();

  static {
    UNTIL.add(Calendar.YEAR, 1);
  }

  public static final String WEEKLY = "weekly";
  public static final String DAILY = "daily";


  public static void apply(@NotNull Map<String, String> properties,
                           @NotNull VEvent event) throws CronParseException {

    String schedulingPolicy = properties.get(SchedulerBuildTriggerService.PROP_SCHEDULING_POLICY);

    CronExpression cronExpression = getCronExpression(properties);

    String year = cronExpression.getExpressionSplitted().get(CronFieldInfo.YEAR);
    String month = cronExpression.getExpressionSplitted().get(CronFieldInfo.MONTH);
    String dm = cronExpression.getExpressionSplitted().get(CronFieldInfo.DM);
    String dw = cronExpression.getExpressionSplitted().get(CronFieldInfo.DW);
    String hour = cronExpression.getExpressionSplitted().get(CronFieldInfo.HOUR);
    String min = cronExpression.getExpressionSplitted().get(CronFieldInfo.MIN);
    String sec = cronExpression.getExpressionSplitted().get(CronFieldInfo.SEC);

    String frequency;
    if (WEEKLY.equalsIgnoreCase(schedulingPolicy)) {
      frequency = Recur.WEEKLY;
      hour = properties.get(HOUR);
      min = properties.get("minute");
    } else if (DAILY.equalsIgnoreCase(schedulingPolicy)) {
      frequency = Recur.DAILY;
      hour = properties.get(HOUR);
      min = properties.get(MINUTE);
    } else {
      throw new UnsupportedOperationException("Unsupported scheduling policy: " + schedulingPolicy);
    }


    Recur recur = new Recur(frequency, Dates.getInstance(UNTIL.getTime(), new net.fortuna.ical4j.model.Date()));
    RRule rrule = new RRule(recur);

    event.getProperties().add(rrule);

    java.util.TimeZone timeZone = cronExpression.getTimeZone() != null ? TimeZone.getTimeZone(cronExpression.getTimeZone())
            : java.util.TimeZone.getDefault();

    Calendar scheduledStart = Calendar.getInstance(timeZone);
    scheduledStart.set(Calendar.MINUTE, 0);
    scheduledStart.set(Calendar.HOUR_OF_DAY, 0);
    scheduledStart.set(Calendar.SECOND, 0);
    scheduledStart.set(Calendar.MILLISECOND, 0);

    if (isNumber(sec)) {
      scheduledStart.set(Calendar.SECOND, Integer.valueOf(sec));
    }
    if (isNumber(min)) {
      scheduledStart.set(Calendar.MINUTE, Integer.valueOf(min));
    }
    if (isNumber(hour)) {
      scheduledStart.set(Calendar.HOUR_OF_DAY, Integer.valueOf(hour));
    }
    if (isNumber(dw)) {
      scheduledStart.set(Calendar.DAY_OF_WEEK, Integer.valueOf(dw));
    }
    if (isNumber((dm))) {
      scheduledStart.set(Calendar.DAY_OF_MONTH, Integer.valueOf(dm));
    }
    if (isNumber(month)) {
      scheduledStart.set(Calendar.MONTH, Integer.valueOf(month));
    }
    if (isNumber(year)) {
      scheduledStart.set(Calendar.YEAR, Integer.valueOf(month));
    }

    event.getProperties().add(new DtStart(new DateTime(scheduledStart.getTime())));
    event.getProperties().add(new Duration(DURATION));
  }


  private static boolean isNumber(@Nullable String value) {
    return value != null && !value.equals("*") && !value.equals("?") && !value.contains("/");
  }

  /**
   * triggerBuildWithPendingChangesOnly -> true
   * schedulingPolicy -> daily
   * <p/>
   * cronExpression_dm -> *
   * cronExpression_year -> *
   * cronExpression_sec -> 0
   * hour -> 12
   * cronExpression_hour -> *
   * cronExpression_month -> *
   * cronExpression_dw -> ?
   * cronExpression_min -> 0
   * minute -> 0
   * timezone -> SERVER
   * schedulingPolicy -> daily
   * <p/>
   * dayOfWeek -> Sunday
   */
  private static CronExpression getCronExpression(final Map<String, String> properties) throws CronParseException {
    return CronExpression.createCronExpression(extractCronParameters(properties));
  }

  private static Map<String, String> extractCronParameters(final Map<String, String> triggerParams) {
    Map<String, String> res = new HashMap<String, String>();
    for (CronFieldInfo info : CronFieldInfo.values()) {
      final String value = triggerParams.get(SchedulerBuildTriggerService.CRON_EXPRESSION_PARAM_PREFIX + info.getKey());
      res.put(info.getKey(), value);
    }
    final String timezone = triggerParams.get(PROP_TIMEZONE);
    if (!DEFAULT_TIMEZONE.equals(timezone)) {
      res.put(PROP_TIMEZONE, timezone);
    }
    return res;
  }

  public static void apply(@Nullable Date startDate, @Nullable Date finishDate,
                           @Nullable java.util.TimeZone timeZone, @NotNull VEvent event) {
    if (startDate == null || finishDate == null) {
      return;
    }
    event.getProperties().add(new DtStart(new DateTime(startDate)));
    event.getProperties().add(new DtEnd(new DateTime(finishDate)));
  }


}
