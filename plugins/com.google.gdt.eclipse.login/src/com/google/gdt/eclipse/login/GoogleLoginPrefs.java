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
package com.google.gdt.eclipse.login;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Handles preferences for Google Login plugin.
 * 
 */
public class GoogleLoginPrefs {

  /**
   * Class for marshaling stored credentials to and from the preferences store.
   */
  public static class Credentials {
    String storedEmail;
    String storedScopes;
    String accessToken;
    String refreshToken;

    public Credentials(String accessToken, String refreshToken, String storedEmail,
        String storedScopes) {
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
      this.storedEmail = storedEmail;
      this.storedScopes = storedScopes;
    }
  }

  private static final String CREDENTIALS_EMAIL_KEY = "credentials_email";

  private static final String CREDENTIALS_ACCESS_TOKEN_KEY = "credentials_access_token";

  private static final String CREDENTIALS_REFRESH_TOKEN_KEY = "credentials_refresh_token";

  private static final String ICON_ONLY_KEY = "icon_only";

  private static final String LOGOUT_ON_EXIT_KEY = "logout_on_exit";
  
  private static final String OAUTH_SCOPES_KEY = "oauth_scopes";

  private static final String PREFERENCES_PATH = "/com/google/gdt/eclipse/login";
  
  public static void clearStoredCredentials() {
    Preferences prefs = getPrefs();
    prefs.remove(CREDENTIALS_ACCESS_TOKEN_KEY);
    prefs.remove(CREDENTIALS_REFRESH_TOKEN_KEY);
    prefs.remove(CREDENTIALS_EMAIL_KEY);
    prefs.remove(OAUTH_SCOPES_KEY);
    flushPrefs(prefs);
  }

  public static boolean getIconOnlyPref() {
    return getPrefs().getBoolean(ICON_ONLY_KEY, false);
  }

  public static boolean getLogoutOnExitPref() {
    return getPrefs().getBoolean(LOGOUT_ON_EXIT_KEY, false);
  }

  public static Credentials loadCredentials() {
    Preferences prefs = getPrefs();

    String accessToken = prefs.get(CREDENTIALS_ACCESS_TOKEN_KEY, null);
    String refreshToken = prefs.get(CREDENTIALS_REFRESH_TOKEN_KEY, null);
    String storedEmail = prefs.get(CREDENTIALS_EMAIL_KEY, null);
    String storedScopes = prefs.get(OAUTH_SCOPES_KEY, null);

    return new Credentials(accessToken, refreshToken, storedEmail, storedScopes);
  }
  
  public static void saveCredentials(Credentials p) {
    Preferences prefs = getPrefs();
    prefs.put(CREDENTIALS_ACCESS_TOKEN_KEY, p.accessToken);
    prefs.put(CREDENTIALS_REFRESH_TOKEN_KEY, p.refreshToken);

    // we save the scopes so that if the user updates the plugin and the
    // scopes change, we can force the plugin to log out.
    prefs.put(OAUTH_SCOPES_KEY, p.storedScopes);

    if (p.storedEmail != null) {
      prefs.put(CREDENTIALS_EMAIL_KEY, p.storedEmail);
    }
    flushPrefs(prefs);
  }

  public static void saveIconOnlyPref(boolean logoutOnExit) {
    Preferences prefs = getPrefs();
    prefs.putBoolean(ICON_ONLY_KEY, logoutOnExit);
    flushPrefs(prefs);
  }
  
  /**
   * Sets and saves to the preferences store if the user should be logged out
   * when Eclipse extis.
   */
  public static void saveLogoutOnExitPref(boolean logoutOnExit) {
    Preferences prefs = getPrefs();
    prefs.putBoolean(LOGOUT_ON_EXIT_KEY, logoutOnExit);
    flushPrefs(prefs);
  }

  private static void flushPrefs(Preferences prefs) {
    try {
      prefs.flush();
    } catch (BackingStoreException e) {
      GoogleLoginPlugin.logError(
          "Could not flush preferences while saving login credentials", e);
    }
  }
  
  private static Preferences getPrefs() {
    return Preferences.userRoot().node(PREFERENCES_PATH);
  }

}
