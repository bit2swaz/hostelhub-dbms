package com.hostelhub.model;

/** A room with its derived occupancy (from V_ROOM_OCCUPANCY). */
public record Room(long blockId, String blockName, String roomNo, int floorNo, RoomType roomType,
        int capacity, int occupancy) {

    public int vacancies() {
        return capacity - occupancy;
    }
}
