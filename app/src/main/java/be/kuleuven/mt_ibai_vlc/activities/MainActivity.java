package be.kuleuven.mt_ibai_vlc.activities;

import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import be.kuleuven.mt_ibai_vlc.R;
import be.kuleuven.mt_ibai_vlc.events.CustomEventListener;
import be.kuleuven.mt_ibai_vlc.model.LogItem;
import be.kuleuven.mt_ibai_vlc.model.enums.AnalyzerState;
import be.kuleuven.mt_ibai_vlc.model.enums.AndroidState;
import be.kuleuven.mt_ibai_vlc.model.enums.ArduinoState;
import be.kuleuven.mt_ibai_vlc.model.enums.TxMode;
import be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseInterface;
import be.kuleuven.mt_ibai_vlc.network.vlc.receiver.LightReceiver;
import be.kuleuven.mt_ibai_vlc.network.vlc.sender.LightSender;


public class MainActivity extends AppCompatActivity implements CustomEventListener {

    // Camera permissions
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE"};
    // Activity instance
    private int REQUEST_CODE_PERMISSIONS = 101;

    // Views and layouts
    private TextureView cameraView;
    private LinearLayout cropLayout;
    private RadioGroup txModeRadioGroup;
    private EditText txDataEditText;
    private EditText txRateEditText;
    private Button txStartButton;

    // Image analysis
    private ImageAnalysis imageAnalysis;
    private Executor executor;

    // Firebase interface
    private FirebaseInterface firebaseInterface;

    // LightSender
    private LightSender lightSender;

