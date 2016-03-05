package com.seafile.seadroid2.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.crypto.Crypto;
import com.seafile.seadroid2.data.DataManager;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

class SetPasswordTask extends TaskDialog.Task {
    public static final String DEBUG_TAG = SetPasswordTask.class.getSimpleName();

    String repoID;
    String password;
    String magic, randomKey;
    int version;
    DataManager dataManager;

    public SetPasswordTask(String repoID, String password, int version, String magic, String randomKey,
                           DataManager dataManager) {
        this.repoID = repoID;
        this.password = password;
        this.magic = magic;
        this.randomKey = randomKey;
        this.version = version;
        this.dataManager = dataManager;
    }

    @Override
    protected void runTask() {
        try {
            // dataManager.setEncKey(repoID, encKey);
            Crypto.verifyRepoPassword(repoID, password, version, magic);
        } catch (SeafException e) {
            setTaskException(e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
    }
}

public class PasswordDialog extends TaskDialog {
    private static final String STATE_TASK_REPO_NAME = "set_password_task.repo_name";
    private static final String STATE_TASK_REPO_ID = "set_password_task.repo_id";
    private static final String STATE_TASK_MAGIC = "set_password_task.magic";
    private static final String STATE_TASK_PASSWORD = "set_password_task.password";
    private static final String STATE_ACCOUNT = "set_password_task.account";

    private EditText encKeyText;
    private String repoID, repoName, magic, randomKey;
    private int version;
    private DataManager dataManager;
    private Account account;
    private String encKey;

    public void setRepo(String repoName, String repoID, String magic, String randomKey, int version, Account account) {
        this.repoName = repoName;
        this.repoID = repoID;
        this.version = version;
        this.account = account;
        this.magic = magic;
        this.randomKey = randomKey;
    }

    private DataManager getDataManager() {
        if (dataManager == null) {
            dataManager = new DataManager(account);
        }

        return dataManager;
    }

    @Override
    protected View createDialogContentView(LayoutInflater inflater, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_password, null);
        encKeyText = (EditText) view.findViewById(R.id.password);

        if (savedInstanceState != null) {
            repoName = savedInstanceState.getString(STATE_TASK_REPO_NAME);
            repoID = savedInstanceState.getString(STATE_TASK_REPO_ID);
            magic = savedInstanceState.getString(STATE_TASK_MAGIC);
            account = (Account)savedInstanceState.getParcelable(STATE_ACCOUNT);
        }

        if (encKey != null) {
            encKeyText.setText(encKey);
        }

        return view;
    }

    @Override
    protected void onDialogCreated(Dialog dialog) {
        dialog.setTitle(repoName);
    }

    @Override
    protected void onSaveDialogContentState(Bundle outState) {
        outState.putString(STATE_TASK_REPO_NAME, repoName);
        outState.putString(STATE_TASK_REPO_ID, repoID);
        outState.putString(STATE_TASK_MAGIC, magic);
        outState.putParcelable(STATE_ACCOUNT, account);
    }

    @Override
    protected void onValidateUserInput() throws Exception {
        String encKey = encKeyText.getText().toString().trim();

        if (encKey.length() == 0) {
            String err = getActivity().getResources().getString(R.string.password_empty);
            throw new Exception(err);
        }
    }

    @Override
    protected void disableInput() {
        super.disableInput();
        encKeyText.setEnabled(false);
    }

    @Override
    protected void enableInput() {
        super.enableInput();
        encKeyText.setEnabled(true);
    }

    @Override
    protected SetPasswordTask prepareTask() {
        String encKey = encKeyText.getText().toString().trim();
        SetPasswordTask task = new SetPasswordTask(repoID, encKey, version, magic, randomKey, getDataManager());
        return task;
    }

    @Override
    protected void onSaveTaskState(Bundle outState) {
        SetPasswordTask task = (SetPasswordTask)getTask();
        if (task != null) {
            outState.putString(STATE_TASK_PASSWORD, task.password);
        }
    }

    @Override
    protected SetPasswordTask onRestoreTaskState(Bundle outState) {
        if (outState == null) {
            return null;
        }

        String password = outState.getString(STATE_TASK_PASSWORD);
        if (password != null) {
            return new SetPasswordTask(repoID, password, version, magic, randomKey, getDataManager());
        } else {
            return null;
        }
    }

    public void setEncKey(String encKey) {
        this.encKey = encKey;
    }

    @Override
    protected boolean executeTaskImmediately() {
        return encKey != null;
    }

    public static final String TAG = PasswordDialog.class.getSimpleName();
    @Override
    public void onTaskSuccess() {
        String password = encKeyText.getText().toString().trim();
        try {
            final String encKey = Crypto.deriveKeyPbkdf2(password, randomKey, version);
            final byte[] encIV = Crypto.deriveIVPbkdf2((Crypto.fromHex(encKey)));
            DataManager.saveRepoSecretKey(repoID, encKey);
            DataManager.setRepoEncIV(repoID, Crypto.toHex(encIV));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        super.onTaskSuccess();
    }

    @Override
    protected String getErrorFromException(SeafException e) {
        if (e.getCode() == 400 || e.getCode() == 7) {
            return getString(R.string.wrong_password);
        }
        return e.getMessage();
    }
}
