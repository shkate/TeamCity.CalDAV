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


import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SProject;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Uid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osaf.cosmo.calendar.ICalendarUtils;
import org.teamcity.caldav.request.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HistoryCalendarDataProvider {

  @NotNull
  private final HistoryProvider historyProvider;
  @NotNull
  private final BuildTypesProvider buildTypesProvider;


  public HistoryCalendarDataProvider(@NotNull BuildTypesProvider buildTypesProvider,
                                     @NotNull HistoryProvider historyProvider) {
    this.historyProvider = historyProvider;
    this.buildTypesProvider = buildTypesProvider;
  }


  @Nullable
  public net.fortuna.ical4j.model.Calendar getBuildHistoryCalendar(@Nullable SProject project) {
    Collection<VEvent> historyEvents = getHistoryEvents(project);
    if (historyEvents.isEmpty()) {
      return null;
    }
    net.fortuna.ical4j.model.Calendar cal = ICalendarUtils.createBaseCalendar(project != null ?
            String.format(Constants.PROJECT_BUILD_HISTORY_CALENDAR_UID, project.getName()) : Constants.PROJECT_BUILD_HISTORY_CALENDAR_UID);
    for (VEvent event : historyEvents) {
      cal.getComponents().add(event);
    }
    return cal;
  }


  @NotNull
  public Collection<VEvent> getHistoryEvents(@Nullable SProject project) {
    List<SBuildType> buildTypes = project == null ? buildTypesProvider.getActiveBuildTypes() : buildTypesProvider.getProjectBuildTypes(project);
    List<VEvent> events = new ArrayList<VEvent>(buildTypes.size());
    int i = 0;
    for (SBuildType type : buildTypes) {
      if (project != null && !type.getProject().equals(project)) {
        continue;
      }
      List<SFinishedBuild> builds = historyProvider.getFinishedBuilds(type);
      if (builds == null) {
        continue;
      }
      for (SBuild build : builds) {
        VEvent event = createHistoryEvent(build, type, i++);
        events.add(event);
      }
    }

    return events;
  }


  private VEvent createHistoryEvent(@NotNull SBuild build, @NotNull SBuildType type, int i) {
    VEvent event = new VEvent();
    event.getProperties().add(new Uid("" + i));
    CronExpressionUtil.apply(build.getClientStartDate(), build.getFinishDate(), build.getClientTimeZone(), event);
    ICalendarUtils.setSummary(String.format(Constants.VEVENT_HISTORY_SUMMARY, type.getExtendedName()), event);
    ICalendarUtils.setDescription(String.format(Constants.VEVENT_HISTORY_DESCRIPTION, build.getAgent().getName()), event);
    return event;
  }


}
