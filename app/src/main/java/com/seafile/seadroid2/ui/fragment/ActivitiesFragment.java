package com.seafile.seadroid2.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.seafile.seadroid2.R;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.SeafRepo;
import com.seafile.seadroid2.ssl.CertsManager;
import com.seafile.seadroid2.ui.NavContext;
import com.seafile.seadroid2.ui.ToastUtils;
import com.seafile.seadroid2.ui.activity.BrowserActivity;
import com.seafile.seadroid2.ui.activity.FileActivity;
import com.seafile.seadroid2.ui.dialog.TaskDialog;
import com.seafile.seadroid2.util.Utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActivitiesFragment extends Fragment {
    private static final String DEBUG_TAG = "ActivitiesFragment";

    private static final String ACTIVITIES_URL = "api2/html/events/";

    private WebView webView = null;
    private FrameLayout mWebViewContainer = null;
    private View mProgressContainer;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(DEBUG_TAG, "ActivitiesFragment Attached");
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    private BrowserActivity getBrowserActivity() {
        return (BrowserActivity)getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activities_fragment, container, false);
    }

    @Override
    public void onPause() {
        Log.d(DEBUG_TAG, "onPause");
        super.onPause();

        if (webView != null) {
            mWebViewContainer.removeView(webView);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.d(DEBUG_TAG, "onActivityCreated");

        mWebViewContainer = (FrameLayout)getView().findViewById(R.id.webViewContainer);
        mProgressContainer = getView().findViewById(R.id.progressContainer);

        if (webView == null) {
            webView = new WebView(getBrowserActivity());
            initWebView();
            loadActivitiesPage();
        }

        getBrowserActivity().supportInvalidateOptionsMenu();

        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        // We add the webView on create and remove it on pause, so as to make
        // it retain state. Otherwise the webview will reload the url every
        // time the tab is switched.
        super.onResume();
        mWebViewContainer.addView(webView);
    }

    public void refreshView() {
        loadActivitiesPage();
    }

    private void initWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new MyWebViewClient());

        webView.setWebChromeClient(new MyWebChromeClient());
    }

    private void loadActivitiesPage() {
        showPageLoading(true);
        Account account = getBrowserActivity().getAccount();
        String url = account.getServer() + ACTIVITIES_URL;

        webView.loadUrl(url, getExtraHeaders());
    }

    private Map<String, String> getExtraHeaders() {
        Account account = getBrowserActivity().getAccount();
        String token = "Token " + account.getToken();
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", token);

        return headers;
    }

    private void showPageLoading(boolean pageLoading) {
        if (getBrowserActivity() == null) {
            return;
        }

        if (!pageLoading) {
            mProgressContainer.startAnimation(AnimationUtils.loadAnimation(
                                    getBrowserActivity(), android.R.anim.fade_out));
            webView.startAnimation(AnimationUtils.loadAnimation(
                                getBrowserActivity(), android.R.anim.fade_in));
            mProgressContainer.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
        } else {
            mProgressContainer.startAnimation(AnimationUtils.loadAnimation(
                    getBrowserActivity(), android.R.anim.fade_in));
            webView.startAnimation(AnimationUtils.loadAnimation(
                    getBrowserActivity(), android.R.anim.fade_out));

            mProgressContainer.setVisibility(View.VISIBLE);
            webView.setVisibility(View.INVISIBLE);
        }
    }

    private void viewRepo(final String repoID) {
        final SeafRepo repo = getBrowserActivity().getDataManager().getCachedRepoByID(repoID);

        if (repo == null) {
            ToastUtils.show(getBrowserActivity(), getString(R.string.repo_not_found));
            return;
        }

        if (repo.encrypted && !DataManager.getRepoEnckeySet(repo.id)) {
            String encKey = DataManager.getRepoEncKey(repo.id);
            getBrowserActivity().showPasswordDialog(repo.name, repo.id,
                    new TaskDialog.TaskDialogListener() {
                        @Override
                        public void onTaskSuccess() {
                            switchTab(repoID, repo.getName(), repo.getRootDirID());
                        }
                    }, encKey);

        } else {
            switchTab(repoID, repo.getName(), repo.getRootDirID());
        }
    }

    private void viewFile(final String repoID, final String path) {
        final SeafRepo repo = getBrowserActivity().getDataManager().getCachedRepoByID(repoID);

        if (repo == null) {
            ToastUtils.show(getBrowserActivity(), R.string.library_not_found);
            return;
        }

        if (repo.encrypted && !DataManager.getRepoEnckeySet(repo.id)) {
            String encKey = DataManager.getRepoEncKey(repo.id);
            getBrowserActivity().showPasswordDialog(repo.name, repo.id,
                    new TaskDialog.TaskDialogListener() {
                        @Override
                        public void onTaskSuccess() {
                            openFile(repoID, repo.getName(), path);
                        }
                    }, encKey);

        } else {
            openFile(repoID, repo.getName(), path);
        }
    }

    private void switchTab(String repoID, String repoName, String repoDir) {
        NavContext nav = getBrowserActivity().getNavContext();
        nav.setRepoID(repoID);
        nav.setRepoName(repoName);
        nav.setDir("/", repoDir);

        // switch to LIBRARY TAB
        getBrowserActivity().setCurrentPosition(BrowserActivity.INDEX_LIBRARY_TAB);
    }

    private void openFile(String repoID, String repoName, String filePath) {
        int taskID = getBrowserActivity().getTransferService().addDownloadTask(getBrowserActivity().getAccount(), repoName, repoID, filePath);
        Intent intent = new Intent(getActivity(), FileActivity.class);
        intent.putExtra("repoName", repoName);
        intent.putExtra("repoID", repoID);
        intent.putExtra("filePath", filePath);
        intent.putExtra("account", getBrowserActivity().getAccount());
        intent.putExtra("taskID", taskID);
        getBrowserActivity().startActivityForResult(intent, BrowserActivity.DOWNLOAD_FILE_REQUEST);
    }

    private class MyWebViewClient extends WebViewClient {
        // Display error messages
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (getBrowserActivity() != null) {
                Toast.makeText(getBrowserActivity(), "Error: " + description, Toast.LENGTH_SHORT).show();
                showPageLoading(false);
            }
        }

        // Ignore SSL certificate validate
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            BrowserActivity mActivity = getBrowserActivity();
            if (mActivity == null) {
                return;
            }

            Account account = mActivity.getAccount();

            SslCertificate sslCert = error.getCertificate();
            X509Certificate savedCert = CertsManager.instance().getCertificate(account);

            if (Utils.isSameCert(sslCert, savedCert)) {
                Log.d(DEBUG_TAG, "trust this cert");
                handler.proceed();
            } else {
                Log.d(DEBUG_TAG, "cert is not trusted");
                ToastUtils.show(mActivity, R.string.ssl_error);
                showPageLoading(false);
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView webView, String url) {
            Log.d(DEBUG_TAG, "loading url " + url);
            String API_URL_PREFIX= "api://";
            if (!url.startsWith(API_URL_PREFIX)) {
                return false;
            }

            String req = url.substring(API_URL_PREFIX.length(), url.length());

            Pattern REPO_PATTERN = Pattern.compile("repos/([-a-f0-9]{36})/?");
            Pattern REPO_FILE_PATTERN = Pattern.compile("repo/([-a-f0-9]{36})/files/\\?p=(.+)");
            Matcher matcher;

            if ((matcher = fullMatch(REPO_PATTERN, req)) != null) {
                String repoID = matcher.group(1);
                viewRepo(repoID);

            } else if ((matcher = fullMatch(REPO_FILE_PATTERN, req)) != null) {
                String repoID = matcher.group(1);

                try {
                    String path = URLDecoder.decode(matcher.group(2), "UTF-8");
                    viewFile(repoID, path);
                } catch (UnsupportedEncodingException e) {
                    // Ignore
                }
            }

            return true;
        }

        @Override
        public void onPageFinished(WebView webView, String url) {
            Log.d(DEBUG_TAG, "onPageFinished " + url);
            if (getBrowserActivity() != null) {
                String js = String.format("javascript:setToken('%s')",
                                          getBrowserActivity().getAccount().getToken());
                webView.loadUrl(js);
            }
            showPageLoading(false);
        }
    }

    private static Matcher fullMatch(Pattern pattern, String str) {
        Matcher matcher = pattern.matcher(str);
        return matcher.matches() ? matcher : null;
    }

    private class MyWebChromeClient extends WebChromeClient {

        // For debug js
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            Log.d(DEBUG_TAG, "alert: " + message);
            return super.onJsAlert(view, url, message, result);
        }
    }
}
