package tk.porrazirauki.mt_leddetection.events;

import androidx.annotation.Nullable;

public interface CustomEventListener {

    void onEvent(String eventCode, @Nullable String info);

}
