package com.example.assistant;

public class User {
    private String displayName; // Изменено с name на displayName
    private String email;

    // Пустой конструктор для Firebase
    public User() {}

    public User(String displayName, String email) {
        this.displayName = displayName;
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return displayName + " (" + email + ")";
    }
}