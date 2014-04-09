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

package org.teamcity.caldav;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osaf.cosmo.calendar.util.CalendarUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.servlet.ModelAndView;
import org.teamcity.caldav.data.DataProvider;
import org.teamcity.caldav.request.Constants;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Partial copy of ApiController used in REST plugin
 */

public class CalDAVController extends BaseController implements ServletContextAware {
  final static Logger LOG = Logger.getInstance(CalDAVController.class.getName());
  public static final String CALDAV_CORS_ORIGINS_INTERNAL_PROPERTY_NAME = "caldav.cors.origins";
  public static final String CALDVA_RESPONSE_PRETTYFORMAT = "caldav.response.prettyformat";
  private static final String PROJECT_PARAM_NAME = "project";
  private static final String HISTORY_PARAM_NAME = "history";
  private static final String TYPE_PARAM_NAME = "type";
  private final ConfigurableApplicationContext myConfigurableApplicationContext;
  private final SecurityContextEx mySecurityContext;
  private final DataProvider dataProvider;
  private ServerPluginInfo myPluginDescriptor;
  private final ExtensionHolder myExtensionHolder;
  private AuthorizationInterceptor myAuthorizationInterceptor;

  private final ClassLoader myClassloader;
  private String myAuthToken;
  private RequestPathTransformInfo myRequestPathTransformInfo;

  public CalDAVController(final SBuildServer server,
                          WebControllerManager webControllerManager,
                          final ConfigurableApplicationContext configurableApplicationContext,
                          final SecurityContextEx securityContext,
                          final RequestPathTransformInfo requestPathTransformInfo,
                          final ServerPluginInfo pluginDescriptor,
                          final ExtensionHolder extensionHolder,
                          final AuthorizationInterceptor authorizationInterceptor,
                          final DataProvider dataProvider) throws ServletException {
    super(server);
    this.dataProvider = dataProvider;
    myPluginDescriptor = pluginDescriptor;
    myExtensionHolder = extensionHolder;
    myAuthorizationInterceptor = authorizationInterceptor;
    setSupportedMethods(new String[]{METHOD_GET, METHOD_HEAD, METHOD_POST, "PUT", "OPTIONS", "DELETE"});

    myConfigurableApplicationContext = configurableApplicationContext;
    mySecurityContext = securityContext;
    myRequestPathTransformInfo = requestPathTransformInfo;

    final List<String> originalBindPaths = getBindPaths(pluginDescriptor);
    List<String> bindPaths = new ArrayList<String>(originalBindPaths);
    bindPaths.addAll(addPrefix(originalBindPaths, StringUtil.removeTailingSlash(WebUtil.HTTP_AUTH_PREFIX)));
    bindPaths.addAll(addPrefix(originalBindPaths, StringUtil.removeTailingSlash(WebUtil.GUEST_AUTH_PREFIX)));

    Map<String, String> transformBindPaths = new HashMap<String, String>();
    addEntries(transformBindPaths, bindPaths, Constants.CALDAV_URL);

    myRequestPathTransformInfo.setPathMapping(transformBindPaths);
    LOG.debug("Will use request mapping: " + myRequestPathTransformInfo);

    registerController(webControllerManager, originalBindPaths);

    myClassloader = getClass().getClassLoader();

    if (TeamCityProperties.getBoolean("rest.use.authToken")) {
      try {
        myAuthToken = URLEncoder.encode(UUID.randomUUID().toString() + (new Date()).toString().hashCode(), "UTF-8");
        LOG.info("Authentication token for Super user generated: '" + myAuthToken + "' (plugin '" + myPluginDescriptor.getPluginName() +
                "', listening for paths " + originalBindPaths + ").");
      } catch (UnsupportedEncodingException e) {
        LOG.warn(e);
      }
    }
  }

  private static void addEntries(final Map<String, String> map, final List<String> keys, final String value) {
    for (String key : keys) {
      map.put(key, value);
    }
  }

  private List<String> addPrefix(final List<String> paths, final String prefix) {
    List<String> result = new ArrayList<String>(paths.size());
    for (String path : paths) {
      result.add(prefix + path);
    }
    return result;
  }

  private List<String> addSuffix(final List<String> paths, final String suffix) {
    List<String> result = new ArrayList<String>(paths.size());
    for (String path : paths) {
      result.add(path + suffix);
    }
    return result;
  }

