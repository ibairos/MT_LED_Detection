package be.kuleuven.mt_ibai_vlc.activities;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
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
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import be.kuleuven.mt_ibai_vlc.R;
import be.kuleuven.mt_ibai_vlc.events.CustomEventListener;
import be.kuleuven.mt_ibai_vlc.model.LogItem;
import be.kuleuven.mt_ibai_vlc.model.enums.AnalyzerState;
import be.kuleuven.mt_ibai_vlc.model.enums.AndroidState;
import be.kuleuven.mt_ibai_vlc.model.enums.MicroState;
import be.kuleuven.mt_ibai_vlc.model.enums.TxMode;
import be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseInterface;
import be.kuleuven.mt_ibai_vlc.network.vlc.receiver.LightReceiver;
import be.kuleuven.mt_ibai_vlc.network.vlc.sender.LightSender;


public class MainActivity extends AppCompatActivity implements CustomEventListener {

    // TAG
    private static final String TAG = "MainActivity";
    // Resolution
    private static final int IMAGE_WIDTH = 640;
    private static final int IMAGE_HEIGHT = 480;
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
    private EditText numberOfSamplesEditText;
    private Button txStartButton;

    private TextView txResultTextView;
    private TextView txAccuracyTextView;

    // Image analysis
    private ImageAnalysis imageAnalysis;
    private Executor executor;

    // Firebase interface
    private FirebaseInterface firebaseInterface;

    // LightSender
    private LightSender lightSender;

    // LogItem
    private LogItem logItem;

    public static void cancelFocus() {
        try {
            CameraX.getCameraControl(CameraX.LensFacing.BACK).cancelFocusAndMetering();
        } catch (CameraInfoUnavailableException ignored) {

        }
    }

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

