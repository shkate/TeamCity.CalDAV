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


import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.scheduler.CronExpression;
import jetbrains.buildServer.buildTriggers.scheduler.CronParseException;
import jetbrains.buildServer.buildTriggers.scheduler.SchedulerBuildTriggerService;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Uid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osaf.cosmo.calendar.ICalendarUtils;
import org.osaf.cosmo.calendar.util.Dates;
import org.teamcity.caldav.request.Constants;

import java.util.*;

public class ScheduledCalendarProvider {

  private BuildTypesProvider buildTypesProvider;

  public ScheduledCalendarProvider(@NotNull BuildTypesProvider buildTypesProvider) {
    this.buildTypesProvider = buildTypesProvider;
  }

  @Nullable
  public net.fortuna.ical4j.model.Calendar getCalendar(@Nullable SProject project) throws CronParseException {
    Collection<VEvent> events = getScheduledEvents(project);
    if (events.isEmpty()) {
      return null;
    }

    net.fortuna.ical4j.model.Calendar cal = ICalendarUtils.createBaseCalendar(project != null ?
            String.format(Constants.PROJECT_CALENDAR_UID, project.getName()) : Constants.CALENDAR_UID);
    for (VEvent event : events) {
      cal.getComponents().add(event);
    }
    return cal;
  }


  @Nullable
  public net.fortuna.ical4j.model.Calendar getCalendarResolved(@Nullable SProject project) throws CronParseException {
    Collection<VEvent> events = getScheduledEvents(project);
    if (events.isEmpty()) {
      return null;
    }

    net.fortuna.ical4j.model.Calendar cal = ICalendarUtils.createBaseCalendar(project != null ?
            String.format(Constants.PROJECT_CALENDAR_UID, project.getName()) : Constants.CALENDAR_UID);
    for (VEvent event : events) {
      PeriodList consumedTime = event.getConsumedTime(Dates.getInstance(new java.util.Date(), new Date()),
              Dates.getInstance(CronExpressionUtil.UNTIL.getTime(), new Date()));
      int i = 0;
      for (Iterator iterator = consumedTime.iterator(); iterator.hasNext(); ) {
        VEvent rEvent = new VEvent();
        rEvent.getProperties().add(new Uid("x" + (i++)));
        ICalendarUtils.setSummary(ICalendarUtils.getXProperty("summary", event), rEvent);
        ICalendarUtils.setDuration(rEvent, CronExpressionUtil.DURATION);
        Object next = iterator.next();
        rEvent.getProperties().add(new DtStart(((Period) next).getStart()));
        cal.getComponents().add(rEvent);
      }
    }
    return cal;
  }


  @NotNull
  public List<VEvent> getScheduledEvents(@Nullable SProject project) throws CronParseException {
    List<SBuildType> buildTypes = project == null ?
            buildTypesProvider.getActiveBuildTypes() :
            buildTypesProvider.getProjectBuildTypes(project);

    List<VEvent> events = new ArrayList<VEvent>(buildTypes.size());
    int i = 0;
    for (SBuildType type : buildTypes) {
      Collection<BuildTriggerDescriptor> triggers = type.getBuildTriggersCollection();
      for (BuildTriggerDescriptor trigger : triggers) {
        if (SchedulerBuildTriggerService.TRIGGER_NAME.equals(trigger.getTriggerName())) {
          VEvent event = createScheduledEvent(trigger, type, i++);
          events.add(event);
        }
      }
    }
    return events;
  }

  private VEvent createScheduledEvent(@NotNull BuildTriggerDescriptor trigger, @NotNull SBuildType type, int i) throws CronParseException {
    VEvent event = new VEvent();
    event.getProperties().add(new Uid("" + i));
    Map<String, String> properties = trigger.getProperties();
    CronExpressionUtil.apply(properties, event);
    ICalendarUtils.setSummary(String.format(Constants.VEVENT_SCHEDULED_SUMMARY, type.getExtendedName()), event);
    ICalendarUtils.setDescription(String.format(Constants.VEVENT_SCHEDULED_DESCRIPTION, type.getCompatibleAgents().size()), event);
    return event;
  }


}
