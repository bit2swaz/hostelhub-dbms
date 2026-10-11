package com.hostelhub.model;

public record Staff(long staffId, String fullName, String phone, long categoryId, String categoryName) {

    @Override
    public String toString() {
        return fullName + " (" + categoryName + ")";
    }
}