    @SuppressLint("RestrictedApi") private void startCamera() {
        CameraX.unbindAll();

        // Size of the screen
        Size screen = new Size(IMAGE_WIDTH, IMAGE_HEIGHT);

        PreviewConfig pConfig =
                new PreviewConfig
                        .Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        //.setTargetResolution(screen)
                        .build();
        Preview preview = new Preview(pConfig);


        // To update the surface texture we  have to destroy it first then re-add it
        preview.setOnPreviewOutputUpdateListener(
                output -> {
                    cancelFocus();
                    ViewGroup parent = (ViewGroup) cameraView.getParent();
                    parent.removeView(cameraView);
                    parent.addView(cameraView, 0);

                    cameraView.setSurfaceTexture(output.getSurfaceTexture());
                    updateTransform();

                });

        ImageAnalysisConfig aConfig =
                new ImageAnalysisConfig
                        .Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        //.setTargetResolution(screen)
                        .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                        .build();

        imageAnalysis = new ImageAnalysis(aConfig);


        // Bind to lifecycle
        CameraX.bindToLifecycle(this, preview, imageAnalysis);

        lightSender = new LightSender(preview, firebaseInterface, this);

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
        Rect cropRect = new Rect((int) (cropLayout.getX() / cameraView.getWidth() * IMAGE_WIDTH),
                (int) (cropLayout.getY() / cameraView.getHeight() * IMAGE_HEIGHT),
                (int) ((cropLayout.getX() + cropLayout.getWidth()) / cameraView.getWidth() *
                        IMAGE_WIDTH),
                (int) ((cropLayout.getY() + cropLayout.getHeight()) / cameraView.getHeight() *
                        IMAGE_HEIGHT));
        LightReceiver analyzer = new LightReceiver(this, cropRect,
                cameraView.getWidth(), firebaseInterface.getTxRate(),
                firebaseInterface.getNumberOfSamples());
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
        numberOfSamplesEditText = findViewById(R.id.samplingRateEditText);
        txModeRadioGroup = findViewById(R.id.txModeRadioGroup);
        txStartButton = findViewById(R.id.txStartButton);
        txStartButton.setOnClickListener(v -> {
            if (firebaseInterface.getAndroidState().equals(AndroidState.WAITING_FOR_START)) {
                if (txDataEditText.getText().toString().isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.empty_text,
                            Toast.LENGTH_LONG).show();
                } else if (txRateEditText.getText().toString().isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.empty_sampling_rate,
                            Toast.LENGTH_LONG).show();
                } else if (!firebaseInterface.getMicroState()
                        .equals(MicroState.WAITING_FOR_TX_DATA)) {
                    Toast.makeText(getApplicationContext(), R.string.micro_not_online,
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
                            txModeRadioGroup.getCheckedRadioButtonId() == R.id.txModeMicroAndroid
                            ? TxMode.MICRO_ANDROID : TxMode.ANDROID_MICRO;
                    int numberOfSamples;
                    if (txMode == TxMode.MICRO_ANDROID) {
                        if (numberOfSamplesEditText.getText().toString().isEmpty()) {
                            Toast.makeText(getApplicationContext(), R.string.empty_rate,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            numberOfSamples =
                                    Integer.parseInt(numberOfSamplesEditText.getText().toString());
                        } catch (NumberFormatException e) {
                            Toast.makeText(getApplicationContext(),
                                    R.string.sampling_rate_not_number,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                    } else {
                        numberOfSamples = 1;
                    }


                    AndroidState nextState =
                            txMode.equals(TxMode.MICRO_ANDROID)
                            ? AndroidState.WAITING_FOR_CHECK_IN_RX
                            : AndroidState.WAITING_FOR_CHECK_IN_TX;
                    firebaseInterface.setAndroidState(nextState);
                    firebaseInterface.setTxData(txData);
                    firebaseInterface.setTxMode(txMode);
                    firebaseInterface.setTxRate(txRate);
                    firebaseInterface.setNumberOfSamples(numberOfSamples);
                    txStartButton.setEnabled(false);
                    txDataEditText.setEnabled(false);
                    txRateEditText.setEnabled(false);
                    numberOfSamplesEditText.setEnabled(false);
                }
            }
        });
        findViewById(R.id.resetButton).setOnClickListener(v -> {
            reset();
        });
        txModeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            numberOfSamplesEditText.setEnabled(checkedId == R.id.txModeMicroAndroid);
        });
        txResultTextView = findViewById(R.id.txResultTextView);
        txAccuracyTextView = findViewById(R.id.txAccuracyTextView);
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
    public void microStateChanged(MicroState state) {
        switch (state) {
            case WAITING_FOR_CHECK_IN:
                if (firebaseInterface.getAndroidState()
                        .equals(AndroidState.WAITING_FOR_CHECK_IN_RX)) {
                    startRx();
                }
                break;
            case RX_WAITING:
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
            case EXIT:
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
        lightSender.setParams(firebaseInterface.getTxData(), firebaseInterface.getTxRate());

        new Thread(lightSender).start();

    }

    public void endTx() {
        saveResults();
    }

    public void initLog() {
        String txData = firebaseInterface.getTxData();
        TxMode txMode = firebaseInterface.getTxMode();
        long txRate = firebaseInterface.getTxRate();
        long numberOfSamples = firebaseInterface.getNumberOfSamples();
        long time = System.currentTimeMillis();
        logItem = new LogItem(txData, time, txMode, txRate, numberOfSamples);
    }

    public void saveResults() {
        // Obtain results
        String rxData = firebaseInterface.getTxMode().equals(TxMode.MICRO_ANDROID)
                        ? firebaseInterface.getAndroidResult()
                        : firebaseInterface.getMicroResult();
        logItem.completeLog(rxData);
        // Print them
        printResults();
        // Save logs
        firebaseInterface.pushLog(logItem);
        // Finish
        firebaseInterface.setAndroidState(AndroidState.EXIT);
        Log.e(TAG, "'" + rxData + "'" + new Gson().toJson(logItem));
    }

    private void printResults() {
        txResultTextView.setText(logItem.getRx_data());
        txAccuracyTextView.setText(
                String.format(getResources().getConfiguration().getLocales().get(0), "%.3f",
                        logItem.getAccuracy()));
    }

    private void reset() {
        firebaseInterface.setAndroidState(AndroidState.WAITING_FOR_START);
        firebaseInterface.setAndroidResult("");
        if (firebaseInterface.getMicroState() == MicroState.EXIT
                || firebaseInterface.getMicroState() == MicroState.RX_WAITING
                || firebaseInterface.getMicroState() == MicroState.RX_STARTING
                || firebaseInterface.getMicroState() == MicroState.RX_STARTED) {
            firebaseInterface.setMicroState(MicroState.LOADING);
        }
        txResultTextView.setText(R.string.no_info);
        txAccuracyTextView.setText(R.string.no_info);
        txStartButton.setEnabled(true);
        txDataEditText.setEnabled(true);
        txRateEditText.setEnabled(true);
        numberOfSamplesEditText
                .setEnabled(txModeRadioGroup.getCheckedRadioButtonId() == R.id.txModeMicroAndroid);
        if (firebaseInterface.getTxMode() == TxMode.MICRO_ANDROID) {
            stopAnalysis();
        } else {
            lightSender.setEnabled(false);
        }

    }

}
