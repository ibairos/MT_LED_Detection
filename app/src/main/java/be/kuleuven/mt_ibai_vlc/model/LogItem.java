package be.kuleuven.mt_ibai_vlc.model;

import com.google.firebase.database.Exclude;

import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import be.kuleuven.mt_ibai_vlc.model.enums.TxMode;


public class LogItem {

    private double accuracy;

    private long elapsed_time;

    private String tx_data;

    private String tx_data_bin;

    private String rx_data;

    private String rx_data_bin;

    private long time;

    private TxMode tx_mode;

    private long tx_rate;

    private long sample_num;

    private int distance;

    private boolean hamming_enabled;

    private boolean on_off_keying;

    public LogItem(String tx_data, long time,
                   TxMode tx_mode, long tx_rate, long sample_num, int distance, boolean hamming_enabled, boolean on_off_keying) {
        this.tx_data = tx_data;
        tx_data_bin = textToBinaryString(tx_data);
        this.time = time;
        this.tx_mode = tx_mode;
        this.tx_rate = tx_rate;
        this.sample_num = sample_num;
        this.distance = distance;
        this.hamming_enabled = hamming_enabled;
        this.on_off_keying = on_off_keying;
    }

    @Exclude
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("accuracy", accuracy);
        result.put("elapsed_time", elapsed_time);
        result.put("tx_data", tx_data);
        result.put("tx_data_bin", tx_data_bin);
        result.put("rx_data", rx_data);
        result.put("rx_data_bin", rx_data_bin);
        result.put("time", time);
        result.put("tx_mode", tx_mode);
        result.put("tx_rate", tx_rate);
        result.put("sample_num", sample_num);
        result.put("distance", distance);
        result.put("hamming_enabled", hamming_enabled);
        result.put("on_off_keying", on_off_keying);

        return result;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public long getElapsed_time() {
        return elapsed_time;
    }

    public String getTx_data() {
        return tx_data;
    }

    public String getRx_data() {
        return rx_data;
    }

    public long getTime() {
        return time;
    }

    public TxMode getTx_mode() {
        return tx_mode;
    }

    public long getTx_rate() {
        return tx_rate;
    }

    public void setTx_rate(long tx_rate) {
        this.tx_rate = tx_rate;
    }

    public long getSample_num() {
        return sample_num;
    }

    public void setSample_num(long sample_num) {
        this.sample_num = sample_num;
    }

    public void completeLog(String rx_data) {
        this.rx_data = rx_data;
        rx_data_bin = textToBinaryString(rx_data);
        elapsed_time = System.currentTimeMillis() - time;
        calculateAccuracy();
    }

    private void calculateAccuracy() {
        JaroWinklerSimilarity jaroWinklerSimilarity = new JaroWinklerSimilarity();
        accuracy = rx_data != null && !rx_data.isEmpty()
                   ? jaroWinklerSimilarity.apply(tx_data_bin, rx_data_bin)
                   : 0;
    }

    private String textToBinaryString(String text) {
        if (text == null) return "";
        StringBuilder s = new StringBuilder();
        for (byte b : BitSet.valueOf(text.getBytes(StandardCharsets.UTF_8)).toByteArray()) {
            s.append(String.format("%8s",
                    Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
        }
        return s.toString();
    }

}
