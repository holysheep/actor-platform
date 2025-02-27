package im.actor.sdk.controllers.auth;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.MenuItem;

import im.actor.core.AuthState;
import im.actor.core.entity.AuthCodeRes;
import im.actor.core.entity.AuthRes;
import im.actor.core.entity.AuthStartRes;
import im.actor.core.entity.Sex;
import im.actor.core.network.RpcException;
import im.actor.core.network.RpcInternalException;
import im.actor.core.network.RpcTimeoutException;
import im.actor.runtime.actors.Actor;
import im.actor.runtime.actors.ActorCreator;
import im.actor.runtime.actors.ActorRef;
import im.actor.runtime.actors.ActorSystem;
import im.actor.runtime.actors.Props;
import im.actor.runtime.function.Consumer;
import im.actor.runtime.promise.Promise;
import im.actor.sdk.ActorSDK;
import im.actor.sdk.R;
import im.actor.sdk.controllers.activity.ActorMainActivity;
import im.actor.sdk.controllers.activity.BaseFragmentActivity;

import static im.actor.sdk.util.ActorSDKMessenger.messenger;

public class AuthActivity extends BaseFragmentActivity {

    public static final String AUTH_TYPE_KEY = "auth_type";
    public static final String SIGN_TYPE_KEY = "sign_type";
    public static final int AUTH_TYPE_PHONE = 1;
    public static final int AUTH_TYPE_EMAIL = 2;

