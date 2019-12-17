package tk.porrazirauki.mt_leddetection.analyzers;

import android.app.Activity;
import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.gson.Gson;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;

import tk.porrazirauki.mt_leddetection.events.CustomEventListener;

public class LuminosityAnalyzer implements ImageAnalysis.Analyzer {

    // Logging tag
    private static final String TAG = "LuminosityAnalyzer";

    // Constant parameters
    private static final Pair[] luminosityToleranceTable =
            new Pair[]{
                    new Pair<>(80, 1.4),
                    new Pair<>(90, 1.3),
                    new Pair<>(100, 1.2),
                    new Pair<>(110, 1.175),
                    new Pair<>(120, 1.15),
                    new Pair<>(130, 1.125),
                    new Pair<>(140, 1.1),
                    new Pair<>(150, 1.075),
                    new Pair<>(Double.MAX_VALUE, 1.05)
            };
    private static final double FRAME_RATE_SECONDS = 12;
    private static final double SECOND_TO_NANOSECOND_RATIO = 1000000000;
    private static final int UPDATE_RATE_LED_SECONDS = 3;
    private static final double FRAME_RATE_NANOSECONDS =
            FRAME_RATE_SECONDS / SECOND_TO_NANOSECOND_RATIO;
    private static final long
            TIME_PER_FRAME_MILLIS =
            TimeUnit.NANOSECONDS.toMillis((long) (1 / FRAME_RATE_NANOSECONDS));
    private static final double FRAME_RATE_TOLERANCE = 0.9;
    private static final int SLIDING_WINDOW_SIZE =
            (int) (FRAME_RATE_SECONDS / UPDATE_RATE_LED_SECONDS);
    //private static final double WINDOW_TOLERANCE = 1 + 1 / (double) SLIDING_WINDOW_SIZE;
    private static final int CHAR_SIZE = 8; // UTF-8
    // Control sequences
    private static final BitSet START_SEQ = BitSet.valueOf(new long[]{0b11100111});
    private static final BitSet END_SEQ = BitSet.valueOf(new long[]{0b11111111});
    private static double luminosityTolerance;
    // Runtime variables
    private TX_STATE TXState;
    private long lastAnalyzedTimestamp;
    private double initialLumAverage;
    private ArrayList<Double> lastLuminosityValues;
    private BitSet charBuffer;
    private String result;
    private BitSet resultBuffer;

    // Sequence numbers
    private long syncSeqNum = 0;
    private long txSeqNum = 0;
    private int syncCharIndex = 0;
    private int txCharIndex = 0;

    // Parent activity
    private Activity activity;

    // Cropped Rect
    private Rect cropRect;
    private int imageWidth;

    public LuminosityAnalyzer(Activity activity, Rect cropRect, int imageWidth) {
        this.activity = activity;
        this.cropRect = cropRect;
        this.imageWidth = imageWidth;
        lastAnalyzedTimestamp = 0L;
        lastLuminosityValues = new ArrayList<>();
        charBuffer = new BitSet(CHAR_SIZE);
        resultBuffer = new BitSet();
        charBuffer.clear();
        result = "";
        TXState = TX_STATE.LOADING;
        ((CustomEventListener) activity).onEvent(TX_STATE.LOADING.toString(), null);

    }

    /**
     * Helper extension function used to extract a byte array from an image plane charBuffer
     */
    private ArrayList<Byte> toCroppedByteArray(ByteBuffer byteBuffer) {
        Buffer b = byteBuffer.rewind();
        byte[] data = new byte[b.remaining()];
        byteBuffer.get(data, b.position(), data.length);
        ArrayList<Byte> ret = new ArrayList<>();

        for (int i = 0; i < data.length; i++) {
            int x = i % imageWidth;
            int y = i / imageWidth;
            if (cropRect.left <= x && x < cropRect.right && cropRect.top <= y &&
                    y < cropRect.bottom) {
                ret.add(data[i]);
            }
        }
        return ret;
    }

