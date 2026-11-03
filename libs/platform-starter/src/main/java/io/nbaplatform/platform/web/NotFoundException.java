package io.nbaplatform.platform.web;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, String id) {
        super(resource + " " + id + " not found");
    }
}
