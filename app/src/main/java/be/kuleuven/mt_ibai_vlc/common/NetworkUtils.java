package be.kuleuven.mt_ibai_vlc.common;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.zip.CRC32;

public class NetworkUtils {

    public static final int WORD_SIZE = 8;
    private CRC32 crc;

    public NetworkUtils() {
        crc = new CRC32();
    }

    public BitSet calculateCRC(BitSet sequence) {
        try {
            crc.update(sequence.toByteArray());
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(crc.getValue());
            return BitSet.valueOf(buffer.array()).get(4 * WORD_SIZE, 8 * WORD_SIZE);
        } finally {
            crc.reset();
        }
    }

}
