package tk.porrazirauki.mt_leddetection;

import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import tk.porrazirauki.mt_leddetection.analyzers.LuminosityAnalyzer;
import tk.porrazirauki.mt_leddetection.events.CustomEventListener;

public class MainActivity extends AppCompatActivity implements CustomEventListener {


    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE"};
    private TextureView textureView;
    private Executor executor;
    private int REQUEST_CODE_PERMISSIONS = 101;
    private MainActivity activity = this;
    private ImageAnalysis imageAnalysis;

    private LinearLayout cropLayout;
    private LuminosityAnalyzer analyzer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = findViewById(R.id.view_finder);
        cropLayout = findViewById(R.id.cropLayout);
        executor = Executors.newSingleThreadExecutor();
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

    private void startCamera() {

        CameraX.unbindAll();

        // Size of the screen
        Size screen = new Size(textureView.getWidth(), textureView.getHeight());

        PreviewConfig pConfig =
                new PreviewConfig
                        .Builder()
                        .setTargetResolution(screen)
                        .build();
        Preview preview = new Preview(pConfig);

        // To update the surface texture we  have to destroy it first then re-add it
        preview.setOnPreviewOutputUpdateListener(
                output -> {
                    ViewGroup parent = (ViewGroup) textureView.getParent();
                    parent.removeView(textureView);
                    parent.addView(textureView, 0);

                    textureView.setSurfaceTexture(output.getSurfaceTexture());
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
        CameraX.bindToLifecycle(activity, preview, imageAnalysis);


        findViewById(R.id.customButton).setOnClickListener(v -> {
            Toast.makeText(getApplicationContext(), "RESULT -> " + analyzer.getResult(), Toast.LENGTH_LONG).show();
        });

        findViewById(R.id.startStopButton).setOnClickListener(v -> {
            Button button = findViewById(R.id.startStopButton);
            int textId =
                    button.getText().toString().equals(getResources().getString(R.string.start)) ?
                    R.string.stop : R.string.start;
            Log.i("CRAP", button.getText().toString());
            button.setText(textId);

            if (textId == R.string.start) {
                if (imageAnalysis.getAnalyzer() != null) {
                    imageAnalysis.removeAnalyzer();
                }
            } else {
                if (imageAnalysis.getAnalyzer() != null) {
                    imageAnalysis.removeAnalyzer();
                }
                Rect cropRect = new Rect((int) cropLayout.getX(), (int) cropLayout.getY(),
                        (int) cropLayout.getX() + cropLayout.getWidth(),
                        (int) cropLayout.getY() + cropLayout.getHeight());
                analyzer = new LuminosityAnalyzer(activity, cropRect,
                        textureView.getWidth());
                imageAnalysis.setAnalyzer(executor, analyzer);
            }
        });


    }

    private void updateTransform() {
        Matrix mx = new Matrix();
        float w = textureView.getMeasuredWidth();
        float h = textureView.getMeasuredHeight();

        float cX = w / 2f;
        float cY = h / 2f;

        int rotationDgr;
        int rotation = (int) textureView.getRotation();

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
        textureView.setTransform(mx);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onEvent(String eventCode, @Nullable String info) {
        String text = info != null ? eventCode + " -> " + info : eventCode;
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        if (eventCode.equals(LuminosityAnalyzer.TX_STATE.TX_ENDED.toString())) {
            Button startStopButton = findViewById(R.id.startStopButton);
            if (startStopButton.getText().toString().equals(getResources().getString(R.string.stop))) {
                startStopButton.performClick();
            }
        }
    }
}
