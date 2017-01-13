/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2016 SonarSource SA
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarlint.eclipse.core.internal.markers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.PreferencesUtils;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.utils.StringUtils;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.Flow;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;

import static java.lang.Integer.valueOf;

public final class MarkerUtils {

  private static final String SEPARATOR = "|";
  public static final String SONAR_MARKER_RULE_KEY_ATTR = "rulekey";
  public static final String SONAR_MARKER_RULE_NAME_ATTR = "rulename";
  public static final String SONAR_MARKER_ISSUE_SEVERITY_ATTR = "sonarseverity";
  public static final String SONAR_MARKER_CREATION_DATE_ATTR = "creationdate";
  public static final String SONAR_MARKER_SERVER_ISSUE_KEY_ATTR = "serverissuekey";
  public static final String SONAR_MARKER_EXTRA_LOCATIONS_ATTR = "extralocations";

  private MarkerUtils() {
  }

  public static void deleteIssuesMarkers(IResource resource) {
    deleteMarkers(resource, SonarLintCorePlugin.MARKER_ID);
  }

  public static void deleteChangeSetIssuesMarkers(IResource resource) {
    deleteMarkers(resource, SonarLintCorePlugin.MARKER_CHANGESET_ID);
  }

  public static void deleteMarkers(IResource resource, String markerId) {
    if (resource.isAccessible()) {
      try {
        resource.deleteMarkers(markerId, true, IResource.DEPTH_INFINITE);
      } catch (CoreException e) {
        SonarLintLogger.get().error(e.getMessage(), e);
      }
    }
  }

  public static List<IMarker> findMarkers(IResource resource) {
    try {
      return Arrays.asList(resource.findMarkers(SonarLintCorePlugin.MARKER_ID, true, IResource.DEPTH_INFINITE));
    } catch (CoreException e) {
      SonarLintLogger.get().error(e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  public static void updateAllSonarMarkerSeverity() throws CoreException {
    for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
      if (project.isAccessible()) {
        for (IMarker marker : project.findMarkers(SonarLintCorePlugin.MARKER_ID, true, IResource.DEPTH_INFINITE)) {
          marker.setAttribute(IMarker.SEVERITY, PreferencesUtils.getMarkerSeverity());
        }
      }
    }
  }

  @CheckForNull
  public static FlatTextRange getFlatTextRange(final IDocument document, @Nullable TextRange textRange) {
    if (textRange == null || textRange.getStartLine() == null) {
      return null;
    }
    if (textRange.getStartLineOffset() == null) {
      return getFlatTextRange(document, textRange.getStartLine());
    }
    return getFlatTextRange(document, textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
  }

  @CheckForNull
  public static FlatTextRange getFlatTextRange(final IDocument document, int startLine) {
    int startLineStartOffset;
    int length;
    String lineDelimiter;
    try {
      startLineStartOffset = document.getLineOffset(startLine - 1);
      length = document.getLineLength(startLine - 1);
      lineDelimiter = document.getLineDelimiter(startLine - 1);
    } catch (BadLocationException e) {
      SonarLintLogger.get().error("failed to compute flat text range for line " + startLine, e);
      return null;
    }

    int lineDelimiterLength = lineDelimiter != null ? lineDelimiter.length() : 0;

    int start = startLineStartOffset;
    int end = startLineStartOffset + length - lineDelimiterLength;
    return new FlatTextRange(start, end);
  }

  @CheckForNull
  private static FlatTextRange getFlatTextRange(final IDocument document, int startLine, int startLineOffset, int endLine, int endLineOffset) {
    int startLineStartOffset;
    int endLineStartOffset;
    try {
      startLineStartOffset = document.getLineOffset(startLine - 1);
      endLineStartOffset = endLine != startLine ? document.getLineOffset(endLine - 1) : startLineStartOffset;
    } catch (BadLocationException e) {
      SonarLintLogger.get().error("failed to compute line offsets for start, end = " + startLine + ", " + endLine, e);
      return null;
    }

    int start = startLineStartOffset + startLineOffset;
    int end = endLineStartOffset + endLineOffset;
    return new FlatTextRange(start, end);
  }

  public static String serialize(IDocument doc, List<Flow> flows) {
    StringBuilder sb = new StringBuilder();
    for (Flow flow : flows) {
      for (IssueLocation l : flow.locations()) {
        FlatTextRange flatTextRange = MarkerUtils.getFlatTextRange(doc, l.getStartLine(), l.getStartLineOffset(), l.getEndLine(), l.getEndLineOffset());
        sb.append(flatTextRange.getStart()).append(SEPARATOR);
        sb.append(flatTextRange.getEnd()).append(SEPARATOR);
        sb.append(l.getMessage()).append("\n");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  public static List<MyFlow> deserialize(String flowsAsString) {
    try (BufferedReader reader = new BufferedReader(new StringReader(flowsAsString))) {
      String line;
      List<MyFlow> result = new ArrayList<>();
      List<MyIssueLocation> locations = new ArrayList<>();
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty()) {
          result.add(new MyFlow(locations));
        } else {
          String startStr = StringUtils.substringBefore(line, SEPARATOR);
          line = line.substring(startStr.length() + 1);
          String endStr = StringUtils.substringBefore(line, SEPARATOR);
          line = line.substring(endStr.length() + 1);
          String message = line;
          locations.add(new MyIssueLocation(valueOf(startStr), valueOf(endStr), message));
        }
      }
      return result;
    } catch (IOException e) {
      // Should never occurs
      throw new IllegalStateException(e);
    }
  }

  public static class MyFlow {

    private final List<MyIssueLocation> locations;

    public MyFlow(List<MyIssueLocation> locations) {
      this.locations = locations;
    }

    public List<MyIssueLocation> locations() {
      return locations;
    }
  }

  public static class MyIssueLocation extends FlatTextRange {

    private final String message;

    public MyIssueLocation(int start, int end, String message) {
      super(start, end);
      this.message = message;
    }

    public String getMessage() {
      return message;
    }

  }

}
