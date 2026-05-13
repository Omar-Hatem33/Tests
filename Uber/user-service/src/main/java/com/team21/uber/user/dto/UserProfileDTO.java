package com.team21.uber.user.dto;


import java.util.List;
import java.util.Map;

public class UserProfileDTO {

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private Map<String, Object> preferences;
    private List<SavedAddressDTO> savedAddresses;
    private Integer totalAddresses;

    public UserProfileDTO(Long userId, String name, String email, String phone,
                          Map<String, Object> preferences,
                          List<SavedAddressDTO> savedAddresses,
                          Integer totalAddresses) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.preferences = preferences;
        this.savedAddresses = savedAddresses;
        this.totalAddresses = totalAddresses;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Map<String, Object> getPreferences() { return preferences; }
    public List<SavedAddressDTO> getSavedAddresses() { return savedAddresses; }
    public Integer getTotalAddresses() { return totalAddresses; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long userId;
        private String name;
        private String email;
        private String phone;
        private Map<String, Object> preferences;
        private List<SavedAddressDTO> savedAddresses;
        private Integer totalAddresses;

        public Builder userId(Long v) { this.userId = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder email(String v) { this.email = v; return this; }
        public Builder phone(String v) { this.phone = v; return this; }
        public Builder preferences(Map<String, Object> v) { this.preferences = v; return this; }
        public Builder savedAddresses(List<SavedAddressDTO> v) { this.savedAddresses = v; return this; }
        public Builder totalAddresses(Integer v) { this.totalAddresses = v; return this; }

        public UserProfileDTO build() {
            return new UserProfileDTO(userId, name, email, phone, preferences, savedAddresses, totalAddresses);
        }
    }
}
