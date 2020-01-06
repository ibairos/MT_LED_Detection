package be.kuleuven.mt_ibai_vlc.network.vlc.sender;

import android.util.Log;

import androidx.camera.core.Preview;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

public class LightSender {

    private static final String TAG = "LightSender";

    private Preview preview;

    private static final int BLINK_DELAY_MILLIS = 500;
    private static final int INITIAL_BLINK_DELAY_MILLIS = 5000;

    private static final BitSet START_SEQ = BitSet.valueOf(new long[]{0b11100111});
    private static final BitSet END_SEQ = BitSet.valueOf(new long[]{0b11111111});

    public LightSender(Preview preview) {
        this.preview = preview;
    }

    public void blinkWholeSequence(String sequence) {
        BitSet seq = BitSet.valueOf(sequence.getBytes(StandardCharsets.UTF_8));

        Log.i(TAG, "Blinking start sequence...");
        blinkSequence(START_SEQ);
        Log.i(TAG, "Blinking message...");
        blinkSequence(seq);
        Log.i(TAG, "Blinking end sequence...");
        blinkSequence(END_SEQ);
    }

    private void blinkSequence(BitSet sequence) {
        for (int i = 0; i < sequence.length(); i++) {
            preview.enableTorch(sequence.get(i));
            try {
                Thread.sleep(BLINK_DELAY_MILLIS);
            } catch (InterruptedException ignored) {

            }
        }
    }

}
