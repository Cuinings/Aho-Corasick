package com.cn.ac.internal;

public final class IntArrayQueue {
    private int[] elements;
    private int head;
    private int tail;
    private int count;

    public IntArrayQueue(int initialCapacity) {
        int cap = Math.max(16, initialCapacity);
        elements = new int[cap];
    }

    public void add(int value) {
        if (count == elements.length) {
            expand();
        }
        elements[tail] = value;
        tail = (tail + 1) % elements.length;
        count++;
    }

    public int remove() {
        if (count == 0) {
            throw new IllegalStateException("Queue is empty");
        }
        int val = elements[head];
        head = (head + 1) % elements.length;
        count--;
        return val;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int size() {
        return count;
    }

    private void expand() {
        int[] newElements = new int[elements.length * 2];
        for (int i = 0; i < count; i++) {
            newElements[i] = elements[(head + i) % elements.length];
        }
        elements = newElements;
        head = 0;
        tail = count;
    }
}
