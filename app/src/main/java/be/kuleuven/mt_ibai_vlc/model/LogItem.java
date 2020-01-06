package be.kuleuven.mt_ibai_vlc.model;

import com.google.firebase.database.Exclude;

import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.util.HashMap;
import java.util.Map;

import be.kuleuven.mt_ibai_vlc.common.Enums;

public class LogItem {

    private double accuracy;

    private long elapsed_time;

    private String tx_data;

    private String rx_data;

    private long time;

    private Enums.TX_MODE tx_mode;

    public LogItem(String tx_data, long time,
                   Enums.TX_MODE tx_mode) {
        this.tx_data = tx_data;
        this.time = time;
        this.tx_mode = tx_mode;
    }

    @Exclude
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("accuracy", accuracy);
        result.put("elapsed_time", elapsed_time);
        result.put("tx_data", tx_data);
        result.put("rx_data", rx_data);
        result.put("time", time);
        result.put("tx_mode", tx_mode);

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

    public Enums.TX_MODE getTx_mode() {
        return tx_mode;
    }

    public void completeLog(String rx_data) {
        this.rx_data = rx_data;
        elapsed_time = System.currentTimeMillis() - time;
        calculateAccuracy();
    }

    private void calculateAccuracy() {
        JaroWinklerDistance jaroWinklerDistance = new JaroWinklerDistance();
        accuracy = jaroWinklerDistance.apply(tx_data, rx_data);
    }

}
