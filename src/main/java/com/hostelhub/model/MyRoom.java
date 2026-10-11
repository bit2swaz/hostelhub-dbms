package com.hostelhub.model;

import java.time.LocalDate;
import java.util.List;

/** FR-06: a student's current room and roommates. */
public record MyRoom(String blockName, String roomNo, int floorNo, RoomType roomType, int capacity,
        LocalDate startDate, List<Student> roommates) {
}
