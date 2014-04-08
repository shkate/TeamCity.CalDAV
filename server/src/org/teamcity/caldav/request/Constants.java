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

package org.teamcity.caldav.request;


public class Constants {
  public static final String API_URL = "/app/caldav";

  public static final String BIND_PATH_PROPERTY_NAME = "api.path";
  public static final String ORIGINAL_REQUEST_URI_HEADER_NAME = "original-request-uri";
  public static final String CALENDAR_PRODUCT = "-//JetBrains TeamCity//Scheduled Builds//EN";
  public static final String BUILD_HISTORY_CALENDAR_PRODUCT = "-//JetBrains TeamCity//Build History//EN";

}
