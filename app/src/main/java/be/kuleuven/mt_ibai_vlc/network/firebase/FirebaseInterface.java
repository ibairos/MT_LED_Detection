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
import be.kuleuven.mt_ibai_vlc.events.CustomEventListener;
import be.kuleuven.mt_ibai_vlc.model.LogItem;
import be.kuleuven.mt_ibai_vlc.model.enums.AndroidState;
import be.kuleuven.mt_ibai_vlc.model.enums.MicroState;
import be.kuleuven.mt_ibai_vlc.model.enums.TxMode;

import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.ANDROID;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.COMMON;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.HAMMING_ENABLED;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.LOGS;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.MICRO;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.ON_OFF_KEYING;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.RESULT;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.SAMPLE_NUM;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.STATE;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.TX_DATA;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.TX_MODE;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.TX_RATE;
import static be.kuleuven.mt_ibai_vlc.network.firebase.FirebaseEndpoints.VARIABLES;

public class FirebaseInterface {

    private static final String TAG = "FirebaseInterface";

    private Activity activity;

    private DatabaseReference myRef;

    private AndroidState androidState;
    private String androidResult;
    private MicroState microState;
    private String microResult;

    private String txData;
    private TxMode txMode;
    private long txRate;
    private long numberOfSamples;
    private Integer distance;
    private boolean hammingEnabled;
    private boolean onOffKeying;


    public FirebaseInterface(Activity activity) {
        this.activity = activity;
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        myRef = database.getReference();

        myRef.child(VARIABLES).child(ANDROID).child(STATE)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        androidState = !((String) Objects.requireNonNull(dataSnapshot.getValue()))
                                .isEmpty() ?
                                       AndroidState.valueOf((String) dataSnapshot.getValue())
                                           :
                                       AndroidState.getDefault();
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

        myRef.child(VARIABLES).child(MICRO).child(STATE)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        microState = !((String) Objects.requireNonNull(dataSnapshot.getValue()))
                                .isEmpty() ?
                                     MicroState.valueOf((String) dataSnapshot.getValue())
                                           :
                                     MicroState.getDefault();
                        ((CustomEventListener) activity).microStateChanged(microState);
                        Log.d(TAG, "MicroState: " + androidState.toString());
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value MicroState");
                    }
                });

        myRef.child(VARIABLES).child(MICRO).child(RESULT)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        microResult = (String) dataSnapshot.getValue();
                        Log.d(TAG, "MicroResult: " + microResult);
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value MicroResult");
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
                                .isEmpty() ? TxMode.valueOf((String) dataSnapshot.getValue())
                                           : TxMode.getDefault();
                        txMode = TxMode.valueOf((String) dataSnapshot.getValue());
                        Log.d(TAG, "TxMode: " + txMode);
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value TxMode");
                    }
                });
        myRef.child(VARIABLES).child(COMMON).child(TX_RATE)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        txRate = (long) dataSnapshot.getValue();
                        Log.d(TAG, "TxRate: " + txRate);
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value TxRate");
                    }
                });
        myRef.child(VARIABLES).child(COMMON).child(SAMPLE_NUM)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        numberOfSamples = (long) dataSnapshot.getValue();
                        Log.d(TAG, "NumberOfSamples: " + numberOfSamples);
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value NumberOfSamples");
                    }
                });
        myRef.child(VARIABLES).child(COMMON).child(HAMMING_ENABLED)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        hammingEnabled = (boolean) dataSnapshot.getValue();
                        Log.d(TAG, "hammingEnabled: " + hammingEnabled);
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value hammingEnabled");
                    }
                });
        myRef.child(VARIABLES).child(COMMON).child(ON_OFF_KEYING)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        onOffKeying = (boolean) dataSnapshot.getValue();
                        Log.d(TAG, "onOffKeying: " + onOffKeying);
                    }

                    @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read value onOffKeying");
                    }
                });
    }

    public AndroidState getAndroidState() {
        return androidState;
    }

    public void setAndroidState(AndroidState androidState) {
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

    public MicroState getMicroState() {
        return microState;
    }

    public void setMicroState(MicroState microState) {
        this.microState = microState;
        myRef.child(VARIABLES).child(MICRO).child(STATE)
                .setValue(microState.toString());
    }

    public String getMicroResult() {
        return microResult;
    }

    public void setMicroResult(String microResult) {
        this.microResult = microResult;
        myRef.child(VARIABLES).child(ANDROID).child(RESULT)
                .setValue(microResult);
    }

    public String getTxData() {
        return txData;
    }

    public void setTxData(String txData) {
        this.txData = txData;
        myRef.child(VARIABLES).child(COMMON).child(TX_DATA)
                .setValue(txData);
    }

    public TxMode getTxMode() {
        return txMode;
    }

    public void setTxMode(TxMode txMode) {
        this.txMode = txMode;
        myRef.child(VARIABLES).child(COMMON).child(TX_MODE)
                .setValue(txMode);
    }

    public long getTxRate() {
        return txRate;
    }

    public void setTxRate(long txRate) {
        this.txRate = txRate;
        myRef.child(VARIABLES).child(COMMON).child(TX_RATE)
                .setValue(txRate);
    }

    public long getNumberOfSamples() {
        return numberOfSamples;
    }

    public void setNumberOfSamples(long numberOfSamples) {
        this.numberOfSamples = numberOfSamples;
        myRef.child(VARIABLES).child(COMMON).child(SAMPLE_NUM)
                .setValue(numberOfSamples);
    }

    public Integer getDistance() {
        return distance;
    }

    public void setDistance(Integer distance) {
        this.distance = distance;
    }

    public boolean isHammingEnabled() {
        return hammingEnabled;
    }

    public void setHammingEnabled(boolean hammingEnabled) {
        this.hammingEnabled = hammingEnabled;
        myRef.child(VARIABLES).child(COMMON).child(HAMMING_ENABLED)
                .setValue(hammingEnabled);
    }

    public boolean isOnOffKeying() {
        return onOffKeying;
    }

    public void setOnOffKeying(boolean onOffKeying) {
        this.onOffKeying = onOffKeying;
        myRef.child(VARIABLES).child(COMMON).child(ON_OFF_KEYING)
                .setValue(onOffKeying);
    }

    public void pushLog(LogItem logItem) {
        String key = myRef.child(LOGS).push().getKey();
        Map<String, Object> postValues = logItem.toMap();

        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put("/" + LOGS + "/" + key, postValues);

        myRef.updateChildren(childUpdates);
    }
}
