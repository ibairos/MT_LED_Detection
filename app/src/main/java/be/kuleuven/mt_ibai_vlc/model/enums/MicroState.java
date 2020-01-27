package be.kuleuven.mt_ibai_vlc.model.enums;

public enum MicroState {
    LOADING, WAITING_FOR_TX_DATA, RX_STARTING, RX_STARTED, RX_ENDED, WAITING_FOR_CHECK_IN,
    TX_STARTING, TX_STARTED, TX_ENDED, EXIT;

    public static MicroState getDefault() {
        return LOADING;
    }
}
