/*******************************************************************************
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.google.appengine.eclipse.core.preferences;

import com.google.appengine.eclipse.core.AppEngineCorePlugin;
import com.google.appengine.eclipse.core.AppEngineCorePluginLog;
import com.google.appengine.eclipse.core.nature.GaeNature;
import com.google.appengine.eclipse.core.resources.GaeProject;
import com.google.appengine.eclipse.core.sdk.AppEngineUpdateWebInfFolderCommand;
import com.google.appengine.eclipse.core.sdk.GaeSdk;
import com.google.appengine.eclipse.core.sdk.GaeSdkContainer;
import com.google.gdt.eclipse.core.WebAppUtilities;
import com.google.gdt.eclipse.core.sdk.ClasspathContainerUpdateJob;
import com.google.gdt.eclipse.core.sdk.SdkManager;
import com.google.gdt.eclipse.core.sdk.SdkSet;
import com.google.gdt.eclipse.core.sdk.SdkManager.SdkUpdateEvent;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.osgi.service.prefs.BackingStoreException;

import java.io.FileNotFoundException;

/**
 * Contains static methods for retrieving and setting GAE plug-in preferences.
 */
public final class GaePreferences {

  private static SdkManager<GaeSdk> sdkManager;

  static {
    sdkManager = new SdkManager<GaeSdk>(GaeSdkContainer.CONTAINER_ID,
        getEclipsePreferences(), GaeSdk.getFactory());
    sdkManager.addSdkUpdateListener(new SdkManager.SdkUpdateListener<GaeSdk>() {
      public void onSdkUpdate(SdkUpdateEvent<GaeSdk> sdkUpdateEvent)
          throws CoreException {
        IJavaProject[] projects = JavaCore.create(
            ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
        for (IJavaProject project : projects) {
          if (!GaeNature.isGaeProject(project.getProject())) {
            continue;
          }

          GaeProject p = GaeProject.create(project.getProject());
          try {
            GaeSdk sdk = p.getSdk();
            if (sdk != null
                && WebAppUtilities.hasManagedWarOut(project.getProject())) {
              new AppEngineUpdateWebInfFolderCommand(project, p.getSdk()).execute();
            }
          } catch (FileNotFoundException e) {
            // Log the error and continue
            AppEngineCorePluginLog.logError(e);
          } catch (BackingStoreException e) {
            // Log the error and continue
            AppEngineCorePluginLog.logError(e);
          }
        }

        ClasspathContainerUpdateJob classpathContainerUpdateJob = new ClasspathContainerUpdateJob(
            "ClasspathContainerUpdateJob", GaeSdkContainer.CONTAINER_ID);
        classpathContainerUpdateJob.schedule();
      }
    });
  }

  public static GaeSdk getDefaultSdk() {
    return getSdks().getDefault();
  }

  public static String getDeployEmailAddress() {
    return AppEngineCorePlugin.getDefault().getPluginPreferences().getString(
        GaePreferenceConstants.DEPLOY_EMAIL_ADDRESS);
  }

  public static SdkManager<GaeSdk> getSdkManager() {
    return sdkManager;
  }

  /**
   * Returns the current {@link com.google.gdt.eclipse.core.sdk.SdkSet} state.
   */
  public static SdkSet<GaeSdk> getSdks() {
    return getSdkManager().getSdks();
  }

  public static void setDefaultSdk(GaeSdk sdk) {
    getSdks().setDefault(sdk);
  }

  public static void setDeployEmailAddress(String address) {
    assert (address != null);
    AppEngineCorePlugin.getDefault().getPluginPreferences().setValue(
        GaePreferenceConstants.DEPLOY_EMAIL_ADDRESS, address);
  }

  /**
   * Sets the current {@link com.google.gdt.eclipse.core.sdk.SdkSet}
   * state without concern for merges, etc.
   */
  public static void setSdks(SdkSet<GaeSdk> sdkSet) {
    try {
      getSdkManager().setSdks(sdkSet);
    } catch (CoreException e) {
      AppEngineCorePluginLog.logError(e);
    }
  }

  private static IEclipsePreferences getEclipsePreferences() {
    InstanceScope scope = new InstanceScope();
    IEclipsePreferences workspacePrefs = scope.getNode(AppEngineCorePlugin.PLUGIN_ID);
    return workspacePrefs;
  }

  private GaePreferences() {
    // Not instantiable
  }

}
