package com.cn.ac.internal;

public final class IntVector {
    private int[] data;
    private int size;

    public IntVector(int initialCapacity) {
        data = new int[Math.max(8, initialCapacity)];
    }

    public void add(int value) {
        if (size == data.length) {
            int newCap = data.length * 2;
            int[] newData = new int[newCap];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }
        data[size++] = value;
    }

    public int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return data[index];
    }

    public void set(int index, int value) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        data[index] = value;
    }

    public int size() {
        return size;
    }

    public int[] toArray() {
        int[] res = new int[size];
        System.arraycopy(data, 0, res, 0, size);
        return res;
    }
}
