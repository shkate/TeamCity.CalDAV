package org.teamcity.caldav;

import org.jetbrains.annotations.NotNull;


public interface PathTransformator {
  @NotNull
  String getTransformedPath(@NotNull String path);
}
