package com.hostelhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hostelhub.model.Block;
import com.hostelhub.model.Room;
import com.hostelhub.model.RoomType;
import com.hostelhub.support.ServiceTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FR-02: own block details and full CRUD on its rooms. */
class RoomServiceTest extends ServiceTestBase {

    @Test
    void fr02_roomsAreOnlyTheWardensBlockWithLiveOccupancy() {
        List<Room> list = rooms.rooms(wardenA());
        assertThat(list).hasSize(12).allMatch(r -> r.roomNo().startsWith("A-"));
        assertThat(list.stream().mapToInt(Room::occupancy).sum()).isEqualTo(14);
    }

    @Test
    void fr02_updateOwnBlockDetails() {
        Block b = rooms.updateBlock(wardenA(), " A Block East ", "M", 5);
        assertThat(b).isEqualTo(new Block(b.blockId(), "A Block East", "M", 5));
        assertThat(rooms.block(wardenA())).isEqualTo(b);
    }

    @Test
    void fr02_blockUpdateRules() {
        assertServiceRejects(() -> rooms.updateBlock(wardenA(), "A Block", "M", 3),
                "A Block has rooms on floor 3, so it needs at least 4 floors.");
        assertServiceRejects(() -> rooms.updateBlock(wardenA(), "B Block", "M", 4),
                "That block name is already in use.");
        assertServiceRejects(() -> rooms.updateBlock(wardenA(), "A Block", "F", 4),
                "Cannot change the block's gender while it has active allocations (BR-3).");
        assertServiceRejects(() -> rooms.updateBlock(wardenA(), "A Block", "M", 21),
                "Number of floors must be between 1 and 20.");
    }

    @Test
    void fr02_addRoomDerivesCapacityFromType() {
        rooms.addRoom(wardenA(), "a-304", 3, RoomType.DOUBLE);
        assertThat(rooms.rooms(wardenA())).filteredOn(r -> r.roomNo().equals("A-304")).singleElement()
                .satisfies(r -> {
                    assertThat(r.capacity()).isEqualTo(2);
                    assertThat(r.occupancy()).isZero();
                    assertThat(r.floorNo()).isEqualTo(3);
                });
    }

    @Test
    void fr02_addRoomValidation() {
        assertServiceRejects(() -> rooms.addRoom(wardenA(), "A-101", 1, RoomType.DOUBLE),
                "Room A-101 already exists in A Block.");
        assertServiceRejects(() -> rooms.addRoom(wardenA(), "A-401", 4, RoomType.DOUBLE),
                "Floor must be between 0 and 3 for this block.");
        assertServiceRejects(() -> rooms.addRoom(wardenA(), "A 401!", 1, RoomType.DOUBLE),
                "Room number must be 1-6 letters, digits or '-'.");
    }

    @Test
    void fr02_updateRoomTypeAndFloor() throws Exception {
        rooms.updateRoom(wardenA(), "A-002", 0, RoomType.DOUBLE);
        assertThat(queryLong("SELECT capacity FROM room WHERE room_no = 'A-002'")).isEqualTo(2);
    }

    @Test
    void br1_roomCannotShrinkBelowOccupancy() {
        assertServiceRejects(() -> rooms.updateRoom(wardenA(), "A-103", 1, RoomType.SINGLE),
                "Room A-103 has 3 occupants; a SINGLE room holds only 1 (BR-1).");
    }

    @Test
    void fr02_deleteRoomWithoutHistory() throws Exception {
        rooms.deleteRoom(wardenA(), "A-303");
        assertThat(queryLong("SELECT COUNT(*) FROM room WHERE room_no = 'A-303'")).isZero();
    }

    @Test
    void fr02_deleteRoomWithHistoryIsBlocked() {
        assertServiceRejects(() -> rooms.deleteRoom(wardenA(), "A-102"),
                "Room A-102 has allocation or complaint history and cannot be deleted.");
    }

    @Test
    void br4_otherBlocksRoomsAreOutOfReach() {
        assertServiceRejects(() -> rooms.deleteRoom(wardenA(), "B-303"), "Room B-303 was not found in your block.");
        assertServiceRejects(() -> rooms.updateRoom(wardenA(), "B-303", 3, RoomType.DOUBLE),
                "Room B-303 was not found in your block.");
    }
}
