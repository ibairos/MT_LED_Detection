package be.kuleuven.mt_ibai_vlc.network.vlc.receiver;

import android.app.Activity;
import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.gson.Gson;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.OptionalDouble;

import be.kuleuven.mt_ibai_vlc.events.CustomEventListener;
import be.kuleuven.mt_ibai_vlc.model.enums.AnalyzerState;
import be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseInterface;

public class LightReceiver implements ImageAnalysis.Analyzer {

    // Logging tag
    private static final String TAG = "LightReceiver";

    // Constant parameters
    private static final Pair[] luminosityToleranceTable =
            new Pair[]{
                    new Pair<>(80.0, 1.2),
                    new Pair<>(90.0, 1.175),
                    new Pair<>(100.0, 1.15),
                    new Pair<>(110.0, 1.125),
                    new Pair<>(120.0, 1.1),
                    new Pair<>(130.0, 1.075),
                    new Pair<>(140.0, 1.05),
                    new Pair<>(150.0, 1.025),
                    new Pair<>(Double.MAX_VALUE, 1.01)
            };
    private static final double FRAME_RATE_TOLERANCE = 0.9;

    private static final int CHAR_SIZE = 8; // UTF-8

    // Control sequences
    private static final BitSet START_SEQ = BitSet.valueOf(new long[]{0b11111111});
    private static final BitSet END_SEQ = BitSet.valueOf(new long[]{0b11111111});
    private static double luminosityTolerance;
    // Runtime variables
    private AnalyzerState TXState;
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

    // Firebase
    private FirebaseInterface firebaseInterface;

    // Cropped Rect
    private Rect cropRect;
    private int imageWidth;

    // Variable rates
    private long samplingRate;
    private long timePerFrameMillis;

