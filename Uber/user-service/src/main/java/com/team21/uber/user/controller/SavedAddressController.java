package com.team21.uber.user.controller;

import com.team21.uber.user.model.SavedAddress;
import com.team21.uber.user.model.User;
import com.team21.uber.user.repository.SavedAddressRepository;
import com.team21.uber.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/addresses")
public class SavedAddressController {

    private final SavedAddressRepository savedAddressRepository;
    private final UserRepository userRepository;

    public SavedAddressController(SavedAddressRepository savedAddressRepository,
                                  UserRepository userRepository) {
        this.savedAddressRepository = savedAddressRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<SavedAddress> create(@PathVariable Long userId,
                                               @RequestBody SavedAddress savedAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        savedAddress.setUser(user);

        if (savedAddress.getIsDefault() == null) {
            savedAddress.setDefault(false);
        }

        SavedAddress saved = savedAddressRepository.save(savedAddress);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<SavedAddress>> getAllForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(savedAddressRepository.findByUserId(userId));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(@PathVariable Long userId,
                                       @PathVariable Long addressId) {
        SavedAddress address = savedAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        if (address.getUser() == null || !address.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address does not belong to user");
        }

        savedAddressRepository.delete(address);
        return ResponseEntity.noContent().build();
    }
}