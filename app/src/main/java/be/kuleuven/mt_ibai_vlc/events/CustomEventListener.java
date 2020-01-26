package be.kuleuven.mt_ibai_vlc.events;

import androidx.annotation.Nullable;

import be.kuleuven.mt_ibai_vlc.model.enums.AnalyzerState;
import be.kuleuven.mt_ibai_vlc.model.enums.ArduinoState;

public interface CustomEventListener {

    void onAnalyzerEvent(AnalyzerState eventCode, @Nullable String info);

    void arduinoStateChanged(ArduinoState state);

}
