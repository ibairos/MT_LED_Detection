package be.kuleuven.mt_ibai_vlc.network.vlc;

import android.app.Activity;
import android.util.Log;

import androidx.camera.core.CameraControl;

import org.apache.commons.codec.binary.Hex;

import java.util.BitSet;

import be.kuleuven.mt_ibai_vlc.common.Hamming74;
import be.kuleuven.mt_ibai_vlc.common.NetworkUtils;
import be.kuleuven.mt_ibai_vlc.model.enums.AndroidState;
import be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseInterface;

import static be.kuleuven.mt_ibai_vlc.common.NetworkUtils.WORD_SIZE;

public class VLCSender implements Runnable {

    private static final String TAG = "LightSender";
    private static final BitSet START_SEQ = BitSet.valueOf(new long[]{0b11111111});

    // Transmission parameters
    private long txRate;
    private BitSet sequence;

    // Transmission control variables
    private long startTime;
    private long seqNum;
    private boolean enabled;

    // Firebase
    private FirebaseInterface firebaseInterface;

    // Camera control
    private CameraControl cameraControl;

    // Activity
    private Activity activity;

    // Network Utils
    private NetworkUtils networkUtils;
    private Hamming74 hamming74;

    public VLCSender(CameraControl cameraControl, FirebaseInterface firebaseInterface,
                     Activity activity) {
        this.cameraControl = cameraControl;
        this.firebaseInterface = firebaseInterface;
        this.activity = activity;
        networkUtils = new NetworkUtils();
        hamming74 = new Hamming74();
        enabled = true;
    }

    @Override public void run() {
        blinkBinSequence();
        activity.runOnUiThread(() -> firebaseInterface.setAndroidState(AndroidState.TX_ENDED));
    }

    private void blinkBinSequence() {


        startTime = System.currentTimeMillis();
        seqNum = 0;

        Log.i(TAG, "TxSequence : " + Hex.encodeHexString(sequence.toByteArray()));


        Log.i(TAG, "Blinking start sequence...");
        blinkSequence(START_SEQ, txRate);
        Log.i(TAG, "Blinking message...");
        for (int i = 0; i <= ((sequence.length() - 1) / WORD_SIZE); i++) {
            blinkSequence(sequence.get(i * WORD_SIZE, (i + 1) * WORD_SIZE), txRate);
        }

        try {
            Thread.sleep(1000 / txRate);
        } catch (InterruptedException ignored) {

        }
    }

    private void torchOff() {
        cameraControl.enableTorch(false);
    }

    private void blinkSequence(BitSet sequence, long txRate) {

        boolean finished = false;
        int counter = 0;

        int zeroPadding =
                sequence.length() % WORD_SIZE == 0 ? 0 : WORD_SIZE - sequence.length() % WORD_SIZE;

        if (sequence.length() != 0) {
            Log.i(TAG, String.format("%8s", Long.toBinaryString(sequence.toLongArray()[0]))
                    .replace(' ', '0') + " -> " + ((int) (seqNum / 8)));
        } else {
            Log.i(TAG, "00000000 -> " + ((int) (seqNum / 8)));
        }

        while (!finished) {
            if (enabled) {
                if (System.currentTimeMillis() - startTime > seqNum * (1000.0 / txRate)) {
                    if (counter < sequence.length()) {
                        cameraControl.enableTorch(sequence.get(counter));
                        counter++;
                        seqNum++;
                    } else if (counter >= sequence.length() &&
                            counter < sequence.length() + zeroPadding) {
                        cameraControl.enableTorch(false);
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

    public void setParams(BitSet sequence, long txRate, boolean hamming) {
        // Calculate length and CRC and append it to sequence
        this.sequence =
                hamming ? addCRC(addLength(BitSet.valueOf(hamming74.encodeByteArray(sequence.toByteArray()))))
                        : addCRC(addLength(sequence));
        this.txRate = txRate;
        enabled = true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            cameraControl.enableTorch(false);
            sequence = new BitSet();
        }
    }

    private BitSet addLength(BitSet sequence) {
        int seqLengthInt = sequence.toByteArray().length;
        // The length is stated 2 bytes
        byte[] seqLength = new byte[2];
        seqLength[0] = (byte) (seqLengthInt & 0xFF);
        seqLength[1] = (byte) ((seqLengthInt >> 8) & 0xFF);
        // newBitSet will put the length of the sequence and then the sequence itself
        BitSet newBitSet = BitSet.valueOf(seqLength);
        for (int i = 0; i < sequence.length(); i++) {
            newBitSet.set(seqLength.length * WORD_SIZE + i, sequence.get(i));
        }
        return newBitSet;
    }

    private BitSet addCRC(BitSet sequence) {
        BitSet sequenceCRC = networkUtils.calculateCRC(sequence);
        Log.i(TAG, "START_TX. Calculated CRC : " + Hex.encodeHexString(sequenceCRC.toByteArray()));
        int sequenceLength = sequence.toByteArray().length * WORD_SIZE;
        for (int i = 0; i < sequenceCRC.length(); i++) {
            sequence.set(sequenceLength + i, sequenceCRC.get(i));
        }
        return sequence;
    }
}