  private void registerController(final WebControllerManager webControllerManager, final List<String> bindPaths) {
    try {
      for (String controllerBindPath : bindPaths) {
        LOG.debug("Binding CalDAV to path '" + controllerBindPath + "'");
        webControllerManager.registerController(controllerBindPath + "/**", this);
      }
    } catch (Exception e) {
      LOG.error("Error registering controller", e);
    }
  }

  private List<String> getBindPaths(final ServerPluginInfo pluginDescriptor) {
    String bindPath = pluginDescriptor.getParameterValue(Constants.BIND_PATH_PROPERTY_NAME);
    if (bindPath == null) {
      return Collections.singletonList(Constants.CALDAV_URL);
    }

    final String[] bindPaths = bindPath.split(",");

    if (bindPath.length() == 0) {
      LOG.error("Invalid CalDAV bind path in plugin descriptor: '" + bindPath + "', using defaults");
      return Collections.singletonList(Constants.CALDAV_URL);
    }

    return Arrays.asList(bindPaths);
  }


  protected ModelAndView doHandle(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) throws Exception {
    if (TeamCityProperties.getBoolean("caldav.disable")) {
      reportCalDAVErrorResponse(response, HttpServletResponse.SC_NOT_IMPLEMENTED, null,
              "CalDAV is disabled on TeamCity server with 'rest.disable' internal property.", request.getRequestURI(),
              Level.INFO);
      return null;
    }

    final long requestStartProcessing = System.nanoTime();
    if (LOG.isDebugEnabled()) {
      LOG.debug("CalDAV request received: " + WebUtil.getRequestDump(request));
    }

    boolean runAsSystem = false;
    if (TeamCityProperties.getBoolean("caldav.use.authToken")) {
      String authToken = request.getParameter("authToken");
      if (StringUtil.isNotEmpty(authToken) && StringUtil.isNotEmpty(getAuthToken())) {
        if (authToken.equals(getAuthToken())) {
          runAsSystem = true;
        } else {
          synchronized (this) {
            Thread.sleep(10000); //to prevent brute-forcing
          }
          reportCalDAVErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, null, "Wrong authToken specified", request.getRequestURI(),
                  Level.INFO);
          return null;
        }
      }
    }

