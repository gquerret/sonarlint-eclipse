/*
 * SonarQube Eclipse
 * Copyright (C) 2010-2015 SonarSource
 * sonarqube@googlegroups.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.eclipse.ui.internal.markers;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMarkerResolution2;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.markers.MarkerUtils;
import org.sonarlint.eclipse.core.internal.markers.MarkerUtils.MyFlow;
import org.sonarlint.eclipse.core.internal.markers.MarkerUtils.MyIssueLocation;
import org.sonarlint.eclipse.ui.internal.SonarLintImages;

public class ShowIssueFlowsMarkerResolver implements IMarkerResolution2 {

  private static final String ISSUE_FLOW_ANNOTATION_TYPE = "org.sonarlint.eclipse.issueFlowAnnotationType";

  @Override
  public String getDescription() {
    return "Show the extra locations to better understand the issue";
  }

  @Override
  public String getLabel() {
    return "Show issue extra locations";
  }

  @Override
  public void run(IMarker marker) {
    try {
      IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
      IEditorPart editorPart = IDE.openEditor(page, marker);
      IEditorInput editorInput = editorPart.getEditorInput();
      if (editorPart instanceof ITextEditor) {
        ITextEditor textEditor = (ITextEditor) editorPart;
        IAnnotationModel annotationModel = textEditor.getDocumentProvider().getAnnotationModel(editorInput);
        annotationModel.getAnnotationIterator().forEachRemaining(a -> {
          if (ISSUE_FLOW_ANNOTATION_TYPE.equals(a.getType())) {
            annotationModel.removeAnnotation(a);
          }
        });
        for (MyFlow f : MarkerUtils.deserialize(marker.getAttribute(MarkerUtils.SONAR_MARKER_EXTRA_LOCATIONS_ATTR, null))) {
          for (MyIssueLocation l : f.locations()) {
            annotationModel.addAnnotation(new Annotation(ISSUE_FLOW_ANNOTATION_TYPE, true, l.getMessage()),
              new Position(l.getStart(), l.getLength()));
          }
        }
      }
    } catch (Exception e) {
      SonarLintLogger.get().error("Unable to show extra locations", e);
    }
  }

  @Override
  public Image getImage() {
    return SonarLintImages.BALLOON_IMG;
  }
}
