package be.kuleuven.mt_ibai_vlc.network.firebase;

import android.app.Activity;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import be.kuleuven.mt_ibai_vlc.R;
import be.kuleuven.mt_ibai_vlc.common.Enums;
import be.kuleuven.mt_ibai_vlc.events.CustomEventListener;
import be.kuleuven.mt_ibai_vlc.model.LogItem;

import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.ANDROID;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.ARDUINO;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.COMMON;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.LOGS;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.RESULT;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.STATE;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.TX_DATA;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.TX_MODE;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.VARIABLES;

public class FirebaseInterface {

    private static final String TAG = "FirebaseInterface";

    private Activity activity;

    private DatabaseReference myRef;

    private Enums.ANDROID_STATE androidState;
    private String androidResult;
    private Enums.ARDUINO_STATE arduinoState;
    private String arduinoResult;

    private String txData;
    private Enums.TX_MODE txMode;

    public FirebaseInterface(Activity activity) {
        this.activity = activity;
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        myRef = database.getReference();

        myRef.child(VARIABLES).child(ANDROID).child(STATE)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        androidState = !((String) Objects.requireNonNull(dataSnapshot.getValue()))
                                .isEmpty() ?
                                       Enums.ANDROID_STATE.valueOf((String) dataSnapshot.getValue())
                                           :
                                       Enums.ANDROID_STATE.getDefault();
                        Log.d(TAG, "AndroidState: " + androidState.toString());
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value AndroidState");
                    }
                });

        myRef.child(VARIABLES).child(ANDROID).child(RESULT)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        androidResult = (String) dataSnapshot.getValue();
                        Log.d(TAG, "AndroidResult: " + androidResult);
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value AndroidResult");
                    }
                });

        myRef.child(VARIABLES).child(ARDUINO).child(STATE)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        arduinoState = !((String) Objects.requireNonNull(dataSnapshot.getValue()))
                                .isEmpty() ?
                                       Enums.ARDUINO_STATE.valueOf((String) dataSnapshot.getValue())
                                           :
                                       Enums.ARDUINO_STATE.getDefault();
                        ((CustomEventListener) activity).arduinoStateChanged(arduinoState);
                        Log.d(TAG, "ArduinoState: " + androidState.toString());
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value ArduinoState");
                    }
                });

        myRef.child(VARIABLES).child(COMMON).child(TX_DATA)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        txData = (String) dataSnapshot.getValue();
                        Log.d(TAG, "TxData: " + txData);
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value TxData");
                    }
                });

        myRef.child(VARIABLES).child(COMMON).child(TX_MODE)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        txMode = !((String) Objects.requireNonNull(dataSnapshot.getValue()))
                                .isEmpty() ? Enums.TX_MODE.valueOf((String) dataSnapshot.getValue())
                                           : Enums.TX_MODE.getDefault();
                        txMode = Enums.TX_MODE.valueOf((String) dataSnapshot.getValue());
                        Log.d(TAG, "TxMode: " + txMode);
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value TxMode");
                    }
                });
    }

    public Enums.ANDROID_STATE getAndroidState() {
        return androidState;
    }

    public void setAndroidState(Enums.ANDROID_STATE androidState) {
        this.androidState = androidState;
        myRef.child(VARIABLES).child(ANDROID).child(STATE)
                .setValue(androidState.toString());
        ((TextView) activity.findViewById(R.id.txStateTextView)).setText(androidState.toString());
    }

    public String getAndroidResult() {
        return androidResult;
    }

    public void setAndroidResult(String androidResult) {
        this.androidResult = androidResult;
        myRef.child(VARIABLES).child(ANDROID).child(RESULT)
                .setValue(androidResult);
    }

    public Enums.ARDUINO_STATE getArduinoState() {
        return arduinoState;
    }

    public void setArduinoState(Enums.ARDUINO_STATE arduinoState) {
        this.arduinoState = arduinoState;
        myRef.child(VARIABLES).child(ARDUINO).child(STATE)
                .setValue(arduinoState.toString());
    }

    public String getArduinoResult() {
        return arduinoResult;
    }

    public void setArduinoResult(String arduinoResult) {
        this.arduinoResult = arduinoResult;
        myRef.child(VARIABLES).child(ANDROID).child(RESULT)
                .setValue(arduinoResult);
    }

    public String getTxData() {
        return txData;
    }

    public void setTxData(String txData) {
        this.txData = txData;
        myRef.child(VARIABLES).child(COMMON).child(TX_DATA)
                .setValue(txData);
    }

    public Enums.TX_MODE getTxMode() {
        return txMode;
    }

    public void setTxMode(Enums.TX_MODE txMode) {
        this.txMode = txMode;
        myRef.child(VARIABLES).child(COMMON).child(TX_MODE)
                .setValue(txMode);
    }

    public void pushLog(LogItem logItem) {
        String key = myRef.child(LOGS).push().getKey();
        Map<String, Object> postValues = logItem.toMap();

        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put("/" + LOGS + "/" + key, postValues);

        myRef.updateChildren(childUpdates);
    }


}
