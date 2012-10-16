package @PackageName@;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.TransientAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.api.client.googleapis.extensions.android2.auth.GoogleAccountManager;
import com.google.api.client.http.BackOffPolicy;
import com.google.api.client.http.ExponentialBackOffPolicy;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;

import java.io.IOException;

/**
 * Manages account selection and authorization for Google accounts.
 */
public final class GoogleAccountCredential implements HttpRequestInitializer {

  final Context context;
  final String scope;
  private String accountName;
  private final GoogleAccountManager accountManager;
  private Account account;

  /**
   * @param context context
   * @param scope scope to use on {@link GoogleAuthUtil#authenticate}
   */
  private GoogleAccountCredential(Context context, String scope) {
    accountManager = new GoogleAccountManager(context);
    this.context = context;
    this.scope = scope;
  }

  /**
   * Constructor a new instance using OAuth 2.0 scopes.
   * 
   * @param context context
   * @param scopes OAuth 2.0 scopes
   * @return new instance
   */
  public static GoogleAccountCredential usingOAuth2(Context context, String... scopes) {
    Preconditions.checkArgument(scopes.length != 0);
    String scope = "oauth2:" + Joiner.on(' ').join(scopes);
    return new GoogleAccountCredential(context, scope);
  }

  /** Sets the audience scope to use with Google Cloud Endpoints. */
  public static GoogleAccountCredential usingAudience(Context context, String audience) {
    Preconditions.checkArgument(audience.length() != 0);
    String scope = "audience:" + audience;
    return new GoogleAccountCredential(context, scope);
  }

  /**
   * Sets the selected Google account name (e-mail address), for example {@code "johndoe@gmail.com"}
   * , or {@code null} for none.
   */
  public GoogleAccountCredential setAccountName(String accountName) {
    this.accountName = accountName;
    account = accountManager.getAccountByName(accountName);
    return this;
  }

  public void initialize(HttpRequest request) {
    RequestHandler handler = new RequestHandler();
    request.setInterceptor(handler);
    request.setUnsuccessfulResponseHandler(handler);
    request.setBackOffPolicy(new ExponentialBackOffPolicy());
  }

  /**
   * Returns the selected Google account name (e-mail address), for example
   * {@code "johndoe@gmail.com"}, or {@code null} for none.
   */
  public String getAccountName() {
    return accountName;
  }

  /** Returns the selected Google account or {@code null} for none. */
  public Account getAccount() {
    return account;
  }

  /** Returns all Google accounts or {@code null} for none. */
  public Account[] getAllAccounts() {
    return accountManager.getAccounts();
  }

  /**
   * Returns an intent to show the user to select a Google account, or create a new one if there are
   * none on the device yet.
   * 
   * <p>
   * Must be run from the main UI thread.
   * </p>
   */
  public Intent newChooseAccountIntent() {
    return AccountPicker.newChooseAccountIntent(account,
        null,
        new String[] {GoogleAccountManager.ACCOUNT_TYPE},
        true,
        null,
        null,
        null,
        null);
  }

  /**
   * Returns an OAuth 2.0 access token.
   * 
   * <p>
   * Must be run from a background thread, not the main UI thread.
   * </p>
   */
  public String getToken() throws IOException {
    BackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    while (true) {
      try {
        try {
          return GoogleAuthUtil.getToken(context, accountName, scope);
        } catch (TransientAuthException e) {
          // network or server error, so retry use
          long backOffMillis = backOffPolicy.getNextBackOffMillis();
          if (backOffMillis == BackOffPolicy.STOP) {
            throw e;
          }
          // sleep
          try {
            Thread.sleep(backOffMillis);
          } catch (InterruptedException e2) {
            // ignore
          }
        }
      } catch (GoogleAuthException exception) {
        IOException io = new IOException();
        io.initCause(exception);
        throw io;
      }
    }
  }

  class RequestHandler implements HttpExecuteInterceptor, HttpUnsuccessfulResponseHandler {

    /** Whether we've received a 401 error code indicating the token is invalid. */
    boolean received401;
    String token;

    public void intercept(HttpRequest request) throws IOException {
      token = getToken();
      request.getHeaders().setAuthorization("Bearer " + token);
    }

    public boolean handleResponse(
        HttpRequest request, HttpResponse response, boolean supportsRetry) {
      if (response.getStatusCode() == 401 && !received401) {
        received401 = true;
        GoogleAuthUtil.invalidateToken(context, token);
        return true;
      }
      return false;
    }
  }
}
