package be.kuleuven.mt_ibai_vlc.events;

import androidx.annotation.Nullable;

import be.kuleuven.mt_ibai_vlc.model.enums.AnalyzerState;
import be.kuleuven.mt_ibai_vlc.model.enums.MicroState;

public interface CustomEventListener {

    void onAnalyzerEvent(AnalyzerState eventCode, @Nullable byte[] info);

    void microStateChanged(MicroState state);

}
