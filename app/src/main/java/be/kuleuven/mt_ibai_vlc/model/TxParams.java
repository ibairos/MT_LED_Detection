package be.kuleuven.mt_ibai_vlc.model;

import be.kuleuven.mt_ibai_vlc.common.Enums;

public class TxParams {

    private String txData;
    private Enums.TX_MODE txMode;

    public TxParams(String txData, Enums.TX_MODE txMode) {
        this.txData = txData;
        this.txMode = txMode;
    }

    public String getTxData() {
        return txData;
    }

    public void setTxData(String txData) {
        this.txData = txData;
    }

    public Enums.TX_MODE getTxMode() {
        return txMode;
    }

    public void setTxMode(Enums.TX_MODE txMode) {
        this.txMode = txMode;
    }
}
