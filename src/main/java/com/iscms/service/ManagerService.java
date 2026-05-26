package com.iscms.service;

import com.iscms.dao.ManagerDAO;
import com.iscms.dao.ManagerDAOImpl;
import com.iscms.model.Manager;

import java.util.List;

// Service class responsible for manager account management operations
// Used exclusively by the Admin role (UC-AD01)
public class ManagerService {

    private final ManagerDAO managerDAO;

    // Default constructor — creates concrete DAO implementation
    public ManagerService() {
        this.managerDAO = new ManagerDAOImpl();
    }

    // Constructor for unit testing — allows injecting a mock DAO object
    public ManagerService(ManagerDAO managerDAO) {
        this.managerDAO = managerDAO;
    }

    // Returns all manager records — used in admin dashboard manager list
    public List<Manager> getAllManagers() {
        return managerDAO.findAll();
    }
    public String validateManagerForRegistration(String fullName,
                                                 String username,
                                                 String email,
                                                 String password,
                                                 String role) {
        if (fullName == null || fullName.isBlank()) return "Full name is required.";
        if (username == null || username.isBlank()) return "Username is required.";
        if (email == null || email.isBlank())       return "Email is required.";
        if (password == null || password.isBlank()) return "Password is required.";
        if (password.length() < 8)                  return "Password must be at least 8 characters.";
        if (role == null || (!"MANAGER".equals(role) && !"ADMIN".equals(role)))
            return "Role must be MANAGER or ADMIN.";

        // Uniqueness — DB lookup for email. Username uniqueness is enforced by the
        // DB schema (unique constraint); duplicate inserts surface as exceptions
        // that the controller catches and renders.
        if (managerDAO.findByEmail(email.trim()).isPresent())
            return "Email is already in use by another account.";

        return null;
    }
    // Adds a new manager account
    // Hashes the plain text password before storing — BCrypt with cost factor 12
    // Password is never stored as plain text
    public void addManager(Manager manager) {
        manager.setPassword(AuthService.hashPassword(manager.getPassword()));
        managerDAO.insert(manager);
    }

    // Removes a manager account permanently
    // Step 1: Nullify created_by FK on all events created by this manager
    //         (required to avoid FK constraint violation on delete)
    // Step 2: Delete the manager record
    // Order is critical — reversing these steps would cause a runtime error
    public void removeManager(int managerId) {
        managerDAO.nullifyManagerEvents(managerId);
        managerDAO.delete(managerId);
    }

    // Locks or unlocks a manager account
    // true = locked (cannot log in), false = unlocked
    public void setLockStatus(int managerId, boolean locked) {
        managerDAO.updateLockStatus(managerId, locked);
    }

    // Resets the password for a manager account
    // Hashes the new password before storing — plain text never saved
    public void resetManagerPassword(int managerId, String newPassword) {
        String hashed = AuthService.hashPassword(newPassword);
        managerDAO.updatePassword(managerId, hashed);
    }
}