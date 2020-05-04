package be.kuleuven.mt_ibai_vlc.common;

import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Hamming74 {

    private final Map<Integer, Integer> errorMapper = new HashMap<>();
    private final SimpleMatrix g = new SimpleMatrix(
            new double[][]{
                    {1, 0, 0, 0, 1, 1, 1},
                    {0, 1, 0, 0, 0, 1, 1},
                    {0, 0, 1, 0, 1, 0, 1},
                    {0, 0, 0, 1, 1, 1, 0}
            });
    private final SimpleMatrix h = new SimpleMatrix(
            new double[][]{
                    {1, 0, 1, 1, 1, 0, 0},
                    {1, 1, 0, 1, 0, 1, 0},
                    {1, 1, 1, 0, 0, 0, 1}
            });
    private final SimpleMatrix r = new SimpleMatrix(
            new double[][]{
                    {1, 0, 0, 0, 0, 0, 0},
                    {0, 1, 0, 0, 0, 0, 0},
                    {0, 0, 1, 0, 0, 0, 0},
                    {0, 0, 0, 1, 0, 0, 0}
            });

    public Hamming74() {
        errorMapper.put(7, 0);
        errorMapper.put(3, 1);
        errorMapper.put(5, 2);
        errorMapper.put(6, 3);
        errorMapper.put(4, 4);
        errorMapper.put(2, 5);
        errorMapper.put(1, 6);
    }

    public byte[] encodeByteArray(byte[] in) {
        List<Double> inAsIntList = binToList(in);

        List<Integer> generated = new ArrayList<>();

        for (int i = 0; i < inAsIntList.size() / 4; i++) {
            generated.addAll(IntStream.of(encode(inAsIntList.subList(i * 4, (i + 1) * 4))).boxed()
                    .collect(Collectors.toList()));
        }
        for (int i = 0; i < generated.size() % 8; i++) {
            generated.add(0);
        }

        return listToBin(generated);
    }

    public byte[] decodeByteArray(byte[] encoded) {
        List<Double> encodedAsIntList = binToList(encoded);

        List<Integer> decodedBinData = new ArrayList<>();
        for (int i = 0; i < encodedAsIntList.size() / 7; i++) {
            decodedBinData.addAll(IntStream
                    .of(decodeAndCorrect(encodedAsIntList.subList(i * 7, (i + 1) * 7))).boxed()
                    .collect(Collectors.toList()));
        }

        return listToBin(decodedBinData);
    }

    private int[] encode(List<Double> original) {
        SimpleMatrix m = new SimpleMatrix(
                new double[][]{original.stream().mapToDouble(Double::doubleValue).toArray()}
        );
        return mulMatrixes(m, g);
    }

    private int[] decodeAndCorrect(List<Double> encoded) {

        int[] decodedParity = decode(encoded);
        if (decodedParity[0] == 0 && decodedParity[1] == 0 && decodedParity[2] == 0) {
            return result(encoded);
        } else {
            return result(correct(encoded, decodedParity));
        }

    }

    private int[] decode(List<Double> encoded) {
        SimpleMatrix m = new SimpleMatrix(
                new double[][]{encoded.stream().mapToDouble(Double::doubleValue).toArray()}
        ).transpose();
        return mulMatrixes(h, m);
    }

    private int[] result(List<Double> encoded) {
        SimpleMatrix m = new SimpleMatrix(
                new double[][]{encoded.stream().mapToDouble(Double::doubleValue).toArray()}
        ).transpose();
        return mulMatrixes(r, m);
    }

    private List<Double> correct(List<Double> encoded, int[] decodedParity) {
        int errorIndex =
                errorMapper.get(decodedParity[0] + decodedParity[1] * 2 + decodedParity[2] * 4);
        return flipBit(encoded, errorIndex);
    }

    private List<Double> flipBit(List<Double> array, int index) {
        array.set(index, array.get(index) == 0.0 ? 1.0 : 0.0);
        return array;
    }

    private int[] mulMatrixes(SimpleMatrix m1, SimpleMatrix m2) {
        SimpleMatrix res = m1.mult(m2);

        int[] out = new int[res.getNumElements()];

        for (int i = 0; i < res.getNumElements(); i++) {
            out[i] = (int) (res.get(i) % 2);
        }

        return out;
    }

    private List<Double> binToList(byte[] in2) {
        StringBuilder s = new StringBuilder();
        for (byte b : in2) {
            s.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
        }
        return Arrays.stream(s.toString().split("")).map(Double::parseDouble)
                .collect(Collectors.toList());
    }

    private byte[] listToBin(List<Integer> inputList) {
        int decodedArrayLen = inputList.size() / 8;
        byte[] outputByteArray = new byte[decodedArrayLen];
        for (int i = 0; i < decodedArrayLen; i++) {
            String binaryString = Arrays.toString(inputList.subList(i * 8, (i + 1) * 8).toArray())
                    .replaceAll(", ", "").substring(1, 9);
            outputByteArray[i] = (byte) Integer.parseInt(binaryString, 2);
        }
        return outputByteArray;
    }

}