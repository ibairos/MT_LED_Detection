package be.kuleuven.mt_ibai_vlc.common;

public class Enums {

    public enum ANALYZER_STATE {
        LOADING, WAITING, STARTING, TX_STARTED, TX_ENDED;

        public static ANALYZER_STATE getDefault() {
            return LOADING;
        }
    }

    public enum ANDROID_STATE {
        LOADING, WAITING_FOR_START, WAITING_FOR_CHECK_IN_TX, TX_STARTING, TX_STARTED,
        TX_ENDED, WAITING_FOR_CHECK_IN_RX, RX_STARTING, RX_STARTED, RX_ENDED,
        EXIT;

        public static ANDROID_STATE getDefault() {
            return LOADING;
        }
    }

    public enum ARDUINO_STATE {
        LOADING, WAITING_FOR_TX_DATA, RX_STARTING, RX_STARTED, RX_ENDED, WAITING_FOR_CHECK_IN,
        TX_STARTING, TX_STARTED, TX_ENDED, EXIT;

        public static ARDUINO_STATE getDefault() {
            return LOADING;
        }
    }

    public enum TX_MODE {
        ARDUINO_ANDROID, ANDROID_ARDUINO;

        public static TX_MODE getDefault() {
            return ARDUINO_ANDROID;
        }
    }

}

