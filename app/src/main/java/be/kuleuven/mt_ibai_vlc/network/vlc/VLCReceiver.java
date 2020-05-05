package be.kuleuven.mt_ibai_vlc.network.vlc;

import android.app.Activity;
import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.gson.Gson;

import org.apache.commons.codec.binary.Hex;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.OptionalDouble;

import be.kuleuven.mt_ibai_vlc.common.Hamming74;
import be.kuleuven.mt_ibai_vlc.common.NetworkUtils;
import be.kuleuven.mt_ibai_vlc.events.CustomEventListener;
import be.kuleuven.mt_ibai_vlc.model.enums.AnalyzerState;

public class VLCReceiver implements ImageAnalysis.Analyzer {

    // Logging tag
    private static final String TAG = "LightReceiver";

    // Constant parameters
    private static final Pair[] luminosityToleranceTable =
            new Pair[]{
                    new Pair<>(80.0, 1.4),
                    new Pair<>(90.0, 1.3),
                    new Pair<>(100.0, 1.25),
                    new Pair<>(110.0, 1.2),
                    new Pair<>(120.0, 1.175),
                    new Pair<>(130.0, 1.15),
                    new Pair<>(140.0, 1.125),
                    new Pair<>(150.0, 1.1),
                    new Pair<>(Double.MAX_VALUE, 1.075)
            };

    private static final int WORD_SIZE = 8; // UTF-8

    // Control sequences
    private static final BitSet START_SEQ = BitSet.valueOf(new long[]{0b11111111});
    private static double luminosityTolerance;

    // Runtime variables
    private boolean analyze;
    private AnalyzerState txState;
    private long lastAnalyzedTimestamp;
    private double initialLumAverage;
    private ArrayList<Double> lastLuminosityValues;

    // Results
    private BitSet tmpRxBuffer;
    private BitSet resultBuffer;
    private BitSet seqLengthBuffer;
    private int numRxWords;

    // Sequence numbers
    private long syncSeqNum;
    private long txSeqNum;
    private int syncCharIndex;
    private int txCharIndex;
    private int sequenceLength;

    // Parent activity
    private Activity activity;

    // Cropped Rect
    private Rect cropRect;

    // Variable rates
    private long samplingRate;
    private long timePerFrameMillis;

    // Network Utils
    private NetworkUtils networkUtils;
    private Hamming74 hamming74;

    // On/Off Keying
    private boolean onOffKeyingEnabled;

