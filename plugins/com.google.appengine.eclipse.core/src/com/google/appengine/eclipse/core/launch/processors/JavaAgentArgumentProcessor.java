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
package com.google.appengine.eclipse.core.launch.processors;

import com.google.appengine.eclipse.core.nature.GaeNature;
import com.google.appengine.eclipse.core.sdk.GaeSdk;
import com.google.gdt.eclipse.core.StringUtilities;
import com.google.gdt.eclipse.core.launch.ILaunchConfigurationProcessor;
import com.google.gdt.eclipse.core.launch.LaunchConfigurationProcessorUtilities;
import com.google.gdt.eclipse.core.sdk.SdkUtils;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import java.util.List;

/**
 * Processes the "-javaagent" argument for App Engine launches.
 */
public class JavaAgentArgumentProcessor implements
    ILaunchConfigurationProcessor {

  private static final String ARG_JAVAAGENT_PREFIX = "-javaagent:";

  /**
   * Minimum version of App Engine SDK that requires a VM argument to specify
   * the KickStart Java agent.
   */
  private static final String MIN_SDK_VERSION_USING_KICKSTART_AGENT = "1.2.6";

  /**
   * Returns the VM flag to start the KickStart Java agent, or <code>null</code>
   * if the project does not require it.
   * 
   * @throws JavaModelException
   */
  public static String computeExpectedJavaAgentVMArgument(IProject project)
      throws JavaModelException {

    if (!GaeNature.isGaeProject(project)) {
      return null;
    }

    GaeSdk sdk = GaeSdk.findSdkFor(JavaCore.create(project));
    if (sdk == null || !sdk.validate().isOK()) {
      return null;
    }

    if (SdkUtils.compareVersionStrings(sdk.getVersion(),
        MIN_SDK_VERSION_USING_KICKSTART_AGENT) >= 0) {
      String agentPath = sdk.getInstallationPath().append(
          "lib/agent/appengine-agent.jar").toOSString();

      return ARG_JAVAAGENT_PREFIX + agentPath;
    }

    return null;
  }

  public void update(ILaunchConfigurationWorkingCopy launchConfig,
      IJavaProject javaProject, List<String> programArgs, List<String> vmArgs)
      throws CoreException {

    IProject project = javaProject.getProject();
    int javaAgentIndex = StringUtilities.indexOfThatStartsWith(vmArgs,
        ARG_JAVAAGENT_PREFIX, 0);
    int insertionIndex = LaunchConfigurationProcessorUtilities.removeArgsAndReturnInsertionIndex(
        vmArgs, javaAgentIndex, false);

    String javaAgent = computeExpectedJavaAgentVMArgument(project);
    if (javaAgent != null) {
      vmArgs.add(insertionIndex, javaAgent);
    }
  }

  public String validate(ILaunchConfiguration launchConfig,
      IJavaProject javaProject, List<String> programArgs, List<String> vmArgs)
      throws CoreException {

    String javaAgentVmArg = computeExpectedJavaAgentVMArgument(javaProject.getProject());
    if (javaAgentVmArg == null || vmArgs.indexOf(javaAgentVmArg) >= 0) {
      return null;
    }

    StringBuilder sb = new StringBuilder();

    int javaAgentIndex = StringUtilities.indexOfThatStartsWith(vmArgs,
        ARG_JAVAAGENT_PREFIX, 0);
    if (javaAgentIndex == -1) {
      sb.append("Projects using App Engine 1.2.6 or later require a Java agent. Add this VM argument:\n");
    } else {
      sb.append("The Java agent VM argument is incorrect for the current project. Change it to:\n");
    }

    sb.append(javaAgentVmArg);
    return sb.toString();
  }
}
