package com.iscms;

import com.iscms.dao.ManagerDAO;
import com.iscms.model.Manager;
import com.iscms.service.ManagerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Unit tests for ManagerService — covers manager CRUD and password operations
// Uses Mockito to mock ManagerDAO interface — no real DB calls
@ExtendWith(MockitoExtension.class)
public class ManagerServiceTest {

    // Mocked DAO interface — injected into ManagerService via constructor
    @Mock private ManagerDAO managerDAO;

    private ManagerService managerService;

    @BeforeEach
    void setUp() {
        managerService = new ManagerService(managerDAO);
    }

    // addManager must hash the plain text password before calling DAO insert
    @Test
    void addManager_hashesPasswordBeforeInsert() {
        Manager m = new Manager();
        m.setFullName("Test Manager");
        m.setEmail("test@test.com");
        m.setPassword("plainpassword");
        m.setRole("MANAGER");

        managerService.addManager(m);

        // Password passed to DAO must be a BCrypt hash starting with $2a$
        verify(managerDAO).insert(argThat(manager ->
                manager.getPassword().startsWith("$2a$")
        ));
    }

    // resetManagerPassword must hash the new password before calling updatePassword
    @Test
    void resetManagerPassword_hashesAndCallsDAO() {
        managerService.resetManagerPassword(1, "newpassword");

        verify(managerDAO).updatePassword(eq(1),
                argThat(hash -> hash.startsWith("$2a$")));
    }

    // removeManager must nullify FK references in event table BEFORE deleting the manager
    // If delete is called first, FK constraint violation would occur
    @Test
    void removeManager_nullifiesEventsAndCallsDelete() {
        managerService.removeManager(5);

        // Both must be called — order is critical
        verify(managerDAO).nullifyManagerEvents(5);
        verify(managerDAO).delete(5);
    }

    // setLockStatus must call updateLockStatus with correct arguments
    @Test
    void setLockStatus_callsDAO() {
        managerService.setLockStatus(3, true);
        verify(managerDAO).updateLockStatus(3, true);
    }

    // getAllManagers must return the list provided by DAO
    @Test
    void getAllManagers_returnsListFromDAO() {
        Manager m = new Manager();
        m.setManagerId(1);
        m.setFullName("Test");
        when(managerDAO.findAll()).thenReturn(List.of(m));

        List<Manager> result = managerService.getAllManagers();

        assertEquals(1, result.size());
        assertEquals("Test", result.get(0).getFullName());
    }

    // ============================================================
    // validateManagerForRegistration — UI-form validation rules
    // Returns null on success, error message String on failure.
    // ============================================================

    @Test
    void validateManagerForRegistration_validInput_returnsNull() {
        when(managerDAO.findByEmail("new@isc.com")).thenReturn(java.util.Optional.empty());
        String result = managerService.validateManagerForRegistration(
                "New Manager", "newmgr", "new@isc.com", "password123", "MANAGER");
        assertNull(result);
    }

    @Test
    void validateManagerForRegistration_blankFullName_returnsError() {
        String result = managerService.validateManagerForRegistration(
                "  ", "newmgr", "new@isc.com", "password123", "MANAGER");
        assertEquals("Full name is required.", result);
    }

    @Test
    void validateManagerForRegistration_shortPassword_returnsError() {
        String result = managerService.validateManagerForRegistration(
                "New Manager", "newmgr", "new@isc.com", "short", "MANAGER");
        assertEquals("Password must be at least 8 characters.", result);
    }

    @Test
    void validateManagerForRegistration_invalidRole_returnsError() {
        String result = managerService.validateManagerForRegistration(
                "New Manager", "newmgr", "new@isc.com", "password123", "TRAINER");
        assertEquals("Role must be MANAGER or ADMIN.", result);
    }

    @Test
    void validateManagerForRegistration_duplicateEmail_returnsError() {
        Manager existing = new Manager();
        existing.setEmail("dup@isc.com");
        when(managerDAO.findByEmail("dup@isc.com")).thenReturn(java.util.Optional.of(existing));

        String result = managerService.validateManagerForRegistration(
                "New Manager", "newmgr", "dup@isc.com", "password123", "MANAGER");
        assertEquals("Email is already in use by another account.", result);
    }

    @Test
    void validateManagerForRegistration_adminRole_returnsNull() {
        when(managerDAO.findByEmail("admin@isc.com")).thenReturn(java.util.Optional.empty());
        String result = managerService.validateManagerForRegistration(
                "New Admin", "newadm", "admin@isc.com", "password123", "ADMIN");
        assertNull(result);
    }
}