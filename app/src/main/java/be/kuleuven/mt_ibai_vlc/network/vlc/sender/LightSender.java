package be.kuleuven.mt_ibai_vlc.network.vlc.sender;

import android.app.Activity;
import android.util.Log;

import androidx.camera.core.CameraControl;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

import be.kuleuven.mt_ibai_vlc.model.enums.AndroidState;
import be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseInterface;

public class LightSender implements Runnable {

    private static final String TAG = "LightSender";
    private static final BitSet START_SEQ = BitSet.valueOf(new long[]{0b11111111});
    private static final BitSet END_SEQ = BitSet.valueOf(new long[]{0b11111111});


    private String sequence;
    private long txRate;

    private long startTime;

    private long seqNum;

    private boolean enabled;

    private FirebaseInterface firebaseInterface;
    private Activity activity;

    private CameraControl cameraControl;

    public LightSender(CameraControl cameraControl, FirebaseInterface firebaseInterface,
                       Activity activity) {
        this.cameraControl = cameraControl;
        this.firebaseInterface = firebaseInterface;
        this.activity = activity;
        enabled = true;
    }

    @Override public void run() {
        blinkWholeSequence();
        activity.runOnUiThread(() -> firebaseInterface.setAndroidState(AndroidState.TX_ENDED));
    }

    private void blinkWholeSequence() {


        startTime = System.currentTimeMillis();
        seqNum = 0;

        Log.i(TAG, "Blinking start sequence...");
        blinkSequence(START_SEQ, txRate);
        Log.i(TAG, "Blinking message...");
        for (int i = 0; i < sequence.length(); i++) {
            BitSet s = BitSet.valueOf(
                    String.valueOf(sequence.charAt(i)).getBytes(StandardCharsets.UTF_8));
            blinkSequence(s, txRate);
        }
        //BitSet seq = BitSet.valueOf(sequence.getBytes());
        //blinkSequence(seq, txRate);
        Log.i(TAG, "Blinking end sequence...");
        blinkSequence(END_SEQ, txRate);

        torchOff();
    }

    private void torchOff() {
        cameraControl.enableTorch(false);
    }

    private void blinkSequence(BitSet sequence, long txRate) {

        boolean finished = false;
        int counter = 0;

        int zeroPadding = sequence.length() <= 8 ? 8 % sequence.length()
                                                 : sequence.length() <= 16 ? 16 % sequence.length()
                                                                           : sequence.length() <= 24
                                                                             ? 24 %
                                                                                     sequence.length()
                                                                             : 32 %
                                                                                     sequence.length();
        Log.i(TAG, new Gson().toJson(sequence) + " + " + zeroPadding);
        while (!finished) {
            if (enabled) {
                if (System.currentTimeMillis() - startTime > seqNum * (1000.0 / txRate)) {
                    if (counter < sequence.length()) {
                        cameraControl.enableTorch(sequence.get(counter));
                        Log.i(TAG, "-> " + (sequence.get(counter) ? 1 : 0));
                        counter++;
                        seqNum++;
                    } else if (counter >= sequence.length() &&
                            counter < sequence.length() + zeroPadding) {
                        cameraControl.enableTorch(false);
                        Log.i(TAG, "-> P");
                        counter++;
                        seqNum++;
                    } else {
                        finished = true;
                    }
                }
            } else {
                return;
            }
        }
    }

    public void setParams(String sequence, long txRate) {
        this.sequence = sequence;
        this.txRate = txRate;
        setEnabled(true);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