    public VLCReceiver(Activity activity, Rect cropRect,
                       long txRate,
                       long samplingRate, boolean onOffKeyingEnabled) {
        this.activity = activity;
        this.cropRect = cropRect;
        this.samplingRate = samplingRate;
        this.onOffKeyingEnabled = onOffKeyingEnabled;
        timePerFrameMillis = 1000 / (samplingRate * txRate); // Input data on seconds
        networkUtils = new NetworkUtils();
        hamming74 = new Hamming74();

        Log.i(TAG, "CropRect: " + new Gson().toJson(cropRect));
        Log.i(TAG, "SamplingRate: " + samplingRate);
        Log.i(TAG, "TimePerFrameMillis: " + timePerFrameMillis);
        Log.i(TAG, "OnOffKeying: " + onOffKeyingEnabled);

        reset();

        setEnabled(true);
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {

        long currentTimestamp = System.currentTimeMillis();

        // Calculate the average luminescence no more often than every FRAME_RATE_SECONDS
        if (currentTimestamp - lastAnalyzedTimestamp >=
                timePerFrameMillis
                && analyze) {

            // Update timestamp of last analyzed frame
            long theoreticalTimestamp = lastAnalyzedTimestamp + timePerFrameMillis;
            lastAnalyzedTimestamp =
                    lastAnalyzedTimestamp != 0L ? theoreticalTimestamp : currentTimestamp;

            // Process frame buffer and return window average
            double windowLumAverage =
                    processValue(image.getPlanes()[0].getBuffer(), image.getWidth());

            image.close();

            // Act according to TXState
            switch (txState) {
                case LOADING:
                    OptionalDouble ilm =
                            lastLuminosityValues.stream().mapToDouble(a -> a).average();
                    initialLumAverage = ilm.isPresent() ? ilm.getAsDouble() : 0;
                    if (lastLuminosityValues.size() == samplingRate) {
                        for (int i = 0;
                             i < luminosityToleranceTable.length && luminosityTolerance == 0; i++) {
                            if ((double) luminosityToleranceTable[i].first >= initialLumAverage) {
                                luminosityTolerance = (double) luminosityToleranceTable[i].second;
                                Log.i(TAG, "ILA: " + initialLumAverage + ", LT: " +
                                        luminosityTolerance);
                            }
                        }
                        txState = AnalyzerState.WAITING;
                        activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                .onAnalyzerEvent(AnalyzerState.WAITING, null));
                    }
                    break;

                case WAITING:
                    if (bitIsSet(windowLumAverage)) {
                        tmpRxBuffer.set(syncCharIndex);
                        syncCharIndex++;
                        txState = AnalyzerState.STARTING;
                        activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                .onAnalyzerEvent(AnalyzerState.STARTING, null));
                    }
                    break;

                case STARTING:
                    syncSeqNum++;
                    if (syncSeqNum % samplingRate == 0) {
                        tmpRxBuffer.set(Math.toIntExact(syncCharIndex), bitIsSet(windowLumAverage));
                        if (syncCharIndex == WORD_SIZE - 1) {
                            if (tmpRxBuffer.equals(START_SEQ)) {
                                txState = AnalyzerState.TX_STARTED;
                                activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                        .onAnalyzerEvent(AnalyzerState.TX_STARTED, null));
                            } else {
                                Log.i(TAG, txState.toString() + " - START_SEQ - Wrong -> " +
                                        Integer.toBinaryString((int) tmpRxBuffer.toLongArray()[0]));
                                activity.runOnUiThread(() -> Toast
                                        .makeText(activity.getApplicationContext(),
                                                Integer.toBinaryString(
                                                        (int) tmpRxBuffer.toLongArray()[0]),
                                                Toast.LENGTH_LONG).show());
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                syncSeqNum = 0;
                                syncCharIndex = 0;
                                tmpRxBuffer.clear();
                                txState = AnalyzerState.WAITING;
                                activity.runOnUiThread(() -> ((CustomEventListener) activity)
                                        .onAnalyzerEvent(AnalyzerState.WAITING, null));
                            }
                        } else {
                            syncCharIndex++;
                        }
                    }
                    break;

                case TX_STARTED:
                    txSeqNum++;
                    if (txSeqNum % samplingRate == 0) {
                        tmpRxBuffer.set(txCharIndex, bitIsSet(windowLumAverage));
                        txCharIndex++;
                        if (txCharIndex == WORD_SIZE) {
                            txCharIndex = 0;
                            Log.i(TAG, String.format("%8s",
                                    Long.toBinaryString(tmpRxBuffer.toLongArray()[0]))
                                    .replace(' ', '0') + " (" + numRxWords + ")");
                            numRxWords++;
                            if (sequenceLength == 0) {
                                for (int i = 0; i < WORD_SIZE; i++) {
                                    seqLengthBuffer
                                            .set(numRxWords * WORD_SIZE + i, tmpRxBuffer.get(i));
                                    resultBuffer
                                            .set(numRxWords * WORD_SIZE + i, tmpRxBuffer.get(i));
                                }
                                if (numRxWords == 2) {
                                    sequenceLength = parseLength(seqLengthBuffer);
                                }
                            } else {
                                for (int i = 0; i < WORD_SIZE; i++) {
                                    resultBuffer
                                            .set(numRxWords * WORD_SIZE + i, tmpRxBuffer.get(i));
                                }
                                if (numRxWords == sequenceLength + 2) {
                                    Log.i(TAG, "Reached end of sequence.");
                                    endRx();
                                }
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

    private void endRx() {
        // Send last event
        txState = AnalyzerState.TX_ENDED;
        // Calculate CRC
        int crcInitPos = (numRxWords - 4) * WORD_SIZE;
        int crcEndPos = numRxWords * WORD_SIZE;

        if (crcInitPos < 0) {
            activity.runOnUiThread(() -> ((CustomEventListener) activity)
                    .onAnalyzerEvent(AnalyzerState.TX_ERROR, resultBuffer.toByteArray()));
            return;
        }

        byte[] rxCrc = resultBuffer.get(crcInitPos, crcEndPos).toByteArray();
        Object[] rxCrcObj = {rxCrc};
        resultBuffer.clear(crcInitPos, crcEndPos);
        byte[] calcCrc = networkUtils.calculateCRC(resultBuffer).toByteArray();
        Object[] calcCrcObj = {calcCrc};

        BitSet rxData = resultBuffer.get(2 * WORD_SIZE, crcInitPos);

        Log.i(TAG, "RX_DATA: " + Hex.encodeHexString(rxData.toByteArray()));
        Log.i(TAG, "RX_CRC: " + Hex.encodeHexString(rxCrc));
        Log.i(TAG, "CALC_CRC: " + Hex.encodeHexString(calcCrc));

        if (Arrays.deepEquals(rxCrcObj, calcCrcObj)) {
            activity.runOnUiThread(() -> ((CustomEventListener) activity)
                    .onAnalyzerEvent(AnalyzerState.TX_ENDED, hamming74.decodeByteArray(rxData.toByteArray())));
        } else {
            activity.runOnUiThread(() -> ((CustomEventListener) activity)
                    .onAnalyzerEvent(AnalyzerState.TX_ERROR, hamming74.decodeByteArray(rxData.toByteArray())));
        }
    }

    private Boolean bitIsSet(double windowLumAverage) {
        return onOffKeyingEnabled ? bitIsSetOOK(windowLumAverage) : bitIsSetAverage(windowLumAverage);
    }

    private Boolean bitIsSetAverage(double windowLumAverage) {
        Boolean ret = windowLumAverage / initialLumAverage > luminosityTolerance;
        Log.d(TAG, txState.toString() + " - BitIsSet: " + (ret ? 1 : 0) + " -> " +
                String.format("%.2f", windowLumAverage / initialLumAverage) + "(" +
                String.format("%.2f", windowLumAverage) + ")" + " - SCI: " + syncCharIndex);
        return ret;
    }

    private Boolean bitIsSetOOK(double windowLumAverage) {
        // Index 0 is false and index 1 is true
        int[] counts = new int[]{0, 0};
        for (double lumValue : lastLuminosityValues) {
            if (lumValue / initialLumAverage > luminosityTolerance) {
                counts[1] = counts[1] + 1;
            } else {
                counts[0] = counts[0] + 1;
            }
        }

        Boolean ret = counts[1] > counts[0];

        Log.d(TAG, txState.toString() + " - BitIsSet: " + ret.toString() + " -> " +
                String.format("%.2f", windowLumAverage / initialLumAverage) + "(" +
                String.format("%.2f", windowLumAverage) + "), " + "(" + counts[1] + " / " +
                (counts[0] + counts[1] + ")" + " - SCI: " + syncCharIndex));
        return ret;
    }

    private double processValue(ByteBuffer buffer, int imageWidth) {

        // Extract image data from callback object
        List<Byte> data = toCroppedByteArray(buffer, imageWidth);

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

    /**
     * Helper extension function used to extract a byte array from an image plane charBuffer
     */
    private List<Byte> toCroppedByteArray(ByteBuffer byteBuffer, int imageWidth) {
        Buffer b = byteBuffer.rewind();
        byte[] data = new byte[b.remaining()];
        byteBuffer.get(data, b.position(), data.length);
        List<Byte> ret = new ArrayList<>();

        int len = data.length;

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
        Log.e(TAG, "Init Len: " + len + ", Final Len: " + ret.size());
        return ret;
    }

    private void reset() {
        txState = AnalyzerState.TX_ENDED;
        lastLuminosityValues = new ArrayList<>();
        tmpRxBuffer = new BitSet(WORD_SIZE);
        resultBuffer = new BitSet();
        seqLengthBuffer = new BitSet();
        lastLuminosityValues.clear();
        lastAnalyzedTimestamp = 0L;
        initialLumAverage = 0;
        luminosityTolerance = 0;
        syncSeqNum = 0;
        txSeqNum = 0;
        syncCharIndex = 0;
        txCharIndex = 0;
        resultBuffer.clear();
        tmpRxBuffer.clear();
        numRxWords = 0;
        sequenceLength = 0;
        analyze = false;
    }

    private int parseLength(BitSet seqLengthBuffer) {
        byte[] seqLenBytes = seqLengthBuffer.toByteArray();
        return seqLenBytes.length == 2 ? ((seqLenBytes[1] & 0xff) << 8) | (seqLenBytes[0] & 0xff) :
               (seqLenBytes[0] & 0xff);
    }

    public void updateParams(Rect cropRect, long txRate, long samplingRate, boolean onOffKeying) {
        this.cropRect = cropRect;
        this.samplingRate = samplingRate;
        this.onOffKeyingEnabled = onOffKeying;
        timePerFrameMillis = 1000 / (samplingRate * txRate); // Input data on seconds
        Log.i(TAG, "CropRect: " + new Gson().toJson(cropRect));
        Log.i(TAG, "SamplingRate: " + samplingRate);
        Log.i(TAG, "TimePerFrameMillis: " + timePerFrameMillis);
        Log.i(TAG, "OnOffKeying: " + onOffKeying);

        reset();
    }

    public void setEnabled(boolean enabled) {
        if (enabled) {
            txState = AnalyzerState.LOADING;
            ((CustomEventListener) activity).onAnalyzerEvent(AnalyzerState.LOADING, null);
            analyze = true;
        } else {
            reset();
        }
    }

}
