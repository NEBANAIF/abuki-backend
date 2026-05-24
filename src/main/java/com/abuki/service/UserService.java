package com.abuki.service;

import com.abuki.model.User;
import com.abuki.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    @Autowired private UserRepository  userRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService      jwtService;

    // ── LOGIN ─────────────────────────────────────────────
    @Transactional
    public String login(String email, String rawPassword) {
        User user = userRepo.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Invalid email or password."));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Account is inactive. Contact admin.");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new RuntimeException("Invalid email or password.");
        }

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepo.saveAndFlush(user);

        return jwtService.generateToken(user.getEmail(), user.getRole(), user.getName());
    }

    // ── CREATE USER ───────────────────────────────────────
    @Transactional
    public User create(User req) {
        if (userRepo.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email already registered: " + req.getEmail());
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters.");
        }

        req.setPassword(passwordEncoder.encode(req.getPassword()));
        if (req.getRole()   == null) req.setRole("CASHIER");
        if (req.getStatus() == null) req.setStatus("ACTIVE");

        return userRepo.saveAndFlush(req);
    }

    // ── GET ALL ───────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<User> getAll() {
        return userRepo.findAllByOrderByCreatedAtDesc();
    }

    // ── GET BY ID ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    // ── UPDATE USER ───────────────────────────────────────
    @Transactional
    public User update(Long id, User req) {
        User existing = getById(id);

        // Check email uniqueness if changed
        if (!existing.getEmail().equals(req.getEmail()) && userRepo.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email already in use: " + req.getEmail());
        }

        existing.setName(req.getName());
        existing.setEmail(req.getEmail());
        existing.setRole(req.getRole());
        existing.setStatus(req.getStatus());

        // Only update password if provided
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            if (req.getPassword().length() < 6) {
                throw new RuntimeException("Password must be at least 6 characters.");
            }
            existing.setPassword(passwordEncoder.encode(req.getPassword()));
        }

        return userRepo.saveAndFlush(existing);
    }

    // ── DELETE USER ───────────────────────────────────────
    @Transactional
    public void delete(Long id) {
        if (!userRepo.existsById(id)) {
            throw new RuntimeException("User not found: " + id);
        }
        userRepo.deleteById(id);
        userRepo.flush();
    }

    // ── GET ME (from token email) ─────────────────────────
    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userRepo.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }
}