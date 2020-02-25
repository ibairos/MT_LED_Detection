package be.kuleuven.mt_ibai_vlc.model;

import com.google.firebase.database.Exclude;

import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.util.HashMap;
import java.util.Map;

import be.kuleuven.mt_ibai_vlc.model.enums.TxMode;


public class LogItem {

    private double accuracy;

    private long elapsed_time;

    private String tx_data;

    private String rx_data;

    private long time;

    private TxMode tx_mode;

    private long tx_rate;

    private long sample_num;

    public LogItem(String tx_data, long time,
                   TxMode tx_mode, long tx_rate, long sample_num) {
        this.tx_data = tx_data;
        this.time = time;
        this.tx_mode = tx_mode;
        this.tx_rate = tx_rate;
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
        result.put("tx_rate", tx_rate);
        result.put("sample_num", sample_num);

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
        elapsed_time = System.currentTimeMillis() - time;
        calculateAccuracy();
    }

    private void calculateAccuracy() {
        JaroWinklerDistance jaroWinklerDistance = new JaroWinklerDistance();
        accuracy =
                rx_data != null && !rx_data.isEmpty() ? jaroWinklerDistance.apply(tx_data, rx_data)
                                                      : 0;
    }

}