    try {

      processCorsRequest(request, response);

      String project = request.getParameter(PROJECT_PARAM_NAME);
      String history = request.getParameter(HISTORY_PARAM_NAME);
      String type = request.getParameter(TYPE_PARAM_NAME);

      SProject sProject = dataProvider.findProject(project);
      String fileName;
      net.fortuna.ical4j.model.Calendar calendar;
      if (history != null && Boolean.parseBoolean(history)) {
        calendar = dataProvider.getBuildHistoryCalendar(sProject);
        fileName = "history.ics";
      } else {
        calendar = dataProvider.getCalendar(sProject);
        fileName = "calendar.ics";
      }
      response.getWriter().write(CalendarUtils.outputCalendar(calendar));

      if ("ics".equals(type)) {
        response.setContentType("text/calendar");
        WebUtil.setContentDisposition(request, response, fileName, false);
        WebUtil.addCacheHeadersForIE(request, response);
      }

    } catch (Throwable throwable) {
      // Sometimes Jersey throws IllegalArgumentException and probably other without utilizing ExceptionMappers
      // forcing plain text error reporting
      reportCalDAVErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, throwable, null, request.getRequestURI(), Level.WARN);
    } finally {
      if (LOG.isDebugEnabled()) {
        final long requestFinishProcessing = System.nanoTime();
        LOG.debug("CalDAV request processing finished in " +
                TimeUnit.MILLISECONDS.convert(requestFinishProcessing - requestStartProcessing, TimeUnit.NANOSECONDS) + " ms, status code: " +
                getStatus(response));
      }
    }
    return null;
  }

  private String getStatus(final HttpServletResponse response) {
    String result = "<unknown>";
    try {
      result = String.valueOf(response.getStatus());
    } catch (NoSuchMethodError e) {
      //ignore: this occurs for Servlet API < 3.0
    }
    return result;
  }

  private void processCorsRequest(final HttpServletRequest request, final HttpServletResponse response) {
    final String origin = request.getHeader("Origin");
    if (StringUtil.isNotEmpty(origin)) {
      final String[] originsArray = getAllowedOrigins();
      if (ArrayUtil.contains(origin, originsArray)) {
        addOriginHeaderToResponse(response, origin);
        addOtherHeadersToResponse(request, response);
      } else if (ArrayUtil.contains("*", originsArray)) {
        LOG.debug("Got CORS request from origin '" + origin + "', but this origin is not allowed. However, '*' is. Replying with '*'." +
                " Add the origin to '" + CALDAV_CORS_ORIGINS_INTERNAL_PROPERTY_NAME +
                "' internal property (comma-separated) to trust the applications hosted on the domain. Current allowed origins are: " +
                Arrays.toString(originsArray));
        addOriginHeaderToResponse(response, "*");
      } else {
        LOG.debug("Got CORS request from origin '" + origin + "', but this origin is not allowed. Add the origin to '" +
                CALDAV_CORS_ORIGINS_INTERNAL_PROPERTY_NAME +
                "' internal property (comma-separated) to trust the applications hosted on the domain. Current allowed origins are: " +
                Arrays.toString(originsArray));
      }
    }
  }

  private void addOriginHeaderToResponse(final HttpServletResponse response, final String origin) {
    response.addHeader("Access-Control-Allow-Origin", origin);
  }

  private void addOtherHeadersToResponse(final HttpServletRequest request, final HttpServletResponse response) {
    response.addHeader("Access-Control-Allow-Methods", request.getHeader("Access-Control-Request-Method"));
    response.addHeader("Access-Control-Allow-Credentials", "true");

    //this will actually not function for OPTION request until http://youtrack.jetbrains.com/issue/TW-22019 is fixed
    response.addHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"));
  }

  private String myAllowedOrigins;
  private String[] myOriginsArray;

  @NotNull
  private synchronized String[] getAllowedOrigins() {
    final String allowedOrigins = TeamCityProperties.getProperty(CALDAV_CORS_ORIGINS_INTERNAL_PROPERTY_NAME);
    if (myAllowedOrigins == null || !myAllowedOrigins.equals(allowedOrigins)) {
      myAllowedOrigins = allowedOrigins;
      myOriginsArray = allowedOrigins.split(",");
      for (int i = 0; i < myOriginsArray.length; i++) {
        myOriginsArray[i] = myOriginsArray[i].trim();
      }
    }
    return myOriginsArray;
  }

  public static void reportCalDAVErrorResponse(@NotNull final HttpServletResponse response,
                                               final int statusCode,
                                               @Nullable final Throwable e,
                                               @Nullable final String message,
                                               @NotNull String requestUri, final Level level) {
    final String responseText = "error";
    //ExceptionMapperUtil.getResponseTextAndLogRestErrorErrorMessage(statusCode, e, message, requestUri, statusCode == HttpServletResponse.SC_INTERNAL_SERVER_ERROR, level);
    response.setStatus(statusCode);
    response.setContentType("text/plain");

    try {
      response.getWriter().print(responseText);
    } catch (Throwable nestedException) {
      final String message1 = "Error while adding error description into response: " + nestedException.getMessage();
      LOG.warn(message1);
      LOG.debug(message1, nestedException);
    }
  }


  //todo: move to RequestWrapper

  private HttpServletRequest patchRequest(final HttpServletRequest request, final String headerName, final String parameterName) {
    final String newValue = request.getParameter(parameterName);
    if (!StringUtil.isEmpty(newValue)) {
      return modifyRequestHeader(request, headerName, newValue);
    }
    return request;
  }

  private HttpServletRequest modifyRequestHeader(final HttpServletRequest request, final String headerName, final String newValue) {
    return new HttpServletRequestWrapper(request) {
      @Override
      public String getHeader(final String name) {
        if (headerName.equalsIgnoreCase(name)) {
          return newValue;
        }
        return super.getHeader(name);
      }

      @Override
      public Enumeration getHeaders(final String name) {
        if (headerName.equalsIgnoreCase(name)) {
          return Collections.enumeration(Collections.singletonList(newValue));
        }
        return super.getHeaders(name);
      }
    };
  }


  private String getAuthToken() {
    return myAuthToken;
  }


}