    public static final int SIGN_TYPE_IN = 3;
    public static final int SIGN_TYPE_UP = 4;
    private static final int OAUTH_DIALOG = 1;
    private ProgressDialog progressDialog;
    private AlertDialog alertDialog;
    private AuthState state;
    private int availableAuthType = AUTH_TYPE_PHONE;
    private int currentAuthType = AUTH_TYPE_PHONE;
    private int signType;
    private BaseAuthFragment signFragment;
    private long currentPhone;
    private String currentEmail;
    private String transactionHash;
    private String currentCode;
    private boolean isRegistered = false;
    private String currentName;
    private Sex currentSex;
    private ActorRef authActor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authActor = ActorSystem.system().actorOf(Props.create(new ActorCreator() {
            @Override
            public Actor create() {
                return new Actor();
            }
        }), "actor/auth_promises_actor");
        signType = getIntent().getIntExtra(SIGN_TYPE_KEY, SIGN_TYPE_IN);
        if (savedInstanceState == null) {
            updateState(AuthState.AUTH_START);
        } else {
            currentPhone = savedInstanceState.getLong("currentPhone", 0);
            currentEmail = savedInstanceState.getString("currentEmail");
            transactionHash = savedInstanceState.getString("transactionHash");
            currentCode = savedInstanceState.getString("currentCode");
            isRegistered = savedInstanceState.getBoolean("isRegistered", false);
            currentName = savedInstanceState.getString("currentName");
            signType = savedInstanceState.getInt("signType", SIGN_TYPE_UP);
            state = Enum.valueOf(AuthState.class, savedInstanceState.getString("state", "AUTH_START"));
            updateState(state);
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
        }
        return super.onOptionsItemSelected(item);
    }


    private void updateState(AuthState state) {
        updateState(state, false);
    }

    private void updateState(AuthState state, boolean force) {
        if (this.state != null && (this.state == state && !force)) {
            return;
        }

        // if we show the next fragment when app is in background and not visible , app crashes!
        // e.g when the GSM data is off and after trying to send code we go to settings to turn on, app is going invisible and ...
        if (state != AuthState.LOGGED_IN && getIsResumed() == false) {
            return;
        }

        this.state = state;

        switch (state) {
            case AUTH_START:

                if (signType == SIGN_TYPE_UP) {
                    updateState(AuthState.SIGN_UP);
                } else if (signType == SIGN_TYPE_IN) {
                    showFragment(new SignInFragment(), false, false);
                }

                break;
            case SIGN_UP:
                showFragment(new SignUpFragment(), false, false);
                break;
            case AUTH_PHONE:
                currentAuthType = AUTH_TYPE_PHONE;
                showFragment(signFragment, false, false);
                break;
            case AUTH_EMAIL:
                currentAuthType = AUTH_TYPE_EMAIL;
                showFragment(signFragment, false, false);
                break;
            case CODE_VALIDATION_PHONE:
            case CODE_VALIDATION_EMAIL:
                Fragment signInFragment = new ValidateCodeFragment();
                Bundle args = new Bundle();

                args.putString("authType", state == AuthState.CODE_VALIDATION_EMAIL ? ValidateCodeFragment.AUTH_TYPE_EMAIL : ValidateCodeFragment.AUTH_TYPE_PHONE);
                args.putBoolean(ValidateCodeFragment.AUTH_TYPE_SIGN, signType == SIGN_TYPE_IN);
                args.putString("authId", state == AuthState.CODE_VALIDATION_EMAIL ? currentEmail : Long.toString(currentPhone));
                signInFragment.setArguments(args);
                showFragment(signInFragment, false, false);
                break;
            case LOGGED_IN:
                finish();
                startActivity(new Intent(this, ActorMainActivity.class));
                break;
        }
    }

    public void startAuth(String name) {
        currentName = name;
        currentSex = Sex.UNKNOWN;
        availableAuthType = ActorSDK.sharedActor().getAuthType();
        BaseAuthFragment baseAuthFragment;
        AuthState authState;
        if ((availableAuthType & AUTH_TYPE_PHONE) == AUTH_TYPE_PHONE) {
            baseAuthFragment = new SignPhoneFragment();
            authState = AuthState.AUTH_PHONE;
        } else if ((availableAuthType & AUTH_TYPE_EMAIL) == AUTH_TYPE_EMAIL) {
            baseAuthFragment = new SignEmailFragment();
            authState = AuthState.AUTH_EMAIL;
        } else {
            // none of valid auth types selected - force crash?
            return;
        }

        signFragment = ActorSDK.sharedActor().getDelegatedFragment(ActorSDK.sharedActor().getDelegate().getAuthStartIntent(), baseAuthFragment, BaseAuthFragment.class);

        updateState(authState);
    }

    public void startPhoneAuth(Promise<AuthStartRes> promise, long phone) {
        currentAuthType = AUTH_TYPE_PHONE;
        currentPhone = phone;
        startAuth(promise);
    }

    public void startEmailAuth(Promise<AuthStartRes> promise, String email) {
        currentAuthType = AUTH_TYPE_EMAIL;
        currentEmail = email;
        startAuth(promise);
    }

    private void startAuth(Promise<AuthStartRes> res) {
        showProgress();
        res.then(new Consumer<AuthStartRes>() {
            @Override
            public void apply(AuthStartRes authStartRes) {
                if (dismissProgress()) {
                    transactionHash = authStartRes.getTransactionHash();
                    isRegistered = authStartRes.isRegistered();
                    switch (authStartRes.getAuthMode()) {
                        case OTP:
                            switch (currentAuthType) {
                                case AUTH_TYPE_PHONE:
                                    updateState(AuthState.CODE_VALIDATION_PHONE);
                                    break;

                                case AUTH_TYPE_EMAIL:
                                    updateState(AuthState.CODE_VALIDATION_EMAIL);
                                    break;
                            }
                            break;

                        default:
                            //not supported AuthMode - force crash?
                    }
                }
            }
        }).failure(new Consumer<Exception>() {
            @Override
            public void apply(Exception e) {
                handleAuthError(e);
            }
        }).done(authActor);
    }


    public void validateCode(Promise<AuthCodeRes> promise, String code) {
        currentCode = code;
        showProgress();
        promise.then(new Consumer<AuthCodeRes>() {
            @Override
            public void apply(AuthCodeRes authCodeRes) {
                if (dismissProgress()) {
                    transactionHash = authCodeRes.getTransactionHash();
                    if (!authCodeRes.isNeedToSignup()) {
                        messenger().doCompleteAuth(authCodeRes.getResult()).then(new Consumer<Boolean>() {
                            @Override
                            public void apply(Boolean aBoolean) {
                                updateState(AuthState.LOGGED_IN);
                            }
                        }).failure(new Consumer<Exception>() {
                            @Override
                            public void apply(Exception e) {
                                handleAuthError(e);
                            }
                        }).done(authActor);
                    } else {
                        signUp(messenger().doSignup(currentName, currentSex, transactionHash), currentName, currentSex);
                    }
                }
            }
        }).failure(new Consumer<Exception>() {
            @Override
            public void apply(Exception e) {
                handleAuthError(e);
            }
        }).done(authActor);
    }

    public void signUp(Promise<AuthRes> promise, String name, Sex sex) {
        currentName = name;
        currentSex = sex;
        promise.then(new Consumer<AuthRes>() {
            @Override
            public void apply(AuthRes authRes) {
                dismissProgress();
                messenger().doCompleteAuth(authRes).then(new Consumer<Boolean>() {
                    @Override
                    public void apply(Boolean aBoolean) {
                        updateState(AuthState.LOGGED_IN);
                    }
                }).failure(new Consumer<Exception>() {
                    @Override
                    public void apply(Exception e) {
                        handleAuthError(e);
                    }
                }).done(authActor);

            }
        }).failure(new Consumer<Exception>() {
            @Override
            public void apply(Exception e) {
                handleAuthError(e);
            }
        })
                .done(authActor).done(authActor);
    }

    public void handleAuthError(final Exception e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (dismissProgress()) {
                    boolean canTryAgain = false;
                    String message = getString(R.string.error_unknown);
                    String tag = "UNKNOWN";
                    if (e instanceof RpcException) {
                        RpcException re = (RpcException) e;
                        tag = re.getTag();
                        if (re instanceof RpcInternalException) {
                            message = getString(R.string.error_unknown);
                            canTryAgain = true;
                        } else if (re instanceof RpcTimeoutException) {
                            message = getString(R.string.error_connection);
                            canTryAgain = true;
                        } else {
                            if ("PHONE_CODE_EXPIRED".equals(re.getTag())) {
                                message = getString(R.string.auth_error_code_expired);
                                canTryAgain = false;
                            } else if ("PHONE_CODE_INVALID".equals(re.getTag())) {
                                message = getString(R.string.auth_error_code_invalid);
                                canTryAgain = false;
                            } else if ("FAILED_GET_OAUTH2_TOKEN".equals(re.getTag())) {
                                message = getString(R.string.auth_error_failed_get_oauth2_token);
                                canTryAgain = false;
                            } else {
                                message = re.getMessage();
                                canTryAgain = re.isCanTryAgain();
                            }
                        }
                    }


                    try {
                        if (canTryAgain) {
                            new AlertDialog.Builder(AuthActivity.this)
                                    .setMessage(message)
                                    .setPositiveButton(R.string.dialog_try_again, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dismissAlert();
                                            switch (state) {
                                                case AUTH_EMAIL:
                                                case AUTH_PHONE:
                                                    switch (currentAuthType) {
                                                        case AUTH_TYPE_PHONE:
                                                            startAuth(messenger().doStartPhoneAuth(currentPhone));
                                                            break;

                                                        case AUTH_TYPE_EMAIL:
                                                            startAuth(messenger().doStartEmailAuth(currentEmail));

                                                            break;
                                                    }
                                                    break;
                                                case CODE_VALIDATION_EMAIL:
                                                case CODE_VALIDATION_PHONE:
                                                    validateCode(messenger().doValidateCode(currentCode, transactionHash), currentCode);
                                                    break;

                                                case SIGN_UP:
                                                    signUp(messenger().doSignup(currentName, currentSex, transactionHash), currentName, currentSex);
                                                    break;
                                            }

                                        }
                                    })
                                    .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dismissAlert();
                                            updateState(AuthState.AUTH_START);
                                        }
                                    }).setCancelable(false)
                                    .show()
                                    .setCanceledOnTouchOutside(false);
                        } else {
                            new AlertDialog.Builder(AuthActivity.this)
                                    .setMessage(message)
                                    .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dismissAlert();
                                            if (signType == SIGN_TYPE_UP) {
                                                if (currentAuthType == AUTH_TYPE_EMAIL) {
                                                    switchToEmailAuth();
                                                } else if (currentAuthType == AUTH_TYPE_PHONE) {
                                                    switchToPhoneAuth();
                                                } else {
                                                    updateState(AuthState.AUTH_START);
                                                }
                                            } else if (signType == SIGN_TYPE_IN) {
                                                startSignIn();
                                            } else {
                                                updateState(AuthState.AUTH_START);
                                            }

                                        }
                                    })
                                    .setCancelable(false)
                                    .show()
                                    .setCanceledOnTouchOutside(false);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

    }

    public void switchToEmailAuth() {
        signFragment = ActorSDK.sharedActor().getDelegatedFragment(ActorSDK.sharedActor().getDelegate().getAuthStartIntent(), new SignEmailFragment(), BaseAuthFragment.class);
        updateState(AuthState.AUTH_EMAIL);
    }

    public void switchToPhoneAuth() {
        signFragment = ActorSDK.sharedActor().getDelegatedFragment(ActorSDK.sharedActor().getDelegate().getAuthStartIntent(), new SignPhoneFragment(), BaseAuthFragment.class);
        updateState(AuthState.AUTH_PHONE);
    }


    public void startSignIn() {
        signType = SIGN_TYPE_IN;
        updateState(AuthState.AUTH_START, true);
    }


    public void startSignUp() {
        signType = SIGN_TYPE_UP;
        updateState(AuthState.AUTH_START, true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("currentPhone", currentPhone);
        outState.putString("currentEmail", currentEmail);
        outState.putString("transactionHash", transactionHash);
        outState.putString("currentCode", currentCode);
        outState.putBoolean("isRegistered", isRegistered);
        outState.putString("currentName", currentName);
        outState.putInt("signType", signType);
        outState.putString("state", state.toString());
    }

    public void showProgress() {
        dismissProgress();
        progressDialog = new ProgressDialog(this);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.setTitle(getString(R.string.progress_common));
        progressDialog.show();
    }

    @Override
    protected void onPause() {
        super.onPause();

        dismissProgress();
        dismissAlert();
    }

    private boolean dismissProgress() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
            return true;
        }
        return false;
    }

    private void dismissAlert() {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
    }

    public String getTransactionHash() {
        return transactionHash;
    }
}



