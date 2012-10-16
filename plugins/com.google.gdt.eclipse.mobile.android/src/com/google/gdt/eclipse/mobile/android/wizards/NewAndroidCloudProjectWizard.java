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
package com.google.gdt.eclipse.mobile.android.wizards;

import com.google.gdt.eclipse.appengine.swarm_backend.wizards.GenerateBackendProject;
import com.google.gdt.eclipse.mobile.android.GdtAndroidImages;
import com.google.gdt.eclipse.mobile.android.GdtAndroidPlugin;
import com.google.gdt.eclipse.mobile.android.wizards.helpers.AndroidProjectCreator;
import com.google.gdt.eclipse.mobile.android.wizards.helpers.ProjectCreationConstants;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.io.StreamException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.ui.wizards.NewElementWizard;
import org.eclipse.jdt.ui.actions.OpenJavaPerspectiveAction;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Wizard for creating App Engine Connected Android project. Creates an Android
 * and an App Engine project
 */
@SuppressWarnings("restriction")
public class NewAndroidCloudProjectWizard extends NewElementWizard implements
    INewWizard {

  private String androidPackageName;

  private HashMap<String, String> androidProjectDictionary;
  // android project
  private Map<String, Object> androidProjectParameters;
  private NewAndroidCloudProjectWizardPage newProjectPage;

  public NewAndroidCloudProjectWizard() {
    NewAndroidCloudProjectWizardPage.isAndroidSdkInstalled();
    setWindowTitle("New App Engine Connected Android Project"); //$NON-NLS-1$
    setNeedsProgressMonitor(true);
  }

  @Override
  public void addPages() {
    newProjectPage = new NewAndroidCloudProjectWizardPage();
    addPage(newProjectPage);
  }

  @Override
  public boolean canFinish() {
    return newProjectPage.isPageComplete();
  }

  @Override
  public IJavaElement getCreatedElement() {
    return null;
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    setDefaultPageImageDescriptor(GdtAndroidPlugin.getDefault().getImageDescriptor(
        GdtAndroidImages.NEW_PROJECT_WIZARD_ICON));
  }

  @Override
  public boolean performFinish() {

    getAndroidProjectParameters();
    getGaeFieldParameters();
    DebugUITools.getLaunchGroups();
    boolean finished = super.performFinish();

    if (finished) {
      // Open the default Java Perspective
      OpenJavaPerspectiveAction action = new OpenJavaPerspectiveAction();
      action.run();
    }
    return finished;
  }

  private void getAndroidProjectParameters() {
    Map<String, Object> mParameters = new HashMap<String, Object>();

    androidPackageName = newProjectPage.getAndroidPackageName();
    mParameters.put(ProjectCreationConstants.PARAM_PROJECT,
        newProjectPage.getAndroidProjectName());
    mParameters.put(ProjectCreationConstants.PARAM_PACKAGE, androidPackageName);
    mParameters.put(ProjectCreationConstants.PARAM_APPLICATION,
        ProjectCreationConstants.STRING_RSRC_PREFIX
            + ProjectCreationConstants.STRING_APP_NAME);
    mParameters.put(ProjectCreationConstants.PARAM_SDK_TOOLS_DIR,
        AdtPlugin.getOsSdkToolsFolder());
    mParameters.put(ProjectCreationConstants.PARAM_MIN_SDK_VERSION,
        newProjectPage.getMinSdkVersion());
    mParameters.put(ProjectCreationConstants.PARAM_SDK_TARGET,
        newProjectPage.getAndroidSdkTarget());
    androidProjectParameters = mParameters;

    // create a dictionary of string that will contain name+content.
    // we'll put all the strings into values/strings.xml
    androidProjectDictionary = new HashMap<String, String>();
    androidProjectDictionary.put(ProjectCreationConstants.STRING_APP_NAME,
        newProjectPage.getAndroidApplicationName());

    if (newProjectPage.isCreateActivity()) {
      // An activity name can be of the form ".package.Class" or ".Class".
      // The initial dot is ignored, as it is always added later in the
      // templates.
      String activityName = newProjectPage.getActivityName();
      if (activityName.startsWith(".")) { //$NON-NLS-1$
        activityName = activityName.substring(1);
      }
      androidProjectParameters.put(ProjectCreationConstants.PARAM_ACTIVITY,
          activityName);
    }
    IPath path = newProjectPage.getLocationPath();
    androidProjectParameters.put(ProjectCreationConstants.PARAM_PROJECT_PATH,
        path);
  }

  private void getGaeFieldParameters() {
    newProjectPage.getGaeProjectName();
    newProjectPage.getGaeSdkContainerPath();
    newProjectPage.getGaePackageName();
    newProjectPage.getCreationLocationURI();
  }

  @Override
  protected void finishPage(IProgressMonitor monitor)
 throws CoreException {

    try {
      // create the android project
      AndroidProjectCreator androidpc = AndroidProjectCreator.createNewAndroidProjectCreator();
      androidpc.setAndroidProjectDictionary(androidProjectDictionary);
      androidpc.setAndroidProjectParameters(androidProjectParameters);
      IProject androidProject = androidpc.create(monitor);
      new GenerateBackendProject().generateBackendProject(androidProject, monitor);

    } catch (MalformedURLException e) {
      throw new CoreException(new Status(IStatus.ERROR,
          GdtAndroidPlugin.PLUGIN_ID, e.getMessage(), e));
    } catch (UnsupportedEncodingException e) {
      throw new CoreException(new Status(IStatus.ERROR,
          GdtAndroidPlugin.PLUGIN_ID, e.getMessage(), e));
    } catch (IOException e) {
      throw new CoreException(new Status(IStatus.ERROR,
          GdtAndroidPlugin.PLUGIN_ID, e.getMessage(), e));
    } catch (StreamException e) {
      throw new CoreException(new Status(IStatus.ERROR,
          GdtAndroidPlugin.PLUGIN_ID, e.getMessage(), e));
    }
  }

}
