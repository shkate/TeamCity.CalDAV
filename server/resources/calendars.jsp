<%@ include file="/include-internal.jsp" %>

<%--
  ~ Copyright 2000-2014 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
<%--@elvariable id="scheduledBuildsCount" type="int"--%>
<%--@elvariable id="historyBuildsCount" type="int"--%>
<%--@elvariable id="scheduledBuildsDownloadLink" type="java.lang.String"--%>
<%--@elvariable id="historyBuildsDownloadLink" type="java.lang.String"--%>
<div>
  <c:choose>
    <c:when test="${scheduledBuildsCount>0}">
      ${scheduledBuildsCount} scheduled build(s) found. <a href="${pageContext.request.contextPath}${scheduledBuildsDownloadLink}">download calendar.ics</a>
    </c:when>
    <c:otherwise>
      This project doesn't contains time scheduled builds
    </c:otherwise>
  </c:choose>
</div>
<div>
  <c:choose>
    <c:when test="${historyBuildsCount>0}">
      ${historyBuildsCount} finished build(s) found. <a href="${pageContext.request.contextPath}${historyBuildsDownloadLink}">download history.ics</a>
    </c:when>
    <c:otherwise>
      This project doesn't contains history of finished builds
    </c:otherwise>
  </c:choose>
</div>


