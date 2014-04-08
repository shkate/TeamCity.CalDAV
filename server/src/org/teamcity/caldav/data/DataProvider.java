/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import edu.emory.mathcs.backport.java.util.Collections;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.BuildTriggerService;
import jetbrains.buildServer.buildTriggers.scheduler.CronParseException;
import jetbrains.buildServer.buildTriggers.scheduler.SchedulerBuildTriggerService;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.teamcity.caldav.errors.AuthorizationFailedException;
import org.teamcity.caldav.request.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DataProvider {


  private static final Logger LOG = Logger.getInstance(DataProvider.class.getName());

  @NotNull
  private final SBuildServer server;
  @NotNull
  private final SecurityContext securityContext;
  @NotNull
  private final ServiceLocator serviceLocator;

  public DataProvider(@NotNull final SBuildServer myServer, @NotNull ServiceLocator serviceLocator,
                      final @NotNull SecurityContext securityContext) {
    this.server = myServer;
    this.securityContext = securityContext;
    this.serviceLocator = serviceLocator;
  }

  @NotNull
  public SBuildServer getServer() {
    return server;
  }

  @NotNull
  public net.fortuna.ical4j.model.Calendar getCalendar() throws CronParseException {
    net.fortuna.ical4j.model.Calendar cal = new net.fortuna.ical4j.model.Calendar();
    for (VEvent event : getScheduledEvents()) {
      cal.getComponents().add(event);
    }
    cal.getProperties().add(new ProdId(Constants.CALENDAR_PRODUCT));
    cal.getProperties().add(Version.VERSION_2_0);
    cal.getProperties().add(CalScale.GREGORIAN);
    return cal;
  }

  @NotNull
  public net.fortuna.ical4j.model.Calendar getBuildHistoryCalendar() {
    net.fortuna.ical4j.model.Calendar cal = new net.fortuna.ical4j.model.Calendar();
    for (VEvent event : getHistoryEvents()) {
      cal.getComponents().add(event);
    }
    cal.getProperties().add(new ProdId(Constants.BUILD_HISTORY_CALENDAR_PRODUCT));
    cal.getProperties().add(Version.VERSION_2_0);
    cal.getProperties().add(CalScale.GREGORIAN);
    return cal;
  }

  @NotNull
  private Collection<VEvent> getScheduledEvents() throws CronParseException {
    List<SBuildType> buildTypes = server.getProjectManager().getActiveBuildTypes();
    List<VEvent> events = new ArrayList<VEvent>(buildTypes.size());
    int i = 0;
    for (SBuildType type : buildTypes) {
      Collection<BuildTriggerDescriptor> triggers = type.getBuildTriggersCollection();
      for (BuildTriggerDescriptor trigger : triggers) {
        BuildTriggerService triggerService = trigger.getBuildTriggerService();
        if (triggerService instanceof SchedulerBuildTriggerService) {
          VEvent event = createEvent(trigger, type, i++);
          events.add(event);
        }
      }
    }
    return events;
  }

  @NotNull
  private Collection<VEvent> getHistoryEvents()  {
    List<SBuildType> buildTypes = server.getProjectManager().getActiveBuildTypes();
    List<VEvent> events = new ArrayList<VEvent>(buildTypes.size());
    int i = 0;
    for (SBuildType type : buildTypes) {
      Collection<SBuild> builds = serviceLocator
              .getSingletonService(BuildsManager.class).findBuildInstances(Collections.singletonList(type.getBuildTypeId()));
      if (builds == null) {
        continue;
      }
      for (SBuild build : builds) {
        VEvent event = createEvent(build, type, i++);
        events.add(event);
      }
    }

    return events;
  }

  private VEvent createEvent(@NotNull BuildTriggerDescriptor trigger, @NotNull SBuildType type, int i) throws CronParseException {
    VEvent event = new VEvent();
    event.getProperties().add(new Uid("" + i));
    Map<String, String> properties = trigger.getProperties();
    CronExpressionUtil.apply(properties, event);
    applyAdditionalProperties(properties, event);
    Summary summary = new Summary(type.getExtendedName());
    event.getProperties().add(summary);
    return event;
  }

  private VEvent createEvent(@NotNull SBuild build, @NotNull SBuildType type, int i)  {
    VEvent event = new VEvent();
    event.getProperties().add(new Uid("" + i));
    CronExpressionUtil.apply(build.getClientStartDate(), build.getFinishDate(), build.getClientTimeZone(), event);
    String agent = build.getAgent().getName();
    Summary summary = new Summary(type.getExtendedName() + " : " + agent);
    event.getProperties().add(summary);
    return event;
  }


  private void applyAdditionalProperties(@NotNull Map<String, String> properties, @NotNull VEvent event) throws CronParseException {
    String onChangesOnly = properties.get("triggerBuildWithPendingChangesOnly");
    if (onChangesOnly != null && Boolean.valueOf(onChangesOnly)) {
      //ICalendarUtils.setXProperty("triggerBuildWithPendingChangesOnly", "true", event);
    }

    //net.fortuna.ical4j.model.TimeZone tz = TimeZoneUtils.getTimeZone(cronExpression.getTimeZone() != null ? cronExpression.getTimeZone() : TimeZone.getDefault().getID());
  }

  public void checkGlobalPermission(final Permission permission) throws AuthorizationFailedException {
    final AuthorityHolder authorityHolder = securityContext.getAuthorityHolder();
    if (!authorityHolder.isPermissionGrantedForAnyProject(permission)) {
      throw new AuthorizationFailedException(
              "User " + authorityHolder.getAssociatedUser() + " does not have global permission " + permission);
    }
  }

  public void checkProjectPermission(@NotNull final Permission permission, @Nullable final String projectId) throws AuthorizationFailedException {
    final AuthorityHolder authorityHolder = securityContext.getAuthorityHolder();
    if (projectId == null) {
      if (authorityHolder.isPermissionGrantedGlobally(permission)) {
        return;
      }
      throw new AuthorizationFailedException("No permission '" + permission + " is granted globally.");
    }
    if (!authorityHolder.isPermissionGrantedForProject(projectId, permission)) {
      throw new AuthorizationFailedException("User " + authorityHolder.getAssociatedUser() + " does not have permission " + permission +
              " in project with internal id: '" + projectId + "'");
    }
  }


}
