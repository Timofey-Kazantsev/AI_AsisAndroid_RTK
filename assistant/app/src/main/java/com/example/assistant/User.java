package com.example.assistant;

public class User {
    private String email;
    private String displayName;

    public User() {}

    public User(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }
}
