package com.hostelhub.model;

/** Room types and the capacity each one implies (ck_room_capacity_type). */
public enum RoomType {
    SINGLE(1), DOUBLE(2), TRIPLE(3), QUAD(4);

    private final int capacity;

    RoomType(int capacity) {
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }
}
