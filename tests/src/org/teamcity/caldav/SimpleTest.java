package org.teamcity.caldav;

import jetbrains.MockBuildType;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.scheduler.CronFieldInfo;
import jetbrains.buildServer.buildTriggers.scheduler.CronParseException;
import jetbrains.buildServer.buildTriggers.scheduler.SchedulerBuildTriggerService;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import junit.framework.Assert;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import org.jetbrains.annotations.NotNull;
import org.osaf.cosmo.calendar.ICalendarUtils;
import org.teamcity.caldav.data.BuildTypesProvider;
import org.teamcity.caldav.data.CronExpressionUtil;
import org.teamcity.caldav.data.ScheduledCalendarProvider;
import org.testng.annotations.Test;

import java.text.SimpleDateFormat;
import java.util.*;


public class SimpleTest extends BaseTestCase {


  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

  @Test
  public void testNoProject() throws CronParseException {
    GregorianCalendar until = new GregorianCalendar();
    until.add(java.util.Calendar.YEAR, 1);


    ScheduledCalendarProvider provider = new ScheduledCalendarProvider(new BuildTypesProvider() {

      private SBuildType bt1 = createBuildType("project1", "bt1", CronExpressionUtil.DAILY, "07", "00");

      @NotNull
      @Override
      public List<SBuildType> getProjectBuildTypes(@NotNull SProject project) {
        return Collections.singletonList(bt1);
      }

      @NotNull
      @Override
      public List<SBuildType> getActiveBuildTypes() {
        return Collections.singletonList(bt1);
      }
    });

    List<VEvent> events = provider.getScheduledEvents(null);
    Assert.assertNotNull(events);
    Assert.assertEquals(events.size(), 1);
    String summary = ICalendarUtils.getXProperty("summary", events.get(0));
    Assert.assertEquals("project1 :: bt1", summary);
    String rrule = ICalendarUtils.getXProperty("rrule", events.get(0));
    Assert.assertEquals("FREQ=DAILY;UNTIL=" + DATE_FORMAT.format(until.getTime()), rrule);
    String start = ICalendarUtils.getXProperty("DtStart", events.get(0));
    Assert.assertTrue(start.contains("T070000"));
    Calendar calendar = provider.getCalendar(null);
    Assert.assertNotNull(calendar);
  }

  private MockBuildType createBuildType(@NotNull final String projectName, @NotNull final String name,
                                        @NotNull final String policy,
                                        @NotNull final String hour, @NotNull final String minutes) {
    return new MockBuildType() {
      @NotNull
      @Override
      public String getProjectName() {
        return projectName;
      }

      @NotNull
      @Override
      public String getName() {
        return name;
      }

      @NotNull
      @Override
      public String getExtendedName() {
        return projectName + " :: " + name;
      }

      @NotNull
      @Override
      public Collection<BuildTriggerDescriptor> getBuildTriggersCollection() {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(SchedulerBuildTriggerService.PROP_SCHEDULING_POLICY, policy);
        properties.put(CronExpressionUtil.HOUR, hour);
        properties.put(CronExpressionUtil.MINUTE, minutes);
        properties.put(SchedulerBuildTriggerService.CRON_EXPRESSION_PARAM_PREFIX + CronFieldInfo.SEC.getKey(), "0");
        properties.put(SchedulerBuildTriggerService.CRON_EXPRESSION_PARAM_PREFIX + CronFieldInfo.MIN.getKey(), "*");
        properties.put(SchedulerBuildTriggerService.CRON_EXPRESSION_PARAM_PREFIX + CronFieldInfo.HOUR.getKey(), "*");
        properties.put(SchedulerBuildTriggerService.CRON_EXPRESSION_PARAM_PREFIX + CronFieldInfo.DM.getKey(), "*");
        properties.put(SchedulerBuildTriggerService.CRON_EXPRESSION_PARAM_PREFIX + CronFieldInfo.DW.getKey(), "*");
        properties.put(SchedulerBuildTriggerService.CRON_EXPRESSION_PARAM_PREFIX + CronFieldInfo.MONTH.getKey(), "*");
        properties.put(SchedulerBuildTriggerService.CRON_EXPRESSION_PARAM_PREFIX + CronFieldInfo.YEAR.getKey(), "*");
        ArrayList<BuildTriggerDescriptor> list = new ArrayList<BuildTriggerDescriptor>();
        list.add(new ScheduledBuildTrigger(properties));
        return list;
      }
    };
  }
}