    // LogItem
    private LogItem logItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        executor = Executors.newSingleThreadExecutor();
        setupUI();
        setupFirebaseData();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT)
                        .show();
                finish();
            }
        }
    }

    private void startCamera() {
        CameraX.unbindAll();

        // Size of the screen
        Size screen = new Size(cameraView.getWidth(), cameraView.getHeight());

        PreviewConfig pConfig =
                new PreviewConfig
                        .Builder()
                        .setTargetResolution(screen)
                        .build();
        Preview preview = new Preview(pConfig);

        // To update the surface texture we  have to destroy it first then re-add it
        preview.setOnPreviewOutputUpdateListener(
                output -> {
                    ViewGroup parent = (ViewGroup) cameraView.getParent();
                    parent.removeView(cameraView);
                    parent.addView(cameraView, 0);

                    cameraView.setSurfaceTexture(output.getSurfaceTexture());
                    updateTransform();

                });

        ImageAnalysisConfig aConfig =
                new ImageAnalysisConfig
                        .Builder()
                        .setTargetResolution(screen)
                        .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                        .build();

        imageAnalysis = new ImageAnalysis(aConfig);

        // Bind to lifecycle
        CameraX.bindToLifecycle(this, preview, imageAnalysis);

        lightSender = new LightSender(preview);

        firebaseInterface.setAndroidState(AndroidState.WAITING_FOR_START); // Setup completed
    }

    private void updateTransform() {
        Matrix mx = new Matrix();
        float w = cameraView.getMeasuredWidth();
        float h = cameraView.getMeasuredHeight();

        float cX = w / 2f;
        float cY = h / 2f;

        int rotationDgr;
        int rotation = (int) cameraView.getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
                rotationDgr = 0;
                break;
            case Surface.ROTATION_90:
                rotationDgr = 90;
                break;
            case Surface.ROTATION_180:
                rotationDgr = 180;
                break;
            case Surface.ROTATION_270:
                rotationDgr = 270;
                break;
            default:
                return;
        }

        mx.postRotate((float) rotationDgr, cX, cY);
        cameraView.setTransform(mx);
    }

    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startAnalysis() {
        if (imageAnalysis.getAnalyzer() != null) {
            imageAnalysis.removeAnalyzer();
        }
        Rect cropRect = new Rect((int) cropLayout.getX(), (int) cropLayout.getY(),
                (int) cropLayout.getX() + cropLayout.getWidth(),
                (int) cropLayout.getY() + cropLayout.getHeight());
        LightReceiver analyzer = new LightReceiver(this, cropRect,
                cameraView.getWidth());
        imageAnalysis.setAnalyzer(executor, analyzer);
    }

    private void stopAnalysis() {
        if (imageAnalysis.getAnalyzer() != null) {
            imageAnalysis.removeAnalyzer();
        }
    }

    private void setupUI() {
        cameraView = findViewById(R.id.mainCameraView);
        cropLayout = findViewById(R.id.mainCropLayout);
        txDataEditText = findViewById(R.id.txDataEditText);
        txRateEditText = findViewById(R.id.txRateEditText);
        txModeRadioGroup = findViewById(R.id.txModeRadioGroup);
        txStartButton = findViewById(R.id.txStartButton);
        txStartButton.setOnClickListener(v -> {
            if (firebaseInterface.getAndroidState().equals(AndroidState.WAITING_FOR_START)) {
                if (txDataEditText.getText().toString().isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.empty_text,
                            Toast.LENGTH_LONG).show();
                } else if (txRateEditText.getText().toString().isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.empty_rate,
                            Toast.LENGTH_LONG).show();
                } else if (!firebaseInterface.getArduinoState()
                        .equals(ArduinoState.WAITING_FOR_TX_DATA)) {
                    Toast.makeText(getApplicationContext(), R.string.arduino_not_online,
                            Toast.LENGTH_LONG).show();
                } else {
                    int txRate;
                    try {
                        txRate = Integer.parseInt(txRateEditText.getText().toString());
                    } catch (NumberFormatException e) {
                        Toast.makeText(getApplicationContext(), R.string.rate_not_number,
                                Toast.LENGTH_LONG).show();

                        return;
                    }
                    String txData = txDataEditText.getText().toString();
                    TxMode txMode =
                            txModeRadioGroup.getCheckedRadioButtonId() == R.id.txModeArduinoAndroid
                            ? TxMode.ARDUINO_ANDROID : TxMode.ANDROID_ARDUINO;
                    AndroidState nextState =
                            txMode.equals(TxMode.ARDUINO_ANDROID)
                            ? AndroidState.WAITING_FOR_CHECK_IN_RX
                            : AndroidState.WAITING_FOR_CHECK_IN_TX;
                    firebaseInterface.setAndroidState(nextState);
                    firebaseInterface.setTxData(txData);
                    firebaseInterface.setTxMode(txMode);
                    firebaseInterface.setTxRate(txRate);
                    txStartButton.setEnabled(false);
                    txDataEditText.setEnabled(false);
                    txRateEditText.setEnabled(false);
                }
            }
        });
    }

    private void setupFirebaseData() {
        firebaseInterface = new FirebaseInterface(this);
        firebaseInterface.setAndroidState(AndroidState.LOADING);
        firebaseInterface.setAndroidResult("");
    }

    @Override
    public void onAnalyzerEvent(AnalyzerState eventCode, @Nullable String info) {
        String text = info != null ? eventCode + " -> " + info : eventCode.toString();
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        switch (eventCode) {
            case WAITING:
                firebaseInterface.setAndroidState(AndroidState.RX_STARTING);
                break;
            case TX_ENDED:
                endRx();
                break;
        }
    }

    @Override
    public void arduinoStateChanged(ArduinoState state) {
        switch (state) {
            case WAITING_FOR_CHECK_IN:
                if (firebaseInterface.getAndroidState()
                        .equals(AndroidState.WAITING_FOR_CHECK_IN_RX)) {
                    startRx();
                }
                break;
            case RX_STARTING:
                if (firebaseInterface.getAndroidState()
                        .equals(AndroidState.WAITING_FOR_CHECK_IN_TX)) {
                    startTx();
                }
                break;
            case TX_ENDED:
                if (firebaseInterface.getAndroidState().equals(AndroidState.RX_STARTING)
                        || firebaseInterface.getAndroidState().equals(AndroidState.RX_STARTED)) {
                    endRx();
                }
                break;
            case RX_ENDED:
                if (firebaseInterface.getAndroidState().equals(AndroidState.TX_ENDED)) {
                    endTx();
                }
                break;
        }
    }

    public void startRx() {
        initLog();
        startAnalysis();
    }

    public void endRx() {
        LightReceiver analyzer = (LightReceiver) imageAnalysis.getAnalyzer();
        String result = analyzer != null ? analyzer.getResult() : "";
        stopAnalysis();
        firebaseInterface.setAndroidResult(result);
        firebaseInterface.setAndroidState(AndroidState.RX_ENDED);
        saveResults();
    }

    public void startTx() {
        firebaseInterface.setAndroidState(AndroidState.TX_STARTING);
        initLog();
        firebaseInterface.setAndroidState(AndroidState.TX_STARTED);
        lightSender.blinkWholeSequence(firebaseInterface.getTxData());
        firebaseInterface.setAndroidState(AndroidState.TX_ENDED);
    }

    public void endTx() {
        firebaseInterface.setAndroidState(AndroidState.EXIT);
        saveResults();
    }

    public void initLog() {
        String txData = firebaseInterface.getTxData();
        TxMode txMode = firebaseInterface.getTxMode();
        long txRate = firebaseInterface.getTxRate();
        long time = System.currentTimeMillis();
        logItem = new LogItem(txData, time, txMode, txRate);
    }

    public void saveResults() {
        // Obtain results
        String rxData = firebaseInterface.getTxMode().equals(TxMode.ARDUINO_ANDROID)
                        ? firebaseInterface.getAndroidResult()
                        : firebaseInterface.getArduinoResult();
        logItem.completeLog(rxData);
        // Print them
        printResults();
        // Save logs
        firebaseInterface.pushLog(logItem);
        // Finish
        firebaseInterface.setAndroidState(AndroidState.EXIT);
    }

    private void printResults() {
        ((TextView) findViewById(R.id.txResultTextView)).setText(logItem.getRx_data());
        ((TextView) findViewById(R.id.txAccuracyTextView))
                .setText(String.format(
                        getResources().getConfiguration().getLocales().get(0),
                        "%.3f",
                        logItem.getAccuracy())
                );
    }

    private void reset() {
        firebaseInterface.setAndroidState(AndroidState.WAITING_FOR_START);
        firebaseInterface.setAndroidResult("");
        txStartButton.setEnabled(true);
        txDataEditText.setEnabled(true);
        txRateEditText.setEnabled(false);
    }

}
