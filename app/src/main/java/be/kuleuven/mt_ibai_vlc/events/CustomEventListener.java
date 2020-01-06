package be.kuleuven.mt_ibai_vlc.events;

import androidx.annotation.Nullable;

import be.kuleuven.mt_ibai_vlc.common.Enums;

public interface CustomEventListener {

    void onAnalyzerEvent(Enums.ANALYZER_STATE eventCode, @Nullable String info);

    void arduinoStateChanged(Enums.ARDUINO_STATE state);

}
