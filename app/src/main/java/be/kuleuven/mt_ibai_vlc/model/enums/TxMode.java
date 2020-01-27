package be.kuleuven.mt_ibai_vlc.model.enums;

public enum TxMode {
    MICRO_ANDROID, ANDROID_MICRO;

    public static TxMode getDefault() {
        return MICRO_ANDROID;
    }
}
