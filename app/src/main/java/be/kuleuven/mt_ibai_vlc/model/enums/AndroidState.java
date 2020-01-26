package be.kuleuven.mt_ibai_vlc.model.enums;

public enum AndroidState {
    LOADING, WAITING_FOR_START, WAITING_FOR_CHECK_IN_TX, TX_STARTING, TX_STARTED,
    TX_ENDED, WAITING_FOR_CHECK_IN_RX, RX_STARTING, RX_STARTED, RX_ENDED,
    EXIT;

    public static AndroidState getDefault() {
        return LOADING;
    }
}
