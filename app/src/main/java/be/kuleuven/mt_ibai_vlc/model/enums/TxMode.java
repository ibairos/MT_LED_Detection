package be.kuleuven.mt_ibai_vlc.model.enums;

public enum TxMode {
    ARDUINO_ANDROID, ANDROID_ARDUINO;

    public static TxMode getDefault() {
        return ARDUINO_ANDROID;
    }
}
