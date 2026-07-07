package com.boltz.tapjacker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {

    private TextView tvPermissionStatus;
    private MaterialButton btnGrantPermission;
    private TextInputLayout tilButtonText;
    private TextInputEditText etButtonText;
    private Slider sliderWidth;
    private Slider sliderHeight;
    private Slider sliderOpacity;
    private TextView tvWidthLabel;
    private TextView tvHeightLabel;
    private TextView tvOpacityLabel;
    private MaterialSwitch switchLock;
    private TextView tvDragHint;
    private MaterialButton btnShowOverlay;

    private int selectedColor;
    private boolean overlayShown = false;

    private OverlayService overlayService;
    private boolean isBound = false;

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> refreshPermissionStatus());

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            OverlayService.LocalBinder binder = (OverlayService.LocalBinder) service;
            overlayService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            overlayService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();
        selectedColor = ContextCompat.getColor(this, R.color.decoy_orange);

        tvWidthLabel.setText(getString(R.string.label_width, (int) sliderWidth.getValue()));
        tvHeightLabel.setText(getString(R.string.label_height, (int) sliderHeight.getValue()));
        tvOpacityLabel.setText(getString(R.string.label_opacity, (int) sliderOpacity.getValue()));

        btnGrantPermission.setOnClickListener(v -> requestOverlayPermission());

        sliderWidth.addOnChangeListener((slider, value, fromUser) -> {
            tvWidthLabel.setText(getString(R.string.label_width, (int) value));
            pushConfigIfShown();
        });
        sliderHeight.addOnChangeListener((slider, value, fromUser) -> {
            tvHeightLabel.setText(getString(R.string.label_height, (int) value));
            pushConfigIfShown();
        });
        sliderOpacity.addOnChangeListener((slider, value, fromUser) -> {
            tvOpacityLabel.setText(getString(R.string.label_opacity, (int) value));
            pushConfigIfShown();
        });
        etButtonText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (s.toString().trim().length() > 0) {
                    tilButtonText.setError(null);
                }
                pushConfigIfShown();
            }
        });

        setupColorSwatch(R.id.swatchRed, R.color.decoy_red);
        setupColorSwatch(R.id.swatchOrange, R.color.decoy_orange);
        setupColorSwatch(R.id.swatchGreen, R.color.decoy_green);
        setupColorSwatch(R.id.swatchBlue, R.color.decoy_blue);
        setupColorSwatch(R.id.swatchPurple, R.color.decoy_purple);

        switchLock.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            tvDragHint.setText(isChecked ? R.string.hint_locked : R.string.hint_drag);
            pushConfigIfShown();
        });

        btnShowOverlay.setOnClickListener(v -> toggleOverlay());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void bindViews() {
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);
        tilButtonText = findViewById(R.id.tilButtonText);
        etButtonText = findViewById(R.id.etButtonText);
        sliderWidth = findViewById(R.id.sliderWidth);
        sliderHeight = findViewById(R.id.sliderHeight);
        sliderOpacity = findViewById(R.id.sliderOpacity);
        tvWidthLabel = findViewById(R.id.tvWidthLabel);
        tvHeightLabel = findViewById(R.id.tvHeightLabel);
        tvOpacityLabel = findViewById(R.id.tvOpacityLabel);
        switchLock = findViewById(R.id.switchLock);
        tvDragHint = findViewById(R.id.tvDragHint);
        btnShowOverlay = findViewById(R.id.btnShowOverlay);
    }

    private void setupColorSwatch(int viewId, int colorRes) {
        findViewById(viewId).setOnClickListener(v -> {
            selectedColor = ContextCompat.getColor(this, colorRes);
            pushConfigIfShown();
        });
    }

    private OverlayConfig buildConfig() {
        return new OverlayConfig(
                etButtonText.getText() != null ? etButtonText.getText().toString() : "",
                (int) sliderWidth.getValue(),
                (int) sliderHeight.getValue(),
                (int) sliderOpacity.getValue(),
                selectedColor,
                switchLock.isChecked());
    }

    private void pushConfigIfShown() {
        if (overlayShown && isBound && overlayService != null) {
            overlayService.updateConfig(buildConfig());
        }
    }

    private void toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }

        if (!overlayShown) {
            CharSequence text = etButtonText.getText();
            if (text == null || text.toString().trim().isEmpty()) {
                tilButtonText.setError(getString(R.string.error_button_text_required));
                return;
            }

            Intent intent = new Intent(this, OverlayService.class);
            ContextCompat.startForegroundService(this, intent);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
            isBound = true;
            overlayShown = true;
            btnShowOverlay.setText(R.string.btn_hide_overlay);
            new android.os.Handler(getMainLooper()).postDelayed(this::pushConfigIfShown, 150);
        } else {
            if (isBound && overlayService != null) {
                overlayService.hideOverlay();
            }
            stopService(new Intent(this, OverlayService.class));
            if (isBound) {
                unbindService(connection);
                isBound = false;
            }
            overlayShown = false;
            btnShowOverlay.setText(R.string.btn_show_overlay);
        }
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        overlayPermissionLauncher.launch(intent);
    }

    private void refreshPermissionStatus() {
        boolean granted = Settings.canDrawOverlays(this);
        tvPermissionStatus.setText(granted
                ? R.string.permission_status_granted
                : R.string.permission_status_denied);
        tvPermissionStatus.setTextColor(ContextCompat.getColor(
                this, granted ? R.color.success : R.color.danger));
        btnGrantPermission.setVisibility(granted ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        super.onDestroy();
    }
}
