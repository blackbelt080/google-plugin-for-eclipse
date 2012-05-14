/*******************************************************************************
 * Copyright 2011 Google Inc. All Rights Reserved.
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
package com.google.gdt.eclipse.suite.launch;

import com.google.appengine.eclipse.core.api.XmlUtil;
import com.google.appengine.eclipse.core.properties.GoogleCloudSqlProperties;
import com.google.gdt.eclipse.core.CorePluginLog;
import com.google.gdt.eclipse.core.WebAppUtilities;
import com.google.gdt.eclipse.core.launch.LaunchConfigurationProcessorUtilities;
import com.google.gdt.eclipse.login.GoogleLogin;
import com.google.gdt.eclipse.platform.launch.WtpPublisher;
import com.google.gdt.eclipse.suite.GdtPlugin;
import com.google.gdt.eclipse.suite.launch.processors.WarArgumentProcessor;
import com.google.gdt.eclipse.suite.launch.processors.WarArgumentProcessor.WarParser;
import com.google.gwt.eclipse.core.GWTPluginLog;
import com.google.gwt.eclipse.core.launch.processors.NoServerArgumentProcessor;
import com.google.gwt.eclipse.core.launch.processors.RemoteUiArgumentProcessor;
import com.google.gwt.eclipse.oophm.model.WebAppDebugModel;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.JavaLaunchDelegate;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.ServerUtil;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

/**
 * Launch delegate for webapps.
 */
@SuppressWarnings({"restriction", "nls"})
public class WebAppLaunchDelegate extends JavaLaunchDelegate {

  private static final String
      ARG_RDBMS_EXTRA_PROPERTIES = "-Drdbms.extra.properties=";
  private static final
      String ARG_RDBMS_EXTRA_PROPERTIES_VALUE_FORMAT = "\"oauth2RefreshToken=%s,oauth2AccessToken=%s,oauth2ClientId=%s,oauth2ClientSecret=%s\"";

  /**
   * Publish any {@link IModule}s that the project has if it is not using a
   * managed war directory and it is running a server.
   */
  public static void maybePublishModulesToWarDirectory(
      ILaunchConfiguration configuration, IProgressMonitor monitor,
      IJavaProject javaProject, boolean forceFullPublish)
      throws CoreException, IOException {

    if (javaProject == null) {
      // No Java Project
      return;
    }

    IProject project = javaProject.getProject();
    try {
      new XmlUtil().insertDevApiServer(project);
    } catch (SAXException e) {
      GdtPlugin.getLogger().logError(e);
    } catch (ParserConfigurationException e) {
      GdtPlugin.getLogger().logError(e);
    } catch (TransformerFactoryConfigurationError e) {
      GdtPlugin.getLogger().logError(e);
    } catch (TransformerException e) {
      GdtPlugin.getLogger().logError(e);
    }
    List<String> args = LaunchConfigurationProcessorUtilities.parseProgramArgs(
        configuration);
    if (WebAppUtilities.hasManagedWarOut(project)
        || NoServerArgumentProcessor.hasNoServerArg(args)) {
      // Project has a managed war directory or it is running in noserver
      // mode
      return;
    }

    WarParser parser = WarArgumentProcessor.WarParser.parse(args, javaProject);
    if (parser.resolvedUnverifiedWarDir == null) {
      // Invalid war directory
      return;
    }

    IModule[] modules = ServerUtil.getModules(project);
    if (modules.length > 0) {
      Path unmanagedWarPath = new Path(parser.resolvedUnverifiedWarDir);
      WtpPublisher.publishModulesToWarDirectory(
          project, modules, unmanagedWarPath, forceFullPublish, monitor);
    }
  }

  /**
   * @return Returns {@code}false if unsuccessful in adding the VM arguments and
   *         the launch should be cancelled.
   */
  private boolean addVmArgs(ILaunchConfiguration configuration)
      throws CoreException {
    IProject project = getJavaProject(configuration).getProject();
    if (!GoogleCloudSqlProperties.getGoogleCloudSqlEnabled(project)
        || GoogleCloudSqlProperties.getLocalDevMySqlEnabled(project)) {
      return true;
    }
    ILaunchConfigurationWorkingCopy workingCopy = configuration.getWorkingCopy();
    String vmArgs = configuration.getAttribute(
        IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, "");
    // The regex is -Drdbms\.extra\.properties="\S+" which matches
    // -Drdbms.extra.properties="<non-whitespace chars>".
    String[] vmArgList = vmArgs.split("-Drdbms\\.extra\\.properties=\"\\S+\"");
    vmArgs = "";
    for (String string : vmArgList) {
      vmArgs += string;
    }
    String refreshToken = null;
    String accessToken = null;
    String clientId = null;
    String clientSecret = null;
    // If the user is not logged in, prompt him to log in.
    GoogleLogin.promptToLogIn("Please Log in to continue launch");
    // Try to get the vm arguments.
    try {
      refreshToken = GoogleLogin.getInstance().fetchOAuth2RefreshToken();
      accessToken = GoogleLogin.getInstance().fetchAccessToken();
      clientId = GoogleLogin.getInstance().fetchOAuth2ClientId();
      clientSecret = GoogleLogin.getInstance().fetchOAuth2ClientSecret();
    } catch (SWTException e) {
      // The exception is thrown if the user did not log in when prompted. Just
      // show an error message and exit.
      Display.getDefault().syncExec(new Runnable() {
        public void run() {
          MessageDialog.openError(null, "Error in authentication",
              "Please sign in with your Google account before launching");
        }
      });
    } catch (IOException e) {
      CorePluginLog.logError(e);
    }
    if (refreshToken == null || accessToken == null || clientId == null
        || clientSecret == null) {
      return false;
    }
    String rdbmsExtraPropertiesValue = String.format(
        ARG_RDBMS_EXTRA_PROPERTIES_VALUE_FORMAT, refreshToken, accessToken,
        clientId, clientSecret);
    vmArgs += " " + ARG_RDBMS_EXTRA_PROPERTIES + rdbmsExtraPropertiesValue;
    workingCopy.setAttribute(
        IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, vmArgs);
    ILaunchConfiguration config = workingCopy.doSave();
    IPath path = config.getLocation();
    if ((path != null) && path.toFile().exists()) {
      try {
        Runtime.getRuntime().exec("chmod 600 " + path.toString());
      } catch (IOException e) {
        CorePluginLog.logError(
            "Could not change permissions on the launch config file");
      }
    }
    return true;
  }