    @Override
    public void analyze(ImageProxy image, int rotationDegrees) {
        try {
            CameraX.getCameraControl(CameraX.LensFacing.BACK).cancelFocusAndMetering();
        } catch (CameraInfoUnavailableException ignored) {
        }

        long currentTimestamp = System.currentTimeMillis();
        // Calculate the average luminescence no more often than every FRAME_RATE_SECONDS

        if (currentTimestamp - lastAnalyzedTimestamp >=
                TIME_PER_FRAME_MILLIS * FRAME_RATE_TOLERANCE) {

            // Update timestamp of last analyzed frame
            long theoreticalTimestamp = lastAnalyzedTimestamp + TIME_PER_FRAME_MILLIS;
            lastAnalyzedTimestamp =
                    lastAnalyzedTimestamp != 0L ? theoreticalTimestamp : currentTimestamp;

            // Process frame buffer and return window average
            double windowLumAverage = processValue(image.getPlanes()[0].getBuffer());

            // Act according to TXState
            switch (TXState) {
                case LOADING:
                    OptionalDouble ilm =
                            lastLuminosityValues.stream().mapToDouble(a -> a).average();
                    initialLumAverage = ilm.isPresent() ? ilm.getAsDouble() : 0;
                    if (lastLuminosityValues.size() == SLIDING_WINDOW_SIZE) {
                        for (int i = 0;
                             i < luminosityToleranceTable.length && luminosityTolerance == 0; i++) {
                            if ((Double) luminosityToleranceTable[i].first >= initialLumAverage) {
                                luminosityTolerance = (double) luminosityToleranceTable[i].second;
                            }
                        }
                        TXState = TX_STATE.WAITING;
                        activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                .onEvent(TX_STATE.WAITING.toString(), null));
                    }
                    break;

                case WAITING:
                    //double lastFrameLum = lastLuminosityValues.get(SLIDING_WINDOW_SIZE - 1);
                    if (bitIsSet(windowLumAverage)) {
                        //&& lastFrameLum / windowLumAverage < WINDOW_TOLERANCE) {
                        charBuffer.set(syncCharIndex);
                        syncCharIndex++;
                        TXState = TX_STATE.STARTING;
                        activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                .onEvent(TX_STATE.STARTING.toString(), null));
                    }
                    break;

                case STARTING:
                    syncSeqNum++;
                    if (syncSeqNum % SLIDING_WINDOW_SIZE == 0) {
                        Boolean bitIsOne = bitIsSet(windowLumAverage);
                        if (bitIsOne) {
                            charBuffer.set(Math.toIntExact(syncCharIndex));
                        }
                        if (syncCharIndex == CHAR_SIZE - 1) {
                            Log.e(TAG,
                                    "COMP - CB - " + new Gson().toJson(charBuffer.toLongArray()));
                            Log.e(TAG, "COMP - SS - " + new Gson().toJson(START_SEQ.toLongArray()));
                            if (charBuffer.equals(START_SEQ)) {
                                TXState = TX_STATE.TX_STARTED;
                                activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                        .onEvent(TX_STATE.TX_STARTED.toString(), null));
                            } else {
                                Log.i(TAG, TXState.toString() + " - START_SEQ - Wrong -> " +
                                        Integer.toBinaryString((int) charBuffer.toLongArray()[0]));
                                activity.runOnUiThread(() -> Toast
                                        .makeText(activity.getApplicationContext(),
                                                Integer.toBinaryString(
                                                        (int) charBuffer.toLongArray()[0]),
                                                Toast.LENGTH_LONG).show());
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                syncSeqNum = 0;
                                syncCharIndex = 0;
                                charBuffer.clear();
                                TXState = TX_STATE.WAITING;
                                activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                        .onEvent(TX_STATE.WAITING.toString(), null));
                            }
                        } else {
                            syncCharIndex++;
                        }
                    }
                    break;

                case TX_STARTED:
                    txSeqNum++;
                    if (txSeqNum > SLIDING_WINDOW_SIZE && txSeqNum % SLIDING_WINDOW_SIZE == 0) {
                        charBuffer.set(txCharIndex, bitIsSet(windowLumAverage));
                        Log.i(TAG, "TXSeq: " + txSeqNum + ", TXChar: " + txCharIndex);
                        txCharIndex++;
                        if (txCharIndex == CHAR_SIZE) {
                            txCharIndex = 0;
                            if (!charBuffer.equals(END_SEQ)) {
                                for (int i = 0; i < 8; i++) {
                                    resultBuffer.set(result.length() * CHAR_SIZE + i,
                                            charBuffer.get(i));
                                }
                                result += new String(charBuffer.toByteArray(),
                                        StandardCharsets.UTF_8);
                                Log.i("STRINGVAL", new String(charBuffer.toByteArray(),
                                        StandardCharsets.UTF_8));
                                charBuffer.clear();
                            } else {
                                // Send last event
                                TXState = TX_STATE.TX_ENDED;
                                activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                        .onEvent(TX_STATE.TX_ENDED.toString(),
                                                new Gson().toJson(result)));
                            }
                        }
                    }
                    break;

                case TX_ENDED:
                    break;
            }

        }
    }

    private double processValue(ByteBuffer buffer) {

        // Extract image data from callback object
        ArrayList<Byte> data = toCroppedByteArray(buffer);

        // Convert the data into an array of pixel values
        OptionalDouble av = data.stream().mapToDouble(a -> a & 0xFF).average();
        // Log the new luminosity value
        double singleLum = av.isPresent() ? av.getAsDouble() : 0;
        //Log.d(TAG, "SingleAverageLum: " + singleLum);
        lastLuminosityValues.add(singleLum);

        if (lastLuminosityValues.size() > SLIDING_WINDOW_SIZE) {
            lastLuminosityValues.remove(0);
        }
        OptionalDouble od = lastLuminosityValues.stream().mapToDouble(a -> a).average();

        return od.isPresent() ? od.getAsDouble() : 0;
    }

    private Boolean bitIsSet(double windowLumAverage) {
        Boolean ret = windowLumAverage / initialLumAverage > luminosityTolerance;
        Log.i(TAG, TXState.toString() + " - BitIsSet: " + ret.toString() + " -> " +
                (windowLumAverage / initialLumAverage) + " - SCI: " + syncCharIndex);
        return ret;
    }

    public String getResult() {
        Log.i(TAG, "RESULT_STR -> " + result);
        Log.i(TAG, "RESULT_ARR ->" + new Gson().toJson(resultBuffer));
        return result;
    }

    public enum TX_STATE {LOADING, WAITING, STARTING, TX_STARTED, TX_ENDED}
}