    public LightReceiver(Activity activity, FirebaseInterface firebaseInterface, Rect cropRect, int imageWidth, long txRate,
                         long samplingRate) {
        Log.e(TAG, new Gson().toJson(cropRect));

        this.activity = activity;
        this.firebaseInterface = firebaseInterface;
        this.cropRect = cropRect;
        this.imageWidth = imageWidth;
        this.samplingRate = samplingRate;
        timePerFrameMillis = 1000 / (samplingRate * txRate); // Input data on seconds
        Log.i(TAG, "SamplingRate: " + samplingRate);
        Log.i(TAG, "TimePerFrameMillis: " + timePerFrameMillis);

        lastAnalyzedTimestamp = 0L;
        lastLuminosityValues = new ArrayList<>();
        charBuffer = new BitSet(CHAR_SIZE);
        resultBuffer = new BitSet();
        charBuffer.clear();
        result = "";
        TXState = AnalyzerState.LOADING;
        ((CustomEventListener) activity).onAnalyzerEvent(AnalyzerState.LOADING, null);
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
            if (cropRect.left <= x
                    && x < cropRect.right
                    && cropRect.top <= y
                    && y < cropRect.bottom) {
                ret.add(data[i]);
            }
        }
        return ret;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {

        long currentTimestamp = System.currentTimeMillis();

        // Calculate the average luminescence no more often than every FRAME_RATE_SECONDS
        if (currentTimestamp - lastAnalyzedTimestamp >=
                timePerFrameMillis * FRAME_RATE_TOLERANCE) {

            // Update timestamp of last analyzed frame
            long theoreticalTimestamp = lastAnalyzedTimestamp + timePerFrameMillis;
            lastAnalyzedTimestamp =
                    lastAnalyzedTimestamp != 0L ? theoreticalTimestamp : currentTimestamp;

            // Process frame buffer and return window average
            double windowLumAverage = processValue(image.getPlanes()[0].getBuffer());

            image.close();

            // Act according to TXState
            switch (TXState) {
                case LOADING:
                    OptionalDouble ilm =
                            lastLuminosityValues.stream().mapToDouble(a -> a).average();
                    initialLumAverage = ilm.isPresent() ? ilm.getAsDouble() : 0;
                    if (lastLuminosityValues.size() == samplingRate) {
                        for (int i = 0;
                             i < luminosityToleranceTable.length && luminosityTolerance == 0; i++) {
                            if ((double) luminosityToleranceTable[i].first >= initialLumAverage) {
                                luminosityTolerance = (double) luminosityToleranceTable[i].second;
                                Log.w(TAG, "ILA: " + initialLumAverage + ", LT: " +
                                        luminosityTolerance);
                            }
                        }
                        TXState = AnalyzerState.WAITING;
                        activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                .onAnalyzerEvent(AnalyzerState.WAITING, null));
                    }
                    break;

                case WAITING:
                    if (bitIsSet(windowLumAverage)) {
                        charBuffer.set(syncCharIndex);
                        syncCharIndex++;
                        TXState = AnalyzerState.STARTING;
                        activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                .onAnalyzerEvent(AnalyzerState.STARTING, null));
                    }
                    break;

                case STARTING:
                    syncSeqNum++;
                    if (syncSeqNum % samplingRate == 0) {
                        if (bitIsSet(windowLumAverage)) {
                            charBuffer.set(Math.toIntExact(syncCharIndex));
                        }
                        if (syncCharIndex == CHAR_SIZE - 1) {
                            Log.e(TAG,
                                    "COMP - CB - " + new Gson().toJson(charBuffer.toLongArray()));
                            Log.e(TAG, "COMP - SS - " + new Gson().toJson(START_SEQ.toLongArray()));
                            if (charBuffer.equals(START_SEQ)) {
                                TXState = AnalyzerState.TX_STARTED;
                                activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                        .onAnalyzerEvent(AnalyzerState.TX_STARTED, null));
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
                                TXState = AnalyzerState.WAITING;
                                activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                        .onAnalyzerEvent(AnalyzerState.WAITING, null));
                            }
                            Log.i(TAG, "SEQ_NUM: " + syncSeqNum);
                        } else {
                            syncCharIndex++;
                        }
                    }
                    break;

                case TX_STARTED:
                    txSeqNum++;
                    if (txSeqNum % samplingRate == 0) {
                        charBuffer.set(txCharIndex, bitIsSet(windowLumAverage));
                        Log.i(TAG, "TXSeq: " + txSeqNum + ", TXChar: " + txCharIndex);
                        txCharIndex++;
                        if (txCharIndex == CHAR_SIZE) {
                            txCharIndex = 0;
                            Log.i(TAG, new Gson()
                                    .toJson(Long.toBinaryString(charBuffer.toLongArray()[0])));
                            if (!charBuffer.equals(END_SEQ)) {
                                for (int i = 0; i < 8; i++) {
                                    resultBuffer.set(result.length() * CHAR_SIZE + i,
                                            charBuffer.get(i));
                                }
                                firebaseInterface.setAndroidResult(result += new String(charBuffer.toByteArray(),
                                        StandardCharsets.UTF_8));

                                Log.i("STRINGVAL", new String(charBuffer.toByteArray(),
                                        StandardCharsets.UTF_8));
                                charBuffer.clear();
                            } else {
                                // Send last event
                                TXState = AnalyzerState.TX_ENDED;
                                activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                        .onAnalyzerEvent(AnalyzerState.TX_ENDED, result));
                                reset();
                            }
                        }
                    }
                    break;

                case TX_ENDED:
                    break;
            }

        } else {
            image.close();
        }
    }

    private double processValue(ByteBuffer buffer) {

        // Extract image data from callback object
        ArrayList<Byte> data = toCroppedByteArray(buffer);

        // Convert the data into an array of pixel values
        OptionalDouble av = data.stream().mapToDouble(a -> a & 0xFF).average();
        double singleLum = av.isPresent() ? av.getAsDouble() : 0;
        lastLuminosityValues.add(singleLum);

        if (lastLuminosityValues.size() > samplingRate) {
            lastLuminosityValues.remove(0);
        }
        OptionalDouble od = lastLuminosityValues.stream().mapToDouble(a -> a).average();

        return od.isPresent() ? od.getAsDouble() : 0;
    }

    private Boolean bitIsSet(double windowLumAverage) {
        Boolean ret = windowLumAverage / initialLumAverage > luminosityTolerance;
        Log.e(TAG, TXState.toString() + " - BitIsSet: " + ret.toString() + " -> " +
                String.format("%.2f", windowLumAverage / initialLumAverage) + "(" +
                String.format("%.2f", windowLumAverage) + ")" + " - SCI: " + syncCharIndex);
        return ret;
    }

    private void reset() {
        lastLuminosityValues.clear();
        initialLumAverage = 0;
        luminosityTolerance = 0;
    }

}
