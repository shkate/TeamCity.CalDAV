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

package org.teamcity.caldav.ui;


import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.project.ProjectTab;
import net.fortuna.ical4j.model.component.VEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.teamcity.caldav.data.HistoryCalendarDataProvider;
import org.teamcity.caldav.data.ScheduledCalendarProvider;
import org.teamcity.caldav.request.Constants;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Map;

public class SchedulerTab extends ProjectTab {


  @NotNull
  private final ScheduledCalendarProvider sbProvider;
  @NotNull
  private final HistoryCalendarDataProvider hProvider;

  public SchedulerTab(@NotNull PagePlaces pagePlaces,
                      @NotNull ProjectManager projectManager,
                      @NotNull PluginDescriptor descriptor,
                      @NotNull ScheduledCalendarProvider sbProvider,
                      @NotNull HistoryCalendarDataProvider hProvider) {
    super("scheduler", "Scheduled Builds", pagePlaces, projectManager, descriptor.getPluginResourcesPath("calendars.jsp"));
    // add your CSS/JS here
    this.sbProvider = sbProvider;
    this.hProvider = hProvider;

  }

  @Override
  protected void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request,
                           @NotNull SProject project, @Nullable SUser user) {

    try {
      Collection<VEvent> events = sbProvider.getScheduledEvents(project);
      if (!events.isEmpty()) {
        model.put("scheduledBuildsCount", events.size());
        model.put("scheduledBuildsDownloadLink", Constants.CALDAV_URL + "?project=" + project.getProjectId() + "&type=ics");
      }
      events = hProvider.getHistoryEvents(project);
      if (!events.isEmpty()) {
        model.put("historyBuildsCount", events.size());
        model.put("historyBuildsDownloadLink", Constants.CALDAV_URL + "?project=" + project.getProjectId() + "&history=true&type=ics");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