  /**
   * In the case of a project with an unmanaged WAR directory or when the
   * project is not a web app but the main type takes a WAR argument, we need to
   * check at launch-time whether the launch config has a runtime WAR. If it
   * does not, then we ask the user for one and insert it into the launch
   * config.
   *
   * @param configuration the launch configuration that may be written to
   * @return true to continue the launch, false to abort silently
   * @throws CoreException
   */
  private boolean ensureWarArgumentExistenceInCertainCases(
      ILaunchConfiguration configuration) throws CoreException {
    IJavaProject javaProject = getJavaProject(configuration);
    if (javaProject != null) {
      IProject project = javaProject.getProject();
      boolean isWebApp = WebAppUtilities.isWebApp(project);
      if ((isWebApp && !WebAppUtilities.hasManagedWarOut(project)) || (!isWebApp
          && WarArgumentProcessor.doesMainTypeTakeWarArgument(configuration))) {

        List<String> args = LaunchConfigurationProcessorUtilities
          .parseProgramArgs(configuration);
        WarParser parser = WarArgumentProcessor.WarParser.parse(
            args, javaProject);

        if (!(parser.isSpecifiedWithWarArg || parser.isWarDirValid)) {
          // The project's output WAR dir is unknown, so ask the user
          IPath warDir = WebAppUtilities.getWarOutLocationOrPrompt(project);
          if (warDir == null) {
            return false;
          }

          // The processor will update to the proper argument style
          // for the
          // current project nature(s)
          WarArgumentProcessor warArgProcessor = new WarArgumentProcessor();
          warArgProcessor.setWarDirFromLaunchConfigCreation(
              warDir.toOSString());

          ILaunchConfigurationWorkingCopy wc = configuration.getWorkingCopy();
          LaunchConfigurationProcessorUtilities.updateViaProcessor(
              warArgProcessor, wc);
          wc.doSave();
        }
      }
    }

    return true;
  }

  @Override
  protected File getDefaultWorkingDirectory(ILaunchConfiguration configuration)
      throws CoreException {

    IJavaProject javaProject = getJavaProject(configuration);
    if (javaProject != null
        && WarArgumentProcessor.doesMainTypeTakeWarArgument(configuration)) {
      WarParser warParser = WarArgumentProcessor.WarParser.parse(
          LaunchConfigurationProcessorUtilities.parseProgramArgs(configuration),
          javaProject);
      if (warParser.isWarDirValid || warParser.isSpecifiedWithWarArg) {
        return new File(warParser.resolvedUnverifiedWarDir);
      }
    }

    // Fallback on super's implementation
    return super.getDefaultWorkingDirectory(configuration);
  }

  /**
   * Returns <code>true</code> if there are any problems with a severity level
   * greater than or equal to error. Note that by default
   * {@link JavaLaunchDelegate} only considers java problems to be launch
   * problems. However, we want GPE errors to also be considered launch
   * problems.
   */
  @Override
  protected boolean isLaunchProblem(IMarker problemMarker)
      throws CoreException {
    Integer severity = (Integer) problemMarker.getAttribute(IMarker.SEVERITY);
    if (severity != null) {
      return severity.intValue() >= IMarker.SEVERITY_ERROR;
    }

    return false;
  }

  @Override
  public void launch(ILaunchConfiguration configuration, String mode,
      ILaunch launch, IProgressMonitor monitor) throws CoreException {
    if (!addVmArgs(configuration)) {
      return;
    }
    try {
      if (!ensureWarArgumentExistenceInCertainCases(configuration)) {
        return;
      }
      IJavaProject javaProject = getJavaProject(configuration);
      maybePublishModulesToWarDirectory(
          configuration, monitor, javaProject, false);

    } catch (Throwable t) {
      // Play safely and continue launch
      GdtPlugin.getLogger().logError(t,
          "Could not ensure WAR argument existence for the unmanaged WAR project.");
    }

    // check if this launch uses -remoteUI. If not, then don't add this launch
    // to the devmode view
    boolean addLaunch = true;
    if (launch != null) {
      try {
        List<String> args = LaunchConfigurationProcessorUtilities
          .parseProgramArgs(launch.getLaunchConfiguration());
        if (!args.contains(RemoteUiArgumentProcessor.ARG_REMOTE_UI)) {
          addLaunch = false;
        }
      } catch (CoreException e1) {
        GWTPluginLog.logError(e1);
      }
    }

    if (addLaunch) {
      /*
       * Add the launch to the DevMode view. This is tightly coupled because at
       * the time of ILaunchListener's changed callback, the launch's process
       * does not have a command-line set. Unfortunately there isn't another
       * listener to solve our needs, so we add this glue here.
       */
      WebAppDebugModel.getInstance()
          .addOrReturnExistingLaunchConfiguration(launch, null, null);
    }

    super.launch(configuration, mode, launch, monitor);
  }
}
