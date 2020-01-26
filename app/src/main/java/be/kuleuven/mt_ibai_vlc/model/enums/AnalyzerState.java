package be.kuleuven.mt_ibai_vlc.model.enums;


public enum AnalyzerState {
    LOADING, WAITING, STARTING, TX_STARTED, TX_ENDED;

    public static AnalyzerState getDefault() {
        return LOADING;
    }
}
