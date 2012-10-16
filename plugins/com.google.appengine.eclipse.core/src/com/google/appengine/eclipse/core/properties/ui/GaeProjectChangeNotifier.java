/*******************************************************************************
 * Copyright 2012 Google Inc. All Rights Reserved.
 * 
 *  All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/

package com.google.appengine.eclipse.core.properties.ui;

import com.google.appengine.eclipse.core.AppEngineCorePlugin;
import com.google.appengine.eclipse.core.AppEngineCorePluginLog;
import com.google.gdt.eclipse.core.extensions.ExtensionQuery;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import java.util.List;
import java.util.Map;

/**
 * Notifies that Gae project has chamnged to all plugins implementing
 * gaeProjectChange extension.
 * 
 */
public class GaeProjectChangeNotifier extends IncrementalProjectBuilder {
  private boolean shouldNotify = false;

  protected IProject[] build(int kind, Map args, IProgressMonitor monitor) {
    IResourceDelta delta = null;
    shouldNotify = false;
    if (kind != IncrementalProjectBuilder.FULL_BUILD) {
      delta = getDelta(getProject());
      if (delta == null) {
        return null;
      }
      try {
        delta.accept(new IResourceDeltaVisitor() {
          public boolean visit(IResourceDelta delta) {
            IPath resourcePath = delta.getResource().getRawLocation();
            if (resourcePath != null
                && resourcePath.toString().endsWith(".class")) {
              shouldNotify = true;
              return false;
            }
            return true;
          }
        });
      } catch (CoreException e) {
        AppEngineCorePluginLog.logError(e);
      }
    } else {
      shouldNotify = true;
    }
    if (!shouldNotify) {
      return null;
    }
    ExtensionQuery<GaeProjectChangeExtension> extQuery = new ExtensionQuery<
        GaeProjectChangeExtension>(
        AppEngineCorePlugin.PLUGIN_ID, "gaeProjectChange", "class");
    List<ExtensionQuery.Data<GaeProjectChangeExtension>> contributors = extQuery
      .getData();
    for (ExtensionQuery.Data<GaeProjectChangeExtension> c : contributors) {
      GaeProjectChangeExtension data = c.getExtensionPointData();
      data.gaeProjectRebuilt(getProject());
    }
    return null;
  }

}
