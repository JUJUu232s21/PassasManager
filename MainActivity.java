import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import javax.crypto.SecretKey;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private final List<String> items = new ArrayList<>();
    private ImageView addNewKey;
    private BiometricPrompt biometricPrompt;
    private char[] currentMasterPassword;
    private int actionBiometricAttempts = 0;
    private final Handler clipboardHandler = new Handler(Looper.getMainLooper());
    private Runnable clipboardClearRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
                overridePendingTransition(0, 0);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        recyclerView = findViewById(R.id.recyclerView);
        addNewKey = findViewById(R.id.addNewKey);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, 0);
            return insets;
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        MyAdapter adapter = new MyAdapter(items);
        adapter.setOnItemClickListener(position -> {
            String passphrase = getCurrentDbPassphrase();
            if (passphrase.isEmpty()) return;

            authenticateForAction(() -> {
                PasswordDatabase db = new PasswordDatabase(MainActivity.this, passphrase);
                List<PasswordDatabase.PasswordItem> list = db.getAllPasswords();
                if (position >= 0 && position < list.size()) {
                    PasswordDatabase.PasswordItem item = list.get(position);
                    SecretKey encKey = getCurrentEncryptionKey();
                    String decryptedPassword = PasswordSecurity.decryptAES(item.getPassword(), encKey);
                    String message = decryptedPassword != null ? "Пароль: " + decryptedPassword : "[Ошибка расшифровки: Данные повреждены]";

                    AlertDialog dialog = new AlertDialog.Builder(MainActivity.this, R.style.CustomAlertDialog)
                            .setTitle(item.getTitle())
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .setNegativeButton("Копировать", (d, which) -> {
                                if (decryptedPassword != null) {
                                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    android.content.ClipDescription description = new android.content.ClipDescription("Password", new String[]{android.content.ClipDescription.MIMETYPE_TEXT_PLAIN});
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        android.os.PersistableBundle extras = new android.os.PersistableBundle();
                                        extras.putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true);
                                        description.setExtras(extras);
                                    }
                                    ClipData clip = new ClipData(description, new ClipData.Item(decryptedPassword));
                                    if (clipboard != null) {
                                        clipboard.setPrimaryClip(clip);
                                        Toast.makeText(MainActivity.this, "Пароль скопирован! (очистка через 30 сек)", Toast.LENGTH_SHORT).show();

                                        if (clipboardClearRunnable != null) {
                                            clipboardHandler.removeCallbacks(clipboardClearRunnable);
                                        }

                                        clipboardClearRunnable = () -> {
                                            try {
                                                if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription() != null) {
                                                    if (clipboard.getPrimaryClipDescription().getLabel() != null &&
                                                            clipboard.getPrimaryClipDescription().getLabel().toString().equals("Password")) {
                                                        clipboard.clearPrimaryClip();
                                                    }
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        };

                                        clipboardHandler.postDelayed(clipboardClearRunnable, 30_000);
                                    }
                                }
                            })
                            .create();
                    dialog.setOnShowListener(dialogInterface -> {
                        if (dialog.getWindow() != null) {
                            dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                        }
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
                    });
                    dialog.show();
                }
            }, () -> {});
        });
        recyclerView.setAdapter(adapter);

        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    String passphrase = getCurrentDbPassphrase();
                    if (passphrase.isEmpty()) {
                        adapter.notifyItemChanged(position);
                        return;
                    }
                    if (direction == ItemTouchHelper.LEFT) {
                        authenticateForAction(() -> {
                            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this, R.style.CustomAlertDialog)
                                    .setTitle("Удалить пароль?")
                                    .setCancelable(false)
                                    .setPositiveButton("Удалить", (d, which) -> {
                                        PasswordDatabase db = new PasswordDatabase(MainActivity.this, passphrase);
                                        List<PasswordDatabase.PasswordItem> list = db.getAllPasswords();
                                        if (position >= 0 && position < list.size()) {
                                            db.deletePassword(list.get(position).getId());
                                        }
                                        loadPassName();
                                    })
                                    .setNegativeButton("Отмена", (d, which) -> adapter.notifyItemChanged(position))
                                    .create();
                            dialog.setOnShowListener(dialogInterface -> {
                                if (dialog.getWindow() != null) {
                                    dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                                }
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
                            });

                            dialog.show();
                        }, () -> adapter.notifyItemChanged(position));
                    } else if (direction == ItemTouchHelper.RIGHT) {
                        authenticateForAction(() -> {
                            PasswordDatabase db = new PasswordDatabase(MainActivity.this, passphrase);
                            List<PasswordDatabase.PasswordItem> list = db.getAllPasswords();
                            if (position >= 0 && position < list.size()) {
                                PasswordDatabase.PasswordItem itemToEdit = list.get(position);

                                View dialogView = getLayoutInflater().inflate(R.layout.dialog_download, null);
                                EditText editText1 = dialogView.findViewById(R.id.editText1);
                                EditText editText2 = dialogView.findViewById(R.id.editText2);

                                editText1.setText(itemToEdit.getTitle());

                                SecretKey encKey = getCurrentEncryptionKey();
                                String decryptedPassword = PasswordSecurity.decryptAES(itemToEdit.getPassword(), encKey);
                                if (decryptedPassword == null) {
                                    decryptedPassword = "";
                                    Toast.makeText(MainActivity.this, "Ошибка расшифровки: запись повреждена", Toast.LENGTH_SHORT).show();
                                }
                                editText2.setText(decryptedPassword);

                                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this, R.style.CustomAlertDialog)
                                        .setTitle("Изменить пароль")
                                        .setView(dialogView)
                                        .setCancelable(false)
                                        .setPositiveButton("Сохранить", null)
                                        .setNegativeButton("Отмена", (d, which) -> adapter.notifyItemChanged(position))
                                        .create();

                                dialog.setOnShowListener(dialogInterface -> {
                                    if (dialog.getWindow() != null) {
                                        dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                                    }
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
                                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));

                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                                        String newTitle = editText1.getText().toString().trim();
                                        Editable ePass = editText2.getText();
                                        if (!newTitle.isEmpty() && ePass.length() > 0) {
                                            char[] newPassChars = new char[ePass.length()];
                                            ePass.getChars(0, ePass.length(), newPassChars, 0);
                                            ePass.clear();

                                            String encPass;

                                            try {
                                                encPass = PasswordSecurity.encryptAES(newPassChars, encKey);
                                            } finally {
                                                Arrays.fill(newPassChars, '\0');
                                            }

                                            if (encPass == null) {
                                                Toast.makeText(MainActivity.this, "Ошибка шифрования!", Toast.LENGTH_LONG).show();
                                                return;
                                            }

                                            db.updatePassword(itemToEdit.getId(), newTitle, encPass);
                                            dialog.dismiss();
                                            loadPassName();
                                            Toast.makeText(MainActivity.this, "Пароль изменен!", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(MainActivity.this, "Заполните все поля!", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                });

                                dialog.show();
                            } else {
                                adapter.notifyItemChanged(position);
                            }
                        }, () -> adapter.notifyItemChanged(position));
                    }
                }
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        addNewKey.setOnClickListener(view -> {
            String passphrase = getCurrentDbPassphrase();
            if (passphrase.isEmpty()) return;

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_download, null);
            EditText editText1 = dialogView.findViewById(R.id.editText1);
            EditText editText2 = dialogView.findViewById(R.id.editText2);
            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this, R.style.CustomAlertDialog)
                    .setTitle("Добавить новый пароль")
                    .setView(dialogView)
                    .setCancelable(false)
                    .setPositiveButton("Добавить", null)
                    .setNegativeButton("Отмена", null)
                    .create();

            dialog.setOnShowListener(dialogInterface -> {
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value1 = editText1.getText().toString().trim();
                    Editable ePass = editText2.getText();
                    if (!value1.isEmpty() && ePass.length() > 0) {
                        char[] passChars = new char[ePass.length()];
                        ePass.getChars(0, ePass.length(), passChars, 0);
                        ePass.clear();

                        SecretKey encKey = getCurrentEncryptionKey();
                        String encPass;

                        try {
                            encPass = PasswordSecurity.encryptAES(passChars, encKey);
                        } finally {
                            Arrays.fill(passChars, '\0');
                        }

                        if (encPass == null) {
                            Toast.makeText(MainActivity.this, "Ошибка шифрования!", Toast.LENGTH_LONG).show();
                            return;
                        }

                        PasswordDatabase db = new PasswordDatabase(MainActivity.this, passphrase);
                        db.addPassword(value1, encPass);

                        dialog.dismiss();
                        loadPassName();
                        Toast.makeText(MainActivity.this, "Пароль добавлен!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Заполните все поля!", Toast.LENGTH_SHORT).show();
                    }
                });
            });

            dialog.show();
        });

        authenticateBiometric();
    }

    private SharedPreferences getEncryptedPrefs() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();

            return EncryptedSharedPreferences.create(this, "secure_app_settings", masterKey, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "ОШибка!", Toast.LENGTH_LONG).show();
            finishAffinity();
            finishAndRemoveTask();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
            return null;
        }
    }

    private SecretKey getCurrentEncryptionKey() {
        if (currentMasterPassword == null) return null;
        SharedPreferences prefs = getEncryptedPrefs();
        if (prefs == null) return null;
        String aesSalt = prefs.getString("aes_salt", "");
        return PasswordSecurity.deriveAesKey(currentMasterPassword, aesSalt);
    }

    private String getCurrentDbPassphrase() {
        if (currentMasterPassword == null) return "";
        SharedPreferences prefs = getEncryptedPrefs();
        if (prefs == null) return "";
        String dbSalt = prefs.getString("db_salt", "");
        String passphrase = PasswordSecurity.deriveDbPassphrase(currentMasterPassword, dbSalt);
        return passphrase != null ? passphrase : "";
    }

    private void loadPassName(){
        String passphrase = getCurrentDbPassphrase();
        if (passphrase.isEmpty()) return;
        items.clear();
        PasswordDatabase db = new PasswordDatabase(this, passphrase);
        List<PasswordDatabase.PasswordItem> list = db.getAllPasswords();
        for (PasswordDatabase.PasswordItem item : list) {
            items.add(item.getTitle());
        }
        if (recyclerView != null && recyclerView.getAdapter() != null) {
            recyclerView.getAdapter().notifyDataSetChanged();
        }
        if (addNewKey != null) {
            addNewKey.setVisibility(View.VISIBLE);
        }
    }

    private void authenticateBiometric() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG;
        int result = biometricManager.canAuthenticate(authenticators);
        if (result != BiometricManager.BIOMETRIC_SUCCESS) {
            String message;
            switch (result) {
                case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                    message = "На устройстве нет подходящего биометрического оборудования.";
                    break;
                case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                    message = "Биометрическое оборудование временно недоступно.";
                    break;
                case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                    message = "Сначала зарегистрируйте отпечаток в настройках телефона.";
                    break;
                default:
                    message = "Биометрическая авторизация недоступна.";
                    break;
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            finishAffinity();
            finishAndRemoveTask();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
            return;
        }
        Executor executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                SharedPreferences prefs = getEncryptedPrefs();
                if (prefs == null) return;
                boolean isFirstLaunch = prefs.getBoolean("first_launch", true);
                if (isFirstLaunch) {
                    showAddMasterPasswordDialog(prefs);
                } else {
                    showEnterMasterPasswordDialog(prefs);
                }
            }
            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                SharedPreferences prefs = getEncryptedPrefs();
                if (prefs == null) return;
                recordFailedAttempt(prefs);
                if (checkLockout(prefs)) {
                    Toast.makeText(MainActivity.this, "Превышено количество попыток. Вход заблокирован.", Toast.LENGTH_LONG).show();
                    finishAffinity();
                    finishAndRemoveTask();
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(0);
                } else {
                    int attempts = prefs.getInt("failed_attempts", 0);
                    Toast.makeText(MainActivity.this, "Неверный отпечаток! Попытка " + attempts, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                finishAffinity();
                finishAndRemoveTask();
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
                overridePendingTransition(0, 0);
            }
        });
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Вход в приложение")
                .setSubtitle("Подтвердите личность с помощью биометрии")
                .setNegativeButtonText("Отмена")
                .setAllowedAuthenticators(authenticators)
                .build();
        biometricPrompt.authenticate(promptInfo);
    }

    private void authenticateForAction(Runnable onSuccess, Runnable onCancel) {
        BiometricManager biometricManager = BiometricManager.from(this);
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG;
        int result = biometricManager.canAuthenticate(authenticators);
        if (result != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Биометрическая авторизация недоступна.", Toast.LENGTH_SHORT).show();
            onCancel.run();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt actionPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                actionBiometricAttempts = 0;
                showVerifyMasterPasswordDialog(onSuccess, onCancel);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                SharedPreferences prefs = getEncryptedPrefs();
                if (prefs == null) {
                    onCancel.run();
                    return;
                }
                recordFailedAttempt(prefs);
                if (checkLockout(prefs)) {
                    Toast.makeText(MainActivity.this, "Превышено количество попыток.", Toast.LENGTH_SHORT).show();
                    onCancel.run();
                } else {
                    int attempts = prefs.getInt("failed_attempts", 0);
                    Toast.makeText(MainActivity.this, "Неверный отпечаток! Попытка " + attempts, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                actionBiometricAttempts = 0;
                onCancel.run();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Подтверждение действия")
                .setSubtitle("Подтвердите личность для изменения или удаления")
                .setNegativeButtonText("Отмена")
                .setAllowedAuthenticators(authenticators)
                .build();

        actionPrompt.authenticate(promptInfo);
    }

    private void showVerifyMasterPasswordDialog(Runnable onSuccess, Runnable onCancel) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download, null);
        EditText editText1 = dialogView.findViewById(R.id.editText1);
        EditText editText2 = dialogView.findViewById(R.id.editText2);
        editText1.setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this, R.style.CustomAlertDialog)
                .setTitle("Введите мастер-пароль")
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("Подтвердить", null)
                .setNegativeButton("Отмена", (d, which) -> onCancel.run())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                SharedPreferences prefs = getEncryptedPrefs();
                if (prefs == null) {
                    onCancel.run();
                    return;
                }
                if (checkLockout(prefs)) {
                    dialog.dismiss();
                    onCancel.run();
                    return;
                }

                Editable e2 = editText2.getText();
                if (e2.length() > 0) {
                    char[] passChars = new char[e2.length()];
                    e2.getChars(0, e2.length(), passChars, 0);
                    e2.clear();

                    String aesSalt = prefs.getString("aes_salt", "");
                    SecretKey key = PasswordSecurity.deriveAesKey(passChars, aesSalt);
                    String verificationBlob = prefs.getString("verification_blob", "");
                    String decrypted = PasswordSecurity.decryptAES(verificationBlob, key);

                    if (decrypted == null || !decrypted.equals("SEMYON_PASS_VALID")) {
                        Arrays.fill(passChars, '\0');
                        recordFailedAttempt(prefs);
                        long lockoutUntil = prefs.getLong("lockout_until_timestamp", 0);
                        long secondsLeft = Math.max(1, (lockoutUntil - System.currentTimeMillis()) / 1000);
                        Toast.makeText(MainActivity.this, "Неверный пароль! Блокировка на " + secondsLeft + " сек.", Toast.LENGTH_SHORT).show();
                        if (checkLockout(prefs)) {
                            dialog.dismiss();
                            onCancel.run();
                        }
                    } else {
                        resetFailedAttempts(prefs);
                        clearMasterPassword();
                        currentMasterPassword = passChars;
                        dialog.dismiss();
                        onSuccess.run();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Введите пароль!", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private void showAddMasterPasswordDialog(SharedPreferences prefs) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download, null);
        EditText editText1 = dialogView.findViewById(R.id.editText1);
        EditText editText2 = dialogView.findViewById(R.id.editText2);
        editText1.setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this, R.style.CustomAlertDialog)
                .setTitle("Добавьте пароль для входа!")
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("Добавить", null)
                .setNegativeButton("Отмена", (d, which) -> finishAffinity())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Editable e2 = editText2.getText();
                if (e2.length() > 0) {
                    char[] passChars = new char[e2.length()];
                    e2.getChars(0, e2.length(), passChars, 0);
                    e2.clear();

                    String aesSalt = PasswordSecurity.generateSalt();
                    String dbSalt = PasswordSecurity.generateSalt();
                    SecretKey key = PasswordSecurity.deriveAesKey(passChars, aesSalt);
                    String verificationBlob = PasswordSecurity.encryptAES("SEMYON_PASS_VALID", key);

                    if (key == null || verificationBlob == null) {
                        Arrays.fill(passChars, '\0');
                        Toast.makeText(MainActivity.this, "Ошибка криптографии!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    prefs.edit().putBoolean("first_launch", false).putString("aes_salt", aesSalt).putString("db_salt", dbSalt).putString("verification_blob", verificationBlob).apply();

                    currentMasterPassword = passChars;
                    dialog.dismiss();
                    loadPassName();
                } else {
                    Toast.makeText(MainActivity.this, "Введите пароль!", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private void showEnterMasterPasswordDialog(SharedPreferences prefs) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download, null);
        EditText editText1 = dialogView.findViewById(R.id.editText1);
        EditText editText2 = dialogView.findViewById(R.id.editText2);
        editText1.setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this, R.style.CustomAlertDialog)
                .setTitle("Введите пароль для входа!")
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("Проверить", null)
                .setNegativeButton("Отмена", (d, which) -> finishAffinity())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (checkLockout(prefs)) {
                    dialog.dismiss();
                    finishAffinity();
                    return;
                }

                Editable e2 = editText2.getText();
                if (e2.length() > 0) {
                    char[] passChars = new char[e2.length()];
                    e2.getChars(0, e2.length(), passChars, 0);
                    e2.clear();

                    String aesSalt = prefs.getString("aes_salt", "");
                    SecretKey key = PasswordSecurity.deriveAesKey(passChars, aesSalt);
                    String verificationBlob = prefs.getString("verification_blob", "");
                    String decrypted = PasswordSecurity.decryptAES(verificationBlob, key);

                    if (decrypted == null || !decrypted.equals("SEMYON_PASS_VALID")) {
                        Arrays.fill(passChars, '\0');
                        recordFailedAttempt(prefs);
                        long lockoutUntil = prefs.getLong("lockout_until_timestamp", 0);
                        long secondsLeft = Math.max(1, (lockoutUntil - System.currentTimeMillis()) / 1000);
                        Toast.makeText(MainActivity.this, "Неверный пароль! Блокировка на " + secondsLeft + " сек.", Toast.LENGTH_SHORT).show();
                        if (checkLockout(prefs)) {
                            dialog.dismiss();
                            finishAffinity();
                        }
                    } else {
                        resetFailedAttempts(prefs);
                        currentMasterPassword = passChars;
                        dialog.dismiss();
                        loadPassName();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Введите пароль!", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private void clearMasterPassword() {
        if (currentMasterPassword != null) {
            Arrays.fill(currentMasterPassword, '\0');
            currentMasterPassword = null;
        }
    }

    private boolean checkLockout(SharedPreferences prefs) {
        long lockoutUntil = prefs.getLong("lockout_until_timestamp", 0);
        long now = System.currentTimeMillis();
        if (now < lockoutUntil) {
            long secondsLeft = (lockoutUntil - now) / 1000;
            Toast.makeText(this, "Превышено количество попыток. Подождите " + secondsLeft + " сек.", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private void recordFailedAttempt(SharedPreferences prefs) {
        int attempts = prefs.getInt("failed_attempts", 0) + 1;
        long lockoutDuration = 0;
        if (attempts >= 3) {
            lockoutDuration = Math.min(Math.round(Math.pow(2, attempts - 3) * 10_000), 300_000);
        }
        long lockoutUntil = System.currentTimeMillis() + lockoutDuration;
        prefs.edit().putInt("failed_attempts", attempts).putLong("lockout_until_timestamp", lockoutUntil).apply();
    }

    private void resetFailedAttempts(SharedPreferences prefs) {
        prefs.edit().putInt("failed_attempts", 0).putLong("lockout_until_timestamp", 0).apply();
    }

    @Override
    protected void onResume(){
        super.onResume();
        UpdateSystemBar.Update(MainActivity.this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearMasterPassword();
        if (clipboardClearRunnable != null) {
            clipboardHandler.removeCallbacks(clipboardClearRunnable);
        }
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription() != null) {
                if (clipboard.getPrimaryClipDescription().getLabel() != null &&
                        clipboard.getPrimaryClipDescription().getLabel().toString().equals("Password")) {
                    clipboard.clearPrimaryClip();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        finishAffinity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearMasterPassword();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        getWindow().getDecorView().post(new Runnable() {
            @Override
            public void run() {
                UpdateSystemBar.Update(MainActivity.this);
            }
        });
    }

}
