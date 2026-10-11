package com.hostelhub.model;

public record Category(long categoryId, String categoryName, int slaDays) {

    @Override
    public String toString() {
        return categoryName;
    }
}
